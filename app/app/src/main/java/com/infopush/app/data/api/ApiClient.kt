package com.infopush.app.data.api

import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.util.concurrent.TimeUnit

object ApiClient {
    private var baseUrl: String = "http://10.0.2.2:8000/" // default for emulator

    private val moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .build()

    private var _api: ApiService? = null

    fun setBaseUrl(url: String) {
        val normalizedUrl = if (url.endsWith("/")) url else "$url/"
        synchronized(this) {
            if (normalizedUrl != baseUrl) {
                baseUrl = normalizedUrl
                _api = null // force rebuild
            }
        }
    }

    fun getBaseUrl(): String = baseUrl

    fun getApi(): ApiService {
        return _api ?: synchronized(this) {
            _api ?: Retrofit.Builder()
                .baseUrl(baseUrl)
                .client(okHttpClient)
                .addConverterFactory(MoshiConverterFactory.create(moshi))
                .build()
                .create(ApiService::class.java)
                .also { _api = it }
        }
    }

    fun getWsUrl(token: String): String {
        val wsBase = baseUrl
            .replace("https://", "wss://")
            .replace("http://", "ws://")
        return "${wsBase}ws/$token"
    }
}
