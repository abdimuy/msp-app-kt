package com.example.msp_app.feature.pagos.screenshot

import androidx.activity.ComponentActivity
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import com.example.msp_app.core.common.cobranza.domain.EstadoCuenta
import com.example.msp_app.core.designsystem.theme.MspTheme
import com.example.msp_app.core.testing.RobolectricTestBase
import com.example.msp_app.core.testing.roborazzi.RoborazziConfig
import com.example.msp_app.feature.pagos.ui.BUSCADOR_COMPACTO_TAG
import com.example.msp_app.feature.pagos.ui.CarteraEnPantalla
import com.example.msp_app.feature.pagos.ui.ListaDeClientesContent
import com.example.msp_app.feature.pagos.ui.ListaDeClientesUiState
import com.example.msp_app.feature.pagos.ui.ListaFixtures
import com.example.msp_app.feature.pagos.ui.SegmentoDeCobranza
import com.example.msp_app.feature.pagos.ui.components.PILDORA_DE_BUSCAR_TAG
import com.github.takahirom.roborazzi.RoborazziOptions
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * La búsqueda compacta abierta (barra encogida → tocar la píldora → teclear): un estado
 * de la pantalla, no del ViewModel, así que se toca y se captura. Sirve para revisar
 * como imagen que el campo ocupa el ancho de los botones de tema y montos (que se
 * esconden) y que el texto se lee (decisión del dueño, 2026-10-07).
 */
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(
    sdk = [33],
    qualifiers = "w360dp-h800dp-xhdpi",
    application = android.app.Application::class
)
class BusquedaCompactaScreenshotTest : RobolectricTestBase() {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun `busqueda compacta light`() = capturar(dark = false)

    @Test
    fun `busqueda compacta dark`() = capturar(dark = true)

    private fun capturar(dark: Boolean) {
        composeTestRule.setContent {
            MspTheme(darkTheme = dark, animateColors = false) {
                var texto by remember { mutableStateOf("") }
                ListaDeClientesContent(
                    state = estado(texto, dark),
                    onBuscar = { texto = it },
                    onElegirSegmento = {},
                    onAbrirCliente = {},
                    onReintentar = {},
                    onAlternarTema = {},
                    onAlternarPrivacidad = {},
                    onElegirOrden = {},
                    onQuitarOrden = {},
                    onAbrirMenu = {},
                    listState = rememberLazyListState(
                        initialFirstVisibleItemIndex = INDICE_DESBORDADO
                    )
                )
            }
        }
        composeTestRule.onNodeWithTag(PILDORA_DE_BUSCAR_TAG).performClick()
        composeTestRule.onNodeWithTag(BUSCADOR_COMPACTO_TAG).performTextInput("Hernández")
        composeTestRule.waitForIdle()
        composeTestRule.onRoot().captureRoboImage(
            filePath = "src/test/screenshots/pagos_busqueda_compacta_${if (dark) "dark" else "light"}.png",
            roborazziOptions = RoborazziOptions(
                compareOptions = RoborazziOptions.CompareOptions(
                    changeThreshold = RoborazziConfig.CHANGE_THRESHOLD
                )
            )
        )
    }

    private fun estado(query: String, dark: Boolean): ListaDeClientesUiState {
        val clientes = (1..QUINCE).map { n ->
            ListaFixtures.cliente(
                clienteId = 97_000 + n,
                nombre = if (n % 2 == 0) "Rosa Hernández Ortega $n" else "Carlos Guzmán Flores $n",
                ventas = listOf(
                    ListaFixtures.venta(
                        ventaId = 98_000 + n,
                        folio = "V-$n",
                        descripcion = "Ropero $n",
                        saldo = ListaFixtures.dinero("1000"),
                        totalVenta = ListaFixtures.dinero("2000"),
                        enganche = ListaFixtures.dinero("200"),
                        fechaVenta = ListaFixtures.HOY.minusDays(n.toLong()),
                        estado = ListaFixtures.estado(EstadoCuenta.SIN_TOCAR)
                    )
                )
            )
        }
        val proyeccion = CarteraEnPantalla.proyectar(
            clientes = clientes,
            segmento = SegmentoDeCobranza.SIN_VISITAR,
            query = query,
            hoy = ListaFixtures.HOY
        )
        return ListaDeClientesUiState(
            cargando = false,
            clientes = proyeccion.clientes,
            conteos = proyeccion.conteos,
            segmento = SegmentoDeCobranza.SIN_VISITAR,
            query = query,
            temaOscuro = dark,
            hoy = ListaFixtures.HOY
        )
    }

    private companion object {
        const val QUINCE = 15
        const val INDICE_DESBORDADO = 6
    }
}
