package com.example.msp_app.feature.pagos.ui

import androidx.compose.runtime.getValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.msp_app.core.designsystem.theme.MspTheme
import com.example.msp_app.core.testing.RobolectricTestBase
import com.example.msp_app.core.testing.telemetry.RecordingTelemetry
import com.example.msp_app.core.testing.time.FakeClock
import com.example.msp_app.feature.pagos.application.CargarDetalleVenta
import com.example.msp_app.feature.pagos.application.DerivarEstadoDelPeriodo
import com.example.msp_app.feature.pagos.application.RegistrarAbono
import com.example.msp_app.feature.pagos.application.ResolverVentanaDeCobro
import com.example.msp_app.feature.pagos.application.ReunirCobranzaDelCliente
import com.example.msp_app.feature.pagos.data.fake.FakeComprobantesPort
import com.example.msp_app.feature.pagos.data.fake.FakeGarantiasPort
import com.example.msp_app.feature.pagos.data.fake.FakeLiquidacionPort
import com.example.msp_app.feature.pagos.data.fake.FakePagosPort
import com.example.msp_app.feature.pagos.data.fake.FakePeriodoDeCobroPort
import com.example.msp_app.feature.pagos.data.fake.FakeProductosPort
import com.example.msp_app.feature.pagos.data.fake.FakeRegistroDeAbonoPort
import com.example.msp_app.feature.pagos.data.fake.FakeVentasPort
import com.example.msp_app.feature.pagos.data.fake.FakeVisitasPort
import com.example.msp_app.feature.pagos.domain.model.Liquidacion
import com.example.msp_app.feature.pagos.domain.port.ResultadoDelAbono
import com.example.msp_app.feature.pagos.ui.components.CONFIRMAR_TAG
import com.example.msp_app.feature.pagos.ui.components.HOJA_TAG
import com.example.msp_app.feature.pagos.ui.components.METODO_TAG
import com.example.msp_app.feature.pagos.ui.components.TECLA_TAG
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.robolectric.annotation.Config

/**
 * La puerta lateral del CTA mudo, tocada de verdad.
 *
 * El defecto que esta clase existe para cerrar: con la verificación pendiente
 * el CTA está apagado y la banda explica por qué — pero el **teclado seguía
 * vivo**, y teclear un dígito limpiaba `fallo`. Eso borraba la banda y su botón
 * de salida, devolvía `sePuedeRegistrar` a `true` y encendía el CTA; tocarlo
 * abría la hoja y `confirmar()` moría contra el guard. El mismo botón mudo con
 * la explicación borrada, por otra puerta.
 *
 * Por eso la prueba no mira estado: **pinta el ViewModel real y toca las teclas
 * reales**, que es el único camino por el que el defecto se alcanzaba. Es la
 * misma forma que la prueba de Compose de la ronda anterior, que sí tocaba el
 * botón.
 *
 * Se usa `StandardTestDispatcher` con su scheduler explícito (nunca
 * `UnconfinedTestDispatcher`): las corrutinas del ViewModel avanzan cuando este
 * test lo dice, no solas.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@Config(qualifiers = "w360dp-h2400dp-xhdpi")
class AbonoPuertaLateralTest : RobolectricTestBase() {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val scheduler = TestCoroutineScheduler()
    private val testDispatcher = StandardTestDispatcher(scheduler)
    private val clock = FakeClock(PagosFixtures.AHORA)
    private val telemetria = RecordingTelemetry(clock)

    private val ventasPort = FakeVentasPort()
    private val pagosPort = FakePagosPort()
    private val visitasPort = FakeVisitasPort()
    private val liquidacionPort = FakeLiquidacionPort()
    private val garantiasPort = FakeGarantiasPort()

    private val productosPort = FakeProductosPort()
    private val periodoPort = FakePeriodoDeCobroPort()
    private val registroPort = FakeRegistroDeAbonoPort()

    private val camaraPort = FakeComprobantesPort()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        ventasPort.ventas = listOf(AbonoFixtures.datosDeVenta())
        liquidacionPort.liquidaciones = mapOf(
            AbonoFixtures.VENTA_ID to Liquidacion(
                monto = AbonoFixtures.LIQUIDACION,
                vigenteHasta = null,
                categoria = "precio a 4 meses"
            )
        )
        // El puerto reporta fallo y, de paso, tumba la relectura: así la pantalla
        // aterriza en "no se pudo comprobar", que es el estado del defecto.
        registroPort.resultado = ResultadoDelAbono.FALLO_EL_GUARDADO
        registroPort.alRegistrar = { ventasPort.falla = IllegalStateException("room caido") }
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `teclear con la verificacion pendiente no reabre el boton mudo`() {
        val vm = pintaElViewModelReal()

        // Todo el camino por la pantalla: registrar -> confirmar -> la duda.
        composeTestRule.onNodeWithTag(CTA_ABONO_TAG).performClick()
        avanza()
        composeTestRule.onNodeWithTag(CONFIRMAR_TAG).performClick()
        avanza()
        composeTestRule.onNodeWithTag(FALLO_DEL_ABONO_TAG).assertIsDisplayed()
        composeTestRule.onNodeWithTag(REVISAR_DE_NUEVO_TAG).assertIsDisplayed()

        // LA PUERTA: el teclado sigue en pantalla; se toca de verdad.
        composeTestRule.onNodeWithTag(TECLA_TAG + "7").performClick()
        composeTestRule.onNodeWithTag(METODO_TAG + "transferencia").performClick()
        avanza()

        // La explicación y su salida sobreviven al tecleo.
        composeTestRule.onNodeWithTag(FALLO_DEL_ABONO_TAG).assertIsDisplayed()
        composeTestRule.onNodeWithTag(REVISAR_DE_NUEVO_TAG).assertIsDisplayed()

        // Y el CTA sigue apagado: tocarlo no abre la hoja.
        composeTestRule.onNodeWithTag(CTA_ABONO_TAG).performClick()
        avanza()
        assertEquals(
            "el CTA no puede reabrirse por la puerta del teclado",
            0,
            composeTestRule.onAllNodesWithTag(HOJA_TAG).fetchSemanticsNodes().size
        )
        assertEquals("y nada se cobró dos veces", 1, registroPort.registrados.size)
    }

    @Test
    fun `control positivo - sin la duda el teclado si edita y el CTA si abre`() {
        // El mismo teclado, sin cerrojo: la puerta existe, solo está cerrada
        // cuando toca. Sin esto, la prueba de arriba pasaría con todo apagado.
        registroPort.alRegistrar = {}
        registroPort.resultado = ResultadoDelAbono.REGISTRADO
        pintaElViewModelReal()

        composeTestRule.onNodeWithTag(TECLA_TAG + "7").performClick()
        avanza()
        composeTestRule.onNodeWithTag(CTA_ABONO_TAG).performClick()
        avanza()
        composeTestRule.onNodeWithTag(HOJA_TAG).assertIsDisplayed()
    }

    private fun avanza() {
        scheduler.advanceUntilIdle()
        composeTestRule.waitForIdle()
    }

    private fun pintaElViewModelReal(): RegistrarAbonoViewModel {
        val vm = RegistrarAbonoViewModel(
            savedStateHandle = SavedStateHandle(
                mapOf(PagosRutas.ARG_VENTA_ID to AbonoFixtures.VENTA_ID)
            ),
            cargarDetalleVenta = CargarDetalleVenta(
                ventasPort = ventasPort,
                garantiasPort = garantiasPort,
                productosPort = productosPort,
                reunirCobranzaDelCliente = ReunirCobranzaDelCliente(
                    ventasPort = ventasPort,
                    pagosPort = pagosPort,
                    visitasPort = visitasPort,
                    liquidacionPort = liquidacionPort,
                    resolverVentanaDeCobro = ResolverVentanaDeCobro(periodoPort, clock),
                    derivarEstadoDelPeriodo = DerivarEstadoDelPeriodo(telemetria)
                ),
                clock = clock
            ),
            registrarAbono = RegistrarAbono(registroPort, telemetria),
            camara = camaraPort,
            telemetry = telemetria,
            clock = clock,
            io = testDispatcher
        )
        scheduler.advanceUntilIdle()
        composeTestRule.setContent {
            MspTheme(darkTheme = false, animateColors = false) {
                val state by vm.state.collectAsStateWithLifecycle()
                RegistrarAbonoContent(
                    state = state,
                    onAtras = {},
                    onDigito = vm::onDigito,
                    onPunto = vm::onPunto,
                    onBorrar = vm::onBorrar,
                    onMetodo = vm::onMetodo,
                    onSugerido = vm::onSugerido,
                    onRegistrar = vm::pedirConfirmacion,
                    onConfirmar = vm::confirmar,
                    onEditar = vm::descartarConfirmacion,
                    onRevisar = vm::cargar,
                    onAgregarFoto = vm::abrirOrigenes,
                    onOrigen = vm::onOrigen,
                    onCerrarOrigenes = vm::cerrarOrigenes,
                    onQuitarFoto = vm::quitarFoto
                )
            }
        }
        avanza()
        return vm
    }
}
