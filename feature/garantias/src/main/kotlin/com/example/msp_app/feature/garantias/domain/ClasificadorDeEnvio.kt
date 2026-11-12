package com.example.msp_app.feature.garantias.domain

/**
 * Lo que devolvió un intento de mandar un comando o una foto.
 *
 * Es la entrada del [ClasificadorDeEnvio] y no un `Result` de Kotlin: el
 * clasificador necesita el **status** y el **código de negocio** por separado,
 * y un `Throwable` no los lleva.
 *
 * [Respuesta.codigo] sale de `errors[].message` (la forma `code=...` de Huma) y
 * no de un campo `code` que el API no publica; [Respuesta.detalle] es el
 * `detail` en español que se le muestra al cobrador.
 */
sealed interface ResultadoDeLaLlamada {

    /** El servidor contestó con un status HTTP. */
    data class Respuesta(
        val status: Int,
        val codigo: String? = null,
        val detalle: String? = null
    ) : ResultadoDeLaLlamada

    /** La petición no llegó: `IOException`, DNS, timeout, sin señal. */
    data class FalloDeRed(val mensaje: String) : ResultadoDeLaLlamada
}

/**
 * Qué hacer con un intento después de mandarlo.
 *
 * Son cuatro y sólo cuatro, y la diferencia entre [Reintentar] y [Rechazado]
 * es la que evita un bucle infinito en la cola: lo rechazado no vuelve a salir
 * y se muestra; lo reintentable se vuelve a intentar con **los mismos bytes y
 * la misma clave** (D3).
 */
sealed interface DecisionDeEnvio {

    /** El servidor tomó el cuerpo. */
    data object Enviado : DecisionDeEnvio

    /** Todavía no se sabe: se reintenta con la misma clave y el mismo cuerpo. */
    data object Reintentar : DecisionDeEnvio

    /** El token venció: se renueva y se reintenta, sin cambiar el comando. */
    data object RenovarToken : DecisionDeEnvio

    /**
     * El servidor no lo va a aceptar nunca tal como está. [motivo] es el texto
     * que ve el cobrador; [sinPermiso] distingue el 403 para que la pantalla
     * no ofrezca "reintentar" sobre una acción que no le toca.
     */
    data class Rechazado(
        val motivo: String,
        val sinPermiso: Boolean = false
    ) : DecisionDeEnvio
}

/**
 * Decide si un comando de garantía se reintenta, se rechaza o ya entró.
 *
 * ## Por qué no se reutiliza `core.upload.classifyUpload`
 *
 * `UploadDecision` trata **todo** 409 como reintentable. Para una visita o un
 * abono eso es razonable: el 409 es una carrera que se resuelve sola. Para
 * garantías no: el 409 `warranty_idempotency_key_mismatch` significa que la
 * clave ya se usó con **otro contenido**, y reintentarlo con los mismos bytes
 * daría 409 otra vez, para siempre. El 409
 * `warranty_idempotency_key_other_folio` es peor: la clave pertenece a otro
 * expediente y seguir insistiendo escribiría en el folio equivocado. Por eso
 * este clasificador mira el **código del cuerpo** y no sólo el status (D6,
 * mutantes A3 y A4).
 *
 * Los códigos se copian de `internal/garantias/domain/errors.go` en `msp-api`
 * y la tabla completa vive en la prueba `ClasificadorDeEnvioTest`.
 */
object ClasificadorDeEnvio {

    /** La clave ya existe con otro contenido: no se reintenta. */
    const val CLAVE_CON_OTRO_CONTENIDO: String = "warranty_idempotency_key_mismatch"

    /** La clave pertenece a otro folio: no se reintenta. */
    const val CLAVE_DE_OTRO_FOLIO: String = "warranty_idempotency_key_other_folio"

    private const val SIN_PERMISO = "Sin permiso"
    private const val RECHAZO_GENERICO = "El servidor rechazó el envío"
    private const val RECHAZO_DE_CLAVE = "La clave ya se usó con otro contenido"

    private const val NO_AUTORIZADO = 401
    private const val PROHIBIDO = 403
    private const val CONFLICTO = 409

    /** Señales explícitas de "vuelve más tarde". */
    private val REINTENTABLES = setOf(408, 425, 429)

    fun clasificar(resultado: ResultadoDeLaLlamada): DecisionDeEnvio = when (resultado) {
        is ResultadoDeLaLlamada.FalloDeRed -> DecisionDeEnvio.Reintentar
        is ResultadoDeLaLlamada.Respuesta -> clasificarRespuesta(resultado)
    }

    private fun clasificarRespuesta(respuesta: ResultadoDeLaLlamada.Respuesta): DecisionDeEnvio =
        when {
            respuesta.status in 200..299 -> DecisionDeEnvio.Enviado
            respuesta.status == NO_AUTORIZADO -> DecisionDeEnvio.RenovarToken
            respuesta.status == PROHIBIDO ->
                DecisionDeEnvio.Rechazado(SIN_PERMISO, sinPermiso = true)
            respuesta.status in REINTENTABLES -> DecisionDeEnvio.Reintentar
            respuesta.status == CONFLICTO -> clasificarConflicto(respuesta)
            respuesta.status in 500..599 -> DecisionDeEnvio.Reintentar
            else -> DecisionDeEnvio.Rechazado(respuesta.detalle ?: RECHAZO_GENERICO)
        }

    /**
     * El 409 sólo se rechaza cuando el código lo delata. Cualquier otro 409 es
     * la carrera de la clave duplicada (`warranty_idempotency_key_duplicate`),
     * que sí se resuelve reintentando: el servidor ya tiene el comando y la
     * repetición devuelve el mismo resultado.
     */
    private fun clasificarConflicto(respuesta: ResultadoDeLaLlamada.Respuesta): DecisionDeEnvio =
        when (respuesta.codigo) {
            CLAVE_CON_OTRO_CONTENIDO,
            CLAVE_DE_OTRO_FOLIO -> DecisionDeEnvio.Rechazado(respuesta.detalle ?: RECHAZO_DE_CLAVE)
            else -> DecisionDeEnvio.Reintentar
        }
}
