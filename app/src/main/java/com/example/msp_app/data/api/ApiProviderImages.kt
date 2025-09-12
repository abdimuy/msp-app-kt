package com.example.msp_app.data.api

import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

object ApiProviderImages {

    private const val LOCAL_IMAGE_API_URL = "https://mspimagenes.loclx.io/"

    private val retrofit: Retrofit by lazy {
        Retrofit.Builder()
            .baseUrl(LOCAL_IMAGE_API_URL)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }

    fun <T> create(service: Class<T>): T {
        return retrofit.create(service)
    }
}
