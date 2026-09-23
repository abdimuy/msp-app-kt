package com.example.msp_app.feature.pagos.screenshot

import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.msp_app.core.designsystem.theme.FontSizeLevel
import com.example.msp_app.feature.pagos.ui.AbonoFixtures
import com.example.msp_app.feature.pagos.ui.RegistrarAbonoUiState
import com.example.msp_app.feature.pagos.ui.components.HojaDeOrigenDelComprobante
import com.example.msp_app.feature.pagos.ui.components.SeccionDeComprobantes
import org.junit.Test

/**
 * La rejilla de comprobantes, **capturada por sí sola** y en la matriz completa:
 * claro × oscuro × 1.0/1.5/2.0.
 *
 * ## Por qué NO va dentro del golden de la pantalla
 *
 * Se intentó primero, y el golden salió **idéntico** al de la captura sin fotos:
 * la sección vive debajo del teclado, dentro de la columna que hace scroll, y a
 * 360×800dp queda entera **fuera de la pantalla**. Un golden así está verde
 * porque no ve nada — exactamente lo que la lección de la Task 21 advierte cuando
 * dice que el verde de Roborazzi significa "no cambió", no "se ve bien".
 * Capturar la pieza sola es lo que hace que estos PNG prueben algo. (Que la
 * sección quede bajo la línea de flotación está reportado como hallazgo de
 * diseño, no escondido aquí.)
 *
 * El estado capturado lleva **todo a la vez** —el «+», dos fotos con miniatura,
 * un PDF sin vista previa y un cuadro ámbar— porque es la combinación con más
 * cosas por pixel, o sea donde 2.0 aprieta de verdad y donde la rejilla tiene que
 * envolver. Probarla vacía sería probarla donde no puede fallar.
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

    /** La rejilla LLENA: cinco fotos y **sin** el «+», porque ya no caben más. */
    @Test
    fun `seccion llena light`() = llena(dark = false)

    @Test
    fun `seccion llena dark`() = llena(dark = true)

    /** La hoja del «+»: las tres opciones con su explicación, y el pie que cuenta. */
    @Test
    fun `origen light normal`() = hoja(dark = false, nivel = FontSizeLevel.NORMAL)

    @Test
    fun `origen light muy grande`() = hoja(dark = false, nivel = FontSizeLevel.MUY_GRANDE)

    @Test
    fun `origen dark normal`() = hoja(dark = true, nivel = FontSizeLevel.NORMAL)

    @Test
    fun `origen dark muy grande`() = hoja(dark = true, nivel = FontSizeLevel.MUY_GRANDE)

    private fun seccion(dark: Boolean, nivel: FontSizeLevel) =
        rejilla(AbonoFixtures.enCapturaConComprobantes(), "seccion", dark, nivel)

    private fun llena(dark: Boolean) =
        rejilla(AbonoFixtures.comprobantesLlenos(), "seccion_llena", dark, FontSizeLevel.NORMAL)

    private fun rejilla(
        state: RegistrarAbonoUiState,
        nombre: String,
        dark: Boolean,
        nivel: FontSizeLevel
    ) = capture(
        name = "pagos_abono_${nombre}_${if (dark) "dark" else "light"}_${sufijoDe(nivel)}",
        dark = dark,
        nivel = nivel
    ) {
        SeccionDeComprobantes(
            comprobantes = state.comprobantes,
            miniaturas = state.miniaturas,
            intentos = state.intentos,
            puedeAgregar = state.sePuedeAgregarFoto,
            onAgregar = {},
            onQuitar = {},
            // El mismo margen horizontal que la columna de la pantalla, para que
            // el ancho de la pieza sea el real y no uno de laboratorio: la
            // rejilla parte ESE ancho en tres.
            modifier = Modifier.padding(horizontal = 16.dp)
        )
    }

    private fun hoja(dark: Boolean, nivel: FontSizeLevel) {
        val state = AbonoFixtures.eligiendoOrigen()
        capture(
            name = "pagos_abono_origen_${if (dark) "dark" else "light"}_${sufijoDe(nivel)}",
            dark = dark,
            nivel = nivel
        ) {
            HojaDeOrigenDelComprobante(
                espaciosLibres = state.espaciosLibres,
                onOrigen = {},
                onCerrar = {}
            )
        }
    }
}
