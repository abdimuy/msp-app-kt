package com.example.msp_app.feature.visitas.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasAnyDescendant
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
import com.example.msp_app.feature.visitas.data.fake.VisitasFixtures
import com.example.msp_app.feature.visitas.domain.model.ResultadoDeVisita
import com.example.msp_app.feature.visitas.ui.components.AGREGAR_FOTO_TAG
import com.example.msp_app.feature.visitas.ui.components.CHIP_TAG
import com.example.msp_app.feature.visitas.ui.components.CUENTA_TAG
import com.example.msp_app.feature.visitas.ui.components.FALLO_FOTO_TAG
import com.example.msp_app.feature.visitas.ui.components.GUARDAR_TAG
import com.example.msp_app.feature.visitas.ui.components.HOJA_DE_ORIGEN_TAG
import com.example.msp_app.feature.visitas.ui.components.OPCION_TAG
import com.example.msp_app.feature.visitas.ui.components.ORIGEN_TAG
import com.example.msp_app.feature.visitas.ui.components.QUITAR_FOTO_TAG
import com.example.msp_app.feature.visitas.ui.components.RAZON_TAG
import com.example.msp_app.feature.visitas.ui.components.RECOMENDACION_TAG
import com.example.msp_app.feature.visitas.ui.components.TODAS_LAS_CUENTAS_TAG
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

    // ─── las cuentas ─────────────────────────────────────────────────────────

    /**
     * **Los renglones de cuenta y el atajo también se tocan**: >=50dp de alto.
     * Son controles nuevos en una pantalla que ya cobraba esa regla, y el piso no
     * se baja nunca.
     */
    @Test
    fun `los controles de las cuentas miden al menos 50dp de alto`() {
        pinta(VisitaFixtures.seNegoEnTodo())

        val tags = VisitasFixtures.dosCuentas().map { CUENTA_TAG + it.ventaId } +
            TODAS_LAS_CUENTAS_TAG
        tags.forEach { tag ->
            val bordes = composeTestRule
                .onNodeWithTag(tag)
                .performScrollTo()
                .getUnclippedBoundsInRoot()
            val alto = bordes.bottom - bordes.top
            assertTrue("$tag mide $alto", alto >= MINIMO_TOCABLE)
        }
    }

    /** Y siguen siendo tocables a escala 2.0, que es donde las filas se aprietan. */
    @Test
    fun `a escala muy grande las cuentas siguen siendo tocables`() {
        pinta(VisitaFixtures.seNegoEnTodo(), FontSizeLevel.MUY_GRANDE)

        VisitasFixtures.dosCuentas().forEach { venta ->
            val bordes = composeTestRule
                .onNodeWithTag(CUENTA_TAG + venta.ventaId)
                .performScrollTo()
                .getUnclippedBoundsInRoot()
            assertTrue(
                "la cuenta ${venta.ventaId} se encoge a escala 2.0",
                bordes.bottom - bordes.top >= MINIMO_TOCABLE
            )
        }
    }

    /** Tocar una cuenta avisa **con el id de esa cuenta**, no con el de la de al lado. */
    @Test
    fun `tocar una cuenta manda el id de esa cuenta`() {
        val tocadas = mutableListOf<Int>()
        pintaCon(VisitaFixtures.seNegoEnTodo()) { it.copy(onCuenta = { id -> tocadas += id }) }

        composeTestRule
            .onNodeWithTag(CUENTA_TAG + VisitasFixtures.REFRIGERADOR)
            .performScrollTo()
            .performClick()

        assertEquals(listOf(VisitasFixtures.REFRIGERADOR), tocadas)
    }

    /**
     * **Con un desenlace de toda la puerta la sección no existe.** Control
     * positivo de las pruebas de arriba: sin esto no distinguirían "se pinta
     * cuando toca" de "se pinta siempre".
     */
    @Test
    fun `un desenlace de toda la puerta no pinta cuentas`() {
        pinta(VisitaFixtures.noEstaba())

        VisitasFixtures.dosCuentas().forEach { venta ->
            assertEquals(
                "no estaba no elige cuentas",
                0,
                composeTestRule
                    .onAllNodesWithTag(CUENTA_TAG + venta.ventaId)
                    .fetchSemanticsNodes()
                    .size
            )
        }
    }

    /** Y bajo "prometió" no hay atajo de "todas": no se pueden marcar varias. */
    @Test
    fun `bajo prometió no hay atajo de todas`() {
        pinta(VisitaFixtures.prometio())

        assertEquals(
            "prometió sí elige cuenta",
            1,
            composeTestRule
                .onAllNodesWithTag(CUENTA_TAG + VisitasFixtures.SALA)
                .fetchSemanticsNodes()
                .size
        )
        assertEquals(
            0,
            composeTestRule
                .onAllNodesWithTag(TODAS_LAS_CUENTAS_TAG)
                .fetchSemanticsNodes()
                .size
        )
    }

    /** Sin una sola cuenta marcada el CTA está apagado y el pie dice qué falta. */
    @Test
    fun `sin cuentas marcadas el CTA esta apagado`() {
        pinta(VisitaFixtures.seNegoSinCuentas())

        composeTestRule.onNodeWithTag(GUARDAR_TAG).assertIsNotEnabled()
        composeTestRule.onNodeWithTag(RAZON_TAG).assert(hasText("Elige una cuenta"))
    }

    /**
     * Control positivo: con las cuentas marcadas, el MISMO pie dice qué se va a
     * guardar en vez de qué falta.
     */
    @Test
    fun `con las cuentas marcadas el pie dice cuantas visitas se guardan`() {
        pinta(VisitaFixtures.seNegoEnTodo())

        composeTestRule.onNodeWithTag(GUARDAR_TAG).assertIsEnabled()
        composeTestRule
            .onNodeWithTag(RAZON_TAG)
            .assert(hasText("Se guardan 2 visitas, una por cuenta"))
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
     * **El «+» es el ÚNICO afordante que agrega, y abre la hoja.** No toma la
     * foto: pregunta de dónde. Antes había tres botones para lo mismo —el chip
     * del encabezado, la pastilla del pie y la lista— y un aviso de fallo que no
     * podía decir cuál de los tres había fallado.
     */
    @Test
    fun `el mas abre la hoja de origenes y es el unico que agrega`() {
        var pedidas = 0
        pintaCon(VisitaFixtures.elegir()) { it.copy(onAgregarFoto = { pedidas++ }) }

        composeTestRule.onNodeWithTag(AGREGAR_FOTO_TAG).performScrollTo().performClick()

        assertEquals(1, pedidas)
        // Control positivo del "único": el barrido cuenta TODO nodo con el tag de
        // agregar, así que un segundo afordante que volviera a aparecer —en el
        // encabezado, al pie o donde sea— pondría esto en rojo.
        assertEquals(
            1,
            composeTestRule.onAllNodesWithTag(AGREGAR_FOTO_TAG).fetchSemanticsNodes().size
        )
    }

    /** La hoja ofrece las TRES, cada una con su explicación, y cada una avisa cuál. */
    @Test
    fun `la hoja del mas ofrece camara galeria y archivo`() {
        val elegidos = mutableListOf<OrigenDeLaFoto>()
        pintaCon(VisitaFixtures.eligiendoOrigen()) {
            it.copy(onOrigen = { origen -> elegidos += origen })
        }

        OrigenDeLaFoto.entries.forEach { origen ->
            composeTestRule.onNodeWithTag(ORIGEN_TAG + origen.name)
                .assertIsDisplayed()
                .performClick()
        }

        assertEquals(OrigenDeLaFoto.entries.toList(), elegidos)
    }

    /** Y dice cuántos espacios quedan, contados — no un número escrito a mano. */
    @Test
    fun `la hoja dice cuantos espacios quedan`() {
        pinta(VisitaFixtures.eligiendoOrigen())

        composeTestRule.onNodeWithTag(HOJA_DE_ORIGEN_TAG)
            .assert(hasAnyDescendant(hasText("Quedan 4 espacios de 5")))
    }

    /** Con la visita ya registrada, el «+» no se pinta: no hay nada que agregar. */
    @Test
    fun `con la visita registrada el mas desaparece`() {
        pinta(VisitaFixtures.elegir().copy(registrada = "visita-1"))

        assertEquals(
            0,
            composeTestRule.onAllNodesWithTag(AGREGAR_FOTO_TAG).fetchSemanticsNodes().size
        )
    }

    /** Llena la rejilla, el «+» tampoco se pinta: un botón mudo es una mentira. */
    @Test
    fun `con la rejilla llena el mas desaparece`() {
        pinta(VisitaFixtures.comprobantesLlenos())

        assertEquals(
            0,
            composeTestRule.onAllNodesWithTag(AGREGAR_FOTO_TAG).fetchSemanticsNodes().size
        )
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

    /**
     * **El fallo se pinta EN SU CUADRO**, con el id del intento — no en un aviso
     * suelto que no dice cuál de los archivos elegidos se cayó. Y **no apaga el
     * CTA**: la visita se registra igual.
     */
    @Test
    fun `el cuadro fallido se ve en su lugar y no apaga el CTA de la visita`() {
        val state = VisitaFixtures.conComprobantes()
        pinta(state)

        val intento = state.intentos.single()
        composeTestRule.onNodeWithTag(FALLO_FOTO_TAG + intento.id)
            .performScrollTo()
            .assertIsDisplayed()
        composeTestRule.onNodeWithTag(GUARDAR_TAG).assertIsEnabled()
    }

    /** Los controles de la foto también se tocan: >=50dp de alto. */
    @Test
    fun `los controles de la foto miden al menos 50dp de alto`() {
        val state = VisitaFixtures.conComprobantes()
        pinta(state)

        val tags = listOf(
            AGREGAR_FOTO_TAG,
            QUITAR_FOTO_TAG + "IMG-1",
            QUITAR_FOTO_TAG + state.intentos.single().id
        )
        tags.forEach { tag ->
            val bordes = composeTestRule.onNodeWithTag(tag).getUnclippedBoundsInRoot()
            val alto = bordes.bottom - bordes.top
            assertTrue("$tag mide $alto", alto >= MINIMO_TOCABLE)
        }
    }

    /** Los tres renglones de la hoja, también. */
    @Test
    fun `los renglones de la hoja miden al menos 50dp de alto`() {
        pinta(VisitaFixtures.eligiendoOrigen())

        OrigenDeLaFoto.entries.forEach { origen ->
            val tag = ORIGEN_TAG + origen.name
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
