package com.eckscanner.data.remote

import android.content.Context
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

object ApiClient {

    private var retrofit: Retrofit? = null
    private var apiService: ApiService? = null

    /** Called when a 401 Unauthorized is received - token expired */
    var onUnauthorized: (() -> Unit)? = null

    fun initialize(baseUrl: String, token: String) {
        val authInterceptor = Interceptor { chain ->
            val request = chain.request().newBuilder()
                .addHeader("Authorization", "Bearer $token")
                .addHeader("Accept", "application/json")
                .build()
            val response = chain.proceed(request)
            if (response.code == 401) {
                onUnauthorized?.invoke()
            }
            response
        }

        val logging = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BASIC
        }

        val client = OkHttpClient.Builder()
            .addInterceptor(authInterceptor)
            .addInterceptor(logging)
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .build()

        val url = if (baseUrl.endsWith("/")) baseUrl else "$baseUrl/"

        retrofit = Retrofit.Builder()
            .baseUrl(url)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()

        apiService = retrofit!!.create(ApiService::class.java)
    }

    fun getService(): ApiService {
        return apiService ?: throw IllegalStateException("ApiClient not initialized. Call initialize() first.")
    }

    fun isInitialized(): Boolean = apiService != null

    // SharedPreferences helpers
    private const val PREFS_NAME = "eck_scanner_prefs"
    private const val KEY_BASE_URL = "base_url"
    private const val KEY_TOKEN = "api_token"
    private const val KEY_WAREHOUSE_ID = "selected_warehouse_id"
    private const val KEY_LAST_PRODUCT_SYNC = "last_product_sync"
    private const val KEY_LAST_STOCK_SYNC = "last_stock_sync"

    fun saveConfig(context: Context, baseUrl: String, token: String) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
            .putString(KEY_BASE_URL, baseUrl)
            .putString(KEY_TOKEN, token)
            .apply()
    }

    fun getConfig(context: Context): Pair<String, String>? {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val url = prefs.getString(KEY_BASE_URL, null) ?: return null
        val token = prefs.getString(KEY_TOKEN, null) ?: return null
        return Pair(url, token)
    }

    fun saveWarehouseId(context: Context, warehouseId: Int) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
            .putInt(KEY_WAREHOUSE_ID, warehouseId)
            .apply()
    }

    fun getWarehouseId(context: Context): Int {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getInt(KEY_WAREHOUSE_ID, -1)
    }

    fun saveLastProductSync(context: Context, timestamp: String) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
            .putString(KEY_LAST_PRODUCT_SYNC, timestamp)
            .apply()
    }

    fun getLastProductSync(context: Context): String? {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_LAST_PRODUCT_SYNC, null)
    }

    fun saveLastStockSync(context: Context, timestamp: String) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
            .putString(KEY_LAST_STOCK_SYNC, timestamp)
            .apply()
    }

    fun getLastStockSync(context: Context): String? {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_LAST_STOCK_SYNC, null)
    }

    fun clearConfig(context: Context) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().clear().apply()
        retrofit = null
        apiService = null
    }
}
