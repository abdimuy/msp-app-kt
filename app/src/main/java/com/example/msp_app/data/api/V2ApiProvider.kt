package com.example.msp_app.data.api

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
     * Base URL del backend Go. `10.0.2.2` es el alias del host de la maquina
     * desde el emulador Android. Para device fisico hay que cambiar por la
     * IP LAN; para staging/prod, por la URL publica.
     */
    private const val BASE_URL = "http://10.0.2.2:3001/"

    @Volatile private var retrofit: Retrofit? = null

    fun <T> create(service: Class<T>): T {
        val r = retrofit ?: synchronized(this) {
            retrofit ?: createClient(BASE_URL).also { retrofit = it }
        }
        return r.create(service)
    }
}
