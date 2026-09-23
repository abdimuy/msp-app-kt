package com.example.msp_app.feature.pagos.screenshot

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.example.msp_app.core.designsystem.theme.MspTheme
import com.example.msp_app.feature.pagos.domain.model.DetalleVenta
import com.example.msp_app.feature.pagos.ui.DatosDeLaVenta
import com.example.msp_app.feature.pagos.ui.PagosFixtures
import com.example.msp_app.feature.pagos.ui.components.FilaDeVenta
import com.example.msp_app.feature.pagos.ui.components.LabelDeSeccion
import org.junit.Test

/**
 * La ficha "datos de la venta" **con los seis campos recuperados**, y la
 * tarjeta de venta con su nuevo pie.
 *
 * Golden propio por la misma razón que la nota y la garantía: las dos piezas
 * viven muy por debajo del pliegue y los `pagos_venta_*` / `pagos_cliente_*` de
 * pantalla completa capturan sólo el viewport (w360dp-h800dp), así que ningún
 * golden existente las retrata. Sin esto, el cambio no tiene ninguna foto.
 *
 * Tres estados, y los tres son casos de campo:
 * - **completa** — dos vendedores y precio a corto plazo. Lo que la pantalla
 *   legada enseñaba.
 * - **pelada** — teléfono, dirección, zona, aval y vendedores vacíos, y sin
 *   oferta a corto plazo: se ve que cada hueco cae en el marcador de "no se
 *   sabe" y que la fila del precio corto **no está**, en vez de decir "$0".
 * - **tarjeta** — el pie de la venta diciendo lo abonado, y el mismo pie con el
 *   ojo cerrado.
 */
class DatosDeLaVentaScreenshotTest : PagosScreenshotTest() {

    @Test
    fun `datos de la venta light`() = ficha(dark = false)

    @Test
    fun `datos de la venta dark`() = ficha(dark = true)

    @Test
    fun `datos de la venta pelada light`() = fichaPelada(dark = false)

    @Test
    fun `tarjeta de venta con lo abonado light`() = tarjeta(dark = false)

    @Test
    fun `tarjeta de venta con lo abonado dark`() = tarjeta(dark = true)

    private fun ficha(dark: Boolean) = capture(
        name = "pagos_venta_datos_${tema(dark)}",
        dark = dark
    ) {
        Ficha(PagosFixtures.detalleVenta())
    }

    private fun fichaPelada(dark: Boolean) = capture(
        name = "pagos_venta_datos_pelada_${tema(dark)}",
        dark = dark
    ) {
        Ficha(
            PagosFixtures.detalleVenta().copy(
                telefono = "",
                direccion = "",
                zona = "",
                aval = "",
                vendedores = emptyList(),
                mesesACortoPlazo = 0
            )
        )
    }

    private fun tarjeta(dark: Boolean) = capture(
        name = "pagos_venta_tarjeta_abonado_${tema(dark)}",
        dark = dark
    ) {
        Tarjetas()
    }

    private fun tema(dark: Boolean): String = if (dark) "dark" else "light"
}

@Composable
private fun Ficha(detalle: DetalleVenta) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = MspTheme.spacing.md)
    ) {
        LabelDeSeccion("datos de la venta")
        DatosDeLaVenta(detalle)
    }
}

@Composable
private fun Tarjetas() {
    val venta = PagosFixtures.detalleCliente().ventas.first()
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = MspTheme.spacing.md)
    ) {
        FilaDeVenta(venta = venta, onAbrir = {})
        FilaDeVenta(venta = venta, onAbrir = {}, ocultos = true)
    }
}
