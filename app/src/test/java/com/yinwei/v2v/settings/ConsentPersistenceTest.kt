package com.yinwei.v2v.settings

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.yinwei.v2v.providers.CloudProvider
import com.yinwei.v2v.providers.GeminiModel
import com.yinwei.v2v.languages.LanguagePair
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.nio.file.Files

class ConsentPersistenceTest {
    private suspend fun open(file: File, block: suspend (SettingsStore) -> Unit) {
        val job = SupervisorJob()
        val store = PreferenceDataStoreFactory.create(scope = CoroutineScope(job + Dispatchers.IO), produceFile = { file })
        try { block(SettingsStore(store)) } finally { job.cancelAndJoin() }
    }
    @Test fun explicitConsentSurvivesRestartAndProviderChanges() = runBlocking {
        val directory = Files.createTempDirectory("v2v-consent").toFile()
        val file = File(directory, "settings.preferences_pb")
        try {
            open(file) { store ->
                assertFalse(store.cloudConsent.first())
                store.saveCloudProvider(CloudProvider.GEMINI)
                store.saveCloudConsent(true)
                assertTrue(store.cloudConsent.first())
            }
            open(file) { store ->
                assertEquals(CloudProvider.GEMINI, store.cloudProvider.first())
                assertTrue(store.cloudConsent.first())
                store.saveCloudProvider(CloudProvider.OPENAI)
                assertTrue(store.cloudConsent.first())
            }
            open(file) { store -> assertEquals(CloudProvider.OPENAI, store.cloudProvider.first()); assertTrue(store.cloudConsent.first()) }
        } finally { directory.deleteRecursively() }
    }
    @Test fun sourceAndTargetLanguagesSurviveRestart() = runBlocking {
        val directory = Files.createTempDirectory("v2v-languages").toFile()
        val file = File(directory, "settings.preferences_pb")
        try {
            open(file) { assertEquals(LanguagePair(), it.languages.first()); it.saveLanguages(LanguagePair("auto", "en")) }
            open(file) { assertEquals(LanguagePair("auto", "en"), it.languages.first()) }
        } finally { directory.deleteRecursively() }
    }
    @Test fun selectedGeminiModelSurvivesReopeningSettings() = runBlocking {
        val directory = Files.createTempDirectory("v2v-model").toFile()
        val file = File(directory, "settings.preferences_pb")
        try {
            open(file) { assertEquals(GeminiModel.DEFAULT, it.geminiModel.first()); it.saveGeminiModel(GeminiModel.FLASH) }
            open(file) { assertEquals(GeminiModel.FLASH, it.geminiModel.first()) }
        } finally { directory.deleteRecursively() }
    }
    @Test fun manualRevocationIsRetainedAfterRestart() = runBlocking {
        val directory = Files.createTempDirectory("v2v-consent-revoke").toFile()
        val file = File(directory, "settings.preferences_pb")
        try {
            open(file) { it.saveCloudConsent(true) }
            open(file) { assertTrue(it.cloudConsent.first()); it.saveCloudConsent(false) }
            open(file) { assertFalse(it.cloudConsent.first()) }
        } finally { directory.deleteRecursively() }
    }
}
