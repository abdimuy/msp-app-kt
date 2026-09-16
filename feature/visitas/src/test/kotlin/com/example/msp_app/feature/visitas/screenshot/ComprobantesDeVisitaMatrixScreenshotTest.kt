package com.example.msp_app.feature.visitas.screenshot

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import com.example.msp_app.core.designsystem.theme.FontSizeLevel
import com.example.msp_app.core.designsystem.theme.MspTheme
import com.example.msp_app.feature.visitas.ui.RegistrarVisitaUiState
import com.example.msp_app.feature.visitas.ui.VisitaFixtures
import com.example.msp_app.feature.visitas.ui.components.HojaDeOrigenDelComprobante
import com.example.msp_app.feature.visitas.ui.components.SeccionDeComprobantesDeVisita
import org.junit.Test

/**
 * La **rejilla de comprobantes** de la visita, sola, en su estado más apretado:
 * el «+», dos fotos, un PDF sin vista previa y un cuadro ámbar — cinco cuadros,
 * que es justo lo que obliga a la rejilla a envolver a la segunda fila.
 *
 * ## Por qué la sección sola y no la pantalla entera
 *
 * Se midió primero con la pantalla completa: a 360×800dp la sección cae **debajo
 * de la línea de flotación** y el golden salía idéntico al de sin fotos. Un
 * golden así está verde porque no ve nada — la lección de la Task 21, que la
 * Task 22 tuvo que volver a aprender del lado del dinero. La matriz de la
 * pantalla completa sigue existiendo aparte y cubre lo que sí se ve; ésta cubre
 * lo que hay que mirar de cerca.
 *
 * Matriz completa: claro × oscuro × 1.0/1.5/2.0. La hoja del «+» y la rejilla
 * llena van aparte y **solo en 1.0 y 2.0**: lo que prueban es una composición
 * distinta, no el comportamiento de la escala intermedia, que ya cubre la matriz
 * de arriba.
 */
class ComprobantesDeVisitaMatrixScreenshotTest : VisitasScreenshotTest() {

    @Test
    fun `comprobantes light normal`() = seccion(false, FontSizeLevel.NORMAL)

    @Test
    fun `comprobantes light grande`() = seccion(false, FontSizeLevel.GRANDE)

    @Test
    fun `comprobantes light muy grande`() = seccion(false, FontSizeLevel.MUY_GRANDE)

    @Test
    fun `comprobantes dark normal`() = seccion(true, FontSizeLevel.NORMAL)

    @Test
    fun `comprobantes dark grande`() = seccion(true, FontSizeLevel.GRANDE)

    @Test
    fun `comprobantes dark muy grande`() = seccion(true, FontSizeLevel.MUY_GRANDE)

    /** La rejilla LLENA: cinco fotos y **sin** el «+», porque ya no caben más. */
    @Test
    fun `comprobantes llenos light`() = llenos(false)

    @Test
    fun `comprobantes llenos dark`() = llenos(true)

    /** La hoja del «+»: las tres opciones con su explicación, y el pie que cuenta. */
    @Test
    fun `origen light normal`() = hoja(false, FontSizeLevel.NORMAL)

    @Test
    fun `origen light muy grande`() = hoja(false, FontSizeLevel.MUY_GRANDE)

    @Test
    fun `origen dark normal`() = hoja(true, FontSizeLevel.NORMAL)

    @Test
    fun `origen dark muy grande`() = hoja(true, FontSizeLevel.MUY_GRANDE)

    private fun seccion(dark: Boolean, nivel: FontSizeLevel) =
        rejilla(VisitaFixtures.conComprobantes(), "comprobantes", dark, nivel)

    private fun llenos(dark: Boolean) = rejilla(
        VisitaFixtures.comprobantesLlenos(),
        "comprobantes_llenos",
        dark,
        FontSizeLevel.NORMAL
    )

    private fun rejilla(
        state: RegistrarVisitaUiState,
        nombre: String,
        dark: Boolean,
        nivel: FontSizeLevel
    ) {
        capture("visitas_${nombre}_${tema(dark)}_${sufijoDe(nivel)}", dark, nivel) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    // El mismo margen horizontal que la columna de la pantalla,
                    // para que el ancho de los cuadros sea el real y no uno de
                    // laboratorio: la rejilla parte ESE ancho en tres.
                    .padding(horizontal = MspTheme.spacing.md)
            ) {
                SeccionDeComprobantesDeVisita(
                    comprobantes = state.comprobantes,
                    miniaturas = state.miniaturas,
                    intentos = state.intentos,
                    puedeAgregar = state.sePuedeAgregarFoto,
                    onAgregar = {},
                    onQuitar = {}
                )
            }
        }
    }

    private fun hoja(dark: Boolean, nivel: FontSizeLevel) {
        val state = VisitaFixtures.eligiendoOrigen()
        capture("visitas_origen_${tema(dark)}_${sufijoDe(nivel)}", dark, nivel) {
            HojaDeOrigenDelComprobante(
                espaciosLibres = state.espaciosLibres,
                onOrigen = {},
                onCerrar = {}
            )
        }
    }
}
