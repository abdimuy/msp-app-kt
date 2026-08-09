package com.example.msp_app.data.api

import retrofit2.Retrofit

/**
 * Proveedor v2 (backend Go). El cliente lo construye ahora el
 * [com.example.msp_app.core.network.RetrofitClientFactory] de `:core:network`
 * (T7): `factory.v2()` = baseURL v2 del flavor + bearer por request (vía
 * [FirebaseAuthTokenProvider]) + timeout de 60 s — el mismo perfil que el
 * `V2BaseApi` eliminado.
 *
 * La baseURL v2 NO está bajo el kill-switch de Firestore (solo el v1 de
 * [ApiProvider] lo está), así que es estática por flavor y el `Retrofit` se
 * cachea una vez — comportamiento idéntico al legacy.
 */
object V2ApiProvider {

    /**
     * BaseURL del backend Go (`NetworkConfig.v2BaseUrl`, derivada del flavor).
     * Expuesta `internal` para que [com.example.msp_app.core.sync.cobranza.CobranzaSseProvider]
     * reutilice la MISMA URL (su OkHttp de SSE es aparte) sin duplicar el literal.
     */
    internal val v2BaseUrl: String get() = appNetworkConfig().v2BaseUrl

    @Volatile
    private var retrofit: Retrofit? = null

    fun <T> create(service: Class<T>): T {
        val r = retrofit ?: synchronized(this) {
            retrofit ?: appRetrofitClientFactory.v2().also { retrofit = it }
        }
        return r.create(service)
    }
}
