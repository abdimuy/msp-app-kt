package com.example.msp_app.feature.pagos.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.dp
import com.example.msp_app.core.designsystem.theme.FontSizeLevel
import com.example.msp_app.core.designsystem.theme.LocalFontSizeLevel
import com.example.msp_app.core.designsystem.theme.MspTheme
import com.example.msp_app.core.testing.RobolectricTestBase
import com.example.msp_app.feature.pagos.domain.model.FichaDelCliente
import com.example.msp_app.feature.pagos.domain.model.SenalDeFicha
import com.example.msp_app.feature.pagos.ui.components.AFORDANTE_TEXTO_TAG
import com.example.msp_app.feature.pagos.ui.components.ATRAS_TAG
import com.example.msp_app.feature.pagos.ui.components.CHIP_DE_FICHA_TAG
import com.example.msp_app.feature.pagos.ui.components.CTA_PRIMARIO_TAG
import com.example.msp_app.feature.pagos.ui.components.CuerpoDeLaFicha
import com.example.msp_app.feature.pagos.ui.components.EDITAR_FICHA_TAG
import com.example.msp_app.feature.pagos.ui.components.NOTA_DE_LA_FICHA_TAG
import com.example.msp_app.feature.pagos.ui.components.SENAL_TAG
import com.example.msp_app.feature.pagos.ui.components.TARJETA_DE_LA_FICHA_TAG
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.robolectric.annotation.Config

/**
 * **La ficha no puede tapar el dinero.** Éste es el test que fija el criterio
 * de aceptación de la ronda 1, y es medible, no una impresión.
 *
 * La primera versión de esta tarea puso la tarjeta de la ficha entre el saldo y
 * "sus ventas": a escala 2.0 no quedaba **ni una venta visible** sin desplazar,
 * y el golden `promesa_sin_fecha` perdió de vista justo el renglón que existe
 * para proteger. El dinero es por lo que el cobrador abre esta pantalla.
 *
 * Lo que se afirma aquí, y que un rediseño futuro no puede romper en silencio:
 *
 * 1. **La ficha cuesta cero dp arriba de "sus ventas"** — el tope de esa
 *    sección es el MISMO con la ficha llena, vacía o ilegible. Medido, no
 *    supuesto, y así **agregar señales al catálogo tampoco empuja nada**.
 * 2. **La primera venta se ve sin desplazar** en 1.0, 1.5 y 2.0.
 * 3. **La barra superior no crece** por llevar el afordante — es el mismo alto
 *    con y sin ficha, que es la única razón por la que el afordante vive ahí.
 */
@Config(qualifiers = "w360dp-h800dp-xhdpi")
class LaFichaSeVeYSeTocaTest : RobolectricTestBase() {

    @get:Rule
    val composeTestRule = createComposeRule()

    private var abrio = 0

    /**
     * La ficha que la pantalla está pintando. Es estado y no parámetro para que
     * un mismo test pueda **cambiarla y volver a medir** — `setContent` solo se
     * puede llamar una vez por prueba, y comparar el layout entre dos fichas es
     * justo lo que cierra I-5.
     */
    private val fichaEnPantalla = mutableStateOf<FichaDelCliente?>(null)

    private fun cliente(
        ficha: FichaDelCliente? = PagosFixtures.fichaDelCliente(),
        nivel: FontSizeLevel = FontSizeLevel.NORMAL
    ) {
        fichaEnPantalla.value = ficha
        composeTestRule.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(
                LocalDensity provides Density(density.density, nivel.nominalScale),
                LocalFontSizeLevel provides nivel
            ) {
                MspTheme(darkTheme = false, animateColors = false) {
                    Pantalla(fichaEnPantalla.value)
                }
            }
        }
    }

    @Composable
    private fun Pantalla(ficha: FichaDelCliente?) {
        DetalleClienteContent(
            state = DetalleClienteUiState(
                cargando = false,
                detalle = PagosFixtures.detalleCliente().copy(ficha = ficha)
            ),
            onAtras = {},
            onAbrirVenta = {},
            onRegistrarAbono = {},
            onRegistrarVisita = {},
            onMasAcciones = {},
            onUsarLiquidacion = {},
            onVerContactos = {},
            fichaDelCliente = AccionesDeLaFicha(onEditar = { abrio += 1 })
        )
    }

    private fun bordesDe(tag: String): DpRect =
        composeTestRule.onNodeWithTag(tag).getUnclippedBoundsInRoot()

    /** El texto que la pastilla de la barra está diciendo. */
    private fun afordante() =
        composeTestRule.onNodeWithTag(AFORDANTE_TEXTO_TAG, useUnmergedTree = true)

    /** Un chip de la TARJETA (no de la hoja). */
    private fun chip(senal: SenalDeFicha) =
        composeTestRule.onNodeWithTag(CHIP_DE_FICHA_TAG + senal.ordinal, useUnmergedTree = true)

    private fun topeDeSusVentas(): Dp =
        composeTestRule.onNodeWithText("sus ventas").getUnclippedBoundsInRoot().top

    private fun altoDeLaBarra(): Dp = bordesDe(ATRAS_TAG).let { it.bottom - it.top }

    // --- I-5: el dinero no se tapa ------------------------------------------

    @Test
    fun `la primera venta se ve sin desplazar a escala NORMAL`() {
        cliente()
        laPrimeraVentaCabeArribaDelDock()
    }

    @Test
    fun `la primera venta se ve sin desplazar a escala GRANDE`() {
        cliente(nivel = FontSizeLevel.GRANDE)
        laPrimeraVentaCabeArribaDelDock()
    }

    @Test
    fun `la primera venta se ve sin desplazar a escala MUY GRANDE`() {
        cliente(nivel = FontSizeLevel.MUY_GRANDE)
        laPrimeraVentaCabeArribaDelDock()
    }

    /**
     * **La medición, no la impresión.** `assertIsDisplayed()` NO sirve para
     * esto y se comprobó: da verde con el nodo apenas asomado, así que pasaba
     * igual con la ficha empujando el dinero fuera de pantalla. Lo que sí
     * distingue es la geometría: el renglón de la primera venta tiene que caber
     * **entero arriba del dock**, que es donde termina el área visible sin
     * desplazar.
     */
    private fun laPrimeraVentaCabeArribaDelDock() {
        val venta = composeTestRule.onNodeWithText(PRIMERA_VENTA).getUnclippedBoundsInRoot()
        val dock = bordesDe(CTA_PRIMARIO_TAG)
        assertTrue(
            "la primera venta termina en " + venta.bottom +
                " y el dock empieza en " + dock.top + ": queda tapada",
            venta.bottom <= dock.top
        )
    }

    /**
     * **La medición que cierra I-5.** El contenido de la ficha no puede mover
     * "sus ventas" ni un dp, porque la ficha vive abajo y su afordante no cuesta
     * alto. Es también la garantía de que **agregar un valor al catálogo**
     * —cosa que esta misma ronda hizo— no vuelve a empujar el dinero.
     */
    @Test
    fun `el tope de sus ventas es el MISMO con ficha llena, vacia o ilegible`() {
        cliente(ficha = PagosFixtures.fichaConAdvertencia())
        val conAdvertencia = topeDeSusVentas()

        fichaEnPantalla.value = FichaDelCliente()
        composeTestRule.waitForIdle()
        assertEquals("una ficha vacía no puede mover el dinero", conAdvertencia, topeDeSusVentas())

        fichaEnPantalla.value = null
        composeTestRule.waitForIdle()
        assertEquals("una ficha ilegible tampoco", conAdvertencia, topeDeSusVentas())

        fichaEnPantalla.value = PagosFixtures.fichaDelCliente()
        composeTestRule.waitForIdle()
        assertEquals(conAdvertencia, topeDeSusVentas())
    }

    @Test
    fun `la barra no crece por llevar el afordante`() {
        cliente(ficha = PagosFixtures.fichaConAdvertencia())
        assertEquals(TOQUE, altoDeLaBarra())
        assertTrue(
            "el afordante no puede pasar del alto de la barra",
            bordesDe(EDITAR_FICHA_TAG).let { it.bottom - it.top } <= TOQUE
        )
    }

    // --- Dónde cae la sección -----------------------------------------------

    @Test
    fun `la ficha vive ABAJO, despues de sus ventas`() {
        cliente()
        val ventas = composeTestRule.onNodeWithText("sus ventas").getUnclippedBoundsInRoot()
        assertTrue(
            "la ficha no puede quedar arriba del dinero",
            bordesDe(TARJETA_DE_LA_FICHA_TAG).top > ventas.bottom
        )
        // Y ahí abajo no se ve sin desplazar — es el precio aceptado, y por eso
        // lo que no puede esperar sube a la barra.
        composeTestRule.onNodeWithTag(TARJETA_DE_LA_FICHA_TAG).assertIsNotDisplayed()
        composeTestRule.onNodeWithTag(TARJETA_DE_LA_FICHA_TAG)
            .performScrollTo()
            .assertIsDisplayed()
    }

    // --- I-2: la advertencia grita ------------------------------------------

    @Test
    fun `la advertencia se ve SIN desplazar, en la barra`() {
        cliente(ficha = PagosFixtures.fichaConAdvertencia())
        afordante().assertIsDisplayed().assertTextEquals("hay perro")
    }

    @Test
    fun `control positivo - sin advertencia la barra no grita nada`() {
        cliente()
        afordante().assertTextEquals("ver ficha")
    }

    @Test
    fun `entre dos advertencias manda la de no ir solo`() {
        cliente(
            ficha = FichaDelCliente(
                senales = setOf(SenalDeFicha.HAY_PERRO, SenalDeFicha.NO_IR_SOLO)
            )
        )
        afordante().assertTextEquals("no ir solo")
    }

    @Test
    fun `la advertencia va primero entre los chips de la tarjeta`() {
        cliente(ficha = PagosFixtures.fichaConAdvertencia())
        val perro = chip(SenalDeFicha.HAY_PERRO).performScrollTo().getUnclippedBoundsInRoot()
        val noche = chip(SenalDeFicha.ESTA_EN_LA_NOCHE).getUnclippedBoundsInRoot()
        assertTrue("la advertencia va primero", perro.top <= noche.top && perro.left <= noche.left)
    }

    @Test
    fun `sin ficha el afordante invita a anotar`() {
        cliente(ficha = FichaDelCliente())
        afordante().assertTextEquals("anotar ficha")
    }

    // --- El afordante --------------------------------------------------------

    @Test
    fun `tocar el afordante de la barra abre el editor`() {
        cliente()
        composeTestRule.onNodeWithTag(EDITAR_FICHA_TAG).performClick()
        assertEquals(1, abrio)
    }

    @Test
    fun `tocar la tarjeta tambien abre el editor`() {
        cliente()
        composeTestRule.onNodeWithTag(TARJETA_DE_LA_FICHA_TAG).performScrollTo().performClick()
        assertEquals(1, abrio)
    }

    @Test
    fun `si no se pudo leer se dice, y NO hay nada que tocar`() {
        cliente(ficha = null)
        afordante().assertTextEquals("ficha ilegible")
        composeTestRule.onNodeWithTag(EDITAR_FICHA_TAG).performClick()
        composeTestRule.onNodeWithTag(TARJETA_DE_LA_FICHA_TAG).performScrollTo().performClick()
        assertEquals("una pastilla muerta no puede abrir el editor", 0, abrio)
        composeTestRule.onNodeWithText("no se pudo leer la ficha").assertIsDisplayed()
    }

    // --- Los tres estados de la tarjeta --------------------------------------

    @Test
    fun `con senales se pintan sus etiquetas en espanol`() {
        cliente()
        chip(SenalDeFicha.ESTA_EN_LA_NOCHE).performScrollTo().assertIsDisplayed()
        chip(SenalDeFicha.ATIENDE_OTRA_PERSONA).assertIsDisplayed()
        composeTestRule.onNodeWithText("está en la noche", useUnmergedTree = true)
            .assertIsDisplayed()
    }

    @Test
    fun `sin ficha se invita a anotar`() {
        cliente(ficha = FichaDelCliente())
        composeTestRule.onNodeWithText("sin ficha — anota lo que sirva mañana")
            .performScrollTo()
            .assertIsDisplayed()
    }

    @Test
    fun `la nota de la VENTA se pinta aparte y con su propia etiqueta`() {
        cliente()
        composeTestRule.onNodeWithText("de la venta").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText("entrega en la puerta de atrás").assertIsDisplayed()
    }

    // --- I-3: el recorte de la nota, por el camino REAL ----------------------

    /**
     * El defecto que este test caza: la guarda de pares suplentes vivía en
     * `limpia`, pero el campo cortaba antes con su propio `take(500)`, así que
     * **ningún camino real la alcanzaba** y su prueba pasaba con el error
     * puesto. Esta versión teclea en el campo de verdad.
     */
    @Test
    fun `tecleando en el campo, el corte no parte un emoji a la mitad`() {
        var capturado = ""
        hoja(onNota = { capturado = it })
        val perro = "🐕"
        composeTestRule.onNodeWithTag(NOTA_DE_LA_FICHA_TAG)
            .performTextInput("a".repeat(FichaDelCliente.NOTA_MAX - 1) + perro)

        assertEquals(FichaDelCliente.NOTA_MAX - 1, capturado.length)
        assertFalse("quedó medio emoji", capturado.last().isHighSurrogate())
        assertEquals("a".repeat(FichaDelCliente.NOTA_MAX - 1), capturado)
    }

    @Test
    fun `tecleando en el campo, un emoji que cabe entero se conserva`() {
        var capturado = ""
        hoja(onNota = { capturado = it })
        val perro = "🐕"
        composeTestRule.onNodeWithTag(NOTA_DE_LA_FICHA_TAG)
            .performTextInput("a".repeat(FichaDelCliente.NOTA_MAX - 2) + perro)

        assertEquals(FichaDelCliente.NOTA_MAX, capturado.length)
        assertTrue(capturado.endsWith(perro))
    }

    @Test
    fun `el catalogo entero cabe en la hoja de edicion`() {
        hoja()
        SenalDeFicha.entries.forEach { senal ->
            composeTestRule.onNodeWithTag(SENAL_TAG + senal.ordinal).assertIsDisplayed()
        }
    }

    private fun hoja(onNota: (String) -> Unit = {}) {
        composeTestRule.setContent {
            MspTheme(darkTheme = false, animateColors = false) {
                CuerpoDeLaFicha(
                    senales = setOf(SenalDeFicha.ESTA_EN_LA_NOCHE),
                    nota = "",
                    guardando = false,
                    fallo = false,
                    onSenal = {},
                    onNota = onNota,
                    onGuardar = {}
                )
            }
        }
    }

    private companion object {
        /** El producto de la primera fila de "sus ventas" del fixture. */
        const val PRIMERA_VENTA = "Sala 3 piezas + base"

        /** El alto de la barra de navegación: lo fija su botón redondo. */
        val TOQUE = 56.dp
    }
}
