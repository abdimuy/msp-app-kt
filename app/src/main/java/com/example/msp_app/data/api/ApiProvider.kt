package com.example.msp_app.data.api

object ApiProvider : BaseApi() {
    private const val BASE_URL = "https://msp2025.loclx.io/"

    val retrofit by lazy {
        createClient(BASE_URL)
    }

    fun <T> create(service: Class<T>): T {
        return retrofit.create(service)
    }
}
