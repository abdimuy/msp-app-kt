package com.example.msp_app.feature.pagos.ui

import com.example.msp_app.core.common.time.AppTime
import com.example.msp_app.core.printing.application.PrintPermission
import com.example.msp_app.core.printing.domain.PrinterDevice
import com.example.msp_app.feature.pagos.domain.model.TicketDePago

/**
 * Lo que la pantalla del ticket de pago necesita saber.
 *
 * [permiso] es el veredicto de la regla del día del cobro **más** el registro de
 * impresiones, y es lo único que decide si el CTA se puede tocar. Vive en el
 * estado (y no se recalcula en el Composable) para que el golden y el test de
 * pantalla vean exactamente el mismo veredicto que produjo el ViewModel.
 */
data class TicketDePagoUiState(
    val cargando: Boolean = true,
    val error: ErrorDelTicket? = null,
    val ticket: TicketDePago? = null,
    val permiso: PrintPermission? = null,
    /** El ticket renderizado a 32 columnas — la vista previa monoespaciada. */
    val vistaPrevia: String = "",
    val impresion: ImpresionUi = ImpresionUi()
) {

    /**
     * El CTA solo se enciende cuando HOY es el día del cobro y no hay una
     * impresión en curso. `FueraDelDia` lo apaga tanto para la primera copia
     * como para una reimpresión — la regla es del día, no del conteo.
     */
    val sePuedeImprimir: Boolean
        get() = ticket != null &&
            permiso != null &&
            permiso != PrintPermission.FueraDelDia &&
            impresion.fase != FaseDeImpresion.IMPRIMIENDO

    /** ¿Este papel sería una copia? Lo que enciende la banda de reimpresión. */
    val esReimpresion: Boolean get() = permiso is PrintPermission.Reimpresion

    /** ¿La regla del día lo bloquea? Lo que enciende la banda de "fuera del día". */
    val fueraDelDia: Boolean get() = permiso == PrintPermission.FueraDelDia

    /**
     * "copia 2 · 10:32" — qué número de copia sería esta y a qué hora salió la
     * primera. Vive aquí y no en el Composable para poder afirmarlo en un test
     * de unidad: es la frase con la que el cobrador detecta la reimpresión.
     *
     * Cadena vacía cuando no hay reimpresión que anunciar.
     */
    val detalleDeCopia: String
        get() {
            val copia = permiso as? PrintPermission.Reimpresion ?: return ""
            val hora = AppTime.formatForDisplay(copia.primeraVez, AppTime.Formats.TIME_24H)
            return "copia ${copia.previas + 1} · $hora"
        }
}

/** Por qué no hay ticket que mostrar. [mensaje] es texto de pantalla. */
enum class ErrorDelTicket(val mensaje: String) {
    /** La ruta apunta a un abono que el teléfono ya no tiene. */
    PAGO_NO_ESTA("el abono ya no está"),

    /** La lectura falló. Distinto de "no está": aquí sí tiene sentido reintentar. */
    NO_SE_PUDO_LEER("no se pudo leer")
}

/** En qué punto va el flujo de impresión. */
enum class FaseDeImpresion {
    /** Nada en curso. La impresora recordada (si hay) ya está resuelta. */
    LISTO,

    /** El picker: la lista de impresoras emparejadas. */
    ELIGIENDO,

    /** El ticket va en camino a la impresora. */
    IMPRIMIENDO,

    /** Salió el papel. El registro de impresión ya quedó escrito. */
    IMPRESO,

    /** El puerto falló, con su mensaje es-MX. */
    FALLO
}

/**
 * El flujo de impresión, con la MISMA forma que el del reporte de cobranza
 * (`PrintSheetUi`): fase, destino, lista para el picker y un mensaje en el
 * fallo.
 */
data class ImpresionUi(
    val fase: FaseDeImpresion = FaseDeImpresion.LISTO,
    val impresora: PrinterDevice? = null,
    val disponibles: List<PrinterDevice> = emptyList(),
    val mensaje: String? = null
)
