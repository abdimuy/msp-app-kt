package com.example.msp_app.feature.collectionreport.ui

import com.example.msp_app.core.telemetry.TelemetryEventType
import com.example.msp_app.core.testing.telemetry.RecordingTelemetry
import com.example.msp_app.core.testing.time.FakeClock
import com.example.msp_app.feature.collectionreport.data.fake.FakeHistoricalTotalsPort
import com.example.msp_app.feature.collectionreport.data.fake.FakePaymentsPort
import com.example.msp_app.feature.collectionreport.data.fake.FakeUserCyclePort
import com.example.msp_app.feature.collectionreport.data.fake.FakeVisitsPort
import com.example.msp_app.feature.collectionreport.domain.DeltaChip
import com.example.msp_app.feature.collectionreport.domain.DeltaDirection
import com.example.msp_app.feature.collectionreport.domain.Insight
import com.example.msp_app.feature.collectionreport.domain.RangeCalculator
import com.example.msp_app.feature.collectionreport.domain.model.CollectionPayment
import com.example.msp_app.feature.collectionreport.domain.model.DateRange
import com.example.msp_app.feature.collectionreport.domain.model.Forgiveness
import com.example.msp_app.feature.collectionreport.domain.model.Money
import com.example.msp_app.feature.collectionreport.domain.model.PaymentMethod
import com.example.msp_app.feature.collectionreport.domain.model.ReportPeriod
import com.example.msp_app.feature.collectionreport.domain.port.PaymentsPort
import com.example.msp_app.feature.collectionreport.domain.port.UserCyclePort
import java.math.BigDecimal
import java.time.Instant
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Cobertura del `@HiltViewModel` piloto: carga inicial (Día) desde los fakes de Task 4,
 * transición Día↔Semana (rango de ciclo vía [FakeUserCyclePort]), máscara/orden/sheet como
 * eventos puros de UI, listas vacías sin crash, y un puerto que falla degradando a estado de
 * error (nunca una excepción sin capturar).
 *
 * **`StandardTestDispatcher` en vez de `MainDispatcherRule`/`Unconfined` (deliberado):** con
 * un dispatcher Unconfined el `viewModelScope.launch` del `init` corre síncrono hasta el
 * final antes de que el test pueda observar el `loading = true` inicial (los fakes no tienen
 * ningún punto de suspensión real). `StandardTestDispatcher` encola el trabajo del `init` sin
 * correrlo hasta `advanceUntilIdle()`, así el test puede aseverar el estado ANTES y DESPUÉS de
 * resolver la carga — necesario para cubrir la transición loading→contenido del brief.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class CollectionReportViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    // Viernes 7-ago-2026 mediodía CDMX (== 18:00Z) — mismo fixture que ReportAggregatorTest,
    // coincide con las cifras del mockup `docs/design/reporte-cobranza-mockup.html`.
    private val clock = FakeClock(Instant.parse("2026-08-07T18:00:00Z"))

    private lateinit var paymentsPort: FakePaymentsPort
    private lateinit var visitsPort: FakeVisitsPort
    private lateinit var userCyclePort: FakeUserCyclePort
    private lateinit var historicalTotalsPort: FakeHistoricalTotalsPort
    private lateinit var telemetry: RecordingTelemetry

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        paymentsPort = FakePaymentsPort()
        visitsPort = FakeVisitsPort()
        userCyclePort = FakeUserCyclePort()
        historicalTotalsPort = FakeHistoricalTotalsPort()
        telemetry = RecordingTelemetry(clock)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun viewModel(payments: PaymentsPort = paymentsPort) = CollectionReportViewModel(
        payments,
        visitsPort,
        userCyclePort,
        historicalTotalsPort,
        clock,
        telemetry
    )

    private fun money(v: String) = Money.of(BigDecimal(v))

    private fun payment(
        id: String = "p",
        cliente: String = "María López Hernández",
        amount: Money = money("1000.00"),
        method: PaymentMethod = PaymentMethod.EFECTIVO,
        paidAt: Instant = Instant.parse("2026-08-07T15:00:00Z"),
        synced: Boolean = true
    ) = CollectionPayment(id, cliente, "Muebles Bahía", amount, method, paidAt, synced)

    // ─── carga inicial (Día) ────────────────────────────────────────────

    @Test
    fun `estado inicial arranca en loading y transiciona a contenido con los datos de los fakes`() =
        runTest(
            testDispatcher
        ) {
            paymentsPort.payments = listOf(
                payment(id = "p1", cliente = "María López Hernández", amount = money("1200.00"), paidAt = Instant.parse("2026-08-07T15:12:00Z")),
                payment(
                    id = "p2",
                    cliente = "Juan Pérez Ramírez",
                    amount = money("850.00"),
                    method = PaymentMethod.TRANSFERENCIA,
                    paidAt = Instant.parse("2026-08-07T14:40:00Z")
                )
            )
            paymentsPort.pending = 3
            userCyclePort.nombre = "Gabriel Roque"

            val vm = viewModel()
            assertTrue("el estado por default debe iniciar en loading", vm.state.value.loading)

            testDispatcher.scheduler.advanceUntilIdle()

            val state = vm.state.value
            assertFalse(state.loading)
            assertNull(state.error)
            assertEquals("Gabriel Roque", state.cobrador)
            assertEquals(3, state.pendingCount)
            assertEquals(money("2050.00"), state.hero.monto)
            assertEquals(money("1200.00"), state.efectivo.amount)
            assertEquals(1, state.efectivo.count)
            assertEquals(money("850.00"), state.transferencia.amount)
            assertEquals(1, state.transferencia.count)
            assertTrue(state.hero.insight is Insight.Daily)

            val detail = state.detail as DetailUi.Payments
            // orden por defecto HORA: 14:40 (p2) antes que 15:12 (p1).
            assertEquals(listOf("p2", "p1"), detail.rows.map { it.id })

            assertTrue(
                "debe emitir screenView de telemetria",
                telemetry.recorded.any {
                    it.type == TelemetryEventType.SCREEN_VIEW && it.name == "collection_report"
                }
            )
        }

    @Test
    fun `sin pagos ni visitas ni condonaciones produce un estado vacio sano sin crash`() = runTest(
        testDispatcher
    ) {
        val vm = viewModel()

        testDispatcher.scheduler.advanceUntilIdle()

        val state = vm.state.value
        assertFalse(state.loading)
        assertNull(state.error)
        assertEquals(Money.ZERO, state.hero.monto)
        assertEquals(0, state.efectivo.count)
        assertEquals(Money.ZERO, state.efectivo.amount)
        assertEquals(0, state.visitas.count)
        assertEquals(Money.ZERO, state.condonado.amount)
        assertEquals(0f, state.hero.progress)
        assertEquals(DeltaChip("—", DeltaDirection.NONE), state.hero.delta)
        assertTrue((state.detail as DetailUi.Payments).rows.isEmpty())
    }

    // ─── Día ↔ Semana ───────────────────────────────────────────────────

    @Test
    fun `setPeriod SEMANA usa el ciclo del cobrador y cambia el detalle a resumen por dia`() =
        runTest(
            testDispatcher
        ) {
            userCyclePort.fechaCarga = Instant.parse("2026-08-03T16:00:00Z") // lunes
            paymentsPort.payments = listOf(payment(amount = money("500.00")))

            val vm = viewModel()
            testDispatcher.scheduler.advanceUntilIdle()
            assertEquals(0, userCyclePort.fechaCargaInicialCalls) // Día no consulta el ciclo

            vm.setPeriod(ReportPeriod.SEMANA)
            testDispatcher.scheduler.advanceUntilIdle()

            val state = vm.state.value
            assertEquals(ReportPeriod.SEMANA, state.period)
            assertEquals(1, userCyclePort.fechaCargaInicialCalls)

            val expectedRange = RangeCalculator.cycleRange(clock, userCyclePort.fechaCarga)
            assertEquals(
                5,
                expectedRange.days
            ) // lun 3 - vie 7 ago, 5 días (mismo ciclo del mockup)
            assertEquals(expectedRange.cycleLabel(), state.rangeLabel)
            assertEquals(
                // historicalTotalsPort vacío -> meta $0.00 -> progreso 0 -> 0%; 1 solo pago -> count=1.
                Insight.Weekly(
                    count = 1,
                    progressPct = 0,
                    cycleDay = expectedRange.days,
                    cycleDays = expectedRange.days
                ),
                state.hero.insight
            )

            val detail = state.detail as DetailUi.Days
            assertEquals(expectedRange.days, detail.rows.size)
        }

    /**
     * Fix round 1 (Important 1): la versión anterior de este test lanzaba
     * `setPeriod(SEMANA)` + `setPeriod(DIA)` seguidos y solo comprobaba el estado final — con
     * fakes SIN ningún punto de suspensión real, eso pasaba IGUAL con o sin `loadJob?.cancel()`
     * (la de SEMANA nunca llegaba a resolver antes de que DIA la pisara por orden de
     * lanzamiento, no por cancelación real). Mismo patrón que
     * `CobranzaReconcilerTest.mutexSerializesConcurrentCalls`: se usa un `CompletableDeferred`
     * para dejar la carga de SEMANA genuinamente EN VUELO (suspendida a mitad de
     * `paymentsIn`) antes de disparar `setPeriod(DIA)`, y solo se libera la compuerta
     * DESPUÉS — si `loadJob?.cancel()` se borrara, el resultado de SEMANA (aplazado, no
     * cancelado) pisaría el de DIA al liberar la compuerta y la aserción final fallaría.
     */
    @Test
    fun `setPeriod cancela genuinamente una carga en vuelo, el resultado viejo no pisa el nuevo`() =
        runTest(
            testDispatcher
        ) {
            userCyclePort.fechaCarga = Instant.parse("2026-08-03T16:00:00Z")
            val semanaRange = RangeCalculator.cycleRange(clock, userCyclePort.fechaCarga)

            val entered = CompletableDeferred<Unit>()
            val gate = CompletableDeferred<Unit>()
            val diaPayments = listOf(payment(id = "dia-1", amount = money("100.00")))
            val semanaPayments = listOf(payment(id = "semana-1", amount = money("999999.00")))

            // Solo bloquea la consulta que coincide EXACTO con el rango de Semana (el rango previo
            // del delta y las cargas de Día usan otro rango, así que pasan directo).
            val gatedPort = object : PaymentsPort {
                override suspend fun paymentsIn(range: DateRange): List<CollectionPayment> =
                    if (range == semanaRange) {
                        entered.complete(Unit)
                        gate.await()
                        semanaPayments
                    } else {
                        diaPayments
                    }
                override suspend fun forgivenessIn(range: DateRange): List<Forgiveness> =
                    emptyList()
                override suspend fun paymentsGroupedByDaySince(
                    startIso: String
                ): Map<String, List<CollectionPayment>> = emptyMap()
                override suspend fun pendingCount(): Int = 0
            }

            val vm = viewModel(payments = gatedPort)
            testDispatcher.scheduler.advanceUntilIdle() // carga inicial Día, sin bloqueo.
            assertEquals(money("100.00"), vm.state.value.hero.monto)

            vm.setPeriod(
                ReportPeriod.SEMANA
            ) // su paymentsIn(semanaRange) queda EN VUELO en `gate`.
            testDispatcher.scheduler.runCurrent()
            assertTrue(
                "la carga de Semana debe estar genuinamente suspendida en la compuerta",
                entered.isCompleted
            )

            vm.setPeriod(
                ReportPeriod.DIA
            ) // debe cancelar el job de Semana (aún colgado) y lanzar uno nuevo.
            testDispatcher.scheduler.advanceUntilIdle() // el nuevo Día no está gateado -> resuelve completo.

            // Soltamos la compuerta recién ahora: si Semana NO fue cancelada, su continuación
            // resume aquí y pisaría el estado con sus datos — la aserción de abajo lo detecta.
            gate.complete(Unit)
            testDispatcher.scheduler.advanceUntilIdle()

            val state = vm.state.value
            assertEquals(ReportPeriod.DIA, state.period)
            assertEquals(
                money("100.00"),
                state.hero.monto
            ) // NUNCA el 999999.00 de la Semana cancelada.
        }

    // ─── eventos puros de UI (máscara / orden / sheet) ─────────────────

    @Test
    fun `toggleMask solo cambia el flag masked y preserva intactos los Money del estado`() =
        runTest(
            testDispatcher
        ) {
            paymentsPort.payments = listOf(payment(amount = money("999.99")))
            val vm = viewModel()
            testDispatcher.scheduler.advanceUntilIdle()
            val before = vm.state.value

            vm.toggleMask()

            assertEquals(before.copy(masked = true), vm.state.value)
        }

    @Test
    fun `setSort NOMBRE reordena el detalle de pagos por nombre de cliente en Dia`() = runTest(
        testDispatcher
    ) {
        paymentsPort.payments = listOf(
            payment(id = "p1", cliente = "Rosa Martínez Cruz", paidAt = Instant.parse("2026-08-07T09:00:00Z")),
            payment(id = "p2", cliente = "Ana Delgado Soto", paidAt = Instant.parse("2026-08-07T10:00:00Z"))
        )
        val vm = viewModel()
        testDispatcher.scheduler.advanceUntilIdle()
        val byHora = (vm.state.value.detail as DetailUi.Payments).rows.map { it.id }
        assertEquals(listOf("p1", "p2"), byHora)

        vm.setSort(DetailSort.NOMBRE)

        val state = vm.state.value
        assertEquals(DetailSort.NOMBRE, state.sort)
        val byNombre = (state.detail as DetailUi.Payments).rows.map { it.cliente }
        assertEquals(listOf("Ana Delgado Soto", "Rosa Martínez Cruz"), byNombre)
    }

    @Test
    fun `openSheet setea el sheet con su kind y argumento, closeSheet lo limpia`() = runTest(
        testDispatcher
    ) {
        val vm = viewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        vm.openSheet(SheetKind.PAGO, "p1")
        assertEquals(SheetUi(SheetKind.PAGO, "p1"), vm.state.value.sheet)

        vm.openSheet(SheetKind.EFECTIVO)
        assertEquals(SheetUi(SheetKind.EFECTIVO, null), vm.state.value.sheet)

        vm.closeSheet()
        assertNull(vm.state.value.sheet)
    }

    @Test
    fun `pendingCount refleja el valor configurado en el fake de PaymentsPort`() = runTest(
        testDispatcher
    ) {
        paymentsPort.pending = 7
        val vm = viewModel()

        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(7, vm.state.value.pendingCount)
    }

    // ─── falla de un puerto → error, nunca un crash ────────────────────

    private class ThrowingPaymentsPort(private val message: String) : PaymentsPort {
        override suspend fun paymentsIn(range: DateRange): List<CollectionPayment> =
            throw IllegalStateException(message)
        override suspend fun forgivenessIn(range: DateRange): List<Forgiveness> = emptyList()
        override suspend fun paymentsGroupedByDaySince(
            startIso: String
        ): Map<String, List<CollectionPayment>> = emptyMap()
        override suspend fun pendingCount(): Int = 0
    }

    @Test
    fun `fallo del puerto de pagos deja el estado en error sin crashear y lo reporta a telemetria`() =
        runTest(
            testDispatcher
        ) {
            val vm = viewModel(payments = ThrowingPaymentsPort("boom: room io"))

            testDispatcher.scheduler.advanceUntilIdle()

            val state = vm.state.value
            assertFalse(state.loading)
            assertEquals("no se pudo cargar el reporte de cobranza", state.error)
            assertTrue(telemetry.recorded.any { it.type == TelemetryEventType.ERROR })
        }

    private class ThrowingUserCyclePort(
        private val delegate: UserCyclePort,
        private val message: String
    ) : UserCyclePort by delegate {
        override suspend fun fechaCargaInicial(): Instant? = throw IllegalStateException(message)
    }

    /**
     * Fix round 1 (Important 2): antes de este fix, un fallo a mitad de `setPeriod(SEMANA)`
     * dejaba `state.period == SEMANA` pero `hero`/`detail`/`rangeLabel` seguían siendo los de
     * Día (la UI mostraría "Semana" seleccionada con las cifras/etiqueta de Día). Se elige
     * conservar el `period` que el usuario pidió (el toggle no "rebota" solo) y blanquear TODO
     * el contenido dependiente del rango junto con el error, en vez de revertir `period` — ver
     * el KDoc de `CollectionReportViewModel.applyError`.
     */
    @Test
    fun `fallo al cambiar a Semana deja period, rangeLabel y contenido consistentes, sin mezclar con Dia`() =
        runTest(testDispatcher) {
            paymentsPort.payments = listOf(payment(amount = money("100.00")))
            val vm = CollectionReportViewModel(
                paymentsPort,
                visitsPort,
                ThrowingUserCyclePort(userCyclePort, "firestore down"),
                historicalTotalsPort,
                clock,
                telemetry
            )
            testDispatcher.scheduler.advanceUntilIdle()
            val diaState = vm.state.value
            assertEquals(
                ReportPeriod.DIA,
                diaState.period
            ) // Día no consulta fechaCargaInicial -> carga bien.
            assertEquals(money("100.00"), diaState.hero.monto)

            vm.setPeriod(ReportPeriod.SEMANA)
            testDispatcher.scheduler.advanceUntilIdle()

            val state = vm.state.value
            assertEquals("el toggle no debe revertirse solo", ReportPeriod.SEMANA, state.period)
            assertEquals("no se pudo cargar el reporte de cobranza", state.error)
            assertFalse(state.loading)
            // Nada del Día sobrevive mezclado con period=SEMANA: todo el contenido está en blanco.
            assertEquals("", state.rangeLabel)
            assertEquals(0, state.pendingCount)
            assertEquals(Money.ZERO, state.hero.monto)
            assertEquals(Money.ZERO, state.efectivo.amount)
            assertEquals(0, state.efectivo.count)
            assertEquals(Money.ZERO, state.transferencia.amount)
            assertNull(state.condonado.amount)
            assertNull(state.visitas.count)
            assertTrue((state.detail as DetailUi.Payments).rows.isEmpty())
        }
}
