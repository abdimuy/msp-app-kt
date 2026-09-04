package com.example.msp_app.feature.pagos.screenshot

import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.msp_app.core.designsystem.theme.FontSizeLevel
import com.example.msp_app.feature.pagos.ui.AbonoFixtures
import com.example.msp_app.feature.pagos.ui.FalloDeLaFoto
import com.example.msp_app.feature.pagos.ui.components.SeccionDeComprobantes
import org.junit.Test

/**
 * La sección de comprobantes (Task 22), **capturada por sí sola** y en la
 * matriz completa: claro × oscuro × 1.0/1.5/2.0.
 *
 * ## Por qué NO va dentro del golden de la pantalla
 *
 * Se intentó primero, y el golden salió **idéntico** al de la captura sin
 * fotos: la sección vive debajo del teclado, dentro de la columna que hace
 * scroll, y a 360×800dp queda entera **fuera de la pantalla**. Un golden así
 * está verde porque no ve nada — exactamente lo que la lección de la Task 21
 * advierte cuando dice que el verde de Roborazzi significa "no cambió", no "se
 * ve bien". Capturar la pieza sola es lo que hace que estos seis PNG prueben
 * algo. (Que la sección quede bajo la línea de flotación está reportado como
 * hallazgo de diseño, no escondido aquí.)
 *
 * El estado capturado lleva **las tres cosas a la vez** —dos comprobantes, el
 * aviso ámbar y el botón— porque es la combinación con más texto por pixel, o
 * sea donde 2.0 aprieta de verdad. Probarla vacía sería probarla donde no puede
 * fallar.
 */
class ComprobantesMatrixScreenshotTest : PagosScreenshotTest() {

    @Test
    fun `seccion light normal`() = seccion(dark = false, nivel = FontSizeLevel.NORMAL)

    @Test
    fun `seccion light grande`() = seccion(dark = false, nivel = FontSizeLevel.GRANDE)

    @Test
    fun `seccion light muy grande`() = seccion(dark = false, nivel = FontSizeLevel.MUY_GRANDE)

    @Test
    fun `seccion dark normal`() = seccion(dark = true, nivel = FontSizeLevel.NORMAL)

    @Test
    fun `seccion dark grande`() = seccion(dark = true, nivel = FontSizeLevel.GRANDE)

    @Test
    fun `seccion dark muy grande`() = seccion(dark = true, nivel = FontSizeLevel.MUY_GRANDE)

    private fun seccion(dark: Boolean, nivel: FontSizeLevel) = capture(
        name = "pagos_abono_seccion_${if (dark) "dark" else "light"}_${sufijoDe(nivel)}",
        dark = dark,
        nivel = nivel
    ) {
        Comprobantes()
    }

    @Composable
    private fun Comprobantes() {
        SeccionDeComprobantes(
            comprobantes = AbonoFixtures.enCapturaConComprobantes().comprobantes,
            fallo = FalloDeLaFoto.NO_SE_PUDO_TOMAR,
            puedeAgregar = true,
            onAgregar = {},
            onQuitar = {},
            // El mismo margen horizontal que la columna de la pantalla, para que
            // el ancho de la pieza sea el real y no uno de laboratorio.
            modifier = Modifier.padding(horizontal = 16.dp)
        )
    }
}
