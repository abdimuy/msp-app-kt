package com.example.msp_app.feature.visitas.screenshot

import androidx.compose.runtime.Composable
import com.example.msp_app.core.designsystem.theme.FontSizeLevel
import com.example.msp_app.feature.visitas.ui.TicketDeVisitaContent
import com.example.msp_app.feature.visitas.ui.TicketDeVisitaFixtures
import com.example.msp_app.feature.visitas.ui.TicketDeVisitaUiState
import org.junit.Test

/**
 * Los estados del ticket de visita, en claro y oscuro.
 *
 * La **primera impresión** —la pantalla donde el cobrador está parado en la
 * puerta— recorre además las TRES escalas reales de `FontSizeLevel`
 * (1.0 / 1.5 / 2.0). El `1.3` de `CollectionReportMatrixScreenshotTest` no
 * corresponde a ningún nivel que un usuario pueda elegir y no se replica.
 *
 * Los otros estados dependen del tema y no del tamaño de letra: **fuera del
 * día** (el CTA apagado, que tiene que VERSE apagado), la promesa con su monto,
 * la copia, el picker y el fallo.
 */
class TicketDeVisitaMatrixScreenshotTest : VisitasScreenshotTest() {

    @Test
    fun `primera light normal`() = matriz(dark = false, nivel = FontSizeLevel.NORMAL)

    @Test
    fun `primera light grande`() = matriz(dark = false, nivel = FontSizeLevel.GRANDE)

    @Test
    fun `primera light muy grande`() = matriz(dark = false, nivel = FontSizeLevel.MUY_GRANDE)

    @Test
    fun `primera dark normal`() = matriz(dark = true, nivel = FontSizeLevel.NORMAL)

    @Test
    fun `primera dark grande`() = matriz(dark = true, nivel = FontSizeLevel.GRANDE)

    @Test
    fun `primera dark muy grande`() = matriz(dark = true, nivel = FontSizeLevel.MUY_GRANDE)

    @Test
    fun `promesa light`() = estado("promesa", TicketDeVisitaFixtures.conPromesa(), dark = false)

    @Test
    fun `promesa dark`() = estado("promesa", TicketDeVisitaFixtures.conPromesa(), dark = true)

    @Test
    fun `fuera del dia light`() =
        estado("fuera", TicketDeVisitaFixtures.fueraDelDia(), dark = false)

    @Test
    fun `fuera del dia dark`() = estado("fuera", TicketDeVisitaFixtures.fueraDelDia(), dark = true)

    @Test
    fun `copia light`() = estado("copia", TicketDeVisitaFixtures.reimpresion(), dark = false)

    @Test
    fun `copia dark`() = estado("copia", TicketDeVisitaFixtures.reimpresion(), dark = true)

    @Test
    fun `picker light`() =
        estado("picker", TicketDeVisitaFixtures.eligiendoImpresora(), dark = false)

    @Test
    fun `picker dark`() = estado("picker", TicketDeVisitaFixtures.eligiendoImpresora(), dark = true)

    @Test
    fun `fallo light`() = estado("fallo", TicketDeVisitaFixtures.falloDeImpresion(), dark = false)

    @Test
    fun `fallo dark`() = estado("fallo", TicketDeVisitaFixtures.falloDeImpresion(), dark = true)

    private fun matriz(dark: Boolean, nivel: FontSizeLevel) = capture(
        name = "visitas_ticket_primera_${tema(dark)}_${sufijoDe(nivel)}",
        dark = dark,
        nivel = nivel
    ) {
        Ticket(TicketDeVisitaFixtures.primeraImpresion())
    }

    private fun estado(nombre: String, state: TicketDeVisitaUiState, dark: Boolean) = capture(
        name = "visitas_ticket_${nombre}_${tema(dark)}",
        dark = dark
    ) {
        Ticket(state)
    }
}

@Composable
private fun Ticket(state: TicketDeVisitaUiState) {
    TicketDeVisitaContent(
        state = state,
        onAtras = {},
        onImprimir = {},
        onCambiarImpresora = {},
        onElegirImpresora = {},
        onCerrarImpresion = {},
        onReintentar = {}
    )
}
