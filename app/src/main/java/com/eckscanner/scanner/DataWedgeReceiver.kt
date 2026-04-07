package com.eckscanner.scanner

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter

/**
 * Receives barcode scans from Zebra DataWedge.
 *
 * DataWedge must be configured with:
 *   - Intent output enabled
 *   - Action: com.eckscanner.ACTION.BARCODE_SCAN
 *   - Category: android.intent.category.DEFAULT
 *   - Delivery: Broadcast intent
 */
class DataWedgeReceiver(private val onScan: (String) -> Unit) : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_BARCODE_SCAN) return

        val barcode = intent.getStringExtra(EXTRA_DATA_STRING)
            ?: intent.getStringExtra(EXTRA_DATA_STRING_ALT)
            ?: return

        val cleanCode = barcode.trim()
        if (cleanCode.isNotEmpty()) {
            onScan(cleanCode)
        }
    }

    companion object {
        const val ACTION_BARCODE_SCAN = "com.eckscanner.ACTION.BARCODE_SCAN"
        private const val EXTRA_DATA_STRING = "com.symbol.datawedge.data_string"
        private const val EXTRA_DATA_STRING_ALT = "com.motorolasolutions.emdk.datawedge.data_string"

        fun getIntentFilter(): IntentFilter {
            return IntentFilter(ACTION_BARCODE_SCAN)
        }

        /**
         * Send DataWedge API command to configure the profile for this app.
         * Call once on first launch to auto-configure DataWedge.
         */
        fun configureDataWedge(context: Context) {
            val profileIntent = Intent().apply {
                action = "com.symbol.datawedge.api.ACTION"
                putExtra("com.symbol.datawedge.api.SET_CONFIG", android.os.Bundle().apply {
                    putString("PROFILE_NAME", "ECKScanner")
                    putString("PROFILE_ENABLED", "true")
                    putString("CONFIG_MODE", "CREATE_IF_NOT_EXIST")

                    // Associate with our app
                    val appConfig = android.os.Bundle().apply {
                        putString("PACKAGE_NAME", context.packageName)
                        putStringArray("ACTIVITY_LIST", arrayOf("*"))
                    }
                    putParcelableArray("APP_LIST", arrayOf(appConfig))

                    // Intent output config
                    val intentPlugin = android.os.Bundle().apply {
                        putString("PLUGIN_NAME", "INTENT")
                        putString("RESET_CONFIG", "true")
                        putBundle("PARAM_LIST", android.os.Bundle().apply {
                            putString("intent_output_enabled", "true")
                            putString("intent_action", ACTION_BARCODE_SCAN)
                            putString("intent_delivery", "2") // Broadcast
                        })
                    }
                    putParcelableArray("PLUGIN_CONFIG", arrayOf(intentPlugin))
                })
            }
            context.sendBroadcast(profileIntent)
        }
    }
}
