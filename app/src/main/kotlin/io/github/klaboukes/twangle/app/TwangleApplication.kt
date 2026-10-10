package io.github.klaboukes.twangle.app

import android.app.Application

class TwangleApplication : Application() {

    /** App-wide dependency graph. See [AppContainer]. */
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
