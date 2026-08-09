package com.example.msp_app.data.api

import retrofit2.Retrofit

/**
 * Proveedor del backend de imágenes. El cliente lo construye ahora el
 * [com.example.msp_app.core.network.RetrofitClientFactory] de `:core:network`
 * (T7): `factory.images()` = baseURL de imágenes del flavor, SIN bearer.
 *
 * Cambio consciente vs legacy (documentado, no regresión): el legacy usaba los
 * defaults de OkHttp (timeout 10 s, sin interceptores). El factory homologa a
 * 60 s (más generoso para descargas de imagen) y adjunta `X-App-Version`
 * (aditivo — el backend ignora headers desconocidos; ver `AppVersionInterceptor`).
 * No cambia ningún formato de request/response.
 */
object ApiProviderImages {

    private val retrofit: Retrofit by lazy { appRetrofitClientFactory.images() }

    fun <T> create(service: Class<T>): T {
        return retrofit.create(service)
    }
}
