package com.yinwei.v2v.providers

import com.yinwei.v2v.diagnostics.DiagnosticJournal
import com.yinwei.v2v.languages.AppLanguage
import com.yinwei.v2v.transcript.AudioChunk
import com.yinwei.v2v.transcript.wavBytes
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.Base64

/** Dedicated ASR followed by text-only MT. Owned by one serial pipeline worker per session. */
class GeminiTranscribeProvider(
    key: String,
    client: OkHttpClient = providerClient(),
    private val baseUrl: String = "https://generativelanguage.googleapis.com/v1beta/",
    diagnostics: DiagnosticJournal? = null,
    model: GeminiModel = GeminiModel.TRANSCRIBE_FLASH,
) : TranslationProvider {
    private val translator = GeminiProvider(key, client, baseUrl, diagnostics, model)
    private val http = ProviderHttp(client, "x-goog-api-key", key, diagnostics, GeminiErrorClassifier::classify)
    private var asrTokens: Long? = null
    override val description = "Google $ASR_MODEL → ${model.apiId}"
    override val partialSupported = false
    override suspend fun healthCheck() {
        val response = http.execute { Request.Builder().url(baseUrl + "models/$ASR_MODEL").get() }
        parse {
            if (JSONObject(response).getString("name") != "models/$ASR_MODEL")
                throw ProviderFailure(0, "語音辨識專用模型資訊不符；請檢查帳號資格")
        }
        translator.healthCheck()
    }
    override suspend fun transcribe(chunk: AudioChunk, language: String): String {
        require(AppLanguage.validSource(language))
        asrTokens = null
        val config = JSONObject().put("mode", JSONObject().put("type", "verbatim"))
        languageHint(language)?.let { config.put("language_codes", JSONArray().put(it)) }
        val body = JSONObject().put("model", ASR_MODEL).put("store", false).put("stream", false).put("background", false)
            .put("input", JSONArray().put(JSONObject().put("type", "audio").put("mime_type", "audio/wav")
                .put("data", Base64.getEncoder().encodeToString(chunk.wavBytes()))))
            .put("generation_config", JSONObject().put("transcription_config", config))
        val response = http.execute { Request.Builder().url(baseUrl + "interactions")
            .post(body.toString().toRequestBody("application/json".toMediaType())) }
        return parse {
            val root = JSONObject(response)
            if (root.optString("status") != "completed" || (root.has("error") && !root.isNull("error")))
                throw ProviderFailure(0, "語音辨識未完整完成，未產生假字幕")
            val steps = root.getJSONArray("steps")
            if (steps.length() == 0) throw ProviderFailure(0, "語音辨識回應沒有文字區塊")
            val text = buildString {
                for (i in 0 until steps.length()) {
                    val step = steps.getJSONObject(i)
                    if (step.getString("type") != "model_output") throw ProviderFailure(0, "語音辨識回應不是逐字稿，未執行工具")
                    val content = step.getJSONArray("content")
                    for (j in 0 until content.length()) {
                        val part = content.getJSONObject(j)
                        if (part.getString("type") != "text" || part.get("text") !is String)
                            throw ProviderFailure(0, "語音辨識文字格式不符")
                        append(part.getString("text"))
                    }
                }
            }.trim()
            if (text.length > 12000) throw ProviderFailure(0, "語音辨識回應過長，未截斷或播報")
            val usage = root.optJSONObject("usage")
            asrTokens = if (usage?.has("total_tokens") == true && usage.opt("total_tokens") is Number)
                usage.getLong("total_tokens").takeIf { it >= 0 } else null
            text
        }
    }
    override suspend fun translate(text: String, language: String, targetLanguage: String): TranslatedText =
        translateInContext(text, language, targetLanguage, TranslationContext.EMPTY)
    override suspend fun translateInContext(text: String, language: String, targetLanguage: String, context: TranslationContext): TranslatedText {
        val sourceTokens = asrTokens
        asrTokens = null
        val result = translator.translateInContext(text, language, targetLanguage, context)
        val mtTokens = result.tokens
        return result.copy(tokens = if (sourceTokens != null && mtTokens != null && sourceTokens <= Long.MAX_VALUE - mtTokens)
            sourceTokens + mtTokens else null)
    }
    private inline fun <T> parse(block: () -> T): T = try { block() }
        catch (e: ProviderFailure) { throw e }
        catch (_: Exception) { throw ProviderFailure(0, "語音辨識 API 回應格式不符；請檢查模型與 API 版本") }
    companion object {
        const val ASR_MODEL = "gemini-3.5-transcribe"
        // Official Transcribe BCP-47 catalog differs from TTS's locale catalog.
        fun languageHint(language: String): String? = when (language) {
            "auto" -> null
            "zh-TW", "zh-CN" -> "cmn-Hans-CN"
            "es" -> "es-419"
            "ar" -> "ar-EG"
            else -> requireNotNull(AppLanguage.fromCode(language)).voiceTag
        }
    }
}
