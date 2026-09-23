package com.example.msp_app.feature.pagos.screenshot

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.example.msp_app.core.designsystem.theme.MspTheme
import com.example.msp_app.feature.pagos.ui.TarjetaDeNotaDeLaVenta
import org.junit.Test

/**
 * La tarjeta de "Notas" del detalle de venta, **sus dos estados**: con nota y
 * sin ella.
 *
 * Golden propio por la misma razón que la garantía y el historial: la sección
 * va AL FONDO —después de "datos de la venta"— y los `pagos_venta_*` de
 * pantalla completa capturan sólo lo que cabe en el viewport (w360dp-h800dp),
 * así que nunca la retratan. Sólo `{light, dark}`, sin la matriz de escala:
 * es texto simple de un renglón sin defecto de layout que proteger a escalas
 * grandes — a diferencia de la garantía, que sí fija un chip de color.
 */
class NotaDeLaVentaScreenshotTest : PagosScreenshotTest() {

    @Test
    fun `nota de la venta light`() = nota(dark = false)

    @Test
    fun `nota de la venta dark`() = nota(dark = true)

    private fun nota(dark: Boolean) = capture(
        name = "pagos_venta_nota_${if (dark) "dark" else "light"}",
        dark = dark
    ) {
        Notas()
    }
}

@Composable
private fun Notas() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = MspTheme.spacing.md)
    ) {
        TarjetaDeNotaDeLaVenta(nota = "Entrega en la puerta de atrás")
        Spacer(Modifier.height(MspTheme.spacing.md))
        TarjetaDeNotaDeLaVenta(nota = null)
    }
}
