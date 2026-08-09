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
 * `GsonConverterFactory` siempre, [BearerAuthInterceptor] solo cuando
 * `auth = true`, y [AppVersionInterceptor] **solo en el cliente v2** (backend Go).
 *
 * **Alcance del header `X-App-Version` (no-regresión estricta):** solo el backend
 * Go v2 fue auditado y confirmado como indiferente a headers desconocidos
 * (2026-08-09, ver [AppVersionInterceptor]). El backend v1 (Node) y el host de
 * imágenes NO se verificaron, así que sus requests salen **byte-idénticas** al
 * comportamiento previo a este plan: SIN `X-App-Version`. Por eso el header es
 * *opt-in* por perfil (`appVersionHeader`), activado únicamente en [v2].
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
     * Cliente v1 (Node legacy): sin bearer, sin `X-App-Version` (backend no
     * verificado — request byte-idéntica al legacy), timeout de 300 s.
     * Lee la baseURL vigente por llamada (kill-switch).
     */
    fun legacy(): Retrofit {
        val config = configProvider.get()
        return build(
            config.legacyBaseUrl,
            config.appVersion,
            auth = false,
            appVersionHeader = false,
            LEGACY_TIMEOUT_SECONDS
        )
    }

    /**
     * Cliente v2 (Go): con bearer y con `X-App-Version` (único backend auditado),
     * timeout de 60 s. Lee la baseURL vigente por llamada (kill-switch).
     */
    fun v2(): Retrofit {
        val config = configProvider.get()
        return build(
            config.v2BaseUrl,
            config.appVersion,
            auth = true,
            appVersionHeader = true,
            V2_TIMEOUT_SECONDS
        )
    }

    /**
     * Cliente de imágenes: sin bearer, sin `X-App-Version` (host no verificado),
     * perfil de timeout v2. Lee la baseURL vigente por llamada (kill-switch).
     */
    fun images(): Retrofit {
        val config = configProvider.get()
        return build(
            config.imagesBaseUrl,
            config.appVersion,
            auth = false,
            appVersionHeader = false,
            V2_TIMEOUT_SECONDS
        )
    }

    /**
     * Primitivo de construcción para baseURL arbitraria. La versión de la app se
     * toma de la [NetworkConfig] vigente al momento de la llamada.
     *
     * @param baseUrl base absoluta (debe terminar en `/`).
     * @param auth si `true`, agrega [BearerAuthInterceptor].
     * @param appVersionHeader si `true`, agrega [AppVersionInterceptor]
     *   (`X-App-Version`). Por defecto `false`: solo backends auditados (el v2 Go)
     *   deben optar por él; el v1 Node y el host de imágenes NO se verificaron.
     * @param timeoutSeconds timeout de connect y read.
     */
    fun create(
        baseUrl: String,
        auth: Boolean,
        appVersionHeader: Boolean = false,
        timeoutSeconds: Long = V2_TIMEOUT_SECONDS
    ): Retrofit = build(
        baseUrl = baseUrl,
        appVersion = configProvider.get().appVersion,
        auth = auth,
        appVersionHeader = appVersionHeader,
        timeoutSeconds = timeoutSeconds
    )

    private fun build(
        baseUrl: String,
        appVersion: String,
        auth: Boolean,
        appVersionHeader: Boolean,
        timeoutSeconds: Long
    ): Retrofit {
        val clientBuilder = OkHttpClient.Builder()
            .connectTimeout(timeoutSeconds, TimeUnit.SECONDS)
            .readTimeout(timeoutSeconds, TimeUnit.SECONDS)

        if (appVersionHeader) {
            clientBuilder.addInterceptor(AppVersionInterceptor(appVersion))
        }

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
