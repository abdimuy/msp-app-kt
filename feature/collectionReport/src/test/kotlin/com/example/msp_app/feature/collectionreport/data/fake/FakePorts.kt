package com.example.msp_app.feature.collectionreport.data.fake

import com.example.msp_app.feature.collectionreport.domain.model.CollectionPayment
import com.example.msp_app.feature.collectionreport.domain.model.CollectionVisit
import com.example.msp_app.feature.collectionreport.domain.model.DateRange
import com.example.msp_app.feature.collectionreport.domain.model.Forgiveness
import com.example.msp_app.feature.collectionreport.domain.model.Money
import com.example.msp_app.feature.collectionreport.domain.port.HistoricalTotalsPort
import com.example.msp_app.feature.collectionreport.domain.port.PaymentsPort
import com.example.msp_app.feature.collectionreport.domain.port.UserCyclePort
import com.example.msp_app.feature.collectionreport.domain.port.VisitsPort
import java.time.Instant

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

/** Fake de [UserCyclePort]. */
class FakeUserCyclePort(
    var fechaCarga: Instant? = null,
    var nombre: String = "Cobrador Prueba"
) : UserCyclePort {

    var fechaCargaInicialCalls: Int = 0
        private set
    var cobradorNombreCalls: Int = 0
        private set

    override suspend fun fechaCargaInicial(): Instant? {
        fechaCargaInicialCalls++
        return fechaCarga
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
