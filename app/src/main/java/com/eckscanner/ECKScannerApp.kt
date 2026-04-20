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

        // Handle token expiration - redirect to login
        ApiClient.onUnauthorized = {
            android.os.Handler(mainLooper).post {
                ApiClient.clearConfig(this)
                val intent = android.content.Intent(this, com.eckscanner.ui.login.LoginActivity::class.java)
                    .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                    .addFlags(android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK)
                intent.putExtra("session_expired", true)
                startActivity(intent)
            }
        }
    }

    companion object {
        lateinit var instance: ECKScannerApp
            private set
    }
}
