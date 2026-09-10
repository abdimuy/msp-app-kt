package com.example.msp_app.feature.pagos.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
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
            onMasAcciones = {},
            onUsarLiquidacion = {},
            onVerAbonos = {},
            onVerGarantia = {}
        )
    }

    @Test
    fun `las filas tocables respetan el piso de 50px del plan`() {
        ventaA(FontSizeLevel.NORMAL)
        val fila = bordesDe("ver los 6 abonos")
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
        composeTestRule.onNodeWithText("notificada").performScrollTo().assertIsDisplayed()
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
     * [bordesDe] también localiza texto que NO va en versalitas —"ver los 6
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

    private companion object {
        /**
         * El piso de toque del plan: ">=50px". Se mide sobre el alto real del
         * nodo, que en este entorno SÍ es determinista (es una restricción de
         * layout, no una medición de texto — ver el KDoc de arriba).
         */
        val PISO_TOCABLE = 50.dp
    }
}
