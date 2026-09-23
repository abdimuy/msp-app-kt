package com.example.msp_app.feature.pagos.screenshot

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.example.msp_app.core.designsystem.theme.FontSizeLevel
import com.example.msp_app.core.designsystem.theme.MspTheme
import com.example.msp_app.feature.pagos.domain.model.EstadoDeGarantia
import com.example.msp_app.feature.pagos.ui.PagosFixtures
import com.example.msp_app.feature.pagos.ui.components.LabelDeSeccion
import com.example.msp_app.feature.pagos.ui.components.TarjetaDeGarantia
import org.junit.Test

/**
 * La tarjeta de garantía, en la misma matriz que el resto:
 * `{light, dark} × {NORMAL 1.0, GRANDE 1.5, MUY_GRANDE 2.0}`.
 *
 * Tiene golden propio por la misma razón que el historial: la sección vive
 * debajo del pliegue de la pantalla de venta (después de productos) y el
 * golden de pantalla completa nunca la retrataría. Se capturan los tres
 * estados juntos para que el chip quede fijado en los tres — el color no es el
 * único portador, pero sí es el que una regresión rompe en silencio.
 */
class GarantiaScreenshotTest : PagosScreenshotTest() {

    @Test
    fun `garantia light normal`() = garantia(dark = false, nivel = FontSizeLevel.NORMAL)

    @Test
    fun `garantia light grande`() = garantia(dark = false, nivel = FontSizeLevel.GRANDE)

    @Test
    fun `garantia light muy grande`() = garantia(dark = false, nivel = FontSizeLevel.MUY_GRANDE)

    @Test
    fun `garantia dark normal`() = garantia(dark = true, nivel = FontSizeLevel.NORMAL)

    @Test
    fun `garantia dark grande`() = garantia(dark = true, nivel = FontSizeLevel.GRANDE)

    @Test
    fun `garantia dark muy grande`() = garantia(dark = true, nivel = FontSizeLevel.MUY_GRANDE)

    private fun garantia(dark: Boolean, nivel: FontSizeLevel) = capture(
        name = "pagos_garantia_${if (dark) "dark" else "light"}_${sufijoDe(nivel)}",
        dark = dark,
        nivel = nivel
    ) {
        Garantias()
    }
}

@Composable
private fun Garantias() {
    val base = PagosFixtures.garantiaDeLaVenta()
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = MspTheme.spacing.md)
    ) {
        LabelDeSeccion("garantía")
        TarjetaDeGarantia(garantia = base, onVerGarantia = {})
        Spacer(Modifier.height(MspTheme.spacing.sm))
        TarjetaDeGarantia(
            garantia = base.copy(estado = EstadoDeGarantia.RECOLECTADA),
            onVerGarantia = {}
        )
        Spacer(Modifier.height(MspTheme.spacing.sm))
        TarjetaDeGarantia(
            garantia = base.copy(estado = EstadoDeGarantia.ENTREGADA),
            onVerGarantia = {}
        )
    }
}
