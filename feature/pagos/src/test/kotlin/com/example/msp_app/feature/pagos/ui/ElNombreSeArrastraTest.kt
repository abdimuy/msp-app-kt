package com.example.msp_app.feature.pagos.ui

import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.unit.dp
import com.example.msp_app.core.common.cobranza.domain.EstadoCuenta
import com.example.msp_app.core.designsystem.theme.MspTheme
import com.example.msp_app.core.testing.RobolectricTestBase
import com.example.msp_app.feature.pagos.ui.components.NOMBRE_EN_LA_TARJETA_TAG
import com.example.msp_app.feature.pagos.ui.components.TEXTO_DEL_NOMBRE_TAG
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * El nombre de la tarjeta que no cabe se puede arrastrar con el dedo (decisión del
 * dueño, 2026-10-07) sin que el deslizado de lado cambie de segmento; uno que cabe no
 * consume el gesto, y el toque sigue abriendo la tarjeta.
 *
 * `NATIVE`: en `LEGACY` Robolectric mide el texto casi en cero (medido: 25.5 dp para
 * el nombre largo), así que ningún nombre "desborda" y la prueba no probaría nada.
 */
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h640dp-xhdpi")
class ElNombreSeArrastraTest : RobolectricTestBase() {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val segmentosElegidos = mutableListOf<SegmentoDeCobranza>()
    private val clientesAbiertos = mutableListOf<Int>()

    @Test
    fun `arrastrar un nombre que no cabe lo desplaza y no cambia de segmento`() {
        montar(NOMBRE_LARGO)
        val antes = textoDelNombre().left
        composeTestRule.mainClock.autoAdvance = false

        nombre().performTouchInput { swipeLeft(startX = right - 10f, endX = left + 10f) }
        composeTestRule.mainClock.advanceTimeBy(MEDIO_SEGUNDO)

        val despues = textoDelNombre().left
        assertTrue("el texto no se movió: $antes → $despues", despues < antes - 20.dp)
        assertEquals(emptyList<SegmentoDeCobranza>(), segmentosElegidos)

        // Tras la pausa regresa al inicio.
        composeTestRule.mainClock.autoAdvance = true
        composeTestRule.mainClock.advanceTimeBy(PAUSA_Y_REGRESO)
        composeTestRule.waitForIdle()
        assertEquals(antes, textoDelNombre().left)
    }

    /** Control positivo: el mismo gesto sobre un nombre que cabe sí cambia de segmento. */
    @Test
    fun `un nombre que cabe no consume el deslizado de segmento`() {
        montar(NOMBRE_CORTO)

        nombre().performTouchInput { swipeLeft(startX = right - 10f, endX = left + 10f) }
        composeTestRule.waitForIdle()

        assertEquals(listOf(SegmentoDeCobranza.VOLVER_A_VISITAR), segmentosElegidos)
    }

    @Test
    fun `tocar un nombre que no cabe abre la tarjeta`() {
        montar(NOMBRE_LARGO)

        nombre().performClick()

        assertEquals(listOf(CLIENTE_ID), clientesAbiertos)
    }

    private fun nombre() = composeTestRule.onAllNodesWithTag(
        NOMBRE_EN_LA_TARJETA_TAG,
        useUnmergedTree = true
    ).onFirst()

    private fun textoDelNombre() = composeTestRule
        .onAllNodesWithTag(TEXTO_DEL_NOMBRE_TAG, useUnmergedTree = true)
        .onFirst()
        .getUnclippedBoundsInRoot()

    private fun montar(nombre: String) {
        val cliente = ListaFixtures.cliente(
            clienteId = CLIENTE_ID,
            nombre = nombre,
            ventas = listOf(
                ListaFixtures.venta(
                    ventaId = 61_001,
                    folio = "V-1",
                    descripcion = "Ropero",
                    saldo = ListaFixtures.dinero("1000"),
                    totalVenta = ListaFixtures.dinero("2000"),
                    enganche = ListaFixtures.dinero("200"),
                    fechaVenta = ListaFixtures.HOY.minusDays(3),
                    estado = ListaFixtures.estado(EstadoCuenta.SIN_TOCAR)
                )
            )
        )
        val estado = ListaDeClientesUiState(
            cargando = false,
            clientes = listOf(cliente),
            segmento = SegmentoDeCobranza.SIN_VISITAR,
            hoy = ListaFixtures.HOY
        )
        composeTestRule.setContent {
            MspTheme(darkTheme = false, animateColors = false) {
                ListaDeClientesContent(
                    state = estado,
                    onBuscar = {},
                    onElegirSegmento = { segmentosElegidos += it },
                    onAbrirCliente = { clientesAbiertos += it },
                    onReintentar = {},
                    onAlternarTema = {},
                    onAlternarPrivacidad = {},
                    onElegirOrden = {},
                    onQuitarOrden = {},
                    onAbrirMenu = {}
                )
            }
        }
    }

    private companion object {
        const val CLIENTE_ID = 61_000
        const val NOMBRE_LARGO = "María Guadalupe Hernández de la Cruz Zepeda Olmedo"
        const val NOMBRE_CORTO = "Rosa Flores"
        const val MEDIO_SEGUNDO = 500L
        const val PAUSA_Y_REGRESO = 4_000L
    }
}
