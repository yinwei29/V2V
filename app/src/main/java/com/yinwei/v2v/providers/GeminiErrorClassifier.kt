package com.yinwei.v2v.providers

import org.json.JSONObject

/** Only these application-owned constants may leave the transient Google error-body parser. */
enum class ProviderIssue(val explanation: String) {
    INVALID_KEY("Gemini API Key 無效、已過期或已撤銷，請重新設定"),
    KEY_RESTRICTED("Gemini Key 的應用程式、IP 或 API 限制不允許此請求，請檢查 Google AI Studio／Cloud 設定"),
    SERVICE_DISABLED("專案尚未啟用 Gemini Generative Language API，請檢查 Google 專案設定"),
    BILLING_DISABLED("Google 專案帳務未啟用，請檢查供應商帳務設定"),
    PROJECT_SETUP_REQUIRED("Gemini 專案資格未符合要求，請檢查地區、方案及帳務設定"),
    INVALID_REQUEST("Gemini 請求參數格式不相容，請更新 App 並匯出 Log 協助修正"),
    MODEL_UNAVAILABLE("目前 Gemini 模型不存在或此專案無法使用，請檢查模型資格"),
    ACCESS_DENIED("Gemini Key 或專案沒有此 API 的存取權限"),
    QUOTA_EXCEEDED("Gemini 速率或額度限制，請檢查帳號用量"),
    UNKNOWN("Gemini 請求失敗，請匯出 Log 並確認 Key、專案及模型設定"),
}

/** Never returns provider messages, metadata, keys, URLs, transcripts, or unrecognized strings. */
internal object GeminiErrorClassifier {
    fun classify(httpStatus: Int, body: String): ProviderIssue {
        return try {
            val error = JSONObject(body).optJSONObject("error") ?: return ProviderIssue.UNKNOWN
            val details = error.optJSONArray("details")
            for (i in 0 until (details?.length() ?: 0)) {
                val detail = details?.optJSONObject(i) ?: continue
                if (detail.optString("@type") != "type.googleapis.com/google.rpc.ErrorInfo") continue
                val issue = when (detail.optString("reason")) {
                    "API_KEY_INVALID", "API_KEY_EXPIRED", "API_KEY_NOT_FOUND" -> ProviderIssue.INVALID_KEY
                    "API_KEY_SERVICE_BLOCKED", "API_KEY_HTTP_REFERRER_BLOCKED", "API_KEY_IP_ADDRESS_BLOCKED",
                    "API_KEY_ANDROID_APP_BLOCKED", "API_KEY_IOS_APP_BLOCKED" -> ProviderIssue.KEY_RESTRICTED
                    "SERVICE_DISABLED" -> ProviderIssue.SERVICE_DISABLED
                    "BILLING_DISABLED" -> ProviderIssue.BILLING_DISABLED
                    else -> null
                }
                if (issue != null) return issue
            }
            when (error.optString("status")) {
                "INVALID_ARGUMENT" -> if (httpStatus == 400) ProviderIssue.INVALID_REQUEST else ProviderIssue.UNKNOWN
                "FAILED_PRECONDITION" -> ProviderIssue.PROJECT_SETUP_REQUIRED
                "UNAUTHENTICATED" -> ProviderIssue.INVALID_KEY
                "PERMISSION_DENIED" -> ProviderIssue.ACCESS_DENIED
                "NOT_FOUND" -> ProviderIssue.MODEL_UNAVAILABLE
                "RESOURCE_EXHAUSTED" -> ProviderIssue.QUOTA_EXCEEDED
                else -> ProviderIssue.UNKNOWN
            }
        } catch (_: Exception) { ProviderIssue.UNKNOWN }
    }
}
