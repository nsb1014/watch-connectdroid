package com.watchrelay.app

import android.app.Application

class WatchRelayApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
