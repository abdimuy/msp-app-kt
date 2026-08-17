package com.example.msp_app.feature.collectionreport.data.fake

import com.example.msp_app.core.printing.domain.PreferredPrinterStore
import com.example.msp_app.core.printing.domain.PrintableTicket
import com.example.msp_app.core.printing.domain.PrinterDevice
import com.example.msp_app.core.printing.domain.PrinterPort
import com.example.msp_app.core.printing.domain.PrinterProfile
import com.example.msp_app.feature.collectionreport.domain.model.CollectionPayment
import com.example.msp_app.feature.collectionreport.domain.model.CollectionVisit
import com.example.msp_app.feature.collectionreport.domain.model.DateRange
import com.example.msp_app.feature.collectionreport.domain.model.Forgiveness
import com.example.msp_app.feature.collectionreport.domain.model.Money
import com.example.msp_app.feature.collectionreport.domain.model.SaleForCobranza
import com.example.msp_app.feature.collectionreport.domain.port.CycleStart
import com.example.msp_app.feature.collectionreport.domain.port.HistoricalTotalsPort
import com.example.msp_app.feature.collectionreport.domain.port.PaymentsPort
import com.example.msp_app.feature.collectionreport.domain.port.ReportThemePort
import com.example.msp_app.feature.collectionreport.domain.port.SalesPort
import com.example.msp_app.feature.collectionreport.domain.port.UserCyclePort
import com.example.msp_app.feature.collectionreport.domain.port.VisitsPort
import java.time.Instant
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Fakes de los puertos del reporte (estado + spy), fakes-only — sin MockK.
 * Consumidos por los tests de app/UI (Task 5+). Cada fake devuelve el estado
 * que se le configure y registra los argumentos con que se le llamó, para
 * poder aseverar tanto el resultado como la interacción.
 */

/**
 * Fake de [PaymentsPort]. Devuelve las colecciones configuradas y registra los
 * rangos/argumentos recibidos.
 */
class FakePaymentsPort : PaymentsPort {

    var payments: List<CollectionPayment> = emptyList()
    var forgiveness: List<Forgiveness> = emptyList()
    var groupedByDay: Map<String, List<CollectionPayment>> = emptyMap()
    var pending: Int = 0

    val paymentsInCalls: MutableList<DateRange> = mutableListOf()
    val forgivenessInCalls: MutableList<DateRange> = mutableListOf()
    val groupedByDaySinceCalls: MutableList<String> = mutableListOf()
    var pendingCountCalls: Int = 0
        private set

    override suspend fun paymentsIn(range: DateRange): List<CollectionPayment> {
        paymentsInCalls += range
        return payments
    }

    override suspend fun forgivenessIn(range: DateRange): List<Forgiveness> {
        forgivenessInCalls += range
        return forgiveness
    }

    override suspend fun paymentsGroupedByDaySince(
        startIso: String
    ): Map<String, List<CollectionPayment>> {
        groupedByDaySinceCalls += startIso
        return groupedByDay
    }

    override suspend fun pendingCount(): Int {
        pendingCountCalls++
        return pending
    }
}

/** Fake de [VisitsPort]. */
class FakeVisitsPort : VisitsPort {

    var visits: List<CollectionVisit> = emptyList()
    val visitsInCalls: MutableList<DateRange> = mutableListOf()

    override suspend fun visitsIn(range: DateRange): List<CollectionVisit> {
        visitsInCalls += range
        return visits
    }
}

/**
 * Fake de [UserCyclePort].
 *
 * [fechaCarga] es el atajo del caso feliz (equivale a `CycleStart.Known`); `null` equivale a
 * [CycleStart.Missing]. Para los casos que el `Instant?` no sabía expresar hay dos palancas:
 *  - [scriptedStarts]: una cola de respuestas consumida en orden (la última se repite) — así un
 *    test puede simular "Firestore falla y DESPUÉS responde" y comprobar la auto-reparación;
 *  - [nextStart]: la respuesta fija cuando no hay guion.
 */
class FakeUserCyclePort(
    var fechaCarga: Instant? = null,
    var nombre: String = "Cobrador Prueba"
) : UserCyclePort {

    /** Respuestas en orden; la última se repite indefinidamente. Vacía = usa [nextStart]. */
    val scriptedStarts: MutableList<CycleStart> = mutableListOf()

    /** Respuesta cuando no hay guion; por defecto se deriva de [fechaCarga]. */
    var nextStart: CycleStart? = null

    var cycleStartCalls: Int = 0
        private set
    var cobradorNombreCalls: Int = 0
        private set

    override suspend fun cycleStart(): CycleStart {
        cycleStartCalls++
        if (scriptedStarts.isNotEmpty()) {
            return if (scriptedStarts.size == 1) {
                scriptedStarts.first()
            } else {
                scriptedStarts.removeAt(0)
            }
        }
        return nextStart ?: fechaCarga?.let { CycleStart.Known(it) } ?: CycleStart.Missing
    }

    override suspend fun cobradorNombre(): String {
        cobradorNombreCalls++
        return nombre
    }
}

/** Fake de [HistoricalTotalsPort]. */
class FakeHistoricalTotalsPort : HistoricalTotalsPort {

    var totals: List<Money> = emptyList()
    val requestedDays: MutableList<Int> = mutableListOf()

    override suspend fun dailyTotals(days: Int): List<Money> {
        requestedDays += days
        return totals
    }
}

/** Fake de [SalesPort]. */
class FakeSalesPort : SalesPort {

    var sales: List<SaleForCobranza> = emptyList()
    var nonContadoActiveSalesCalls: Int = 0
        private set

    /** Último rango recibido — el ViewModel debe pasar el MISMO que usa para los pagos. */
    var lastRange: DateRange? = null
        private set

    override suspend fun nonContadoActiveSales(range: DateRange): List<SaleForCobranza> {
        nonContadoActiveSalesCalls++
        lastRange = range
        return sales
    }
}

/**
 * Fake de [PrinterPort] (`:core:printing`) — estado configurable + spy. Registra el ticket y
 * la impresora con que se llamó a [print], y devuelve el [Result] que se le configure para
 * cada método (para ejercer tanto éxito como fallos tipados).
 */
class FakePrinterPort : PrinterPort {

    var pairedResult: Result<List<PrinterDevice>> = Result.success(emptyList())
    var printResult: Result<Unit> = Result.success(Unit)
    var testConnectionResult: Result<Unit> = Result.success(Unit)

    var lastPrintedTicket: PrintableTicket? = null
        private set
    var lastPrintedDevice: PrinterDevice? = null
        private set
    var lastPrintedProfile: PrinterProfile? = null
        private set
    var printCalls: Int = 0
        private set
    var listPairedCalls: Int = 0
        private set

    override suspend fun listPairedPrinters(): Result<List<PrinterDevice>> {
        listPairedCalls++
        return pairedResult
    }

    override suspend fun testConnection(device: PrinterDevice): Result<Unit> = testConnectionResult

    override suspend fun print(
        device: PrinterDevice,
        ticket: PrintableTicket,
        profile: PrinterProfile
    ): Result<Unit> {
        printCalls++
        lastPrintedDevice = device
        lastPrintedTicket = ticket
        lastPrintedProfile = profile
        return printResult
    }
}

/**
 * Fake de [ReportThemePort] — estado configurable + spy, respaldado por un [MutableStateFlow]
 * real (no un `List` grabado) para que un test pueda simular "el tema cambia mientras la
 * pantalla está abierta" (p. ej. desde Configuración) empujando un nuevo valor y observando que
 * el `StateFlow` del ViewModel lo refleje sin llamar [toggle].
 */
class FakeReportThemePort(initialDark: Boolean = false) : ReportThemePort {

    private val darkState = MutableStateFlow(initialDark)

    override val isDark = darkState

    val toggleCalls: MutableList<Boolean> = mutableListOf()

    override fun currentIsDark(): Boolean = darkState.value

    override fun toggle() {
        darkState.value = !darkState.value
        toggleCalls += darkState.value
    }

    /** Simula un cambio de tema originado FUERA del reporte (p. ej. Configuración). */
    fun setDarkExternally(dark: Boolean) {
        darkState.value = dark
    }
}

/**
 * Fake en memoria de [PreferredPrinterStore]. Replica el self-heal real
 * ([preferredPrinter] re-valida la dirección guardada contra las emparejadas y limpia si ya
 * no existe) sin `SharedPreferences`.
 */
class FakePreferredPrinterStore(initial: String? = null) : PreferredPrinterStore {

    private var saved: String? = initial
    val savedAddresses: MutableList<String> = mutableListOf()

    override fun readPreferredAddress(): String? = saved

    override fun savePreferredAddress(address: String) {
        saved = address
        savedAddresses += address
    }

    override fun clear() {
        saved = null
    }

    override fun preferredPrinter(pairedPrinters: List<PrinterDevice>): PrinterDevice? {
        val current = saved ?: return null
        val match = pairedPrinters.firstOrNull { it.address == current }
        if (match == null) clear()
        return match
    }
}
