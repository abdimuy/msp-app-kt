package com.example.msp_app.feature.ubicacion.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import com.example.msp_app.core.testing.RobolectricTestBase
import com.example.msp_app.feature.ubicacion.domain.ClaseDeLugar
import com.example.msp_app.feature.ubicacion.screenshot.MapaDelDuenoFixture
import com.example.msp_app.feature.ubicacion.ui.components.CARD_TAG
import com.example.msp_app.feature.ubicacion.ui.components.ENCABEZADO_DEL_DETALLE_TAG
import com.example.msp_app.feature.ubicacion.ui.components.VER_TODOS_TAG
import com.example.msp_app.feature.ubicacion.ui.components.VOLVER_A_LA_LISTA_TAG
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.robolectric.annotation.Config

/**
 * **Al tocar un lugar la hoja abre en la altura media y deja ver el mapa.**
 *
 * El dueño: *"ese segundo sheet no se puede bajar y ocupa mucho espacio de la
 * pantalla"*. Antes el detalle medía el 64.8% del alto (482 dp de 744). Ahora
 * abre en 300 dp (mock aprobado `detalle-del-lugar.html`) y deja ≥ 440 dp de mapa.
 *
 * Se mide el alto REAL de los nodos, no una constante. Control positivo: en la
 * completa la misma medición da menos de 440 dp de mapa, así que la afirmación
 * sí puede fallar.
 */
// h800 con barras = 744 dp de área útil, el aparato del mock.
@Config(qualifiers = "w360dp-h800dp-xhdpi")
class ElDetalleDejaVerElMapaTest : RobolectricTestBase() {

    @get:Rule
    val composeTestRule = createComposeRule()

    private fun montar() {
        composeTestRule.setContent {
            var estado by remember { mutableStateOf(MapaDelDuenoFixture.estado()) }
            UbicacionScreen(
                state = estado,
                onAtras = {},
                onComoLlegar = {},
                onFiltro = {},
                onTocarLugar = { estado = estado.copy(lugarTocado = it) },
                pintarMapa = false
            )
        }
        composeTestRule.waitForIdle()
    }

    private fun dp(px: Int): Float = px / composeTestRule.density.density
    private fun altoHoja(): Float = dp(
        composeTestRule.onNodeWithTag(HOJA_TAG).fetchSemanticsNode().size.height
    )
    private fun mapaLibre(): Float =
        dp(composeTestRule.onRoot().fetchSemanticsNode().size.height) - altoHoja()

    @Test
    fun `tocar un lugar abre la media y deja al menos 440 dp de mapa`() {
        montar()
        composeTestRule.onNodeWithTag(CARD_TAG + ClaseDeLugar.DONDE_MAS_PAGA.name).performClick()
        composeTestRule.waitForIdle()

        assertEquals("la hoja no abrió en la media", 300f, altoHoja(), 2f)
        assertTrue("sólo quedan ${mapaLibre()} dp de mapa", mapaLibre() >= 440f)

        // Control positivo: "Ver todos" sube a la completa y ahí el mapa SÍ queda chico.
        composeTestRule.onNodeWithTag(VER_TODOS_TAG).performClick()
        composeTestRule.waitForIdle()
        assertTrue("la medición no distingue alturas: ${mapaLibre()} dp", mapaLibre() < 440f)
    }

    @Test
    fun `arrastrar hacia abajo baja a la minima sin cerrar y el encabezado la regresa a la media`() {
        montar()
        composeTestRule.onNodeWithTag(CARD_TAG + ClaseDeLugar.DONDE_MAS_PAGA.name).performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag(ENCABEZADO_DEL_DETALLE_TAG).performTouchInput {
            swipe(center, center + Offset(0f, 600f), 150L)
        }
        composeTestRule.waitForIdle()
        assertEquals("no bajó a la mínima", 124f, altoHoja(), 2f)
        composeTestRule.onNodeWithTag(VOLVER_A_LA_LISTA_TAG).assertExists()

        // Otro arrastre hacia abajo no cierra: la mínima es el piso.
        composeTestRule.onNodeWithTag(ENCABEZADO_DEL_DETALLE_TAG).performTouchInput {
            swipe(center, center + Offset(0f, 600f), 150L)
        }
        composeTestRule.waitForIdle()
        assertEquals("el arrastre cerró o bajó de la mínima", 124f, altoHoja(), 2f)
        composeTestRule.onNodeWithTag(VOLVER_A_LA_LISTA_TAG).assertExists()

        composeTestRule.onNodeWithTag(ENCABEZADO_DEL_DETALLE_TAG).performClick()
        composeTestRule.waitForIdle()
        assertEquals("el encabezado no subió a la media", 300f, altoHoja(), 2f)
    }
}
