package com.example.utilitiestracker.data.remote

import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

object RetrofitProvider {

    // Doar ca Retrofit cere un baseUrl valid; nu va fi folosit efectiv când trimitem @Url.
    private const val DUMMY_BASE_URL = "https://example.com/"

    fun createTariffsApi(): TariffsApi {
        val logging = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BASIC
        }

        val client = OkHttpClient.Builder()
            .addInterceptor(logging)
            .build()

        return Retrofit.Builder()
            .baseUrl(DUMMY_BASE_URL)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(TariffsApi::class.java)
    }
}
