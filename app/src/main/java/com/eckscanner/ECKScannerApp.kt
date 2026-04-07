package com.eckscanner

import android.app.Application
import com.eckscanner.data.local.AppDatabase
import com.eckscanner.data.remote.ApiClient

class ECKScannerApp : Application() {

    lateinit var database: AppDatabase
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this
        database = AppDatabase.getInstance(this)

        // Initialize ApiClient from saved config so it's always ready
        val config = ApiClient.getConfig(this)
        if (config != null && !ApiClient.isInitialized()) {
            ApiClient.initialize(config.first, config.second)
        }
    }

    companion object {
        lateinit var instance: ECKScannerApp
            private set
    }
}
