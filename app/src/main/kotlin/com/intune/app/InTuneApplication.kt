package com.intune.app

import android.app.Application

class InTuneApplication : Application() {

    /** App-wide dependency graph. See [AppContainer]. */
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
