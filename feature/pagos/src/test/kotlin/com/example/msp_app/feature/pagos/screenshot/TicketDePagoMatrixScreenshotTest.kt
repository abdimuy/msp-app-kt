package com.example.msp_app.feature.pagos.screenshot

import androidx.compose.runtime.Composable
import com.example.msp_app.core.designsystem.theme.FontSizeLevel
import com.example.msp_app.feature.pagos.ui.TicketDePagoContent
import com.example.msp_app.feature.pagos.ui.TicketDePagoUiState
import com.example.msp_app.feature.pagos.ui.TicketFixtures
import org.junit.Test

/**
 * Los estados del ticket de pago, en claro y oscuro.
 *
 * La **primera impresión** —la pantalla donde el cobrador pasa el tiempo y donde
 * la vista previa monoespaciada aprieta de verdad— recorre además las TRES
 * escalas reales de `FontSizeLevel` (1.0 / 1.5 / 2.0). El `1.3` de
 * `CollectionReportMatrixScreenshotTest` no corresponde a ningún nivel que un
 * usuario pueda elegir y no se replica (ver el KDoc de [PagosScreenshotTest]).
 *
 * Los otros cuatro estados dependen del tema y no del tamaño de letra, así que
 * van en NORMAL: **fuera del día** (el CTA apagado, que tiene que VERSE
 * apagado), **copia ya impresa**, el picker de impresoras y el fallo.
 */
class TicketDePagoMatrixScreenshotTest : PagosScreenshotTest() {

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
    fun `fuera del dia light`() = estado("fuera", TicketFixtures.fueraDelDia(), dark = false)

    @Test
    fun `fuera del dia dark`() = estado("fuera", TicketFixtures.fueraDelDia(), dark = true)

    @Test
    fun `copia light`() = estado("copia", TicketFixtures.reimpresion(), dark = false)

    @Test
    fun `copia dark`() = estado("copia", TicketFixtures.reimpresion(), dark = true)

    @Test
    fun `picker light`() = estado("picker", TicketFixtures.eligiendoImpresora(), dark = false)

    @Test
    fun `picker dark`() = estado("picker", TicketFixtures.eligiendoImpresora(), dark = true)

    @Test
    fun `fallo light`() = estado("fallo", TicketFixtures.falloDeImpresion(), dark = false)

    @Test
    fun `fallo dark`() = estado("fallo", TicketFixtures.falloDeImpresion(), dark = true)

    private fun matriz(dark: Boolean, nivel: FontSizeLevel) = capture(
        name = "pagos_ticket_primera_${tema(dark)}_${sufijoDe(nivel)}",
        dark = dark,
        nivel = nivel
    ) {
        Ticket(TicketFixtures.primeraImpresion())
    }

    private fun estado(nombre: String, state: TicketDePagoUiState, dark: Boolean) = capture(
        name = "pagos_ticket_${nombre}_${tema(dark)}",
        dark = dark
    ) {
        Ticket(state)
    }

    private fun tema(dark: Boolean) = if (dark) "dark" else "light"
}

@Composable
private fun Ticket(state: TicketDePagoUiState) {
    TicketDePagoContent(
        state = state,
        onAtras = {},
        onImprimir = {},
        onCambiarImpresora = {},
        onElegirImpresora = {},
        onReintentar = {}
    )
}
