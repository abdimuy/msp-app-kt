package com.example.msp_app.feature.collectionreport.ui

import com.example.msp_app.core.testing.telemetry.RecordingTelemetry
import com.example.msp_app.core.testing.time.FakeClock
import com.example.msp_app.feature.collectionreport.data.fake.FakeHistoricalTotalsPort
import com.example.msp_app.feature.collectionreport.data.fake.FakePaymentsPort
import com.example.msp_app.feature.collectionreport.data.fake.FakePreferredPrinterStore
import com.example.msp_app.feature.collectionreport.data.fake.FakePrinterPort
import com.example.msp_app.feature.collectionreport.data.fake.FakeReportThemePort
import com.example.msp_app.feature.collectionreport.data.fake.FakeSalesPort
import com.example.msp_app.feature.collectionreport.data.fake.FakeUserCyclePort
import com.example.msp_app.feature.collectionreport.data.fake.FakeVisitsPort
import com.example.msp_app.feature.collectionreport.domain.CobranzaPorcentaje
import com.example.msp_app.feature.collectionreport.domain.model.CollectionPayment
import com.example.msp_app.feature.collectionreport.domain.model.Money
import com.example.msp_app.feature.collectionreport.domain.model.PaymentMethod
import com.example.msp_app.feature.collectionreport.domain.model.ReportPeriod
import com.example.msp_app.feature.collectionreport.domain.model.SaleForCobranza
import com.example.msp_app.feature.collectionreport.domain.port.CycleStart
import java.math.BigDecimal
import java.time.Instant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * DEFECTO D5 — el tablero mostraba **$0.00 cobrado en la semana con la tabla de pagos llena**.
 *
 * La causa no era una resincronización pendiente sino la VENTANA DE FECHAS: sin
 * `FECHA_CARGA_INICIAL` (Firestore ausente o caído, degradado a `null` por el adapter) el rango
 * de la semana se encogía al día de hoy. La prueba de campo que lo separa de cualquier otra
 * hipótesis está en el mismo tablero: el contador de VENTAS (103, **sin filtro de fecha**)
 * sobrevivía intacto mientras los pagos daban 0. Sólo un rango malo produce `0/103`.
 *
 * Y era peor en el reporte que en el inicio: el inicio se alimenta de Flows y se recupera solo
 * cuando el dato llega; el reporte era todo `suspend` one-shot y se quedaba en $0 hasta que el
 * cobrador salía y volvía a entrar.
 *
 * Esta suite fija las cuatro mitades del arreglo:
 *  1. sin dato de semana NO se inventa ventana (ni hoy, ni ahora);
 *  2. el estado es HONESTO en pantalla en vez de un $0 que parece cifra real;
 *  3. cuando el dato llega tarde, el reporte se repara SOLO — sin que el usuario toque nada;
 *  4. un fallo posterior no borra la semana que ya se conocía.
 *
 * `StandardTestDispatcher` + `advanceUntilIdle()` corren el reintento en tiempo VIRTUAL: las
 * esperas del backoff no cuestan segundos reales y el test sigue siendo determinista.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class CollectionReportCycleGapTest {

    private val testDispatcher = StandardTestDispatcher()

    // Viernes 7-ago-2026 mediodía CDMX (== 18:00Z) — mismo fixture que el resto de la suite.
    private val clock = FakeClock(Instant.parse("2026-08-07T18:00:00Z"))

    /** Carga de ruta del lunes 3-ago 10:00 CDMX: semana de 5 días (lun 3 … vie 7). */
    private val cargaLunes = Instant.parse("2026-08-03T16:00:00Z")

    private lateinit var paymentsPort: FakePaymentsPort
    private lateinit var visitsPort: FakeVisitsPort
    private lateinit var userCyclePort: FakeUserCyclePort
    private lateinit var historicalTotalsPort: FakeHistoricalTotalsPort
    private lateinit var salesPort: FakeSalesPort
    private lateinit var printerPort: FakePrinterPort
    private lateinit var preferredPrinterStore: FakePreferredPrinterStore
    private lateinit var telemetry: RecordingTelemetry
    private lateinit var reportThemePort: FakeReportThemePort

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        paymentsPort = FakePaymentsPort()
        visitsPort = FakeVisitsPort()
        userCyclePort = FakeUserCyclePort()
        historicalTotalsPort = FakeHistoricalTotalsPort()
        salesPort = FakeSalesPort()
        printerPort = FakePrinterPort()
        preferredPrinterStore = FakePreferredPrinterStore()
        telemetry = RecordingTelemetry(clock)
        reportThemePort = FakeReportThemePort()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun viewModel() = CollectionReportViewModel(
        paymentsPort,
        visitsPort,
        userCyclePort,
        historicalTotalsPort,
        salesPort,
        printerPort,
        preferredPrinterStore,
        clock,
        telemetry,
        reportThemePort,
        testDispatcher
    )

    private fun money(v: String) = Money.of(BigDecimal(v))

    private fun payment(
        id: String = "p",
        amount: Money = money("1000.00"),
        paidAt: Instant = Instant.parse("2026-08-05T15:00:00Z")
    ) = CollectionPayment(
        id,
        "María López Hernández",
        "Muebles Bahía",
        amount,
        PaymentMethod.EFECTIVO,
        paidAt,
        synced = true
    )

    /** Las 103 ventas vivas del tablero de campo — sin filtro de fecha, por eso sobrevivían. */
    private fun ventasVivas(n: Int) = (1..n).map { i ->
        SaleForCobranza(
            doctoCcAcrId = 5000 + i,
            parcialidad = money("350.00"),
            totalImporte = money("0.00"),
            saldoHoy = money("7000.00"),
            frecuencia = CobranzaPorcentaje.Frecuencia.SEMANAL,
            fechaCargo = Instant.parse("2026-06-01T15:00:00Z")
        )
    }

    // ─── 1 + 2: sin dato de semana no se inventa ventana, y se dice ──────────────────────

    @Test
    fun `Semana sin fecha de carga no consulta ningun rango y lo anuncia en pantalla`() =
        runTest(testDispatcher) {
            userCyclePort.nextStart = CycleStart.Missing
            paymentsPort.payments = listOf(payment(amount = money("4300.00")))

            val vm = viewModel()
            testDispatcher.scheduler.advanceUntilIdle()
            paymentsPort.paymentsInCalls.clear()

            vm.setPeriod(ReportPeriod.SEMANA)
            testDispatcher.scheduler.advanceUntilIdle()

            val state = vm.state.value
            assertEquals(ReportPeriod.SEMANA, state.period)
            assertFalse(state.loading)
            // No es un error: es un dato que falta. El banner rojo mentiría igual que el $0.
            assertNull(state.error)
            assertEquals("sin inicio de semana", state.cycleNotice)
            // Y sobre todo: NO se consultó ningún rango inventado.
            assertTrue(
                "Semana sin ventana no debe consultar pagos: ${paymentsPort.paymentsInCalls}",
                paymentsPort.paymentsInCalls.isEmpty()
            )
            assertEquals("", state.rangeLabel)
            assertEquals(Money.ZERO, state.hero.monto)
        }

    @Test
    fun `el caso 0 de 103 - con la ventana desconocida no se sirve un cero junto a las ventas vivas`() =
        runTest(testDispatcher) {
            // Reproduce el tablero de campo: 103 ventas vivas (sin filtro de fecha) y pagos
            // reales en Room. Con el fallback viejo esto daba "0 / 103" y parecía un dato.
            userCyclePort.nextStart = CycleStart.Missing
            salesPort.sales = ventasVivas(103)
            paymentsPort.payments = listOf(
                payment(id = "p1", amount = money("1500.00")),
                payment(id = "p2", amount = money("2800.00"))
            )

            val vm = viewModel()
            vm.setPeriod(ReportPeriod.SEMANA)
            testDispatcher.scheduler.advanceUntilIdle()

            val state = vm.state.value
            // El numerador en cero SÓLO puede aparecer junto a un denominador vivo si de verdad
            // se midió una ventana. Aquí no hay ventana, así que tampoco hay pareja que leer.
            assertTrue(state.cycleNotice.isNotEmpty())
            assertEquals(0, state.hero.clientesTotal)
            assertEquals(0, state.hero.clientesPagaron)
            assertEquals(0f, state.hero.porcentajeCobro)
            // Ni siquiera se pagó la consulta de ventas: no hay contra qué compararlas.
            assertEquals(0, salesPort.nonContadoActiveSalesCalls)
        }

    @Test
    fun `Dia sigue funcionando sin fecha de carga - hoy no depende de la semana`() =
        runTest(testDispatcher) {
            userCyclePort.nextStart = CycleStart.Missing
            paymentsPort.payments = listOf(payment(amount = money("900.00")))

            val vm = viewModel()
            testDispatcher.scheduler.advanceUntilIdle()

            val state = vm.state.value
            assertEquals(ReportPeriod.DIA, state.period)
            assertEquals("", state.cycleNotice)
            assertEquals(money("900.00"), state.hero.monto)
            assertNotEquals("", state.rangeLabel)
        }

    // ─── 3: auto-reparación cuando el dato llega tarde ───────────────────────────────────

    @Test
    fun `el puerto que falla primero y responde despues repara la Semana solo, sin tocar nada`() =
        runTest(testDispatcher) {
            // Guion: las TRES primeras lecturas fallan (la del `init`, la del toque en Semana y
            // el primer reintento) y sólo la cuarta responde. Así el arreglo NO puede pasar por
            // una relectura provocada por el usuario: la única vía posible es el reintento.
            userCyclePort.scriptedStarts += CycleStart.Unavailable
            userCyclePort.scriptedStarts += CycleStart.Unavailable
            userCyclePort.scriptedStarts += CycleStart.Unavailable
            userCyclePort.scriptedStarts += CycleStart.Known(cargaLunes)
            paymentsPort.payments = listOf(payment(amount = money("4300.00")))

            val vm = viewModel()
            vm.setPeriod(ReportPeriod.SEMANA)
            // A partir de aquí NADIE toca la pantalla: ni setPeriod, ni selectDay, ni una
            // recomposición forzada. Sólo pasa el tiempo (virtual).
            testDispatcher.scheduler.advanceUntilIdle()

            val reparado = vm.state.value
            assertNull(reparado.error)
            assertEquals("", reparado.cycleNotice)
            assertEquals(ReportPeriod.SEMANA, reparado.period)
            assertEquals(money("4300.00"), reparado.hero.monto)
            assertEquals("semana · lun 3 – vie 7 ago · 5 días", reparado.rangeLabel)
            assertTrue(
                "la reparación tuvo que pasar por el reintento: ${userCyclePort.cycleStartCalls}",
                userCyclePort.cycleStartCalls >= 4
            )
        }

    @Test
    fun `mientras la fuente sigue caida el tablero dice semana no disponible, no cero`() =
        runTest(testDispatcher) {
            userCyclePort.nextStart = CycleStart.Unavailable
            paymentsPort.payments = listOf(payment(amount = money("4300.00")))

            val vm = viewModel()
            vm.setPeriod(ReportPeriod.SEMANA)
            testDispatcher.scheduler.advanceUntilIdle()

            val state = vm.state.value
            assertEquals("semana no disponible", state.cycleNotice)
            assertEquals("", state.rangeLabel)
            assertEquals(Money.ZERO, state.hero.monto)
            // Se agotaron los reintentos: la fuente se consultó varias veces, no una sola.
            assertTrue(
                "debe haber reintentado: ${userCyclePort.cycleStartCalls}",
                userCyclePort.cycleStartCalls > 2
            )
        }

    // ─── 4: un fallo posterior no borra la semana ya conocida ────────────────────────────

    @Test
    fun `un fallo posterior de la fuente no degrada la semana que ya se conocia`() =
        runTest(testDispatcher) {
            userCyclePort.nextStart = CycleStart.Known(cargaLunes)
            paymentsPort.payments = listOf(payment(amount = money("4300.00")))

            val vm = viewModel()
            vm.setPeriod(ReportPeriod.SEMANA)
            testDispatcher.scheduler.advanceUntilIdle()
            assertEquals("semana · lun 3 – vie 7 ago · 5 días", vm.state.value.rangeLabel)

            // Se cae Firestore DESPUÉS de haber resuelto la semana.
            userCyclePort.nextStart = CycleStart.Unavailable
            vm.setPeriod(ReportPeriod.DIA)
            vm.setPeriod(ReportPeriod.SEMANA)
            testDispatcher.scheduler.advanceUntilIdle()

            val state = vm.state.value
            assertEquals("", state.cycleNotice)
            assertEquals("semana · lun 3 – vie 7 ago · 5 días", state.rangeLabel)
            assertEquals(money("4300.00"), state.hero.monto)
        }

    // ─── fecha de carga en el FUTURO: dato sucio, no "no cobré nada" ─────────────────────

    @Test
    fun `una carga en el futuro se anuncia como fecha invalida en vez de un tablero en cero`() =
        runTest(testDispatcher) {
            userCyclePort.nextStart = CycleStart.Known(Instant.parse("2026-09-01T16:00:00Z"))
            paymentsPort.payments = listOf(payment(amount = money("4300.00")))

            val vm = viewModel()
            testDispatcher.scheduler.advanceUntilIdle()

            // Con una carga futura la guarda `minOf(carga, cycleEnd)` deja TAMBIÉN el rango del
            // día vacío, así que ni Día ni Semana pueden decir nada honesto sobre cifras.
            assertEquals("fecha de semana inválida", vm.state.value.cycleNotice)

            vm.setPeriod(ReportPeriod.SEMANA)
            testDispatcher.scheduler.advanceUntilIdle()
            assertEquals("fecha de semana inválida", vm.state.value.cycleNotice)
            assertEquals(Money.ZERO, vm.state.value.hero.monto)
        }
}
