package com.example.msp_app.feature.collectionreport.ui

import com.example.msp_app.core.common.time.AppClock
import com.example.msp_app.core.common.time.AppTime
import com.example.msp_app.core.designsystem.component.formatMoneyMxn
import com.example.msp_app.core.testing.telemetry.RecordingTelemetry
import com.example.msp_app.core.testing.time.FakeClock
import com.example.msp_app.feature.collectionreport.data.fake.FakeHistoricalTotalsPort
import com.example.msp_app.feature.collectionreport.data.fake.FakePreferredPrinterStore
import com.example.msp_app.feature.collectionreport.data.fake.FakePrinterPort
import com.example.msp_app.feature.collectionreport.data.fake.FakeReportThemePort
import com.example.msp_app.feature.collectionreport.data.fake.FakeSalesPort
import com.example.msp_app.feature.collectionreport.data.fake.FakeUserCyclePort
import com.example.msp_app.feature.collectionreport.data.fake.FakeVisitsPort
import com.example.msp_app.feature.collectionreport.domain.model.CollectionPayment
import com.example.msp_app.feature.collectionreport.domain.model.DateRange
import com.example.msp_app.feature.collectionreport.domain.model.Forgiveness
import com.example.msp_app.feature.collectionreport.domain.model.Money
import com.example.msp_app.feature.collectionreport.domain.model.PaymentMethod
import com.example.msp_app.feature.collectionreport.domain.model.ReportPeriod
import com.example.msp_app.feature.collectionreport.domain.port.PaymentsPort
import com.example.msp_app.feature.collectionreport.ui.actions.ReportActionsController
import com.example.msp_app.feature.collectionreport.ui.actions.pdf.buildPdfReportModel
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
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
 * El camino COMPLETO del día mostrado: `CollectionReportViewModel` -> `CollectionReportStateBuilder`
 * -> `RangeCalculator`, con un puerto de pagos que sí respeta el rango que se le pide (a
 * diferencia del fake general, que devuelve siempre la misma lista) — sin eso, "cambiar de día
 * cambia el total" sería una aserción que pasa sola.
 *
 * Escenario de producción de la ruta 34 (medido el 13-ago-2026): el cobrador cargó ruta el
 * jueves 6 de agosto a las **19:33** hora de negocio y hoy es jueves 13. Ocho días de ciclo, dos
 * de ellos en cero, $43,850 en total — ver [MockupFixtures.TOTALES_RUTA_34].
 */
@OptIn(ExperimentalCoroutinesApi::class)
class CollectionReportDaySelectionTest {

    private val testDispatcher = StandardTestDispatcher()

    private lateinit var visitsPort: FakeVisitsPort
    private lateinit var userCyclePort: FakeUserCyclePort
    private lateinit var historicalTotalsPort: FakeHistoricalTotalsPort
    private lateinit var salesPort: FakeSalesPort
    private lateinit var telemetry: RecordingTelemetry
    private lateinit var reportThemePort: FakeReportThemePort

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        visitsPort = FakeVisitsPort()
        userCyclePort = FakeUserCyclePort(fechaCarga = MockupFixtures.CARGA_RUTA_34)
        historicalTotalsPort = FakeHistoricalTotalsPort()
        salesPort = FakeSalesPort()
        telemetry = RecordingTelemetry(FakeClock(MockupFixtures.AHORA_RUTA_34))
        reportThemePort = FakeReportThemePort()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // ══════════════════════════════════════════════════════════════════════════════════════
    // TAREA 1 — regresión del recorte del día
    // ══════════════════════════════════════════════════════════════════════════════════════

    /**
     * **La prueba de regresión del defecto corregido.** Falla con el código anterior, donde el
     * ViewModel mandaba `fechaCargaInicial = null` en el periodo Día: el dominio sabía recortar y
     * el `StateBuilder` ya le pasaba el parámetro, pero al llegar `null` el recorte quedaba
     * INERTE y el día de la carga arrancaba a medianoche.
     *
     * Reloj: el MISMO jueves de la carga, más tarde (21:00 hora de negocio). El rango con que se
     * consultan los pagos debe abrir a las 19:33 —el instante de la carga— y no a las 00:00, que
     * es lo que volvía a contar los cobros del ciclo ANTERIOR ($48,200 contra los $43,850 reales
     * de la ruta 34).
     */
    @Test
    fun `en DIA el rango arranca a la hora de la carga, no a medianoche`() = runTest(
        testDispatcher
    ) {
        val esaNoche = AppTime.parseWireFormat("2026-08-07T03:00:00Z") // jue 6, 21:00 CDMX
        val payments = RangeAwarePaymentsPort(emptyList())

        viewModel(payments = payments, clock = FakeClock(esaNoche))
        testDispatcher.scheduler.advanceUntilIdle()

        val rango = payments.paymentsInCalls.first()
        assertEquals(
            "el día debe abrir en el instante de la carga (19:33), no a medianoche",
            AppTime.toWireFormat(MockupFixtures.CARGA_RUTA_34),
            rango.startIso
        )
        assertEquals(
            AppTime.toWireFormat(AppTime.startOfNextDay(LocalDate.of(2026, 8, 6))),
            rango.endExclusiveIso
        )
    }

    /**
     * El contra-chequeo del fix: pedir `fechaCargaInicial` en Día NO enciende "Meta de la semana".
     * El gate de la meta es el PERIODO (`SalesPort` solo se consulta en Semana y `buildHero`
     * devuelve la meta vacía en Día), no la presencia de la fecha de carga.
     */
    @Test
    fun `pedir la fecha de carga en DIA no activa la meta semanal`() = runTest(testDispatcher) {
        val vm = viewModel(payments = RangeAwarePaymentsPort(pagosRuta34()))
        testDispatcher.scheduler.advanceUntilIdle()

        val state = vm.state.value
        assertEquals(ReportPeriod.DIA, state.period)
        assertEquals(1, userCyclePort.fechaCargaInicialCalls) // la fecha SÍ se pide
        assertEquals(0, salesPort.nonContadoActiveSalesCalls) // la meta NO se calcula
        assertEquals(0f, state.hero.porcentajeCobro)
        assertEquals(0f, state.hero.porcentajeCuentas)
        assertEquals(0, state.hero.clientesPagaron)
        assertEquals(0, state.hero.clientesTotal)
    }

    // ══════════════════════════════════════════════════════════════════════════════════════
    // TAREA 2 — la tira de días
    // ══════════════════════════════════════════════════════════════════════════════════════

    @Test
    fun `la tira lista exactamente los dias del ciclo, de la carga a hoy`() = runTest(
        testDispatcher
    ) {
        val vm = viewModel(payments = RangeAwarePaymentsPort(pagosRuta34()))
        testDispatcher.scheduler.advanceUntilIdle()

        val tira = vm.state.value.cycleDays
        assertEquals(MockupFixtures.CICLO_RUTA_34, tira.map { it.date })
        assertEquals(MockupFixtures.CICLO_RUTA_34.size, tira.size)
        assertTrue(tira.last().isToday)
        assertTrue(tira.last().isSelected)
        assertFalse(tira.first().isToday)
    }

    @Test
    fun `los dias en cero siguen en la tira, marcados y no escondidos`() = runTest(
        testDispatcher
    ) {
        val vm = viewModel(payments = RangeAwarePaymentsPort(pagosRuta34()))
        testDispatcher.scheduler.advanceUntilIdle()

        val tira = vm.state.value.cycleDays
        val jueves6 = tira.first { it.date == LocalDate.of(2026, 8, 6) }
        val viernes7 = tira.first { it.date == LocalDate.of(2026, 8, 7) }
        assertFalse("el día de la carga debe verse, atenuado", jueves6.hasCollections)
        assertFalse(viernes7.hasCollections)
        assertTrue(tira.first { it.date == LocalDate.of(2026, 8, 9) }.hasCollections)
    }

    @Test
    fun `elegir un dia pasado cambia el total y la lista de pagos`() = runTest(testDispatcher) {
        val vm = viewModel(payments = RangeAwarePaymentsPort(pagosRuta34()))
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals(money("5800"), vm.state.value.hero.monto) // hoy, jue 13

        vm.selectDay(LocalDate.of(2026, 8, 9)) // domingo, el día grande del ciclo
        testDispatcher.scheduler.advanceUntilIdle()

        val state = vm.state.value
        assertEquals(money("15350"), state.hero.monto)
        assertEquals(LocalDate.of(2026, 8, 9), state.selectedDay)
        val filas = (state.detail as DetailUi.Payments).rows
        assertEquals(1, filas.size)
        assertEquals(money("15350"), filas.single().amount)
        // La tira mueve la selección, pero "hoy" sigue siendo jueves: son estados distintos.
        assertTrue(state.cycleDays.first { it.date == LocalDate.of(2026, 8, 9) }.isSelected)
        assertTrue(state.cycleDays.first { it.date == MockupFixtures.HOY_RUTA_34 }.isToday)
        assertFalse(state.cycleDays.first { it.date == MockupFixtures.HOY_RUTA_34 }.isSelected)
    }

    /**
     * Lo que pidió el dueño con todas sus letras: poder **imprimir** cualquier día del ciclo. Las
     * tres acciones de salida se arman desde el estado ([ReportActionsController]), así que basta
     * con que el estado apunte al día elegido — pero eso hay que verificarlo, no suponerlo.
     */
    @Test
    fun `elegir un dia pasado cambia el destino de Compartir, Imprimir y PDF`() = runTest(
        testDispatcher
    ) {
        val clock = FakeClock(MockupFixtures.AHORA_RUTA_34)
        val vm = viewModel(payments = RangeAwarePaymentsPort(pagosRuta34()), clock = clock)
        testDispatcher.scheduler.advanceUntilIdle()

        vm.selectDay(LocalDate.of(2026, 8, 9))
        testDispatcher.scheduler.advanceUntilIdle()
        val state = vm.state.value

        val montoDelDomingo = formatMoneyMxn(money("15350").amount)
        val montoDeHoy = formatMoneyMxn(money("5800").amount)

        // Compartir (ACTION_SEND de texto) — resumen del día elegido.
        val compartir = ReportActionsController.buildShareText(state)
        assertTrue(
            "compartir no lleva el total del día elegido",
            compartir.contains(montoDelDomingo)
        )
        assertFalse("compartir sigue llevando el total de hoy", compartir.contains(montoDeHoy))
        assertTrue(compartir.contains(state.rangeLabel))
        assertTrue(compartir.contains("domingo"))

        // Imprimir (mismo ticket que recibe la impresora térmica).
        val ticket = ReportActionsController.buildTicketText(state, clock)
        assertTrue("el ticket no lleva el total del día elegido", ticket.contains(montoDelDomingo))
        assertTrue(ticket.contains(state.rangeLabel))

        // PDF: se afirma sobre el MODELO del documento (puro), no sobre el `PdfDocument`/`Intent`
        // de Android — esos necesitan un runtime real y no son lo que este test cuida. El modelo
        // es el único insumo de `generatePdf`, así que si él apunta al día elegido, el PDF también.
        val pdf = buildPdfReportModel(state, clock)
        assertTrue(
            "el encabezado del PDF no nombra el día elegido: '${pdf.header.subtitle}'",
            pdf.header.subtitle.contains(state.rangeLabel)
        )
        assertEquals(1, pdf.payments.size)
        assertEquals(money("15350"), pdf.payments.single().amount)
    }

    @Test
    fun `el dia de la carga cierra en cero y lo explica con la hora de arranque`() = runTest(
        testDispatcher
    ) {
        val vm = viewModel(payments = RangeAwarePaymentsPort(pagosRuta34()))
        testDispatcher.scheduler.advanceUntilIdle()

        vm.selectDay(LocalDate.of(2026, 8, 6)) // el jueves de la carga
        testDispatcher.scheduler.advanceUntilIdle()

        val state = vm.state.value
        assertEquals(Money.ZERO, state.hero.monto)
        assertTrue(state.selectedDayEmpty)
        assertTrue(
            "la nota debe llevar la hora de arranque",
            state.selectedDayNote.contains("7:33")
        )
        assertTrue(state.selectedDayNote.contains("inicio de semana"))
    }

    /**
     * Con un banner de error el detalle también queda vacío, pero por otra razón: la carga
     * falló. Ahí "Sin cobros" sería una mentira — el estado vacío honesto solo aplica cuando de
     * verdad no hubo cobros.
     */
    @Test
    fun `un fallo de carga no se disfraza de dia sin cobros`() = runTest(testDispatcher) {
        val vm = viewModel(payments = ThrowingPaymentsPort())
        testDispatcher.scheduler.advanceUntilIdle()

        val state = vm.state.value
        assertEquals("no se pudo cargar el reporte de cobranza", state.error)
        assertFalse(state.selectedDayEmpty)
    }

    @Test
    fun `en SEMANA no hay tira ni dia seleccionado`() = runTest(testDispatcher) {
        val vm = viewModel(payments = RangeAwarePaymentsPort(pagosRuta34()))
        testDispatcher.scheduler.advanceUntilIdle()

        vm.setPeriod(ReportPeriod.SEMANA)
        testDispatcher.scheduler.advanceUntilIdle()

        val state = vm.state.value
        assertTrue(state.cycleDays.isEmpty())
        assertNull(state.selectedDay)
        assertEquals("", state.selectedDayNote)
        // El ciclo entero sí suma los $43,850 reales de la ruta 34 — la suma de los días cuadra.
        assertEquals(money("43850"), state.hero.monto)
    }

    @Test
    fun `volver de Semana a Dia conserva el dia que estaba viendo`() = runTest(testDispatcher) {
        val vm = viewModel(payments = RangeAwarePaymentsPort(pagosRuta34()))
        testDispatcher.scheduler.advanceUntilIdle()
        vm.selectDay(LocalDate.of(2026, 8, 10))
        testDispatcher.scheduler.advanceUntilIdle()

        vm.setPeriod(ReportPeriod.SEMANA)
        testDispatcher.scheduler.advanceUntilIdle()
        vm.setPeriod(ReportPeriod.DIA)
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(LocalDate.of(2026, 8, 10), vm.state.value.selectedDay)
        assertEquals(money("8800"), vm.state.value.hero.monto)
    }

    /**
     * Cambio de ciclo: el cobrador vuelve a cargar ruta el lunes 10 mientras la pantalla tenía
     * elegido el domingo 9. Ese día ya no existe en la tira nueva, así que la selección vuelve a
     * hoy — no se queda apuntando a un día fantasma con rango vacío.
     */
    @Test
    fun `una carga de ruta nueva devuelve el dia seleccionado a hoy`() = runTest(testDispatcher) {
        val vm = viewModel(payments = RangeAwarePaymentsPort(pagosRuta34()))
        testDispatcher.scheduler.advanceUntilIdle()
        vm.selectDay(LocalDate.of(2026, 8, 9))
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals(LocalDate.of(2026, 8, 9), vm.state.value.selectedDay)

        userCyclePort.fechaCarga = AppTime.parseWireFormat("2026-08-10T15:00:00Z") // lun 10, 09:00
        vm.setPeriod(ReportPeriod.DIA) // cualquier recarga revalida la selección
        testDispatcher.scheduler.advanceUntilIdle()

        val state = vm.state.value
        assertEquals(MockupFixtures.HOY_RUTA_34, state.selectedDay)
        assertEquals(money("5800"), state.hero.monto)
        assertEquals(
            listOf(10, 11, 12, 13).map { LocalDate.of(2026, 8, it) },
            state.cycleDays.map { it.date }
        )
    }

    @Test
    fun `un cobrador sin ciclo no ve tira y sigue viendo hoy`() = runTest(testDispatcher) {
        userCyclePort.fechaCarga = null

        val vm = viewModel(payments = RangeAwarePaymentsPort(pagosRuta34()))
        testDispatcher.scheduler.advanceUntilIdle()

        val state = vm.state.value
        assertTrue(state.cycleDays.isEmpty())
        assertEquals(MockupFixtures.HOY_RUTA_34, state.selectedDay)
    }

    /**
     * Regresión (visto en un teléfono real el 2026-08-14): con el proceso vivo al cruzar la
     * medianoche, la tira seguía marcando el día de AYER como seleccionado.
     *
     * La causa era guardar el día auto-resuelto como si el cobrador lo hubiera pedido: la primera
     * carga resolvía "hoy = jue 13" y lo congelaba, y al pasar a viernes ese día seguía dentro del
     * ciclo, así que se quedaba elegido. Sin haberlo tocado nadie.
     *
     * Falla con el código viejo: `selectedDay` se queda en el 13.
     */
    @Test
    fun `sin eleccion del usuario, el dia seleccionado sigue a hoy al cruzar la medianoche`() =
        runTest(testDispatcher) {
            val clock = FakeClock(MockupFixtures.AHORA_RUTA_34)
            val vm = viewModel(payments = RangeAwarePaymentsPort(pagosRuta34()), clock = clock)
            testDispatcher.scheduler.advanceUntilIdle()
            assertEquals(MockupFixtures.HOY_RUTA_34, vm.state.value.selectedDay)

            // Pasa la medianoche con el proceso vivo. Nadie tocó la tira.
            clock.setNow(AppTime.parseWireFormat("2026-08-14T16:00:00Z"))
            vm.setPeriod(ReportPeriod.SEMANA)
            testDispatcher.scheduler.advanceUntilIdle()
            vm.setPeriod(ReportPeriod.DIA)
            testDispatcher.scheduler.advanceUntilIdle()

            val manana = MockupFixtures.HOY_RUTA_34.plusDays(1)
            assertEquals(
                "el día por defecto se quedó congelado en ayer",
                manana,
                vm.state.value.selectedDay
            )
            assertTrue(
                "el nuevo hoy no entró al ciclo",
                vm.state.value.cycleDays.any { it.isToday }
            )
        }

    /**
     * El complemento del anterior: una elección EXPLÍCITA sí se respeta mientras el día siga
     * dentro del ciclo. Si no, "seguir a hoy" se comería la selección del cobrador en cada
     * recarga y la tira sería inútil.
     */
    @Test
    fun `una eleccion explicita sobrevive a la recarga`() = runTest(testDispatcher) {
        val vm = viewModel(payments = RangeAwarePaymentsPort(pagosRuta34()))
        testDispatcher.scheduler.advanceUntilIdle()

        val domingo = LocalDate.of(2026, 8, 9)
        vm.selectDay(domingo)
        testDispatcher.scheduler.advanceUntilIdle()

        vm.setPeriod(ReportPeriod.SEMANA)
        testDispatcher.scheduler.advanceUntilIdle()
        vm.setPeriod(ReportPeriod.DIA)
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(domingo, vm.state.value.selectedDay)
    }

    // ─── helpers ────────────────────────────────────────────────────────────────────────

    private fun viewModel(
        payments: PaymentsPort,
        clock: AppClock = FakeClock(MockupFixtures.AHORA_RUTA_34)
    ) = CollectionReportViewModel(
        payments,
        visitsPort,
        userCyclePort,
        historicalTotalsPort,
        salesPort,
        FakePrinterPort(),
        FakePreferredPrinterStore(),
        clock,
        telemetry,
        reportThemePort,
        testDispatcher
    )

    /** Un pago por día CON cobro, por el total exacto de ese día en la ruta 34. */
    private fun pagosRuta34(): List<CollectionPayment> = MockupFixtures.TOTALES_RUTA_34
        .filterValues { it > Money.ZERO }
        .map { (day, total) ->
            CollectionPayment(
                id = "p-$day",
                cliente = "Rosa Martínez Cruz",
                ventaLabel = "70001",
                amount = total,
                method = PaymentMethod.EFECTIVO,
                paidAt = AppTime.startOfDay(day).plusSeconds(SECONDS_MIDMORNING),
                synced = true
            )
        }

    private fun money(value: String) = Money.of(BigDecimal(value))

    /**
     * Puerto de pagos que FILTRA por el rango medio-abierto que se le pide, como el adapter Room
     * real. El fake general (`FakePaymentsPort`) devuelve siempre la misma lista sin mirar el
     * rango: con él, "elegir otro día cambia el total" pasaría aunque el rango nunca cambiara.
     */
    private class RangeAwarePaymentsPort(
        private val all: List<CollectionPayment>
    ) : PaymentsPort {

        val paymentsInCalls: MutableList<DateRange> = mutableListOf()
        val groupedSinceCalls: MutableList<String> = mutableListOf()

        override suspend fun paymentsIn(range: DateRange): List<CollectionPayment> {
            paymentsInCalls += range
            val start = AppTime.parseWireFormat(range.startIso)
            val end = AppTime.parseWireFormat(range.endExclusiveIso)
            return all.filter { !it.paidAt.isBefore(start) && it.paidAt.isBefore(end) }
        }

        override suspend fun forgivenessIn(range: DateRange): List<Forgiveness> = emptyList()

        override suspend fun paymentsGroupedByDaySince(
            startIso: String
        ): Map<String, List<CollectionPayment>> {
            groupedSinceCalls += startIso
            val start: Instant = AppTime.parseWireFormat(startIso)
            return all.filter { !it.paidAt.isBefore(start) }
                .groupBy { AppTime.toWireDate(AppTime.toBusinessDate(it.paidAt)) }
        }

        override suspend fun pendingCount(): Int = 0
    }

    /** Puerto de pagos que siempre truena — degrada la pantalla a estado de error. */
    private class ThrowingPaymentsPort : PaymentsPort {
        override suspend fun paymentsIn(range: DateRange): List<CollectionPayment> =
            error("boom: room io")

        override suspend fun forgivenessIn(range: DateRange): List<Forgiveness> = emptyList()

        override suspend fun paymentsGroupedByDaySince(
            startIso: String
        ): Map<String, List<CollectionPayment>> = emptyMap()

        override suspend fun pendingCount(): Int = 0
    }

    private companion object {
        /** ~10:00 de la mañana desde la medianoche de negocio. */
        const val SECONDS_MIDMORNING = 36_000L
    }
}
