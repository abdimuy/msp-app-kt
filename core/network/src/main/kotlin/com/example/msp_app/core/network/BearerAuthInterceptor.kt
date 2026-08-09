package com.example.msp_app.core.network

import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.Response

/**
 * Adjunta `Authorization: Bearer <token>` a cada request usando el puerto
 * [AuthTokenProvider]. Reescritura limpia del `FirebaseBearerInterceptor` legacy
 * de `:app`, con el mismo comportamiento verificado contra el backend Go
 * (`internal/auth/infra/authhttp/authn.go`):
 *
 * 1. Obtiene el token cacheado (`forceRefresh = false`). Si es `null`, la request
 *    pasa **sin header** — el backend responde 401 explícito (`missing_authorization`)
 *    en vez de colgarse esperando credenciales.
 * 2. Si el backend responde **401 y había token**, renueva forzando
 *    (`forceRefresh = true`) y **reintenta una sola vez** con el token fresco.
 * 3. Un 401 **sin** token previo NO se reintenta (evita el bucle): la sesión
 *    simplemente no existe.
 *
 * `runBlocking` es correcto aquí: `intercept` corre en el thread de I/O de OkHttp,
 * nunca en el main thread. El caché con TTL vive en la impl del puerto
 * ([AuthTokenProvider]), no aquí — este interceptor es *stateless* y sin Firebase.
 */
class BearerAuthInterceptor(
    private val tokenProvider: AuthTokenProvider
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val token = runBlocking { tokenProvider.token(forceRefresh = false) }
        val request = if (token != null) {
            chain.request().newBuilder()
                .header(HEADER_AUTHORIZATION, bearer(token))
                .build()
        } else {
            chain.request()
        }

        val response = chain.proceed(request)
        if (response.code != HTTP_UNAUTHORIZED || token == null) {
            return response
        }

        // 401 con token: el token pudo haber expirado a mitad de vuelo. Cerramos
        // la respuesta, forzamos renovación y reintentamos exactamente una vez.
        response.close()
        val fresh = runBlocking { tokenProvider.token(forceRefresh = true) }
        val retried = chain.request().newBuilder()
            .removeHeader(HEADER_AUTHORIZATION)
            .apply { if (fresh != null) header(HEADER_AUTHORIZATION, bearer(fresh)) }
            .build()
        return chain.proceed(retried)
    }

    private fun bearer(token: String): String = "$BEARER_PREFIX$token"

    private companion object {
        const val HEADER_AUTHORIZATION = "Authorization"
        const val BEARER_PREFIX = "Bearer "
        const val HTTP_UNAUTHORIZED = 401
    }
}
