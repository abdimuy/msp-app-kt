package com.example.msp_app.core.common.visitas.envio

import com.example.msp_app.core.upload.UploadDecision
import com.example.msp_app.core.upload.classifyUpload

/**
 * Las tres salidas posibles del worker de visitas (spec §4.5, I1). No existe
 * una cuarta: "enviada sin prueba" (E-VIS-001) ya no es una salida.
 */
enum class SalidaDeEnvio { CONFIRMADA, RESGUARDADA, REINTENTA }

/**
 * Resultado de `GET /v2/visitas/{id}`: [codigo] nulo = no hubo respuesta HTTP
 * (red, excepción, cuerpo ilegible); [visita] nulo = sin cuerpo utilizable.
 */
data class ResultadoGet(val codigo: Int?, val visita: VisitaEnServidor?)

/**
 * Salida tras un 2xx del POST. Función pura y total.
 *
 * "HTTP 200 no es éxito": sin eco del mismo id no se suelta (spec §4.2-2,
 * Review Focus 1). Un cuerpo vacío, HTML de un portal cautivo o el JSON de
 * otra visita llegan aquí como `idEnCuerpo` nulo o distinto: REINTENTA, y el
 * worker decide por la prueba de existencia.
 *
 * **Desviación del plan P1 (Tarea 9) aprobada por el dueño el 2026-10-17
 * (opción A):** si la visita tiene [fotosPendientes] locales, el eco del id
 * NO basta. Una foto con fila y sin archivo en el servidor (H11) recibe un 201
 * por replay con el mismo id sin que nadie repare el archivo (medido por el
 * revisor de la Tarea 5 en dev, 2026-10-17). Se exige además [get] con
 * existencia COMPLETA: GET 200, mismo id, y cada foto pendiente en
 * `imagen_ids`, que el servidor lista solo con fila Y archivo en disco
 * (E-VIS-022). Sin GET, GET fallido (red, 5xx, 404), `imagen_ids` ausente
 * (servidor viejo) o una foto que falta: REINTENTA. El worker solo borra las
 * fotos locales después de una CONFIRMADA. Sin fotos pendientes basta el eco,
 * como dice el plan, y [get] se ignora.
 */
fun salidaTras2xx(
    enviada: String,
    idEnCuerpo: String?,
    fotosPendientes: Set<String>,
    get: ResultadoGet?
): SalidaDeEnvio = when {
    enviada.isBlank() || idEnCuerpo != enviada -> SalidaDeEnvio.REINTENTA
    fotosPendientes.isEmpty() -> SalidaDeEnvio.CONFIRMADA
    get != null &&
        existenciaCompleta(enviada, get.visita, fotosPendientes, get.codigo) == Existencia.COMPLETA ->
        SalidaDeEnvio.CONFIRMADA
    else -> SalidaDeEnvio.REINTENTA
}

/**
 * Error HTTP: primero la existencia completa; si no, decide la tabla
 * compartida [classifyUpload] (un solo lugar decide, spec §2.2-2). RELEASE
 * por custodia (`X-Intent-Captured`) = RESGUARDADA: un 413
 * `visita_body_too_large` llega con la visita entera en la bandeja y se
 * resguarda; el `_to_capture` llega sin cabecera y se reintenta (E-VIS-020).
 */
fun salidaTrasError(existencia: Existencia, codigo: Int, captureConfirmed: Boolean): SalidaDeEnvio {
    if (existencia == Existencia.COMPLETA) return SalidaDeEnvio.CONFIRMADA
    return when (
        classifyUpload(
            codigo,
            reachedMspApi = false,
            captureConfirmed = captureConfirmed
        )
    ) {
        // Un 2xx no llega aquí (lo atiende salidaTras2xx); esta rama solo
        // blinda el caso imposible: RELEASE por 2xx no es custodia.
        UploadDecision.RELEASE -> if (codigo in HTTP_2XX) SalidaDeEnvio.REINTENTA else SalidaDeEnvio.RESGUARDADA
        UploadDecision.RETRY -> SalidaDeEnvio.REINTENTA
    }
}

private val HTTP_2XX = 200..299
