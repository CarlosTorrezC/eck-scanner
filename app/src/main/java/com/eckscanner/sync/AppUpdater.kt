package com.eckscanner.sync

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Environment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.URL

object AppUpdater {

    private const val GITHUB_API = "https://api.github.com/repos/CarlosTorrezC/eck-scanner/releases/latest"
    private const val PREFS = "eck_scanner_prefs"
    private const val KEY_SKIPPED_VERSION = "skipped_version"

    data class UpdateInfo(
        val tagName: String,
        val downloadUrl: String,
        val releaseName: String
    )

    suspend fun checkForUpdate(context: Context): UpdateInfo? = withContext(Dispatchers.IO) {
        try {
            val response = URL(GITHUB_API).readText()
            val json = org.json.JSONObject(response)
            val tagName = json.getString("tag_name")
            val releaseName = json.optString("name", tagName)

            // Compare with installed app version
            val installedVersion = getInstalledVersionTag(context)
            if (tagName == installedVersion) return@withContext null

            // Check if user skipped this version
            val skipped = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getString(KEY_SKIPPED_VERSION, null)
            if (tagName == skipped) return@withContext null

            // Find APK asset
            val assets = json.getJSONArray("assets")
            for (i in 0 until assets.length()) {
                val asset = assets.getJSONObject(i)
                if (asset.getString("name").endsWith(".apk")) {
                    return@withContext UpdateInfo(
                        tagName = tagName,
                        downloadUrl = asset.getString("browser_download_url"),
                        releaseName = releaseName
                    )
                }
            }
            null
        } catch (_: Exception) {
            null
        }
    }

    fun downloadAndInstall(context: Context, update: UpdateInfo) {
        try {
            val request = DownloadManager.Request(Uri.parse(update.downloadUrl))
                .setTitle("ECK Scanner ${update.tagName}")
                .setDescription("Descargando actualizacion...")
                .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, "ECKScanner-${update.tagName}.apk")
                .setMimeType("application/vnd.android.package-archive")

            val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            dm.enqueue(request)
        } catch (_: Exception) {
            // Fallback: open browser to download
            val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, Uri.parse(update.downloadUrl))
            intent.flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK
            context.startActivity(intent)
        }
    }

    fun skipVersion(context: Context, tagName: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(KEY_SKIPPED_VERSION, tagName)
            .apply()
    }

    private fun getInstalledVersionTag(context: Context): String {
        return try {
            val info = context.packageManager.getPackageInfo(context.packageName, 0)
            "v${info.versionName}"
        } catch (_: Exception) {
            ""
        }
    }
}
