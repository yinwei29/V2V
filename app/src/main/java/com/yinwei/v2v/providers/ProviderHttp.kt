package com.yinwei.v2v.providers

import com.yinwei.v2v.diagnostics.*
import kotlinx.coroutines.*
import okhttp3.*
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume

internal fun providerClient(): OkHttpClient = OkHttpClient.Builder().connectTimeout(15, TimeUnit.SECONDS)
    .readTimeout(30, TimeUnit.SECONDS).callTimeout(40, TimeUnit.SECONDS)
    .followRedirects(false).followSslRedirects(false).retryOnConnectionFailure(false).build()

/** Shared bounded, cancellable HTTP; error bodies and credential values never enter diagnostics or UI. */
internal class ProviderHttp(private val client: OkHttpClient, private val authHeader: String,
    private val authValue: String, private val diagnostics: DiagnosticJournal?,
    private val errorClassifier: ((Int, String) -> ProviderIssue)? = null) {
    suspend fun execute(build: () -> Request.Builder): String = request(build)
    private suspend fun request(build: () -> Request.Builder): String {
        repeat(2) { attempt ->
            currentCoroutineContext().ensureActive()
            val request = build().header(authHeader, authValue).build()
            val began = System.nanoTime()
            val response = try { client.newCall(request).awaitResult() }
                catch (e: CancellationException) { throw e }
                catch (e: IOException) {
                    diagnostics?.record(DiagnosticEvent.PROVIDER_FAILURE, error = e.javaClass)
                    throw ProviderFailure(0, "網路斷線或逾時；不會偷偷切換離線或其他供應商")
                }
            diagnostics?.record(DiagnosticEvent.PROVIDER_STATE, metrics = mapOf(Metric.HTTP_STATUS to response.status,
                Metric.LATENCY_MS to (System.nanoTime() - began) / 1_000_000))
            if (response.status in 200..299) return response.body
            val issue = errorClassifier?.invoke(response.status, response.body)
            if (issue != null) diagnostics?.record(DiagnosticEvent.PROVIDER_FAILURE,
                metrics = mapOf(Metric.HTTP_STATUS to response.status), tags = mapOf(Tag.PROVIDER_REASON to issue))
            if (attempt == 0 && (response.status == 429 || response.status in 500..599))
                delay((response.retryAfter?.toLongOrNull() ?: 1).coerceIn(1, 8) * 1000)
            else throw ProviderFailure(response.status, issue?.takeUnless { it == ProviderIssue.UNKNOWN }?.let { "${response.status}：${it.explanation}（${it.name}）" } ?: when (response.status) {
                400 -> "400：請檢查 API Key、專案資格與模型設定"
                401 -> "401：API Key 無效或已撤銷，請重新輸入"
                403 -> "403：API Key 或專案沒有此模型的存取權限"
                404 -> "404：模型或 API 不存在，請檢查供應商設定"
                429 -> "429：速率或額度限制；已停止自動重試，請檢查帳號用量"
                in 500..599 -> "供應商服務暫時失敗；已達重試上限"
                else -> "供應商 HTTP ${response.status}：請檢查帳號、檔案與模型設定"
            }, issue)
        }
        error("unreachable")
    }
    private data class HttpResult(val status: Int, val body: String, val retryAfter: String?)
    /** Cancellation owns the Call through response-body reading, not just receipt of headers. */
    private suspend fun Call.awaitResult(): HttpResult = suspendCancellableCoroutine { continuation ->
        continuation.invokeOnCancellation { cancel() }
        enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                if (continuation.isActive) continuation.resumeWith(Result.failure(e))
            }
            override fun onResponse(call: Call, response: Response) {
                try {
                    val result = response.use { received ->
                        var text = ""
                        if (received.isSuccessful || errorClassifier != null) {
                            val body = received.body ?: throw ProviderFailure(0, "供應商回應為空")
                            val maxBytes = if (received.isSuccessful) 1024 * 1024 else 64 * 1024
                            text = body.byteStream().use { stream ->
                                val out = ByteArrayOutputStream(); val buffer = ByteArray(8192)
                                while (continuation.isActive) {
                                    val count = stream.read(buffer, 0, minOf(buffer.size, maxBytes - out.size() + 1))
                                    if (count < 0) break
                                    if (out.size() + count > maxBytes) {
                                        if (received.isSuccessful) throw ProviderFailure(0, "供應商回應過大")
                                        // Oversized error: discard it, preserving only HTTP status.
                                        out.reset(); break
                                    }
                                    out.write(buffer, 0, count)
                                }
                                out.toString("UTF-8")
                            }
                        }
                        HttpResult(received.code, text, received.header("Retry-After"))
                    }
                    if (continuation.isActive) continuation.resume(result)
                } catch (e: Exception) {
                    if (continuation.isActive) continuation.resumeWith(Result.failure(e))
                }
            }
        })
    }
}
