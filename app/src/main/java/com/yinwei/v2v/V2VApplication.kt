package com.yinwei.v2v

import android.app.Application
import com.yinwei.v2v.history.HistoryStore
import java.io.File
import com.yinwei.v2v.session.SessionRepository
import com.yinwei.v2v.session.SessionController
import com.yinwei.v2v.settings.SettingsStore
import com.yinwei.v2v.security.ApiKeyStore
import com.yinwei.v2v.diagnostics.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin
import org.koin.dsl.module

class V2VApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        // Remove abandoned synthesis output after a previous process termination.
        cacheDir.listFiles()?.filter { it.name.startsWith("tts-") && it.name.endsWith(".wav") }?.forEach { it.delete() }
        val application = startKoin { androidContext(this@V2VApplication); modules(module {
            single { SessionRepository() }
            single { HistoryStore(File(androidContext().noBackupFilesDir, "history/translations.json")) }
            factory { SessionController(get(), get(), get(), get()) }
            single { SettingsStore(get<android.content.Context>()) }
            single { ApiKeyStore(get()) }
            single { Diagnostics(get()) }
        }) }
        val diagnostics = application.koin.get<Diagnostics>()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch { application.koin.get<HistoryStore>().load() }
        diagnostics.journal.enabled = false
        CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
            application.koin.get<SettingsStore>().diagnosticsEnabled.collect { enabled ->
                diagnostics.journal.enabled = enabled
                if (enabled) {
                    diagnostics.journal.record(DiagnosticEvent.APP_START)
                    diagnostics.session(application.koin.get<SessionRepository>().state.value, force = true)
                    diagnostics.mix(application.koin.get<SettingsStore>().settings.first())
                }
            }
        }
        CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
            application.koin.get<SessionRepository>().state.collect { diagnostics.session(it) }
        }
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            diagnostics.journal.record(DiagnosticEvent.CRASH, error = error.javaClass)
            if (thread.name != "v2v-diagnostics") runCatching { diagnostics.journal.flush() }
            previous?.uncaughtException(thread, error)
        }
    }
}
