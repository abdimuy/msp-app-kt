package com.example.msp_app.feature.pagos.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.DpRect
import com.example.msp_app.core.designsystem.theme.FontSizeLevel
import com.example.msp_app.core.designsystem.theme.LocalFontSizeLevel
import com.example.msp_app.core.designsystem.theme.MspTheme
import com.example.msp_app.core.testing.RobolectricTestBase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.robolectric.annotation.Config

/**
 * A escala grande, los tres datos de la venta se apilan en vez de pelearse por
 * 360dp de ancho.
 *
 * El golden `pagos_venta_light_2_0` mostró el defecto antes de que existiera
 * `TresDatos`: la fila `abonos · parcialidad · frecuencia` no cabe a
 * `MUY_GRANDE` y el tercer dato quedaba aplastado hasta desaparecer. Un dato
 * que no se puede leer no es un detalle visual — es información perdida justo
 * para el usuario que más ayuda necesita.
 *
 * ## Por qué se afirma el layout y no el ancho del texto
 *
 * El primer intento midió el ancho de cada etiqueta con
 * `getUnclippedBoundsInRoot()`. **No sirve, y se comprobó:** sin
 * `@GraphicsMode(NATIVE)` Robolectric no mide texto de verdad — "abonos"
 * reportaba 3.5dp de ancho en TODAS las variantes, con y sin el arreglo, así
 * que el aserto pasaba o fallaba por igual y no distinguía nada (regla de
 * control positivo: una ausencia no es un hallazgo hasta probar que la consulta
 * la habría encontrado).
 *
 * Lo que sí es determinista en este entorno es la POSICIÓN: en fila los tres
 * comparten renglón, apilados cada uno tiene el suyo. Eso es exactamente la
 * conducta que `TresDatos` decide, y el control de reversión lo confirma —
 * con la fila fija, los dos tests de escala grande se ponen rojos.
 */
@Config(qualifiers = "w360dp-h800dp-xhdpi")
class NadaSeSaleDePantallaTest : RobolectricTestBase() {

    @get:Rule
    val composeTestRule = createComposeRule()

    private fun ventaA(nivel: FontSizeLevel) {
        composeTestRule.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(
                LocalDensity provides Density(density.density, nivel.nominalScale),
                LocalFontSizeLevel provides nivel
            ) {
                MspTheme(darkTheme = false, animateColors = false) { Venta() }
            }
        }
    }

    @Composable
    private fun Venta() {
        DetalleVentaContent(
            state = DetalleVentaUiState(cargando = false, detalle = PagosFixtures.detalleVenta()),
            onAtras = {},
            onRegistrarAbono = {},
            onRegistrarVisita = {},
            onMasAcciones = {},
            onUsarLiquidacion = {},
            onVerAbonos = {}
        )
    }

    private fun bordesDe(etiqueta: String): DpRect =
        composeTestRule.onNodeWithText(etiqueta).performScrollTo().getUnclippedBoundsInRoot()

    @Test
    fun `en NORMAL los tres datos comparten renglon`() {
        ventaA(FontSizeLevel.NORMAL)
        val abonos = bordesDe("abonos")
        val parcialidad = bordesDe("parcialidad")
        val frecuencia = bordesDe("frecuencia")
        assertEquals(abonos.top, parcialidad.top)
        assertEquals(abonos.top, frecuencia.top)
        assertTrue(
            "no van en fila",
            abonos.left < parcialidad.left && parcialidad.left < frecuencia.left
        )
    }

    @Test
    fun `en GRANDE los tres datos se apilan`() {
        ventaA(FontSizeLevel.GRANDE)
        afirmaApilados()
    }

    @Test
    fun `en MUY_GRANDE los tres datos se apilan`() {
        ventaA(FontSizeLevel.MUY_GRANDE)
        afirmaApilados()
    }

    private fun afirmaApilados() {
        val abonos = bordesDe("abonos")
        val parcialidad = bordesDe("parcialidad")
        val frecuencia = bordesDe("frecuencia")
        assertTrue(
            "parcialidad sigue en el renglón de abonos: " + abonos.bottom + " vs " + parcialidad.top,
            parcialidad.top >= abonos.bottom
        )
        assertTrue(
            "frecuencia sigue en el renglón de parcialidad: " + parcialidad.bottom + " vs " + frecuencia.top,
            frecuencia.top >= parcialidad.bottom
        )
        assertEquals(abonos.left, parcialidad.left)
        assertEquals(abonos.left, frecuencia.left)
    }
}
