package com.example.msp_app.feature.pagos.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.msp_app.core.common.money.Money
import com.example.msp_app.core.designsystem.theme.FontSizeLevel
import com.example.msp_app.core.designsystem.theme.LocalFontSizeLevel
import com.example.msp_app.core.designsystem.theme.MspTheme
import com.example.msp_app.core.designsystem.theme.mspDarkColors
import com.example.msp_app.core.designsystem.theme.mspLightColors
import com.example.msp_app.core.testing.RobolectricTestBase
import com.example.msp_app.feature.pagos.domain.MontosSugeridos
import com.example.msp_app.feature.pagos.domain.model.MetodoDeCobro
import com.example.msp_app.feature.pagos.ui.components.ABONO_CORTO_TAG
import com.example.msp_app.feature.pagos.ui.components.AFIRMAR_EL_MONTO
import com.example.msp_app.feature.pagos.ui.components.ALERTA_RARO_TAG
import com.example.msp_app.feature.pagos.ui.components.BLOQUEO_TAG
import com.example.msp_app.feature.pagos.ui.components.CHIP_SUGERIDO_TAG
import com.example.msp_app.feature.pagos.ui.components.CONFIRMAR_TAG
import com.example.msp_app.feature.pagos.ui.components.DUPLICADO_TAG
import com.example.msp_app.feature.pagos.ui.components.EDITAR_TAG
import com.example.msp_app.feature.pagos.ui.components.HOJA_TAG
import com.example.msp_app.feature.pagos.ui.components.METODOS_DE_CAPTURA
import com.example.msp_app.feature.pagos.ui.components.METODO_TAG
import com.example.msp_app.feature.pagos.ui.components.TECLA_BORRAR
import com.example.msp_app.feature.pagos.ui.components.TECLA_PUNTO
import com.example.msp_app.feature.pagos.ui.components.TECLA_TAG
import com.example.msp_app.feature.pagos.ui.components.VELO_TAG
import com.example.msp_app.feature.pagos.ui.components.contenidoDelSugerido
import com.example.msp_app.feature.pagos.ui.components.fondoDelSugerido
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.robolectric.annotation.Config

/**
 * Lo que se MIDE de la pantalla del dinero, en vez de declararse.
 *
 * El alto tocable se mide de verdad (`getUnclippedBoundsInRoot`) porque la Task
 * 16 shipeó un control de 49.5dp y la Task 17 rechazó otro de ~38dp: confiar en
 * el modificador no fue suficiente ninguna de las dos veces.
 *
 * Y el bloqueo se prueba por su **consecuencia** —el CTA no abre el paso dos—
 * con control positivo: el mismo toque, sobre un monto sano, sí lo abre. Un
 * test que solo comprobara la banda roja pasaría con el botón vivo debajo.
 */
@Config(qualifiers = "w360dp-h2400dp-xhdpi")
class AbonoSeVeYSeTocaTest : RobolectricTestBase() {

    @get:Rule
    val composeTestRule = createComposeRule()

    private var confirmaciones = 0
    private var registros = 0
    private var ediciones = 0
    private var revisiones = 0
    private val digitos = mutableListOf<Int>()
    private val metodos = mutableListOf<MetodoDeCobro>()
    private val sugeridosTocados = mutableListOf<Money>()
    private var puntos = 0
    private var borrados = 0

    // --- Toques ---------------------------------------------------------------

    @Test
    fun `cada tecla del teclado mide al menos 50dp`() {
        pinta(AbonoFixtures.enCaptura())
        (0..9).map { it.toString() }.plus(listOf(TECLA_PUNTO, TECLA_BORRAR)).forEach { tecla ->
            assertTocable(TECLA_TAG + tecla, "tecla $tecla")
        }
    }

    @Test
    fun `cada pastilla de metodo mide al menos 50dp`() {
        pinta(AbonoFixtures.enCaptura())
        METODOS_DE_CAPTURA.forEach { metodo ->
            assertTocable(METODO_TAG + metodo.name.lowercase(), "método ${metodo.etiqueta}")
        }
    }

    @Test
    fun `cada chip sugerido mide al menos 50dp`() {
        pinta(AbonoFixtures.enCaptura())
        MontosSugeridos.Sugerencia.entries.forEach { cual ->
            assertTocable(CHIP_SUGERIDO_TAG + cual.name.lowercase(), "chip ${cual.etiqueta}")
        }
    }

    @Test
    fun `el CTA mide al menos 50dp`() {
        pinta(AbonoFixtures.enCaptura())
        assertTocable(CTA_ABONO_TAG, "CTA")
    }

    @Test
    fun `los dos botones del paso dos miden al menos 50dp`() {
        pinta(AbonoFixtures.enConfirmacion())
        assertTocable(CONFIRMAR_TAG, "confirmar")
        assertTocable(EDITAR_TAG, "editar")
    }

    @Test
    fun `a escala muy grande todo sigue siendo tocable`() {
        pinta(AbonoFixtures.enCaptura(), FontSizeLevel.MUY_GRANDE)
        assertTocable(TECLA_TAG + "7", "tecla 7")
        assertTocable(METODO_TAG + "efectivo", "método efectivo")
        assertTocable(CTA_ABONO_TAG, "CTA")
    }

    @Test
    fun `las teclas, los metodos y los chips responden al toque`() {
        // No basta con que midan: la Task 16 y la 17 dejaron controles que se
        // veían bien y no se podían usar. Aquí se tocan de verdad.
        pinta(AbonoFixtures.enCaptura())
        composeTestRule.onNodeWithTag(TECLA_TAG + "7").performClick()
        composeTestRule.onNodeWithTag(TECLA_TAG + TECLA_PUNTO).performClick()
        composeTestRule.onNodeWithTag(TECLA_TAG + TECLA_BORRAR).performClick()
        composeTestRule.onNodeWithTag(METODO_TAG + "transferencia").performClick()
        composeTestRule
            .onNodeWithTag(CHIP_SUGERIDO_TAG + MontosSugeridos.Sugerencia.LIQUIDAR.name.lowercase())
            .performClick()
        assertEquals(listOf(7), digitos)
        assertEquals(listOf(MetodoDeCobro.TRANSFERENCIA), metodos)
        assertEquals(listOf(AbonoFixtures.LIQUIDACION), sugeridosTocados)
        assertEquals(1, puntos)
        assertEquals(1, borrados)
    }

    @Test
    fun `a escala normal los tres chips van en fila`() {
        // Control positivo del test de abajo: a 1.0 SÍ comparten renglón, así que
        // que a 2.0 no lo compartan es el cambio de layout y no un accidente.
        pinta(AbonoFixtures.enCaptura())
        val bordes = bordesDeLosChips()
        assertEquals(bordes[0].top, bordes[1].top)
        assertEquals(bordes[1].top, bordes[2].top)
    }

    @Test
    fun `a escala muy grande los chips se apilan y ningun monto se trunca`() {
        // A 2.0 los tres en tercios de 360dp no caben y "liquidar $1,290" se
        // leía "$1,29" — un monto recortado es un bug de dinero. Se afirma el
        // LAYOUT (apilados, a ancho completo), no el texto: Robolectric no mide
        // texto sin gráficos nativos y una aserción de ancho pasaría igual.
        pinta(AbonoFixtures.enCaptura(), FontSizeLevel.MUY_GRANDE)
        val bordes = bordesDeLosChips()
        bordes.forEach { assertEquals(bordes[0].left, it.left) }
        bordes.forEach { assertEquals(bordes[0].right, it.right) }
        assertTrue("el segundo chip va debajo del primero", bordes[1].top >= bordes[0].bottom)
        assertTrue("el tercero debajo del segundo", bordes[2].top >= bordes[1].bottom)
    }

    // --- El bloqueo duro, por su consecuencia --------------------------------

    @Test
    fun `con un sobrepago el CTA no abre el paso dos`() {
        pinta(AbonoFixtures.enBloqueo())
        composeTestRule.onNodeWithTag(BLOQUEO_TAG).assertIsDisplayed()
        composeTestRule.onNodeWithTag(CTA_ABONO_TAG).performClick()
        assertEquals("un sobrepago no puede abrir la confirmación", 0, confirmaciones)
    }

    @Test
    fun `control positivo - con un monto sano el MISMO toque si abre el paso dos`() {
        pinta(AbonoFixtures.enCaptura())
        assertEquals(
            "sin bloqueo no se pinta la banda roja",
            0,
            composeTestRule.onAllNodesWithTag(BLOQUEO_TAG).fetchSemanticsNodes().size
        )
        composeTestRule.onNodeWithTag(CTA_ABONO_TAG).performClick()
        assertEquals(1, confirmaciones)
    }

    @Test
    fun `la banda del bloqueo dice el maximo, que es el saldo`() {
        pinta(AbonoFixtures.enBloqueo())
        composeTestRule.onNodeWithText(
            "el abono excede el saldo · máximo $1,450"
        ).assertIsDisplayed()
    }

    // --- La duda: CTA apagado y un reintento que sí hace algo ----------------

    @Test
    fun `con la verificacion pendiente el CTA no abre nada y la banda ofrece revisar`() {
        pinta(AbonoFixtures.enDudaDeVerificacion())
        composeTestRule.onNodeWithTag(FALLO_DEL_ABONO_TAG).assertIsDisplayed()
        composeTestRule.onNodeWithTag(CTA_ABONO_TAG).performClick()
        assertEquals("el CTA no puede quedar vivo y mudo", 0, confirmaciones)
        // Y hay salida sin abandonar la pantalla.
        composeTestRule.onNodeWithTag(REVISAR_DE_NUEVO_TAG).performClick()
        assertEquals(1, revisiones)
        assertTocable(REVISAR_DE_NUEVO_TAG, "volver a revisar")
    }

    @Test
    fun `control positivo - sin duda no se pinta el reintento y el CTA si abre`() {
        pinta(AbonoFixtures.enCaptura())
        assertEquals(
            0,
            composeTestRule.onAllNodesWithTag(REVISAR_DE_NUEVO_TAG).fetchSemanticsNodes().size
        )
        composeTestRule.onNodeWithTag(CTA_ABONO_TAG).performClick()
        assertEquals(1, confirmaciones)
    }

    // --- El paso dos ----------------------------------------------------------

    @Test
    fun `velo y hoja tapan la pantalla entera, sin rendijas`() {
        // La captura queda DEBAJO y no se puede alcanzar mientras el paso dos
        // esté arriba. Se afirma por geometría —velo pegado arriba, hoja pegada
        // abajo, y sin hueco entre los dos— en vez de por un toque que en
        // Robolectric podría pasar igual aunque hubiera rendija.
        pinta(AbonoFixtures.enConfirmacion())
        val velo = composeTestRule.onNodeWithTag(VELO_TAG).getUnclippedBoundsInRoot()
        val hoja = composeTestRule.onNodeWithTag(HOJA_TAG).getUnclippedBoundsInRoot()
        val raiz = composeTestRule.onRoot().getUnclippedBoundsInRoot()
        assertEquals("el velo arranca en el borde de arriba", raiz.top, velo.top)
        assertEquals("y no deja rendija con la hoja", velo.bottom, hoja.top)
        assertEquals("la hoja llega hasta abajo", raiz.bottom, hoja.bottom)
        assertEquals(raiz.left, velo.left)
        assertEquals(raiz.right, velo.right)
    }

    @Test
    fun `tocar el velo sale del paso dos sin registrar`() {
        pinta(AbonoFixtures.enConfirmacion())
        composeTestRule.onNodeWithTag(VELO_TAG).performClick()
        assertEquals(1, ediciones)
        assertEquals(0, registros)
    }

    @Test
    fun `el paso dos normal registra con el boton azul, sin boton rojo`() {
        pinta(AbonoFixtures.enConfirmacion())
        composeTestRule.onNodeWithTag(HOJA_TAG).assertIsDisplayed()
        composeTestRule.onNodeWithText("confirmar y registrar").assertIsDisplayed()
        assertEquals(
            "sin rarezas no hay botón rojo",
            0,
            composeTestRule.onAllNodesWithText(AFIRMAR_EL_MONTO).fetchSemanticsNodes().size
        )
        assertEquals(
            "sin rarezas no hay alerta",
            0,
            composeTestRule.onAllNodesWithTag(ALERTA_RARO_TAG).fetchSemanticsNodes().size
        )
        composeTestRule.onNodeWithTag(CONFIRMAR_TAG).performClick()
        assertEquals(1, registros)
    }

    // --- La alerta de monto raro ---------------------------------------------

    @Test
    fun `un monto raro cambia el boton por el rojo que obliga a afirmar`() {
        pinta(AbonoFixtures.enMontoRaro())
        composeTestRule.onNodeWithTag(ALERTA_RARO_TAG).assertIsDisplayed()
        composeTestRule.onNodeWithTag(DUPLICADO_TAG).assertIsDisplayed()
        composeTestRule.onNodeWithText(AFIRMAR_EL_MONTO).assertIsDisplayed()
        composeTestRule.onNodeWithText("corregir monto").assertIsDisplayed()
        assertEquals(
            "el botón azul de continuar NO está: hay que afirmar el monto",
            0,
            composeTestRule.onAllNodesWithText("confirmar y registrar").fetchSemanticsNodes().size
        )
        assertEquals("y no registra por sí solo", 0, registros)
        composeTestRule.onNodeWithTag(CONFIRMAR_TAG).performClick()
        assertEquals(1, registros)
    }

    /**
     * **El aviso del abono corto, repuesto (AL) y en su tono (AM).**
     *
     * `NewPaymentDialog` pintaba "el pago es menor a la parcialidad acordada de
     * $X" y ese aviso se fue con él. Vuelve, pero **en ámbar**: un abono parcial
     * es un desenlace que el dominio ya modela como normal (`EstadoCuenta`
     * distingue *Pagó* de *Abonó parcial*), y una alarma que suena en el caso
     * común entrena al cobrador a descartar también la que sí importa.
     *
     * Se afirman las tres cosas que la ronda 3 decidió, no solo la banda:
     * que está, que **no** hay alerta roja, y que el CTA sigue siendo el azul de
     * "confirmar y registrar" en vez del `Danger` que obliga a afirmar el monto.
     *
     * **Control de reversión (verificado, ver `task-21-fix-1-report.md`):**
     * volver `esRaro` a `rarezas.isNotEmpty()` pone este test en ROJO.
     */
    @Test
    fun `un abono corto avisa en ambar, sin alerta roja y sin CTA de peligro`() {
        pinta(AbonoFixtures.enAbonoCorto())
        composeTestRule.onNodeWithTag(ABONO_CORTO_TAG).assertIsDisplayed()
        composeTestRule.onNodeWithText("abono corto").assertIsDisplayed()
        composeTestRule.onNodeWithText("esperado $220 · este abono $150").assertIsDisplayed()

        assertEquals(
            "un desenlace normal no pinta la hoja de peligro",
            0,
            composeTestRule.onAllNodesWithTag(ALERTA_RARO_TAG).fetchSemanticsNodes().size
        )
        assertEquals(
            "ni cambia el CTA por el que obliga a afirmar el monto",
            0,
            composeTestRule.onAllNodesWithText(AFIRMAR_EL_MONTO).fetchSemanticsNodes().size
        )
        composeTestRule.onNodeWithText("confirmar y registrar").assertIsDisplayed()

        assertEquals("y no registra por sí solo", 0, registros)
        composeTestRule.onNodeWithTag(CONFIRMAR_TAG).performClick()
        assertEquals("un abono corto es legítimo: avisa, no bloquea", 1, registros)
    }

    /**
     * **La otra dirección: gana la más grave.** Un abono corto encima de un
     * posible duplicado sí escala — el aviso suave no puede apagar al que sí
     * importaba, que es justo el daño que el Ruling AM vino a evitar.
     *
     * Las dos bandas ámbar conviven; lo que cambia es que ahora la hoja está en
     * rojo y el CTA obliga a afirmar el monto.
     */
    @Test
    fun `un abono corto encima de un duplicado si escala la hoja`() {
        pinta(AbonoFixtures.enAbonoCortoYDuplicado())
        composeTestRule.onNodeWithTag(ABONO_CORTO_TAG).assertIsDisplayed()
        composeTestRule.onNodeWithTag(DUPLICADO_TAG).assertIsDisplayed()
        composeTestRule.onNodeWithText(AFIRMAR_EL_MONTO).assertIsDisplayed()
        assertEquals(
            "el botón azul de continuar NO está: hay que afirmar el monto",
            0,
            composeTestRule.onAllNodesWithText("confirmar y registrar").fetchSemanticsNodes().size
        )
    }

    @Test
    fun `el indicador dice que falta confirmar el monto raro`() {
        pinta(AbonoFixtures.enMontoRaro())
        composeTestRule.onNodeWithText("revisado").assertIsDisplayed()
        composeTestRule.onNodeWithText("confirmar monto raro").assertIsDisplayed()
    }

    // --- Los colores de los tres sugeridos, tal cual la tabla del Task 2 ------

    @Test
    fun `cada sugerido usa su token, no un color inventado`() {
        listOf(mspLightColors(), mspDarkColors()).forEach { colors ->
            assertEquals(
                colors.statusPaid,
                contenidoDelSugerido(MontosSugeridos.Sugerencia.ESPERADO_HOY, colors)
            )
            assertEquals(
                colors.statusPaidTint,
                fondoDelSugerido(MontosSugeridos.Sugerencia.ESPERADO_HOY, colors)
            )
            assertEquals(
                colors.statusTeal,
                contenidoDelSugerido(MontosSugeridos.Sugerencia.AL_CORRIENTE, colors)
            )
            assertEquals(
                colors.statusTealTint,
                fondoDelSugerido(MontosSugeridos.Sugerencia.AL_CORRIENTE, colors)
            )
            assertEquals(
                colors.promise,
                contenidoDelSugerido(MontosSugeridos.Sugerencia.LIQUIDAR, colors)
            )
            assertEquals(
                colors.promiseTint,
                fondoDelSugerido(MontosSugeridos.Sugerencia.LIQUIDAR, colors)
            )
        }
    }

    // --- Plomería ------------------------------------------------------------

    private fun bordesDeLosChips() = MontosSugeridos.Sugerencia.entries.map { cual ->
        composeTestRule
            .onNodeWithTag(CHIP_SUGERIDO_TAG + cual.name.lowercase())
            .getUnclippedBoundsInRoot()
    }

    private fun assertTocable(tag: String, que: String) {
        val bordes = composeTestRule.onNodeWithTag(tag).getUnclippedBoundsInRoot()
        val alto = bordes.bottom - bordes.top
        assertTrue("$que mide $alto", alto >= MINIMO_TOCABLE)
    }

    private fun pinta(state: RegistrarAbonoUiState, nivel: FontSizeLevel = FontSizeLevel.NORMAL) {
        composeTestRule.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(
                LocalDensity provides Density(density.density, nivel.nominalScale),
                LocalFontSizeLevel provides nivel
            ) {
                MspTheme(darkTheme = false, animateColors = false) { Pantalla(state) }
            }
        }
    }

    @Composable
    private fun Pantalla(state: RegistrarAbonoUiState) {
        RegistrarAbonoContent(
            state = state,
            onAtras = {},
            onDigito = { digitos += it },
            onPunto = { puntos += 1 },
            onBorrar = { borrados += 1 },
            onMetodo = { metodos += it },
            onSugerido = { sugeridosTocados += it },
            onRegistrar = { confirmaciones += 1 },
            onConfirmar = { registros += 1 },
            onEditar = { ediciones += 1 },
            onRevisar = { revisiones += 1 }
        )
    }

    private companion object {
        /** El piso del plan. El token del design system (56dp) va por encima. */
        val MINIMO_TOCABLE: Dp = 50.dp
    }
}
