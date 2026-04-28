package com.gaje48.lms

import android.app.Application
import com.gaje48.lms.di.module
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin

class LmsApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        startKoin {
            androidContext(this@LmsApplication)
            modules(module)
        }
    }
}