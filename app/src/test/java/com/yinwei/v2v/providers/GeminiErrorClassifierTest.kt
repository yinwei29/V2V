package com.yinwei.v2v.providers

import org.junit.Assert.*
import org.junit.Test

class GeminiErrorClassifierTest {
    @Test fun knownGoogleReasonsMapToSafeApplicationOwnedEnums() {
        for ((reason, issue) in mapOf("API_KEY_EXPIRED" to ProviderIssue.INVALID_KEY,
            "API_KEY_ANDROID_APP_BLOCKED" to ProviderIssue.KEY_RESTRICTED,
            "SERVICE_DISABLED" to ProviderIssue.SERVICE_DISABLED, "BILLING_DISABLED" to ProviderIssue.BILLING_DISABLED)) {
            val body = """{"error":{"status":"INVALID_ARGUMENT","details":[{"@type":"type.googleapis.com/google.rpc.ErrorInfo","reason":"$reason"}]}}"""
            assertEquals(issue, GeminiErrorClassifier.classify(400, body))
        }
    }
    @Test fun projectAndQuotaFailuresAreNotCalledInvalidKeys() {
        for ((status, issue) in mapOf("UNAVAILABLE" to ProviderIssue.SERVICE_UNAVAILABLE, "FAILED_PRECONDITION" to ProviderIssue.PROJECT_SETUP_REQUIRED,
            "PERMISSION_DENIED" to ProviderIssue.ACCESS_DENIED, "NOT_FOUND" to ProviderIssue.MODEL_UNAVAILABLE,
            "RESOURCE_EXHAUSTED" to ProviderIssue.QUOTA_EXCEEDED)) {
            assertEquals(issue, GeminiErrorClassifier.classify(400, """{"error":{"status":"$status"}}"""))
        }
    }
    @Test fun messagesAndUnknownReasonsCannotBecomeDiagnosticText() {
        for (body in listOf("private invalid JSON", "{}",
            """{"error":{"message":"API_KEY_INVALID private transcript","status":"private-secret","details":[{"@type":"type.googleapis.com/google.rpc.ErrorInfo","reason":"private-secret"}]}}""")) {
            assertEquals(ProviderIssue.UNKNOWN, GeminiErrorClassifier.classify(400, body))
        }
    }
}
