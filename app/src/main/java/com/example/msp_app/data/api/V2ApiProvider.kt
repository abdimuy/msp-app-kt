package com.example.msp_app.data.api

import com.example.msp_app.BuildConfig
import retrofit2.Retrofit

/**
 * Builds Retrofit interfaces for the v2 Go backend. Mantiene un base URL
 * propio (independiente del v1 de [ApiProvider]) porque hoy el backend Go
 * vive en una direccion distinta — en desarrollo, el host local desde el
 * emulador Android.
 *
 * El interceptor que adjunta el Firebase ID token como Bearer vive en
 * [V2BaseApi]; cada servicio construido aqui lo hereda gratis.
 */
object V2ApiProvider : V2BaseApi() {

    /**
     * Base URL del backend Go, vía `BuildConfig.V2_BASE_URL`, según el flavor:
     *   - devlocal  → API local (`local.properties`, def 10.0.2.2:3001)
     *   - devserver → apidev (server de pruebas)
     *   - prod      → host del Go de prod
     *
     * Visible `internal` para que [CobranzaSseProvider] pueda reutilizar la
     * misma URL sin duplicarla.
     */
    internal val BASE_URL = BuildConfig.V2_BASE_URL

    @Volatile private var retrofit: Retrofit? = null

    fun <T> create(service: Class<T>): T {
        val r = retrofit ?: synchronized(this) {
            retrofit ?: createClient(BASE_URL).also { retrofit = it }
        }
        return r.create(service)
    }
}
