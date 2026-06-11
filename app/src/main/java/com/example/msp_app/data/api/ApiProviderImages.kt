package com.example.msp_app.data.api

import com.example.msp_app.BuildConfig
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

object ApiProviderImages {

    // Imagenes backend, por flavor vía `BuildConfig.IMAGES_BASE_URL`.
    private val IMAGES_BASE_URL = BuildConfig.IMAGES_BASE_URL

    private val retrofit: Retrofit by lazy {
        Retrofit.Builder()
            .baseUrl(IMAGES_BASE_URL)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }

    fun <T> create(service: Class<T>): T {
        return retrofit.create(service)
    }
}
