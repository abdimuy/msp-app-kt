package com.example.msp_app.features.sales.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.height
import androidx.compose.ui.unit.width
import com.example.msp_app.core.common.location.SaleDistance
import com.example.msp_app.core.database.dao.sale.EstadoCobranza
import com.example.msp_app.data.models.sale.FrecuenciaPago
import com.example.msp_app.data.models.sale.SaleWithProducts
import com.example.msp_app.features.sales.components.secondarysaleitem.SecondarySaleItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * La tarjeta de la pantalla principal mostraba `9.223372036854776E18 m` —el
 * centinela de "sin ubicacion" interpolado crudo— y ese token de 20 caracteres
 * indivisibles dejaba a "SALDO:" con un caracter de ancho, partido en vertical.
 * Estos tests fijan las dos mitades del arreglo: que el centinela no se pinte y
 * que la fila no se pueda romper.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [33], application = android.app.Application::class)
class SecondarySaleItemDistanceTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun `sin ubicacion muestra un guion, no un numero`() {
        contenido(SaleDistance.Unknown)

        composeTestRule.onNodeWithText("—").assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("Sin ubicación").assertIsDisplayed()
    }

    @Test
    fun `el centinela nunca llega a la pantalla`() {
        // Long.MAX_VALUE es el valor que la capa de clustering usaba para decir
        // "esta venta no tiene ubicacion".
        contenido(SaleDistance.of(Long.MAX_VALUE))

        val textos = composeTestRule.textosVisibles()
        assertTrue(
            "notacion cientifica en pantalla: $textos",
            textos.none { NOTACION_CIENTIFICA.containsMatchIn(it) }
        )
        assertTrue(
            "numero gigante en pantalla: $textos",
            textos.none { texto -> texto.count { it.isDigit() } > MAX_DIGITOS }
        )
        composeTestRule.onNodeWithText("—").assertIsDisplayed()
    }

    @Test
    fun `una distancia real se muestra formateada`() {
        contenido(SaleDistance.of(1234.0))

        composeTestRule.onNodeWithText("1.2 km").assertIsDisplayed()
    }

    @Test
    fun `una distancia corta se muestra en metros`() {
        contenido(SaleDistance.of(85.0))

        composeTestRule.onNodeWithText("85 m").assertIsDisplayed()
    }

    @Test
    fun `el saldo sigue visible junto a la distancia`() {
        contenido(SaleDistance.of(1234.0))

        composeTestRule.onNodeWithText("SALDO:", substring = true).assertIsDisplayed()
        composeTestRule.onNodeWithText("1.2 km").assertIsDisplayed()
    }

    @Test
    fun `en una tarjeta angosta la distancia mas larga no parte el saldo`() {
        // 240 dp: el peor caso realista (equipo chico, tarjeta con margenes).
        val distancia = mutableStateOf<SaleDistance>(SaleDistance.Unknown)
        contenido(distancia, ancho = ANCHO_ANGOSTO)

        val altoBase = composeTestRule.altoDelSaldo()

        // "20000 km": el texto de distancia mas largo que el tipo puede producir.
        composeTestRule.runOnUiThread {
            distancia.value = SaleDistance.of(SaleDistance.MAX_PLAUSIBLE_METERS)
        }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("SALDO:", substring = true).assertIsDisplayed()
        assertEquals(
            "el saldo cambio de alto: se partio en mas de un renglon",
            altoBase.value,
            composeTestRule.altoDelSaldo().value,
            TOLERANCIA_DP
        )
    }

    @Test
    fun `la distancia no le quita el ancho al saldo`() {
        contenido(
            SaleDistance.of(SaleDistance.MAX_PLAUSIBLE_METERS),
            fontScale = ESCALA_LETRA_MAXIMA
        )

        val anchoDistancia = composeTestRule
            .onNodeWithText("20000 km", useUnmergedTree = true)
            .getUnclippedBoundsInRoot()
            .width
        val anchoSaldo = composeTestRule
            .onNodeWithText("SALDO:", substring = true, useUnmergedTree = true)
            .getUnclippedBoundsInRoot()
            .width

        assertTrue(
            "la distancia ($anchoDistancia) le gana el ancho al saldo ($anchoSaldo)",
            anchoSaldo > anchoDistancia
        )
    }

    @Test
    fun `con letra grande el saldo tampoco se parte`() {
        // Escala 2.0: el maximo que ofrece la pantalla de Configuracion.
        val distancia = mutableStateOf<SaleDistance>(SaleDistance.Unknown)
        contenido(distancia, fontScale = ESCALA_LETRA_MAXIMA)

        val altoBase = composeTestRule.altoDelSaldo()

        composeTestRule.runOnUiThread {
            distancia.value = SaleDistance.of(SaleDistance.MAX_PLAUSIBLE_METERS)
        }
        composeTestRule.waitForIdle()

        assertEquals(
            "el saldo cambio de alto con letra grande",
            altoBase.value,
            composeTestRule.altoDelSaldo().value,
            TOLERANCIA_DP
        )
    }

    private fun contenido(
        distancia: SaleDistance,
        fontScale: Float = 1f,
        ancho: Dp = ANCHO_TARJETA
    ) = contenido(mutableStateOf(distancia), fontScale, ancho)

    private fun contenido(
        distancia: MutableState<SaleDistance>,
        fontScale: Float = 1f,
        ancho: Dp = ANCHO_TARJETA
    ) {
        composeTestRule.setContent {
            val densidad = LocalDensity.current
            CompositionLocalProvider(
                LocalDensity provides Density(densidad.density, fontScale)
            ) {
                MaterialTheme {
                    Box(modifier = Modifier.width(ancho)) {
                        SecondarySaleItem(
                            sale = VENTA,
                            distanceToCurrentLocation = distancia.value
                        )
                    }
                }
            }
        }
    }

    private fun ComposeContentTestRule.altoDelSaldo() =
        onNodeWithText("SALDO:", substring = true, useUnmergedTree = true)
            .getUnclippedBoundsInRoot()
            .height

    private fun ComposeContentTestRule.textosVisibles(): List<String> =
        onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.Text))
            .fetchSemanticsNodes()
            .flatMap { nodo -> nodo.config[SemanticsProperties.Text].map { it.text } }

    private companion object {
        val ANCHO_TARJETA = 320.dp
        val ANCHO_ANGOSTO = 240.dp
        const val ESCALA_LETRA_MAXIMA = 2f
        const val TOLERANCIA_DP = 0.5f
        const val MAX_DIGITOS = 12
        val NOTACION_CIENTIFICA = Regex("""\d[eE][+-]?\d""")

        val VENTA = SaleWithProducts(
            DOCTO_CC_ACR_ID = 1,
            DOCTO_CC_ID = 1,
            FOLIO = "MSP-1042",
            CLIENTE_ID = 88,
            APLICADO = "S",
            COBRADOR_ID = 7,
            CLIENTE = "María Fernanda Villalobos Treviño",
            ZONA_CLIENTE_ID = 3,
            LIMITE_CREDITO = 30_000.0,
            NOTAS = "",
            ZONA_NOMBRE = "Zona Centro",
            IMPORTE_PAGO_PROMEDIO = 450.0,
            TOTAL_IMPORTE = 12_000.0,
            NUM_IMPORTES = 24,
            FECHA = "2026-01-15T00:00:00-06:00",
            PARCIALIDAD = 500,
            ENGANCHE = 2_000.0,
            TIEMPO_A_CORTO_PLAZOMESES = 6,
            MONTO_A_CORTO_PLAZO = 10_000.0,
            VENDEDOR_1 = "Ernesto Zúñiga",
            VENDEDOR_2 = "",
            VENDEDOR_3 = "",
            PRECIO_TOTAL = 24_000.0,
            IMPTE_REST = 18_450.0,
            SALDO_REST = 18_450.0,
            FECHA_ULT_PAGO = "2026-08-01T10:30:00-06:00",
            CALLE = "Av. Francisco I. Madero 1204",
            CIUDAD = "Cuauhtémoc",
            ESTADO = "Chihuahua",
            TELEFONO = "6251234567",
            NOMBRE_COBRADOR = "Gabriel Roque",
            ESTADO_COBRANZA = EstadoCobranza.NO_PAGADO,
            DIA_COBRANZA = "LUNES",
            DIA_TEMPORAL_COBRANZA = "",
            PRECIO_DE_CONTADO = 20_000.0,
            AVAL_O_RESPONSABLE = "Rosa Elena Márquez",
            FREC_PAGO = FrecuenciaPago.SEMANAL,
            PRODUCTOS = "Sala Toscana 3 piezas, Comedor Roble 6 sillas",
            NUM_PAGOS_ATRASADOS = 2
        )
    }
}
