package com.example.msp_app.feature.pagos.screenshot

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.example.msp_app.core.designsystem.theme.FontSizeLevel
import com.example.msp_app.core.designsystem.theme.MspTheme
import com.example.msp_app.feature.pagos.ui.AccionesDeLaLinea
import com.example.msp_app.feature.pagos.ui.LineaDeLaVenta
import com.example.msp_app.feature.pagos.ui.PagosFixtures
import com.example.msp_app.feature.pagos.ui.components.LabelDeSeccion
import org.junit.Test

/**
 * **"Lo que ha pasado"** —el alcance, los filtros y la línea de contactos— en
 * la matriz de siempre: `{light, dark} × {NORMAL 1.0, GRANDE 1.5,
 * MUY_GRANDE 2.0}`.
 *
 * Tiene golden propio por la misma razón que la garantía y el historial: la
 * sección vive muy por debajo del pliegue de la pantalla de venta y los
 * `pagos_venta_*` de pantalla completa **nunca la retratan** — se comprobó
 * mirándolos, y a `MUY_GRANDE` la foto se acaba en el bloque del saldo.
 *
 * ## Los dos defectos que esta foto existe para no dejar volver
 *
 * **Uno — *"Todo el cliente"* quedaba cortado para siempre.** La fila del
 * alcance era `fillMaxWidth()` sin `horizontalScroll`, al revés que la de
 * filtros que va pegada abajo. A 360 dp y `MUY_GRANDE` la segunda pastilla toca
 * el borde, y sin desplazamiento la etiqueta se quedaba en *"Todo el"*: el
 * cobrador leyendo una opción que no existe. Como el `Text` tampoco traía
 * `overflow`, el corte era a media palabra y **sin elipsis**, o sea
 * indistinguible de una etiqueta completa. Ninguna de las dos mitades se puede
 * cobrar con un assert —Robolectric no mide texto de verdad—, así que las cobra
 * esta foto.
 *
 * **Dos — la pastilla apagada no se leía como control.** Iba en
 * `Color.Transparent`: texto pelón justo encima de una fila de filtros cuyas
 * pastillas apagadas sí traen `surface2`. Dos filas de controles pegadas
 * hablando dos idiomas. En la foto las dos se miran juntas, que es como se ven
 * en el teléfono.
 *
 * ## Por qué se capturan los dos alcances
 *
 * Con *"Esta venta"* puesto —el arranque— la pastilla **apagada** es *"Todo el
 * cliente"*, que es la larga: el caso del recorte. Con el alcance abierto la
 * misma etiqueta va **encendida** y además se ve la línea completa del
 * domicilio, con la barra de marca distinguiendo lo de esta cuenta. Un solo
 * estado dejaría la mitad del arreglo sin foto.
 */
class LineaDeLaVentaScreenshotTest : PagosScreenshotTest() {

    @Test
    fun `linea light normal`() = linea(dark = false, nivel = FontSizeLevel.NORMAL)

    @Test
    fun `linea light grande`() = linea(dark = false, nivel = FontSizeLevel.GRANDE)

    @Test
    fun `linea light muy grande`() = linea(dark = false, nivel = FontSizeLevel.MUY_GRANDE)

    @Test
    fun `linea dark normal`() = linea(dark = true, nivel = FontSizeLevel.NORMAL)

    @Test
    fun `linea dark grande`() = linea(dark = true, nivel = FontSizeLevel.GRANDE)

    @Test
    fun `linea dark muy grande`() = linea(dark = true, nivel = FontSizeLevel.MUY_GRANDE)

    /** El alcance abierto: la etiqueta larga encendida y la línea entera debajo. */
    @Test
    fun `linea todo el cliente light`() = todoElCliente(dark = false)

    @Test
    fun `linea todo el cliente dark`() = todoElCliente(dark = true)

    private fun linea(dark: Boolean, nivel: FontSizeLevel) = capture(
        name = "pagos_venta_linea_${tema(dark)}_${sufijoDe(nivel)}",
        dark = dark,
        nivel = nivel
    ) {
        Seccion(soloEstaVenta = true)
    }

    private fun todoElCliente(dark: Boolean) = capture(
        name = "pagos_venta_linea_todo_${tema(dark)}",
        dark = dark,
        nivel = FontSizeLevel.MUY_GRANDE
    ) {
        Seccion(soloEstaVenta = false)
    }

    @Composable
    private fun Seccion(soloEstaVenta: Boolean) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = MspTheme.spacing.md)
        ) {
            LabelDeSeccion("lo que ha pasado")
            LineaDeLaVenta(
                detalle = PagosFixtures.detalleVenta(),
                linea = AccionesDeLaLinea(soloEstaVenta = soloEstaVenta),
                onVerAbonos = {}
            )
        }
    }

    private fun tema(dark: Boolean) = if (dark) "dark" else "light"
}
