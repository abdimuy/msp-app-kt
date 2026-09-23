package com.example.msp_app.feature.pagos.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import com.example.msp_app.core.common.money.Money
import com.example.msp_app.core.designsystem.theme.FontSizeLevel
import com.example.msp_app.core.designsystem.theme.LocalFontSizeLevel
import com.example.msp_app.core.designsystem.theme.MspTheme
import com.example.msp_app.core.testing.RobolectricTestBase
import com.example.msp_app.feature.pagos.domain.model.DetalleVenta
import com.example.msp_app.feature.pagos.ui.components.SIN_DATO
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.robolectric.annotation.Config

/**
 * **Los seis datos que la pantalla legada enseñaba y el detalle nuevo había
 * perdido.**
 *
 * No era que la pantalla los tuviera y no los pintara: **no llegaban desde la
 * capa de datos**. `DetalleVenta` no tenía teléfono, ni zona, ni dirección, ni
 * aval, ni precio a corto plazo, y de los tres vendedores de Microsip sólo
 * viajaba el primero que no viniera en blanco. La pantalla legada
 * (`SaleClientDetailsSection`) los enseñaba todos.
 *
 * Lo que este test fija, y que un rediseño no puede deshacer en silencio:
 *
 * 1. **Cada campo se pinta con su etiqueta y su valor**, no sólo con uno de los
 *    dos.
 * 2. **Un campo vacío no abre un renglón en blanco**: se resuelve con el
 *    [SIN_DATO] que la ficha ya usaba. El caso de los vendedores 2 y 3 en
 *    blanco es el normal en campo, no un borde inventado.
 * 3. **"Precio a N meses" no aparece cuando no aplica**, con control positivo
 *    de que la misma búsqueda SÍ lo encuentra cuando la venta lo trae — sin
 *    ese control, un selector roto pasaría en verde igual que una ausencia de
 *    verdad.
 */
@Config(qualifiers = "w360dp-h800dp-xhdpi")
class LosDatosDeLaVentaVolvieronTest : RobolectricTestBase() {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun `el telefono, la zona y la direccion de la venta se ven`() {
        monta(PagosFixtures.detalleVenta())

        veTexto("Teléfono")
        veTexto("238 162 7597")
        veTexto("Zona")
        veTexto("ruta 25 · centro")
        veTexto("Dirección")
        // Con su entidad federativa pegada: es el pedazo que también se había
        // perdido, y el que distingue dos "Centro" de dos estados distintos.
        veTexto("C. Hidalgo 214, Centro, Puebla")
    }

    @Test
    fun `el aval o responsable se ve`() {
        monta(PagosFixtures.detalleVenta())

        veTexto("Aval o responsable")
        veTexto("Rosa María Ramírez")
    }

    @Test
    fun `los tres vendedores se ven, no solo el primero`() {
        monta(PagosFixtures.detalleVenta())

        // La etiqueta va en plural porque hay más de uno, y los nombres caen
        // uno por renglón dentro del mismo valor — como el legado.
        veTexto("Vendedores")
        composeTestRule.onNodeWithText("J. Carlos Méndez\nLaura Iveth Zepeda")
            .performScrollTo()
            .assertIsDisplayed()
    }

    @Test
    fun `con un solo vendedor la etiqueta va en singular y sin renglon vacio`() {
        monta(PagosFixtures.detalleVenta().copy(vendedores = listOf(UNICO_VENDEDOR)))

        veTexto("Vendedor")
        // `assertTextEquals` y no "contiene": un `"$UNICO\n\n"` —los dos
        // vendedores vacíos que Microsip trae de fábrica— pintaría dos
        // renglones en blanco debajo del único nombre, y "contiene" lo dejaría
        // pasar. Es exactamente lo que hacía la pantalla legada.
        composeTestRule.onNodeWithText(UNICO_VENDEDOR, substring = true)
            .performScrollTo()
            .assertTextEquals(UNICO_VENDEDOR)
    }

    @Test
    fun `sin ningun vendedor la fila dice sin dato, no queda en blanco`() {
        monta(PagosFixtures.detalleVenta().copy(vendedores = emptyList()))

        veTexto("Vendedor")
        assertEquals(
            "la fila de vendedores no cayó en el marcador de \"no se sabe\"",
            1,
            cuantos(SIN_DATO)
        )
    }

    @Test
    fun `los campos vacios o nulos no rompen la pantalla ni dejan renglon en blanco`() {
        // Los cinco de golpe, que es como llega una venta recién sincronizada
        // desde un padrón incompleto.
        monta(
            PagosFixtures.detalleVenta().copy(
                telefono = "",
                direccion = "",
                zona = "",
                aval = "",
                vendedores = emptyList(),
                fechaVenta = null
            )
        )

        veTexto("Teléfono")
        veTexto("Dirección")
        veTexto("Zona")
        veTexto("Aval o responsable")
        veTexto("Vendedor")
        assertEquals(
            "alguna de las seis filas vacías se pintó en blanco en vez de con el marcador",
            SEIS_FILAS_VACIAS,
            cuantos(SIN_DATO)
        )
    }

    @Test
    fun `el precio a N meses lleva el plazo en la etiqueta`() {
        monta(PagosFixtures.detalleVenta())

        veTexto("Precio a 4 meses")
        veTexto("$5,800")
    }

    @Test
    fun `con un solo mes la etiqueta va en singular`() {
        monta(PagosFixtures.detalleVenta().copy(mesesACortoPlazo = 1))

        veTexto("Precio a 1 mes")
    }

    @Test
    fun `sin plazo no se pinta la fila del precio a corto plazo`() {
        monta(PagosFixtures.detalleVenta().copy(mesesACortoPlazo = 0))

        assertEquals(
            "se pintó \"$0 a 0 meses\", que no es un dato: es la ausencia del dato",
            0,
            cuantosConteniendo(PRECIO_A)
        )
    }

    @Test
    fun `sin monto tampoco se pinta`() {
        monta(PagosFixtures.detalleVenta().copy(montoACortoPlazo = Money.ZERO))

        assertEquals(
            "un plazo sin monto es una promesa vacía y aun así se pintó",
            0,
            cuantosConteniendo(PRECIO_A)
        )
    }

    @Test
    fun `control positivo, la misma busqueda SI encuentra la fila cuando aplica`() {
        // Sin esto, los dos tests de arriba pasarían igual si `onAllNodesWithText`
        // dejara de encontrar nada — una ausencia no es hallazgo hasta demostrar
        // que la consulta habría encontrado la fila estando el dato.
        monta(PagosFixtures.detalleVenta())

        assertEquals(
            "el selector de la ausencia no encuentra la fila ni cuando existe",
            1,
            cuantosConteniendo(PRECIO_A)
        )
    }

    private fun veTexto(texto: String) {
        composeTestRule.onNodeWithText(texto).performScrollTo().assertIsDisplayed()
    }

    private fun cuantos(texto: String): Int =
        composeTestRule.onAllNodesWithText(texto).fetchSemanticsNodes().size

    private fun cuantosConteniendo(texto: String): Int =
        composeTestRule.onAllNodesWithText(texto, substring = true).fetchSemanticsNodes().size

    private fun monta(detalle: DetalleVenta) {
        composeTestRule.setContent {
            CompositionLocalProvider(LocalFontSizeLevel provides FontSizeLevel.NORMAL) {
                MspTheme(animateColors = false) { Pantalla(detalle) }
            }
        }
    }

    @Composable
    private fun Pantalla(detalle: DetalleVenta) {
        DetalleVentaContent(
            state = DetalleVentaUiState(cargando = false, detalle = detalle),
            onAtras = {},
            onRegistrarAbono = {},
            onRegistrarVisita = {},
            onUsarLiquidacion = {},
            onVerAbonos = {},
            onVerGarantia = {}
        )
    }

    private companion object {
        const val UNICO_VENDEDOR = "J. Carlos Méndez"
        const val PRECIO_A = "Precio a "

        /** Teléfono, dirección, zona, aval, vendedores y fecha de venta. */
        const val SEIS_FILAS_VACIAS = 6
    }
}
