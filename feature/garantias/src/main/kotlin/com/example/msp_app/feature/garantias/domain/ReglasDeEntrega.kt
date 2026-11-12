package com.example.msp_app.feature.garantias.domain

import com.example.msp_app.feature.garantias.domain.model.FirmaDeConformidad
import com.example.msp_app.feature.garantias.domain.model.Ubicacion

/**
 * Lo que impide encolar una entrega. La pantalla lo usa para apagar el botón y
 * explicar por qué; el caso de uso lo vuelve a evaluar antes de escribir.
 *
 * Es un enum y no un texto para que la pantalla no invente mensajes distintos
 * en cada lugar y para que la prueba pueda comparar el bloqueo exacto.
 */
enum class BloqueoDeEntrega {
    /** Todavía no hay coordenadas del cobrador. */
    SIN_UBICACION,

    /** El cliente no firmó. */
    SIN_FIRMA,

    /** El PNG está vacío: no es una firma. */
    FIRMA_VACIA,

    /** El PNG pesa más de lo que acepta el servidor. */
    FIRMA_MUY_GRANDE
}

/**
 * Las reglas de la entrega. Dominio PURO: cero Android, cero Compose, cero
 * archivos abiertos.
 *
 * **El GPS es obligatorio.** El servidor rechaza la entrega sin coordenadas
 * (`warranty_delivery_gps_required`, `internal/garantias/domain/errors.go`) y
 * un comando encolado así se quedaría en 422 para siempre: el cobrador ya no
 * podría corregirlo desde el teléfono. Por eso se frena ANTES de encolar
 * (D5, mutante A8).
 *
 * **La firma es obligatoria y el tope es 1 MB.** Es el mismo tope del handler
 * del servidor (`maxFirmaBytes = 1 << 20` en `handlers_comandos.go`): si el
 * teléfono aceptara más, el comando se encolaría para morir en 422.
 *
 * El `Content-Type` es fijo ([MIME_DE_LA_FIRMA]) y no se deduce del archivo:
 * el servidor olfatea los bytes de todos modos, y un tipo calculado desde la
 * ruta podría cambiar entre reintentos (mutante A9).
 */
object ReglasDeEntrega {

    /** Un mebibyte, el mismo tope del servidor. */
    const val MAX_BYTES_DE_FIRMA: Int = 1 shl 20

    /** El tipo de la parte `firma` del multipart; fijo, nunca deducido. */
    const val MIME_DE_LA_FIRMA: String = "image/png"

    /**
     * Todo lo que impide entregar, en el orden en que la pantalla lo muestra.
     * Lista vacía = se puede encolar.
     */
    fun bloqueosDe(ubicacion: Ubicacion?, firma: FirmaDeConformidad?): List<BloqueoDeEntrega> =
        buildList {
            if (ubicacion == null) add(BloqueoDeEntrega.SIN_UBICACION)
            when {
                firma == null -> add(BloqueoDeEntrega.SIN_FIRMA)
                firma.tamanoBytes <= 0 -> add(BloqueoDeEntrega.FIRMA_VACIA)
                firma.tamanoBytes > MAX_BYTES_DE_FIRMA -> add(BloqueoDeEntrega.FIRMA_MUY_GRANDE)
            }
        }

    /** ¿Se puede encolar? Azúcar sobre [bloqueosDe], para que nadie lo repita. */
    fun sePuedeEntregar(ubicacion: Ubicacion?, firma: FirmaDeConformidad?): Boolean =
        bloqueosDe(ubicacion, firma).isEmpty()

    /** Los bytes del PNG son una firma que el servidor aceptaría. */
    fun firmaValida(bytes: ByteArray): Boolean =
        bytes.isNotEmpty() && bytes.size <= MAX_BYTES_DE_FIRMA
}
