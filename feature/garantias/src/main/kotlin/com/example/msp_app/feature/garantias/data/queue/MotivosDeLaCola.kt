package com.example.msp_app.feature.garantias.data.queue

import com.example.msp_app.feature.garantias.domain.ResultadoDeLaLlamada

/**
 * Los textos que la cola escribe en `ULTIMO_ERROR` y que la pantalla muestra
 * tal cual.
 *
 * Son constantes y no literales repartidos por el código por la misma razón
 * que [com.example.msp_app.feature.garantias.domain.BloqueoDeEntrega]: el
 * cobrador ve el mismo mensaje en la bandeja, en el detalle y en el aviso del
 * trabajador, y una prueba puede comparar el texto exacto.
 *
 * Ninguno de estos motivos es un rechazo definitivo salvo que la cola lo
 * decida: [SIN_RED], [TOKEN_VENCIDO], [SIN_REFERENCIAS] y [SIN_RESOLVER] se
 * reintentan; [SIN_CUERPO], [FIRMA_ILEGIBLE] y [FOTO_ILEGIBLE] no, porque el
 * dato no puede aparecer solo.
 */
internal object MotivosDeLaCola {

    /** El teléfono no alcanzó el servidor; se reintenta con los mismos bytes. */
    const val SIN_RED: String = "Sin conexión; se reintentará"

    /** El token venció y el cliente ya lo renovó una vez; se reintenta después. */
    const val TOKEN_VENCIDO: String = "La sesión venció; se reintentará"

    /** El comando anterior del folio todavía no devolvió el id que este necesita. */
    const val SIN_REFERENCIAS: String = "Falta el identificador del servidor"

    /** `abrir` respondió 2xx pero sin el `id` del folio. */
    const val SIN_ID_DE_FOLIO: String = "El servidor no devolvió el folio"

    /** El servidor aceptó el comando pero el detalle todavía no lo cuenta. */
    const val SIN_RESOLVER: String = "El servidor todavía no registra el cambio"

    /** La fila no tiene un cuerpo que se pueda mandar. */
    const val SIN_CUERPO: String = "El comando no tiene un cuerpo válido"

    /** El PNG de la firma ya no está en el teléfono. */
    const val FIRMA_ILEGIBLE: String = "La firma ya no está en el teléfono"

    /** El archivo de una foto ya no está en el teléfono. */
    const val FOTO_ILEGIBLE: String = "La foto ya no está en el teléfono"

    /** El servidor rechazó el envío sin un `detail` legible. */
    const val RECHAZO_SIN_DETALLE: String = "El servidor rechazó el envío"
}

/**
 * El motivo que se guarda para un intento fallido.
 *
 * Prefiere el `detail` del servidor, que está escrito para el cobrador; si no
 * vino, usa [MotivosDeLaCola.RECHAZO_SIN_DETALLE]. Un fallo de red no trae
 * `detail` y se nombra con [MotivosDeLaCola.SIN_RED].
 */
internal fun motivoDe(resultado: ResultadoDeLaLlamada): String = when (resultado) {
    is ResultadoDeLaLlamada.FalloDeRed -> MotivosDeLaCola.SIN_RED
    is ResultadoDeLaLlamada.Respuesta ->
        resultado.detalle ?: MotivosDeLaCola.RECHAZO_SIN_DETALLE
}
