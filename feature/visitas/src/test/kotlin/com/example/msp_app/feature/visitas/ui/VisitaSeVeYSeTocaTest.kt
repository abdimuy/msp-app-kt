package com.example.msp_app.feature.visitas.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.example.msp_app.core.designsystem.theme.FontSizeLevel
import com.example.msp_app.core.designsystem.theme.LocalFontSizeLevel
import com.example.msp_app.core.designsystem.theme.MspTheme
import com.example.msp_app.core.testing.RobolectricTestBase
import com.example.msp_app.feature.visitas.domain.model.ResultadoDeVisita
import com.example.msp_app.feature.visitas.ui.components.AGREGAR_FOTO_TAG
import com.example.msp_app.feature.visitas.ui.components.CHIP_TAG
import com.example.msp_app.feature.visitas.ui.components.FALLO_FOTO_TAG
import com.example.msp_app.feature.visitas.ui.components.FOTO_EN_LINEA_TAG
import com.example.msp_app.feature.visitas.ui.components.GUARDAR_TAG
import com.example.msp_app.feature.visitas.ui.components.OPCION_TAG
import com.example.msp_app.feature.visitas.ui.components.QUITAR_FOTO_TAG
import com.example.msp_app.feature.visitas.ui.components.RAZON_TAG
import com.example.msp_app.feature.visitas.ui.components.RECOMENDACION_TAG
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.robolectric.annotation.Config

/**
 * Tres cosas que se **miden**, no se declaran:
 *
 * 1. **Los controles se pueden tocar.** El plan pide >=50px y la Task 16 shipeó
 *    un control de 49.5dp que hubo que corregir, así que aquí se mide el alto
 *    real de cada renglón y de cada chip en vez de confiar en el modificador.
 * 2. **Un control apagado se ve apagado Y no responde.** La Task 18 tuvo que
 *    arreglar dos veces un teclado que parecía vivo y no hacía nada. Aquí el CTA
 *    apagado se afirma por las dos vías: `assertIsNotEnabled` y un clic que no
 *    llama al callback.
 * 3. **La recomendación se muestra**, porque guardarla sin haberla mostrado no
 *    significaría nada.
 */
// Pantalla alta a propósito: la columna hace scroll, y con 800dp el dock taparía
// los renglones de abajo antes de poder medirlos.
@Config(qualifiers = "w360dp-h2400dp-xhdpi")
class VisitaSeVeYSeTocaTest : RobolectricTestBase() {

    @get:Rule
    val composeTestRule = createComposeRule()

    private var guardados = 0

    private fun pinta(state: RegistrarVisitaUiState, nivel: FontSizeLevel = FontSizeLevel.NORMAL) {
        composeTestRule.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(
                LocalDensity provides Density(density.density, nivel.nominalScale),
                LocalFontSizeLevel provides nivel
            ) {
                MspTheme(darkTheme = false, animateColors = false) { Visita(state) }
            }
        }
    }

    @Composable
    private fun Visita(state: RegistrarVisitaUiState) {
        RegistrarVisitaContent(
            state = state,
            acciones = AccionesDeLaVisita.NINGUNA.copy(onGuardar = { guardados++ })
        )
    }

    // ─── tocable ─────────────────────────────────────────────────────────────

    @Test
    fun `los cinco desenlaces miden al menos 50dp de alto`() {
        pinta(VisitaFixtures.elegir())
        ResultadoDeVisita.entries.forEach { resultado ->
            val bordes = composeTestRule
                .onNodeWithTag(OPCION_TAG + resultado.name.lowercase())
                .getUnclippedBoundsInRoot()
            val alto = bordes.bottom - bordes.top
            assertTrue("${resultado.titulo} mide $alto", alto >= MINIMO_TOCABLE)
        }
    }

    @Test
    fun `a escala muy grande los desenlaces siguen siendo tocables`() {
        pinta(VisitaFixtures.elegir(), FontSizeLevel.MUY_GRANDE)
        ResultadoDeVisita.entries.forEach { resultado ->
            val bordes = composeTestRule
                .onNodeWithTag(OPCION_TAG + resultado.name.lowercase())
                .getUnclippedBoundsInRoot()
            assertTrue(
                "${resultado.titulo} se encoge a escala 2.0",
                bordes.bottom - bordes.top >= MINIMO_TOCABLE
            )
        }
    }

    /**
     * Elegido un desenlace la lista se colapsa al elegido — la composición del
     * mock. Y el renglón elegido sigue siendo el camino de vuelta.
     */
    @Test
    fun `elegido un desenlace solo se pinta ese renglon`() {
        pinta(VisitaFixtures.prometio())

        composeTestRule
            .onNodeWithTag(OPCION_TAG + ResultadoDeVisita.PROMETIO.name.lowercase())
            .assertIsDisplayed()
        assertEquals(
            0,
            composeTestRule
                .onAllNodesWithTag(OPCION_TAG + ResultadoDeVisita.NO_ESTABA.name.lowercase())
                .fetchSemanticsNodes()
                .size
        )
    }

    /** Y sin desenlace se pintan los cinco: el colapso no esconde opciones. */
    @Test
    fun `sin desenlace se pintan los cinco renglones`() {
        pinta(VisitaFixtures.elegir())

        ResultadoDeVisita.entries.forEach {
            composeTestRule.onNodeWithTag(OPCION_TAG + it.name.lowercase()).assertIsDisplayed()
        }
    }

    @Test
    fun `los chips de fecha de la promesa miden al menos 50dp`() {
        pinta(VisitaFixtures.prometio())
        val chips = listOf(
            CHIP_TAG + "dia_${VisitaFixtures.HOY.plusDays(1)}",
            CHIP_TAG + "otro_dia"
        )
        chips.forEach { tag ->
            val bordes = composeTestRule.onNodeWithTag(tag).getUnclippedBoundsInRoot()
            val alto = bordes.bottom - bordes.top
            assertTrue("$tag mide $alto", alto >= MINIMO_TOCABLE)
        }
    }

    @Test
    fun `el CTA mide al menos 50dp`() {
        pinta(VisitaFixtures.prometio())
        val bordes = composeTestRule.onNodeWithTag(GUARDAR_TAG).getUnclippedBoundsInRoot()
        val alto = bordes.bottom - bordes.top
        assertTrue("el CTA mide $alto", alto >= MINIMO_TOCABLE)
    }

    // ─── apagado de verdad ───────────────────────────────────────────────────

    /**
     * **El control apagado no miente.** Sin desenlace elegido el CTA está
     * deshabilitado en la semántica *y* un clic no llama al callback — las dos
     * afirmaciones, porque cada una sola dejaría pasar la mitad del defecto.
     */
    @Test
    fun `sin desenlace el CTA esta apagado y un clic no guarda nada`() {
        pinta(VisitaFixtures.elegir())

        composeTestRule.onNodeWithTag(GUARDAR_TAG).assertIsNotEnabled()
        composeTestRule.onNodeWithTag(GUARDAR_TAG).performClick()

        assertEquals("un CTA apagado no puede guardar", 0, guardados)
        composeTestRule.onNodeWithTag(RAZON_TAG).assertIsDisplayed()
    }

    /**
     * Control positivo del apagado: con la captura completa el MISMO clic **sí**
     * guarda. Sin esta prueba, la de arriba pasaría también con un CTA roto que
     * nunca funciona.
     */
    @Test
    fun `con la captura completa el mismo clic si guarda`() {
        pinta(VisitaFixtures.noEstaba())

        composeTestRule.onNodeWithTag(GUARDAR_TAG).performClick()

        assertEquals(1, guardados)
    }

    /** Una promesa sin fecha apaga el CTA y dice por qué, con la fecha a un toque. */
    @Test
    fun `una promesa sin fecha apaga el CTA y explica la razon`() {
        val sinFecha = VisitaFixtures.prometio().let {
            VisitaFixtures.estado(it.captura.copy(fechaPromesa = null))
        }
        pinta(sinFecha)

        composeTestRule.onNodeWithTag(GUARDAR_TAG).assertIsNotEnabled()
        composeTestRule.onNodeWithTag(RAZON_TAG).assertIsDisplayed()
    }

    // ─── la recomendación se ve ──────────────────────────────────────────────

    @Test
    fun `la recomendacion mostrada se pinta`() {
        pinta(VisitaFixtures.conRecomendacion())

        composeTestRule.onNodeWithTag(RECOMENDACION_TAG).assertIsDisplayed()
    }

    /**
     * Control positivo: sin recomendación la banda **no** existe. Sin esto, la
     * prueba de arriba no distinguiría "se pintó la que había" de "siempre hay
     * banda".
     */
    @Test
    fun `sin recomendacion no hay banda`() {
        pinta(VisitaFixtures.elegir())

        assertEquals(
            0,
            composeTestRule.onAllNodesWithTag(RECOMENDACION_TAG).fetchSemanticsNodes().size
        )
    }

    // ─── la foto (Task 23) ───────────────────────────────────────────────────

    /**
     * **El afordante se ve sin scroll.** Es la diferencia entera entre "cara de
     * alcanzar" y "a un toque": la sección de comprobantes vive al pie de la
     * columna, debajo de la línea de flotación a 360×800dp, así que el punto de
     * entrada tiene que estar arriba. Se afirma **sin `performScrollTo`**.
     */
    @Test
    fun `el boton de foto en linea se ve sin scroll y abre la camara`() {
        var pedidas = 0
        pintaCon(VisitaFixtures.elegir()) { it.copy(onAgregarFoto = { pedidas++ }) }

        composeTestRule.onNodeWithTag(FOTO_EN_LINEA_TAG).assertIsDisplayed().performClick()

        assertEquals(1, pedidas)
    }

    /** Y enseña el conteo, así que dice "la evidencia está puesta" sin bajar. */
    @Test
    fun `el boton de foto en linea cuenta los comprobantes`() {
        pinta(VisitaFixtures.conComprobantes())

        composeTestRule.onNodeWithTag(FOTO_EN_LINEA_TAG).assert(hasText("foto 2"))
    }

    /** Con la visita ya registrada, el afordante está apagado. */
    @Test
    fun `con la visita registrada el boton de foto esta apagado`() {
        var pedidas = 0
        pintaCon(VisitaFixtures.elegir().copy(registrada = "visita-1")) {
            it.copy(onAgregarFoto = { pedidas++ })
        }

        composeTestRule.onNodeWithTag(FOTO_EN_LINEA_TAG).assertIsNotEnabled()
        assertEquals(0, pedidas)
    }

    /** Quitar una foto avisa **con el id de esa foto**, no con el de la de al lado. */
    @Test
    fun `quitar una foto manda el id de esa foto`() {
        val quitadas = mutableListOf<String>()
        pintaCon(
            VisitaFixtures.conComprobantes()
        ) { it.copy(onQuitarFoto = { id -> quitadas += id }) }

        composeTestRule.onNodeWithTag(QUITAR_FOTO_TAG + "IMG-2").performScrollTo().performClick()

        assertEquals(listOf("IMG-2"), quitadas)
    }

    /** El aviso de la foto es visible, y **no apaga el CTA**. */
    @Test
    fun `el aviso de la foto no apaga el CTA de la visita`() {
        pinta(VisitaFixtures.conComprobantes())

        composeTestRule.onNodeWithTag(FALLO_FOTO_TAG).performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag(GUARDAR_TAG).assertIsEnabled()
    }

    /** Los controles de la foto también se tocan: >=50dp de alto. */
    @Test
    fun `los controles de la foto miden al menos 50dp de alto`() {
        pinta(VisitaFixtures.conComprobantes())

        listOf(FOTO_EN_LINEA_TAG, QUITAR_FOTO_TAG + "IMG-1", AGREGAR_FOTO_TAG).forEach { tag ->
            val bordes = composeTestRule.onNodeWithTag(tag).getUnclippedBoundsInRoot()
            val alto = bordes.bottom - bordes.top
            assertTrue("$tag mide $alto", alto >= MINIMO_TOCABLE)
        }
    }

    private fun pintaCon(
        state: RegistrarVisitaUiState,
        nivel: FontSizeLevel = FontSizeLevel.NORMAL,
        acciones: (AccionesDeLaVisita) -> AccionesDeLaVisita
    ) {
        composeTestRule.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(
                LocalDensity provides Density(density.density, nivel.nominalScale),
                LocalFontSizeLevel provides nivel
            ) {
                MspTheme(darkTheme = false, animateColors = false) {
                    RegistrarVisitaContent(
                        state = state,
                        acciones = acciones(AccionesDeLaVisita.NINGUNA)
                    )
                }
            }
        }
    }

    private companion object {
        /** El piso del plan. El design system pide 56dp, que nunca lo viola. */
        val MINIMO_TOCABLE = 50.dp
    }
}
