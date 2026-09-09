package com.example.msp_app.feature.pagos.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.lifecycle.SavedStateHandle
import com.example.msp_app.core.printing.application.EvaluatePrintPermission
import com.example.msp_app.core.printing.application.PrintTicketUseCase
import com.example.msp_app.core.printing.application.PrinterDirectory
import com.example.msp_app.core.printing.application.TicketPrinting
import com.example.msp_app.core.testing.RobolectricTestBase
import com.example.msp_app.core.testing.telemetry.RecordingTelemetry
import com.example.msp_app.core.testing.time.FakeClock
import com.example.msp_app.feature.pagos.application.CargarDetalleCliente
import com.example.msp_app.feature.pagos.application.CargarDetalleVenta
import com.example.msp_app.feature.pagos.application.CargarTicketDePago
import com.example.msp_app.feature.pagos.application.DerivarEstadoDelPeriodo
import com.example.msp_app.feature.pagos.application.GuardarFichaDelCliente
import com.example.msp_app.feature.pagos.application.RegistrarAbono
import com.example.msp_app.feature.pagos.application.ResolverVentanaDeCobro
import com.example.msp_app.feature.pagos.application.ReunirCartera
import com.example.msp_app.feature.pagos.application.ReunirCobranzaDelCliente
import com.example.msp_app.feature.pagos.data.fake.FakeComprobantesPort
import com.example.msp_app.feature.pagos.data.fake.FakeFichaPort
import com.example.msp_app.feature.pagos.data.fake.FakeGarantiasPort
import com.example.msp_app.feature.pagos.data.fake.FakeImpresoraPreferida
import com.example.msp_app.feature.pagos.data.fake.FakeLiquidacionPort
import com.example.msp_app.feature.pagos.data.fake.FakePagosPort
import com.example.msp_app.feature.pagos.data.fake.FakePeriodoDeCobroPort
import com.example.msp_app.feature.pagos.data.fake.FakePrintLog
import com.example.msp_app.feature.pagos.data.fake.FakePrinterPort
import com.example.msp_app.feature.pagos.data.fake.FakeRegistroDeAbonoPort
import com.example.msp_app.feature.pagos.data.fake.FakeVentasPort
import com.example.msp_app.feature.pagos.data.fake.FakeVisitasPort
import java.io.File
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * **El punto de entrada real, montado SIN tema.**
 *
 * Todo lo demás de la suite monta `*Content` y le REGALA un
 * `MspTheme { }` de andamiaje: los goldens lo hacen en el `capture()` de
 * `PagosScreenshotTest`, los tests de pantalla lo hacen uno por uno. Eso prueba
 * el render *dado* el tema — que es exactamente la premisa que producción
 * viola: `:app` monta `MspappTheme` (Material legado) y su `NavHost` no tiene
 * ningún `MspTheme` en la cadena de ancestros, así que la primera lectura de
 * `MspTheme.colors` dentro del destino revienta con
 * `IllegalStateException("MspTheme ausente")`.
 *
 * Aquí se monta el `*Screen` —la capa que ningún test del repo componía— **sin
 * aportar tema**, que es el entorno del teléfono. Si la pantalla no se envuelve
 * a sí misma (precedente: `ConfiguracionScreen`), este test se pone rojo con la
 * excepción exacta del crash de producción.
 *
 * ## Por qué el `*Screen` y no el `NavHost`
 *
 * Componer el `NavHost` real sería mejor todavía —enumeraría los destinos desde
 * `destinosDeCobranza`, la misma función que corre en el teléfono— y **no se
 * puede en la JVM**: medido, no supuesto. Un `NavHost` con ese grafo en
 * Robolectric muere antes de llegar a la pantalla, en el `hiltViewModel()` de la
 * lambda de la ruta:
 *
 * ```
 * IllegalStateException: Given component holder class androidx.activity.ComponentActivity
 *   does not implement interface dagger.hilt.internal.GeneratedComponentManager
 *     at androidx.hilt.navigation.compose.HiltViewModelKt.createHiltViewModelFactory
 *     at com.example.msp_app.feature.pagos.ui.PagosRutasKt$destinoDeListaDeClientes$1.invoke(PagosRutas.kt:209)
 * ```
 *
 * `hiltViewModel()` exige una Activity `@AndroidEntryPoint`, y la única del repo
 * es `MainActivity` — o sea `androidTest`, que **ninguna compuerta corre**
 * (`build.gradle.kts` excluye las tareas `connected*`). Una compuerta que no
 * corre no es una compuerta. Por eso la medición baja un piso: el `*Screen`, que
 * recibe el ViewModel por parámetro y sí se monta en la JVM.
 *
 * ## La compuerta contra la pantalla número ocho
 *
 * Este defecto ya ocurrió DOS veces, y la primera dejó una nota en el KDoc de
 * `CollectionReportScreen` que **no impidió la segunda**. Una nota no es una
 * red. Por eso [las pantallas no se listan a mano][cadaPantallaDelModuloTieneSuPrueba]:
 * salen de `src/main`, y una pantalla nueva pone rojo este archivo el día que
 * nace.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ElTemaLoPoneLaPantallaTest : RobolectricTestBase() {

    @get:Rule
    val composeTestRule = createComposeRule()

    // Los puertos se quedan vacíos a propósito: en el emulador el crash ocurrió
    // con la base SIN sincronizar, en el estado `Cargando`. La lectura del tema
    // está en el modificador más externo, antes de cualquier dato.
    private val io = StandardTestDispatcher()
    private val clock = FakeClock(PagosFixtures.AHORA)
    private val telemetria = RecordingTelemetry(clock)

    private val ventasPort = FakeVentasPort()
    private val pagosPort = FakePagosPort()
    private val visitasPort = FakeVisitasPort()
    private val liquidacionPort = FakeLiquidacionPort()
    private val garantiasPort = FakeGarantiasPort()
    private val periodoPort = FakePeriodoDeCobroPort()
    private val fichaPort = FakeFichaPort()
    private val registroPort = FakeRegistroDeAbonoPort()
    private val camaraPort = FakeComprobantesPort()
    private val printer = FakePrinterPort()
    private val impresoraPreferida = FakeImpresoraPreferida()
    private val printLog = FakePrintLog()

    private fun cobranzaDelCliente() = ReunirCobranzaDelCliente(
        ventasPort = ventasPort,
        pagosPort = pagosPort,
        visitasPort = visitasPort,
        liquidacionPort = liquidacionPort,
        resolverVentanaDeCobro = ResolverVentanaDeCobro(periodoPort, clock),
        derivarEstadoDelPeriodo = DerivarEstadoDelPeriodo(telemetria)
    )

    private fun cargarDetalleVenta() = CargarDetalleVenta(
        ventasPort = ventasPort,
        garantiasPort = garantiasPort,
        reunirCobranzaDelCliente = cobranzaDelCliente(),
        clock = clock
    )

    private fun impresion() = TicketPrinting(
        directorio = PrinterDirectory(printer, impresoraPreferida),
        evaluar = EvaluatePrintPermission(printLog, clock),
        imprimirYRegistrar = PrintTicketUseCase(printer, printLog, clock)
    )

    /**
     * Monta [pantalla] **sin `MspTheme` y sin ningún `CompositionLocalProvider`
     * de andamiaje**: exactamente el árbol que `:app` construye alrededor de un
     * destino del `NavHost`. Cualquier envoltorio aquí volvería este test una
     * copia de los que ya existen — o sea, ninguna medición.
     */
    private fun montarSinTema(pantalla: @Composable () -> Unit) {
        composeTestRule.setContent { pantalla() }
        composeTestRule.waitForIdle()
        composeTestRule.onRoot().assertExists()
    }

    /**
     * Las cinco pantallas del módulo, cada una con su ViewModel real armado con
     * fakes. La clave es el nombre del composable, que es lo que
     * [cadaPantallaDelModuloTieneSuPrueba] compara contra `src/main`.
     */
    private fun pantallas(): Map<String, @Composable () -> Unit> = mapOf(
        "ListaDeClientesScreen" to listaDeClientes(),
        "DetalleClienteScreen" to detalleDeCliente(),
        "DetalleVentaScreen" to detalleDeVenta(),
        "RegistrarAbonoScreen" to registrarAbono(),
        "TicketDePagoScreen" to ticketDePago()
    )

    private fun listaDeClientes(): @Composable () -> Unit = {
        ListaDeClientesScreen(
            viewModel = ListaDeClientesViewModel(
                savedStateHandle = SavedStateHandle(),
                reunirCartera = ReunirCartera(
                    ventasPort = ventasPort,
                    pagosPort = pagosPort,
                    visitasPort = visitasPort,
                    resolverVentanaDeCobro = ResolverVentanaDeCobro(periodoPort, clock),
                    derivarEstadoDelPeriodo = DerivarEstadoDelPeriodo(telemetria),
                    telemetry = telemetria
                ),
                telemetry = telemetria,
                io = io
            ),
            onAtras = {},
            onAbrirCliente = {},
            onAbrirVenta = {}
        )
    }

    private fun detalleDeCliente(): @Composable () -> Unit = {
        DetalleClienteScreen(
            viewModel = DetalleClienteViewModel(
                savedStateHandle = SavedStateHandle(
                    mapOf(PagosRutas.ARG_CLIENTE_ID to PagosFixtures.CLIENTE_ID)
                ),
                cargarDetalleCliente = CargarDetalleCliente(
                    fichaPort = fichaPort,
                    reunirCobranzaDelCliente = cobranzaDelCliente()
                ),
                guardarFichaDelCliente = GuardarFichaDelCliente(fichaPort),
                telemetry = telemetria,
                io = io
            ),
            onAtras = {},
            onAbrirVenta = {},
            onRegistrarAbono = {},
            onRegistrarVisita = { _, _ -> },
            onMasAcciones = {}
        )
    }

    private fun detalleDeVenta(): @Composable () -> Unit = {
        DetalleVentaScreen(
            viewModel = DetalleVentaViewModel(
                savedStateHandle = SavedStateHandle(
                    mapOf(PagosRutas.ARG_VENTA_ID to PagosFixtures.VENTA_EN_PROMESA)
                ),
                cargarDetalleVenta = cargarDetalleVenta(),
                telemetry = telemetria,
                io = io
            ),
            onAtras = {},
            onRegistrarAbono = {},
            onRegistrarVisita = { _, _ -> },
            onMasAcciones = {},
            onVerGarantia = {}
        )
    }

    private fun registrarAbono(): @Composable () -> Unit = {
        RegistrarAbonoScreen(
            viewModel = RegistrarAbonoViewModel(
                savedStateHandle = SavedStateHandle(
                    mapOf(PagosRutas.ARG_VENTA_ID to PagosFixtures.VENTA_EN_PROMESA)
                ),
                cargarDetalleVenta = cargarDetalleVenta(),
                registrarAbono = RegistrarAbono(registroPort, telemetria),
                camara = camaraPort,
                telemetry = telemetria,
                clock = clock,
                io = io
            ),
            onAtras = {},
            onRegistrado = {}
        )
    }

    private fun ticketDePago(): @Composable () -> Unit = {
        TicketDePagoScreen(
            viewModel = TicketDePagoViewModel(
                savedStateHandle = SavedStateHandle(
                    mapOf(PagosRutas.ARG_PAGO_ID to TicketFixtures.PAGO_ID)
                ),
                cargarTicket = CargarTicketDePago(pagosPort, ventasPort),
                impresion = impresion(),
                telemetry = telemetria,
                io = io
            ),
            onAtras = {}
        )
    }

    private fun montar(nombre: String) = montarSinTema(pantallas().getValue(nombre))

    @Test
    fun `la lista de clientes se monta sin que nadie le de el tema`() {
        montar("ListaDeClientesScreen")
    }

    @Test
    fun `el detalle de cliente se monta sin que nadie le de el tema`() {
        montar("DetalleClienteScreen")
    }

    @Test
    fun `el detalle de venta se monta sin que nadie le de el tema`() {
        montar("DetalleVentaScreen")
    }

    @Test
    fun `registrar abono se monta sin que nadie le de el tema`() {
        montar("RegistrarAbonoScreen")
    }

    @Test
    fun `el ticket de pago se monta sin que nadie le de el tema`() {
        montar("TicketDePagoScreen")
    }

    /**
     * **La compuerta.** Las pantallas cubiertas arriba no son una lista escrita
     * a mano: tienen que ser EXACTAMENTE las que declara `src/main`. Una
     * pantalla número seis en este módulo pone rojo este test el día que se
     * escribe, no el día que un cobrador abre la app.
     *
     * Control positivo: el escaneo tiene que encontrar algo. Un glob roto
     * devolvería el conjunto vacío y el `assertEquals` pasaría contra un mapa
     * vacío si el mapa también se vaciara — de ahí el `assertTrue` previo, que
     * prueba que el método SÍ ve pantallas.
     */
    @Test
    fun cadaPantallaDelModuloTieneSuPrueba() {
        val declaradas = pantallasDeclaradasEn(File("src/main/kotlin"))
        assertTrue(
            "el escaneo no encontró ninguna pantalla: el método no probaría nada",
            declaradas.isNotEmpty()
        )
        assertEquals(declaradas, pantallas().keys.toSortedSet())
    }

    private companion object {

        /** `fun XxxScreen(` de nivel superior, que es la forma que el grafo monta. */
        val PANTALLA = Regex("""^(?:internal )?fun ([A-Z][A-Za-z0-9]*Screen)\(""")

        fun pantallasDeclaradasEn(raiz: File) = raiz.walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .flatMap { archivo ->
                archivo.readLines().mapNotNull { PANTALLA.find(it)?.groupValues?.get(1) }
            }
            .toSortedSet()
    }
}
