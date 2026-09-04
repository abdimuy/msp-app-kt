package com.example.msp_app.feature.visitas.screenshot

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import com.example.msp_app.core.designsystem.theme.FontSizeLevel
import com.example.msp_app.core.designsystem.theme.MspTheme
import com.example.msp_app.feature.visitas.ui.VisitaFixtures
import com.example.msp_app.feature.visitas.ui.components.BotonDeFotoEnLinea
import com.example.msp_app.feature.visitas.ui.components.SeccionDeComprobantesDeVisita
import org.junit.Test

/**
 * La **sección de comprobantes** de la visita, sola, en su estado más apretado:
 * dos fotos, el aviso ámbar y el botón de agregar — más el afordante de la fila
 * del encabezado, que es el punto de entrada visible.
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
 * Matriz completa: claro × oscuro × 1.0/1.5/2.0.
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

    private fun seccion(dark: Boolean, nivel: FontSizeLevel) {
        val state = VisitaFixtures.conComprobantes()
        capture("visitas_comprobantes_${tema(dark)}_${sufijoDe(nivel)}", dark, nivel) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(MspTheme.spacing.md)
            ) {
                BotonDeFotoEnLinea(
                    cuantos = state.comprobantes.size,
                    habilitado = true,
                    onAgregar = {}
                )
                SeccionDeComprobantesDeVisita(
                    comprobantes = state.comprobantes,
                    fallo = state.falloDeLaFoto,
                    puedeAgregar = true,
                    onAgregar = {},
                    onQuitar = {}
                )
            }
        }
    }
}
