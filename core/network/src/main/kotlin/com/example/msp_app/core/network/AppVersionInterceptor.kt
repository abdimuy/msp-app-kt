package com.example.msp_app.core.network

import okhttp3.Interceptor
import okhttp3.Response

/**
 * Adjunta el header `X-App-Version` con la versión de la app a cada request.
 *
 * **El servidor SÍ lo lee desde 2026-08-15.** El middleware `MinAppVersion`
 * (`internal/platform/middleware/appversion.go`) compara este valor contra
 * `MIN_APP_VERSION` y rechaza las versiones por debajo del mínimo con **409**
 * (un código que la tabla de entrega garantizada reintenta siempre, para que un
 * rechazo por versión no suelte nunca una captura). Es el respaldo del bloqueo
 * en la app: un teléfono que nunca lee la configuración de Firestore sigue
 * alcanzando el API.
 *
 * Dos consecuencias que hay que respetar al tocar esta clase:
 *
 * 1. **Se compara el NOMBRE de versión, no el `versionCode`.** Es lo que este
 *    header lleva, y el bloqueo existe para detener builds viejos, que sólo
 *    mandan el nombre. Cambiar el formato del valor rompe la compuerta.
 * 2. **El servidor falla ABIERTO**: si el header falta o no se puede leer, deja
 *    pasar. Escritorio, web y clientes v1 no lo mandan y no pueden quedar fuera.
 *
 * El header sigue siendo compatible hacia atrás: un servidor con
 * `MIN_APP_VERSION` vacío no rechaza nada. Si el Go adopta un nombre canónico
 * distinto, se ajusta [HEADER_APP_VERSION] **y** la constante del middleware.
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
