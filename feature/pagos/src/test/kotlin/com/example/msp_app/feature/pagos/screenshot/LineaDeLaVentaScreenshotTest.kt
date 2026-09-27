package com.example.msp_app.feature.pagos.screenshot

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.example.msp_app.core.designsystem.theme.FontSizeLevel
import com.example.msp_app.core.designsystem.theme.MspTheme
import com.example.msp_app.feature.pagos.ui.LineaDeLaVenta
import com.example.msp_app.feature.pagos.ui.PagosFixtures
import com.example.msp_app.feature.pagos.ui.components.LabelDeSeccion
import org.junit.Test

/**
 * **"Lo que ha pasado"** —la línea de contactos de la cuenta— en la matriz de
 * siempre: `{light, dark} × {NORMAL 1.0, GRANDE 1.5, MUY_GRANDE 2.0}`.
 *
 * Tiene golden propio por la misma razón que la garantía y el historial: la
 * sección vive muy por debajo del pliegue de la pantalla de venta y los
 * `pagos_venta_*` de pantalla completa **nunca la retratan** — se comprobó
 * mirándolos, y a `MUY_GRANDE` la foto se acaba en el bloque del saldo.
 *
 * ## Ya no hay pastillas de alcance ni de filtro que fotografiar
 *
 * Hasta la ronda anterior esta clase también cubría dos defectos de las
 * pastillas *"Esta venta"* / *"Todo el cliente"* —el recorte sin
 * `horizontalScroll` y la pastilla apagada transparente—. El dueño las quitó:
 * la sección ya no mezcla cuentas ajenas ni filtra por tipo, sólo pinta los
 * cinco contactos más recientes de ESTA cuenta y un enlace a "ver los N
 * contactos". Los dos defectos que esas fotos protegían no pueden volver
 * porque el control que los producía ya no existe.
 *
 * ## Con historia larga, no con el contacto único del fixture compartido
 *
 * `PagosFixtures.detalleVenta()` sólo trae UN contacto de esa venta —bastaba
 * para los goldens que comparte con `pagos_cliente_*`/`pagos_venta_*`, pero no
 * fotografía ni el tope de cinco ni dos meses de calendario. Se usa
 * [PagosFixtures.detalleVentaConHistoriaLarga] en su lugar, **sólo aquí y en
 * `BitacoraMatrixScreenshotTest`**, para no mover los goldens de las demás
 * pantallas.
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

    private fun linea(dark: Boolean, nivel: FontSizeLevel) = capture(
        name = "pagos_venta_linea_${tema(dark)}_${sufijoDe(nivel)}",
        dark = dark,
        nivel = nivel
    ) {
        Seccion()
    }

    @Composable
    private fun Seccion() {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = MspTheme.spacing.md)
        ) {
            LabelDeSeccion("lo que ha pasado")
            LineaDeLaVenta(
                detalle = PagosFixtures.detalleVentaConHistoriaLarga(),
                onVerAbonos = {},
                onVerContactos = {}
            )
        }
    }

    private fun tema(dark: Boolean) = if (dark) "dark" else "light"
}
