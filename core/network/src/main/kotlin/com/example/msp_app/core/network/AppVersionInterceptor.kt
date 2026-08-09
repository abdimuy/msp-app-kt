package com.example.msp_app.core.network

import okhttp3.Interceptor
import okhttp3.Response

/**
 * Adjunta el header `X-App-Version` con la versión de la app a cada request.
 *
 * **Contrato verificado (best-effort, NO load-bearing):** una auditoría del
 * backend Go (`/Volumes/M2-1TB/Developer/msp-api`, 2026-08-09) confirmó que
 * NINGÚN middleware ni handler LEE un header de versión. Los únicos headers de
 * request que el backend consume son `Authorization`
 * (`internal/auth/infra/authhttp/authn.go:108`), `X-Request-ID`
 * (`internal/platform/middleware/middleware.go:27`), `Idempotency-Key`,
 * `If-None-Match`, `Content-Type`, `X-Internal-Replay` y `Origin`. No existe
 * `X-App-Version` ni gating por versión en ningún punto.
 *
 * Por lo tanto el header es **puramente aditivo**: se envía para observabilidad
 * futura del lado servidor, pero el comportamiento de la app NO depende de que
 * el servidor actúe sobre él (los backends ignoran headers desconocidos). Si más
 * adelante el Go adopta un nombre canónico distinto, se ajusta [HEADER_APP_VERSION].
 *
 * **Anti-PII:** la versión no es dato personal; es seguro enviarla siempre.
 */
class AppVersionInterceptor(
    private val appVersion: String
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request().newBuilder()
            .header(HEADER_APP_VERSION, appVersion)
            .build()
        return chain.proceed(request)
    }

    companion object {
        /** Nombre del header. No canónico en el servidor: ver KDoc de la clase. */
        const val HEADER_APP_VERSION = "X-App-Version"
    }
}
