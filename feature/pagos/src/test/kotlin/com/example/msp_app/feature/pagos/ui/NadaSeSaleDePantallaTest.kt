package com.example.msp_app.feature.pagos.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.dp
import com.example.msp_app.core.common.time.BUSINESS_LOCALE
import com.example.msp_app.core.designsystem.theme.FontSizeLevel
import com.example.msp_app.core.designsystem.theme.LocalFontSizeLevel
import com.example.msp_app.core.designsystem.theme.MspTheme
import com.example.msp_app.core.testing.RobolectricTestBase
import com.example.msp_app.feature.pagos.ui.components.TARJETA_DE_GARANTIA_TAG
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
            onUsarLiquidacion = {},
            onVerAbonos = {},
            onVerGarantia = {}
        )
    }

    @Test
    fun `las filas tocables respetan el piso de 50px del plan`() {
        ventaA(FontSizeLevel.NORMAL)
        val fila = bordesDe("Ver los 6 abonos")
        assertTrue(
            "la fila mide " + (fila.bottom - fila.top) + ", bajo el piso de " + PISO_TOCABLE,
            (fila.bottom - fila.top) >= PISO_TOCABLE
        )
    }

    /**
     * `testTag` de la tarjeta de garantía: la sección existe y se pinta debajo
     * de los productos, con el mock como referencia.
     */
    @Test
    fun `la garantia de la venta se pinta`() {
        ventaA(FontSizeLevel.NORMAL)
        composeTestRule.onNodeWithTag(TARJETA_DE_GARANTIA_TAG).performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText("Notificada").performScrollTo().assertIsDisplayed()
    }

    /** Los bordes del nodo cuyo texto es [etiqueta], tal cual se pinta. */
    private fun bordesDe(etiqueta: String): DpRect =
        composeTestRule.onNodeWithText(etiqueta).performScrollTo().getUnclippedBoundsInRoot()

    /**
     * Los bordes de uno de los tres labels del pie de la tarjeta de saldo
     * (`.pgrid` del mock: `abonos · parcialidad · frecuencia`).
     *
     * **Busca en VERSALITAS a propósito.** `DatoDelPie` los pinta con
     * `.uppercase()`, como el `.pgrid .k` del mock y como kollect, así que
     * buscarlos en minúscula no encuentra el nodo. La aserción se arregla; la
     * mayúscula se queda en la pantalla.
     *
     * Es un helper aparte y no un `.uppercase()` dentro de [bordesDe] porque
     * [bordesDe] también localiza texto que NO va en versalitas —"Ver los 6
     * abonos", el enlace del riel— y uppercasearlo ahí lo dejaba sin nodo. La
     * primera versión de este arreglo hizo exactamente eso.
     */
    private fun bordesDelPie(clave: String): DpRect = bordesDe(clave.uppercase(BUSINESS_LOCALE))

    @Test
    fun `en NORMAL los tres datos comparten renglon`() {
        ventaA(FontSizeLevel.NORMAL)
        val abonos = bordesDelPie("abonos")
        val parcialidad = bordesDelPie("parcialidad")
        val frecuencia = bordesDelPie("frecuencia")
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
        val abonos = bordesDelPie("abonos")
        val parcialidad = bordesDelPie("parcialidad")
        val frecuencia = bordesDelPie("frecuencia")
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

    // --- El alcance de la línea se desplaza, como sus vecinas ----------------

    /**
     * **La fila del alcance se puede desplazar a lo ancho.**
     *
     * El defecto que esto cierra: `AlcanceDeLaLinea` era `fillMaxWidth()` **sin**
     * `horizontalScroll`, al revés que la fila de filtros que va pegada abajo.
     * Medido a 360 dp, a `MUY_GRANDE` la segunda pastilla va de 164 a 344 dp y
     * toca el borde; sin desplazamiento no hay a dónde ir, así que *"Todo el
     * cliente"* quedaba recortado **para siempre** en *"Todo el"* — el cobrador
     * leía una opción que no existe.
     *
     * Se afirma la **semántica de desplazamiento** y no el ancho del texto: en
     * este entorno el texto no se mide de verdad (ver el KDoc de arriba), pero
     * `horizontalScroll` sí publica `HorizontalScrollAxisRange` en el árbol, y
     * eso es exactamente lo que el arreglo agregó.
     *
     * **Lo que este test NO ve:** la elipsis. `overflow = TextOverflow.Ellipsis`
     * —la otra mitad del arreglo— es cosa del render y la cobran los goldens
     * `pagos_venta_*`.
     */
    @Test
    fun `la fila del alcance se desplaza a lo ancho`() {
        ventaA(FontSizeLevel.MUY_GRANDE)

        composeTestRule.onNode(
            seDesplazaALoAncho and hasAnyDescendant(hasTestTag(ALCANCE_TAG + true))
        ).assertExists()
    }

    /**
     * **Control positivo del de arriba.** La MISMA consulta encuentra un
     * descendiente de un `horizontalScroll` de verdad. Sin esto, un matcher mal
     * escrito —o un `assertExists` que se cumpliera por cualquier ancestro
     * desplazable— daría el mismo verde con el arreglo y sin él.
     *
     * **Ya no reutiliza la fila de filtros** (`FiltrosDeContacto`): la Ronda de
     * arreglo 1 de la Task 4 le quitó el `horizontalScroll` — a letra grande
     * ahora pasa a una rejilla de dos renglones en vez de rodar, y por eso ya no
     * sirve como referencia conocida de "esto sí se desplaza". La referencia
     * pasa a ser un `Row` sintético, compuesto aquí mismo, cuyo único trabajo es
     * desplazarse — prueba la consulta, no una pantalla real, que es exactamente
     * lo que este test necesita.
     */
    @Test
    fun `control positivo - la misma consulta encuentra un descendiente que si se desplaza`() {
        composeTestRule.setContent {
            MspTheme(darkTheme = false, animateColors = false) {
                Row(modifier = Modifier.horizontalScroll(rememberScrollState())) {
                    Box(modifier = Modifier.testTag(REFERENCIA_QUE_SI_SE_DESPLAZA))
                }
            }
        }

        composeTestRule.onNode(
            seDesplazaALoAncho and hasAnyDescendant(hasTestTag(REFERENCIA_QUE_SI_SE_DESPLAZA))
        ).assertExists()
    }

    private companion object {
        /**
         * El piso de toque del plan: ">=50px". Se mide sobre el alto real del
         * nodo, que en este entorno SÍ es determinista (es una restricción de
         * layout, no una medición de texto — ver el KDoc de arriba).
         */
        val PISO_TOCABLE = 50.dp

        /**
         * El nodo que `Modifier.horizontalScroll` marca. El `verticalScroll` de
         * la pantalla entera **no** publica esta propiedad, así que la consulta
         * no se lo lleva por error.
         */
        val seDesplazaALoAncho: SemanticsMatcher =
            SemanticsMatcher.keyIsDefined(SemanticsProperties.HorizontalScrollAxisRange)

        /** `testTag` del nodo sintético del control positivo — no existe en producción. */
        const val REFERENCIA_QUE_SI_SE_DESPLAZA = "test_referencia_que_si_se_desplaza"
    }
}
