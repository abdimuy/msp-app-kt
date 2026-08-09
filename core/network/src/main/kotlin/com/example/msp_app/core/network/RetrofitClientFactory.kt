package com.example.msp_app.core.network

import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Provider
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

/**
 * Fábrica *stateless* de clientes Retrofit. Reemplaza a `BaseApi.createClient`
 * (v1) y `V2BaseApi.createClient` (v2) del legacy, unificando la construcción:
 * `GsonConverterFactory` + [AppVersionInterceptor] siempre, y [BearerAuthInterceptor]
 * solo cuando `auth = true`.
 *
 * **Kill-switch (crux de esta tarea):** la fábrica NO cachea ningún `Retrofit`
 * ni congela la baseURL. La [NetworkConfig] se resuelve por llamada vía
 * `Provider<NetworkConfig>` ([configProvider]), de modo que un cambio de baseURL
 * (kill-switch remoto de Firestore, cableado en `:app` T7) alcance a la siguiente
 * request. Un `Retrofit` fija su baseURL al construirse; por eso cada
 * [legacy]/[v2]/[images] construye uno **nuevo** leyendo la config vigente.
 * Nunca marcar como `@Singleton` un servicio producido a partir de estos
 * clientes (regla global del proyecto).
 *
 * **Timeouts por perfil (auditados del legacy):**
 * - v1 (Node): 300 s connect/read — endpoints legacy lentos (sync masivos).
 * - v2 (Go): 60 s connect/read.
 * - imágenes: comparte el perfil v2 (el legacy `ApiProviderImages` usaba los
 *   defaults de OkHttp; se homologa a 60 s, más generoso que el default de 10 s,
 *   para descargas de imagen sin regresión de comportamiento).
 */
class RetrofitClientFactory @Inject constructor(
    private val configProvider: Provider<NetworkConfig>,
    private val authTokenProvider: AuthTokenProvider
) {

    /**
     * Cliente v1 (Node legacy): sin bearer, timeout de 300 s.
     * Lee la baseURL vigente por llamada (kill-switch).
     */
    fun legacy(): Retrofit {
        val config = configProvider.get()
        return build(config.legacyBaseUrl, config.appVersion, auth = false, LEGACY_TIMEOUT_SECONDS)
    }

    /**
     * Cliente v2 (Go): con bearer, timeout de 60 s.
     * Lee la baseURL vigente por llamada (kill-switch).
     */
    fun v2(): Retrofit {
        val config = configProvider.get()
        return build(config.v2BaseUrl, config.appVersion, auth = true, V2_TIMEOUT_SECONDS)
    }

    /**
     * Cliente de imágenes: sin bearer, perfil de timeout v2.
     * Lee la baseURL vigente por llamada (kill-switch).
     */
    fun images(): Retrofit {
        val config = configProvider.get()
        return build(config.imagesBaseUrl, config.appVersion, auth = false, V2_TIMEOUT_SECONDS)
    }

    /**
     * Primitivo de construcción para baseURL arbitraria. La versión de la app se
     * toma de la [NetworkConfig] vigente al momento de la llamada.
     *
     * @param baseUrl base absoluta (debe terminar en `/`).
     * @param auth si `true`, agrega [BearerAuthInterceptor].
     * @param timeoutSeconds timeout de connect y read.
     */
    fun create(
        baseUrl: String,
        auth: Boolean,
        timeoutSeconds: Long = V2_TIMEOUT_SECONDS
    ): Retrofit = build(baseUrl, configProvider.get().appVersion, auth, timeoutSeconds)

    private fun build(
        baseUrl: String,
        appVersion: String,
        auth: Boolean,
        timeoutSeconds: Long
    ): Retrofit {
        val clientBuilder = OkHttpClient.Builder()
            .addInterceptor(AppVersionInterceptor(appVersion))
            .connectTimeout(timeoutSeconds, TimeUnit.SECONDS)
            .readTimeout(timeoutSeconds, TimeUnit.SECONDS)

        if (auth) {
            clientBuilder.addInterceptor(BearerAuthInterceptor(authTokenProvider))
        }

        return Retrofit.Builder()
            .baseUrl(baseUrl)
            .addConverterFactory(GsonConverterFactory.create())
            .client(clientBuilder.build())
            .build()
    }

    private companion object {
        const val LEGACY_TIMEOUT_SECONDS = 300L
        const val V2_TIMEOUT_SECONDS = 60L
    }
}
