package com.yinwei.v2v

import android.app.Application
import com.yinwei.v2v.session.SessionRepository
import com.yinwei.v2v.session.SessionController
import com.yinwei.v2v.settings.SettingsStore
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin
import org.koin.dsl.module

class V2VApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        startKoin { androidContext(this@V2VApplication); modules(module {
            single { SessionRepository() }
            factory { SessionController(get(), get()) }
            single { SettingsStore(get()) }
        }) }
    }
}
