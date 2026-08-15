package com.example.msp_app.core.upload

/**
 * Qué hacer con una captura tras una respuesta del servidor.
 *
 * El invariante, único para todos los módulos que suben capturas desde campo:
 *
 * > El teléfono suelta una captura sólo cuando el servidor confirma una de dos
 * > cosas: «la apliqué» o «la tengo guardada para corregir». Cualquier otra
 * > respuesta se reintenta.
 *
 * Antes cada módulo tenía su propia política —pagos, ventas, visitas y
 * garantías divergían en los mismos códigos HTTP— y dos de ellas podían perder
 * dinero. Esta es la única definición.
 */
enum class UploadDecision {
    /**
     * El servidor lo tiene: aplicado (2xx), ya existente (el GET lo encontró),
     * o rechazado pero resguardado con `X-Intent-Captured`. La oficina lo
     * corrige; el teléfono terminó.
     */
    RELEASE,

    /**
     * Nadie lo tiene. Nunca marcar como entregado: reintentar. Cubre el
     * parpadeo de token (401), las señales de backoff (408/409/425/429), y
     * cualquier 4xx/5xx **sin** confirmación de custodia.
     */
    RETRY
}

/**
 * Códigos que siempre son señal de reintento, nunca de rechazo definitivo.
 * 401 es parpadeo de token; el resto son señales explícitas de backoff.
 */
private val ALWAYS_RETRY = setOf(401, 408, 409, 425, 429)

/**
 * La tabla de decisión de entrega garantizada.
 *
 * Se evalúa **después** de que la verificación por GET no haya resuelto: si el
 * GET devolvió 200, el llamador ya soltó la captura y nunca llega aquí.
 *
 * | Situación | Decisión |
 * |---|---|
 * | 2xx | RELEASE |
 * | 401, 408, 409, 425, 429 | RETRY |
 * | 4xx o 5xx **con** `X-Intent-Captured` | RELEASE |
 * | 4xx o 5xx **sin** `X-Intent-Captured` | RETRY |
 * | cualquier otro código | RETRY |
 *
 * Los fallos de red (IOException) ni siquiera llegan aquí: el llamador los
 * reintenta siempre, porque el servidor no vio la petición.
 *
 * @param code código HTTP de la respuesta.
 * @param captureConfirmed llegó la cabecera `X-Intent-Captured`. Es la ÚNICA
 *   prueba de custodia: el servidor la emite sólo cuando su `Store.Save`
 *   devolvió nil.
 * @param reachedMspApi el `Content-Type` traía `problem+json`. Se conserva
 *   **sólo como dato de diagnóstico para el log** — ya no decide nada. Antes se
 *   usaba como prueba de custodia y era incorrecto: cuando el pool de Firebird
 *   se traba, la petición falla Y la captura falla a la vez, pero la respuesta
 *   sigue siendo `problem+json`. Ese es el caso que perdió dos pagos.
 */
@Suppress("UNUSED_PARAMETER")
fun classifyUpload(
    code: Int,
    reachedMspApi: Boolean = false,
    captureConfirmed: Boolean = false
): UploadDecision = when {
    code in 200..299 -> UploadDecision.RELEASE
    code in ALWAYS_RETRY -> UploadDecision.RETRY
    // Resguardado server-side: la oficina lo corrige. Es la condición de paro
    // que evita reintentar para siempre una captura genuinamente inválida.
    captureConfirmed && code in 400..599 -> UploadDecision.RELEASE
    // Sin confirmación de custodia nadie lo tiene. Ante la duda, conservar.
    else -> UploadDecision.RETRY
}

/**
 * Nombre de la cabecera con la que el servidor confirma la custodia. Su valor
 * es el UUID del intento capturado.
 */
const val HEADER_INTENT_CAPTURED: String = "X-Intent-Captured"

/**
 * Comprueba si una captura ya existe en el servidor, por su id.
 *
 * Cada módulo lo implementa contra su propio endpoint (`GET /pagos/{id}`,
 * `GET /ventas/{id}`, …). Es lo único que se comparte además de la tabla: los
 * workers se quedan en su módulo porque difieren en multipart, imágenes y
 * rutas, y forzarlos a un molde común sería peor que la duplicación que evita.
 */
fun interface ExistenceVerifier {
    /**
     * @return `true` si existe, `false` si no existe, `null` si es
     *   indeterminado (error de red, 5xx, excepción). Un `null` NO es un «no
     *   existe»: el llamador sigue a la tabla de decisión.
     */
    suspend fun exists(id: String): Boolean?
}
