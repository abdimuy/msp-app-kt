package com.example.msp_app.data.api

import retrofit2.Retrofit

/**
 * Proveedor del backend de imágenes. El cliente lo construye ahora el
 * [com.example.msp_app.core.network.RetrofitClientFactory] de `:core:network`
 * (T7): `factory.images()` = baseURL de imágenes del flavor, SIN bearer.
 *
 * Único cambio consciente vs legacy (documentado, no regresión): el legacy usaba
 * el timeout default de OkHttp (10 s); el factory lo homologa a 60 s (más generoso
 * para descargas de imagen). NO adjunta `X-App-Version` — el host de imágenes no
 * fue auditado, así que la request sale sin headers extra, byte-idéntica al legacy
 * salvo el timeout. No cambia ningún formato de request/response.
 */
object ApiProviderImages {

    private val retrofit: Retrofit by lazy { appRetrofitClientFactory.images() }

    fun <T> create(service: Class<T>): T {
        return retrofit.create(service)
    }
}
