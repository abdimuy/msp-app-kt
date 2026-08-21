package com.example.msp_app.core.network

import java.io.IOException
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
        val token = pedirToken(forceRefresh = false)
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
        val fresh = pedirToken(forceRefresh = true)
        val retried = chain.request().newBuilder()
            .removeHeader(HEADER_AUTHORIZATION)
            .apply { if (fresh != null) header(HEADER_AUTHORIZATION, bearer(fresh)) }
            .build()
        return chain.proceed(retried)
    }

    /**
     * Pide el token y garantiza que de aquí sólo salga una [IOException].
     *
     * `Interceptor.intercept` puede lanzar **únicamente** `IOException`.
     * Cualquier otro `Throwable` lo relanza OkHttp en su hilo de despacho,
     * donde nadie lo atrapa: Android lo reporta como `FATAL EXCEPTION: OkHttp
     * Dispatcher` y mata el proceso.
     *
     * Eso es lo que pasaba sin señal: vencido el caché de 50 minutos del ID
     * token, `getIdToken` fallaba con `FirebaseNetworkException` —que no es
     * `IOException`— y la app se cerraba sola. Como el sync reintenta cada 30
     * segundos, volvía a morir al reabrir.
     *
     * Convertirla en `IOException` es lo correcto y no un disfraz: para quien
     * llama, no poder alcanzar al proveedor de identidad ES un fallo de red, y
     * la app ya trata el modo sin conexión como un estado normal — un cobrador
     * pasa el día sin señal. La causa se conserva para poder diagnosticar.
     *
     * Lo que NO se hace: devolver `null` y seguir sin header. Eso confundiría
     * "no te puedo alcanzar" con "no hay sesión", el backend contestaría 401 y
     * eso puede cerrarle la sesión a alguien que sólo estaba sin señal.
     */
    // Atrapar `Exception` es justo el arreglo, no un descuido: `:core:network`
    // NO depende de Firebase —el puerto existe para eso— así que aquí es
    // imposible nombrar `FirebaseNetworkException`, y cualquier
    // implementación futura del puerto puede fallar de otra forma. Lo que
    // escape mata el proceso, así que la red se pone amplia a propósito.
    // `Error` sí pasa: un OOM no se disfraza de fallo de red.
    @Suppress("TooGenericExceptionCaught")
    private fun pedirToken(forceRefresh: Boolean): String? = try {
        runBlocking { tokenProvider.token(forceRefresh) }
    } catch (e: IOException) {
        throw e // ya es del tipo que OkHttp espera; envolverla entierra la causa
    } catch (e: Exception) {
        throw IOException("no se pudo obtener el token de sesión", e)
    }

    private fun bearer(token: String): String = "$BEARER_PREFIX$token"

    private companion object {
        const val HEADER_AUTHORIZATION = "Authorization"
        const val BEARER_PREFIX = "Bearer "
        const val HTTP_UNAUTHORIZED = 401
    }
}
