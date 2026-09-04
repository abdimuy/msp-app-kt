package com.example.msp_app.feature.visitas.ui

import com.example.msp_app.core.common.time.AppTime
import com.example.msp_app.core.printing.application.PrintPermission
import com.example.msp_app.core.printing.domain.PrinterDevice
import com.example.msp_app.feature.visitas.domain.model.TicketDeVisita

/**
 * Lo que la pantalla del ticket de visita necesita saber. Misma forma que la del
 * ticket de pago a propósito: son la misma regla de negocio sobre dos papeles
 * distintos, y dos formas distintas de estado serían dos sitios donde la regla
 * puede empezar a diferir.
 */
data class TicketDeVisitaUiState(
    val cargando: Boolean = true,
    val error: ErrorDelTicketDeVisita? = null,
    val ticket: TicketDeVisita? = null,
    val permiso: PrintPermission? = null,
    /** El ticket renderizado a 32 columnas — la vista previa monoespaciada. */
    val vistaPrevia: String = "",
    val impresion: ImpresionDeVisitaUi = ImpresionDeVisitaUi()
) {

    /**
     * El CTA solo se enciende cuando HOY es el día de la visita y no hay una
     * impresión en curso. `FueraDelDia` lo apaga tanto para la primera copia
     * como para una reimpresión — la regla es del día, no del conteo.
     */
    val sePuedeImprimir: Boolean
        get() = ticket != null &&
            permiso != null &&
            permiso != PrintPermission.FueraDelDia &&
            impresion.fase != FaseDeImpresionDeVisita.IMPRIMIENDO

    /** ¿Este papel sería una copia? */
    val esReimpresion: Boolean get() = permiso is PrintPermission.Reimpresion

    /** ¿La regla del día lo bloquea? */
    val fueraDelDia: Boolean get() = permiso == PrintPermission.FueraDelDia

    /** "copia 2 · 10:32" — qué copia sería y a qué hora salió la primera. */
    val detalleDeCopia: String
        get() {
            val copia = permiso as? PrintPermission.Reimpresion ?: return ""
            val numero = "copia ${copia.previas + 1}"
            // Hora desconocida: el registro conservó el conteo y perdió la fecha.
            // Se dice CUÁL copia es, que es lo que la hace detectable.
            val hora = copia.primeraVez ?: return numero
            return "$numero · ${AppTime.formatForDisplay(hora, AppTime.Formats.TIME_24H)}"
        }
}

/** Por qué no hay ticket que mostrar. [mensaje] es texto de pantalla. */
enum class ErrorDelTicketDeVisita(val mensaje: String) {
    /** La ruta apunta a una visita que el teléfono ya no tiene. */
    VISITA_NO_ESTA("la visita ya no está"),

    /** La lectura falló. Distinto de "no está": aquí sí tiene sentido reintentar. */
    NO_SE_PUDO_LEER("no se pudo leer")
}

/** En qué punto va el flujo de impresión. */
enum class FaseDeImpresionDeVisita { LISTO, ELIGIENDO, IMPRIMIENDO, IMPRESO, FALLO }

/** El flujo de impresión: fase, destino, lista del picker y mensaje del fallo. */
data class ImpresionDeVisitaUi(
    val fase: FaseDeImpresionDeVisita = FaseDeImpresionDeVisita.LISTO,
    val impresora: PrinterDevice? = null,
    val disponibles: List<PrinterDevice> = emptyList(),
    val mensaje: String? = null
)
