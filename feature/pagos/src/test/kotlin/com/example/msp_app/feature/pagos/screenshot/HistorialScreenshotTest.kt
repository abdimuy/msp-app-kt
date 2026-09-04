package com.example.msp_app.feature.pagos.screenshot

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.example.msp_app.core.designsystem.theme.FontSizeLevel
import com.example.msp_app.core.designsystem.theme.MspTheme
import com.example.msp_app.feature.pagos.ui.PagosFixtures
import com.example.msp_app.feature.pagos.ui.components.LabelDeSeccion
import com.example.msp_app.feature.pagos.ui.components.RielDeMeses
import com.example.msp_app.feature.pagos.ui.components.RitmoDeSemanas
import org.junit.Test

/**
 * El historial de pagos —**ritmo + riel**— con su propio golden.
 *
 * Existe aparte de [DetalleMatrixScreenshotTest] por una razón concreta: el
 * viewport del golden es 360×800 y el historial vive DEBAJO del pliegue de la
 * pantalla de venta, así que la matriz de pantalla completa nunca lo retrata.
 * Un golden que no incluye la pieza que la tarea vino a construir no la
 * protege de una regresión.
 *
 * Lo que estos goldens sostienen: las doce barras con sus tres tratamientos, y
 * que **la agrupación por mes es visible sin colapsar** — el nodo cuadrado del
 * mes, su nombre y su subtotal interrumpiendo el riel, con los pagos colgando.
 */
class HistorialScreenshotTest : PagosScreenshotTest() {

    @Test
    fun `historial light normal`() = historial(dark = false, nivel = FontSizeLevel.NORMAL)

    @Test
    fun `historial light grande`() = historial(dark = false, nivel = FontSizeLevel.GRANDE)

    @Test
    fun `historial light muy grande`() = historial(dark = false, nivel = FontSizeLevel.MUY_GRANDE)

    @Test
    fun `historial dark normal`() = historial(dark = true, nivel = FontSizeLevel.NORMAL)

    @Test
    fun `historial dark grande`() = historial(dark = true, nivel = FontSizeLevel.GRANDE)

    @Test
    fun `historial dark muy grande`() = historial(dark = true, nivel = FontSizeLevel.MUY_GRANDE)

    private fun historial(dark: Boolean, nivel: FontSizeLevel) = capture(
        name = "pagos_historial_${if (dark) "dark" else "light"}_${sufijoDe(nivel)}",
        dark = dark,
        nivel = nivel
    ) {
        Historial()
    }
}

@Composable
private fun Historial() {
    val historial = PagosFixtures.historial()
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = MspTheme.spacing.md)
    ) {
        LabelDeSeccion("ritmo · últimas 12 semanas")
        RitmoDeSemanas(historial)
        LabelDeSeccion("pagos")
        RielDeMeses(historial.meses)
    }
}
