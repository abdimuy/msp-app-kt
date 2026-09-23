package com.example.msp_app.feature.pagos.ui

import android.content.Context
import android.provider.Settings
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.lifecycle.SavedStateHandle
import androidx.test.core.app.ApplicationProvider
import com.example.msp_app.core.testing.RobolectricTestBase
import com.example.msp_app.core.testing.telemetry.RecordingTelemetry
import com.example.msp_app.core.testing.time.FakeClock
import com.example.msp_app.feature.pagos.application.CargarDetalleVenta
import com.example.msp_app.feature.pagos.application.DerivarEstadoDelPeriodo
import com.example.msp_app.feature.pagos.application.ResolverVentanaDeCobro
import com.example.msp_app.feature.pagos.application.ReunirCobranzaDelCliente
import com.example.msp_app.feature.pagos.data.fake.FakeGarantiasPort
import com.example.msp_app.feature.pagos.data.fake.FakeLiquidacionPort
import com.example.msp_app.feature.pagos.data.fake.FakePagosPort
import com.example.msp_app.feature.pagos.data.fake.FakePeriodoDeCobroPort
import com.example.msp_app.feature.pagos.data.fake.FakeProductosPort
import com.example.msp_app.feature.pagos.data.fake.FakeTemaDeLaAppPort
import com.example.msp_app.feature.pagos.data.fake.FakeVentasPort
import com.example.msp_app.feature.pagos.data.fake.FakeVisitasPort
import com.example.msp_app.feature.pagos.ui.components.CTA_CONDONAR_TAG
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.robolectric.annotation.Config

/**
 * **"Condonar" del detalle de venta llama con el `ventaId` de ESTA cuenta, no
 * con otro.**
 *
 * El dueño pidió la condonación también en el detalle de venta nuevo
 * (`:feature:pagos`), sin depender de "Ver los N abonos" ni de que haya
 * oferta de liquidación vigente. `DetalleVentaScreen` cablea el botón con
 * `{ onCondonar(viewModel.ventaId) }` — el mismo molde de una línea que ya
 * usan [DetalleVentaScreen.onVerAbonos] y `.onVerGarantia` — y este test
 * cobra que ese molde entregue el id correcto y no, por ejemplo, el
 * `CLIENTE_ID` o un cero por un `SavedStateHandle` mal leído.
 *
 * Monta la pantalla real —`DetalleVentaScreen`, no `DetalleVentaContent`—
 * porque el `ventaId` que viaja al callback sale del `SavedStateHandle` del
 * ViewModel, un dato que `*Content` no tiene: pasar por alto el `Screen`
 * dejaría sin probar exactamente la línea que un defecto de esta familia ya
 * costó en producción (commit `721c5551`, documentado en
 * [com.example.msp_app.features.sales.SaleIdSpaces]).
 *
 * `io = Dispatchers.Unconfined` y no un `TestDispatcher` con
 * `advanceUntilIdle`: mismo criterio que
 * [ElToggleDeTemaEnLaListaTest] — la carga tiene que terminar ANTES de que el
 * test toque el botón, y acá no hay una corrutina de prueba que avanzar
 * dentro de la composición.
 */
@Config(qualifiers = "w360dp-h800dp-xhdpi")
class CondonarLlevaElVentaIdDeLaRutaTest : RobolectricTestBase() {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val clock = FakeClock(PagosFixtures.AHORA)
    private val telemetria = RecordingTelemetry(clock)

    private val ventasPort = FakeVentasPort()
    private val pagosPort = FakePagosPort()
    private val visitasPort = FakeVisitasPort()
    private val liquidacionPort = FakeLiquidacionPort()
    private val garantiasPort = FakeGarantiasPort()
    private val productosPort = FakeProductosPort()
    private val periodoPort = FakePeriodoDeCobroPort()

    /**
     * `MspThemeRevealHost` graba un `GraphicsLayer` por frame en su rama
     * animada, que Robolectric no soporta de forma confiable fuera de
     * `GraphicsMode.NATIVE` — mismo gotcha que documenta
     * [ElToggleDeTemaEnLaListaTest]. Sin apagar las animaciones este test
     * mediría otra cosa o se colgaría.
     */
    @Before
    fun sembrarLaVentaYApagarAnimaciones() {
        ventasPort.ventas = PagosFixtures.datosDeVentas()
        pagosPort.pagos = PagosFixtures.pagosDeLaVenta()
        Settings.Global.putFloat(
            ApplicationProvider.getApplicationContext<Context>().contentResolver,
            Settings.Global.ANIMATOR_DURATION_SCALE,
            0f
        )
    }

    private fun viewModel(ventaId: Int) = DetalleVentaViewModel(
        savedStateHandle = SavedStateHandle(mapOf(PagosRutas.ARG_VENTA_ID to ventaId)),
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
        tema = FakeTemaDeLaAppPort(),
        telemetry = telemetria,
        io = Dispatchers.Unconfined
    )

    @Test
    fun `tocar Condonar llama a onCondonar con el ventaId de la ruta`() {
        var recibido: Int? = null

        composeTestRule.setContent {
            DetalleVentaScreen(
                viewModel = viewModel(ventaId = PagosFixtures.VENTA_EN_PROMESA),
                onAtras = {},
                onRegistrarAbono = {},
                onRegistrarVisita = { _, _ -> },
                onVerAbonos = {},
                onVerGarantia = {},
                onVerUbicacion = { _, _ -> },
                onCondonar = { ventaId -> recibido = ventaId }
            )
        }

        composeTestRule.onNodeWithTag(CTA_CONDONAR_TAG).performClick()

        assertEquals(PagosFixtures.VENTA_EN_PROMESA, recibido)
    }

    /**
     * **Control negativo del id.** Si `DetalleVentaScreen` alguna vez leyera
     * otra venta —por ejemplo, si dos pantallas compartieran por accidente el
     * mismo `ViewModel`—, este test lo delata: el `ventaId` recibido tiene que
     * seguir a la ruta, no quedarse pegado al primero que se montó.
     */
    @Test
    fun `otra ruta, otro ventaId — no queda pegado al primero`() {
        var recibido: Int? = null

        composeTestRule.setContent {
            DetalleVentaScreen(
                viewModel = viewModel(ventaId = PagosFixtures.VENTA_PAGADA),
                onAtras = {},
                onRegistrarAbono = {},
                onRegistrarVisita = { _, _ -> },
                onVerAbonos = {},
                onVerGarantia = {},
                onVerUbicacion = { _, _ -> },
                onCondonar = { ventaId -> recibido = ventaId }
            )
        }

        composeTestRule.onNodeWithTag(CTA_CONDONAR_TAG).performClick()

        assertEquals(PagosFixtures.VENTA_PAGADA, recibido)
    }
}
