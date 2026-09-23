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
import com.example.msp_app.feature.pagos.ui.components.TarjetaDeNotaDestacada
import org.junit.Test

/**
 * Las notas del detalle de venta, **sus dos estados y sus dos sitios**: la nota
 * con contenido, arriba y en ámbar, y la tarjeta gris del fondo que dice que no
 * hay ninguna.
 *
 * Golden propio por la misma razón que la garantía y el historial: la tarjeta
 * del vacío va AL FONDO —después de "datos de la venta"— y los `pagos_venta_*`
 * de pantalla completa capturan sólo lo que cabe en el viewport
 * (w360dp-h800dp), así que nunca la retratan.
 *
 * Las dos se fotografían juntas **a propósito**: es la foto que deja ver de un
 * vistazo que son piezas distintas y que la de arriba NO trae botón de editar
 * —la nota la manda la oficina—, que es lo que un assert cuenta pero no enseña.
 * Sólo `{light, dark}`, sin la matriz de escala: es texto simple sin defecto de
 * layout que proteger a escalas grandes.
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
        TarjetaDeNotaDestacada(
            rotulo = "nota de la venta",
            nota = "Entrega en la puerta de atrás"
        )
        Spacer(Modifier.height(MspTheme.spacing.md))
        TarjetaDeNotaDeLaVenta()
    }
}
