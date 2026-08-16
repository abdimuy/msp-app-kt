package com.example.msp_app.feature.collectionreport.domain

import com.example.msp_app.core.testing.time.FakeClock
import com.example.msp_app.feature.collectionreport.domain.model.CollectionPayment
import com.example.msp_app.feature.collectionreport.domain.model.DateRange
import com.example.msp_app.feature.collectionreport.domain.model.Forgiveness
import com.example.msp_app.feature.collectionreport.domain.model.Money
import com.example.msp_app.feature.collectionreport.domain.model.PaymentMethod
import com.example.msp_app.feature.collectionreport.domain.model.ReportPeriod
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Robustez SUPREMA del agregador PURO del reporte: totales/splits/ticket con
 * [Money] EXACTO (sin drift de centavos), timeline por hora/día, delta con signo
 * y %, progreso con clamp, insight es-MX literal, mejor momento y — la invariante
 * dura — **conteo único**: cada pago cuenta una vez, los splits particionan.
 * JVM puro con [FakeClock]; zona negocio CDMX (UTC-6, sin DST desde 2022).
 */
class ReportAggregatorTest {

    // Mediodía del vie 7-ago-2026 en CDMX (== 18:00Z). "hoy" = 2026-08-07.
    private val clock = FakeClock(Instant.parse("2026-08-07T18:00:00Z"))

    // Ciclo lun 3 – vie 7 ago (5 días).
    private val cycle: DateRange = requireNotNull(
        RangeCalculator.cycleRange(clock, Instant.parse("2026-08-03T16:00:00Z"))
    )

    private fun money(v: String) = Money.of(BigDecimal(v))

    private fun payment(
        amount: Money,
        method: PaymentMethod = PaymentMethod.EFECTIVO,
        paidAt: Instant = Instant.parse("2026-08-07T15:00:00Z"),
        id: String = "p",
        cliente: String = "María López",
        synced: Boolean = true
    ) = CollectionPayment(id, cliente, "Muebles Bahía", amount, method, paidAt, synced)

    // region — totales / conteo / vacío ---------------------------------------

    @Test
    fun `total de lista vacia es cero con dos decimales`() {
        assertEquals(Money.ZERO, ReportAggregator.total(emptyList()))
        assertEquals(0, ReportAggregator.count(emptyList()))
    }

    @Test
    fun `total de un solo pago es su importe`() {
        assertEquals(money("1200.50"), ReportAggregator.total(listOf(payment(money("1200.50")))))
        assertEquals(1, ReportAggregator.count(listOf(payment(money("1200.50")))))
    }

    @Test
    fun `total suma muchos importes sin drift de centavos`() {
        // 100 pagos de 0.01 == 1.00 EXACTO (con Double: 0.01*100 != 1.0).
        val cents = (1..100).map { payment(money("0.01"), id = "c$it") }
        assertEquals(money("1.00"), ReportAggregator.total(cents))
    }

    @Test
    fun `total suma importes con centavos arbitrarios exacto`() {
        val ps = listOf(
            payment(money("1200.50")),
            payment(money("850.25")),
            payment(money("1500.75")),
            payment(money("2000.99"))
        )
        assertEquals(money("5552.49"), ReportAggregator.total(ps))
    }

    // region — splits por método (partición disjunta, sin double-count) --------

    @Test
    fun `split por metodo particiona el total sin doble conteo`() {
        val ps = listOf(
            payment(money("100.00"), PaymentMethod.EFECTIVO),
            payment(money("200.00"), PaymentMethod.EFECTIVO),
            payment(money("300.00"), PaymentMethod.TRANSFERENCIA),
            payment(money("50.00"), PaymentMethod.CHEQUE)
        )
        val ef = ReportAggregator.efectivo(ps)
        val tr = ReportAggregator.transferencia(ps)
        val ch = ReportAggregator.cheque(ps)
        val ot = ReportAggregator.otros(ps)

        assertEquals(money("300.00"), ef.total)
        assertEquals(2, ef.count)
        assertEquals(money("300.00"), tr.total)
        assertEquals(1, tr.count)
        assertEquals(money("50.00"), ch.total)
        assertEquals(1, ch.count)
        assertEquals(MethodBreakdown(Money.ZERO, 0), ot) // sin remanente aquí

        // Partición EXHAUSTIVA: las cuatro categorías suman EXACTAMENTE el total,
        // y los conteos también: cada pago cuenta una sola vez.
        assertEquals(ReportAggregator.total(ps), ef.total + tr.total + ch.total + ot.total)
        assertEquals(ReportAggregator.count(ps), ef.count + tr.count + ch.count + ot.count)
    }

    @Test
    fun `la particion es exhaustiva incluso con un pago de metodo OTRO`() {
        val ps = listOf(
            payment(money("100.00"), PaymentMethod.EFECTIVO),
            payment(money("300.00"), PaymentMethod.TRANSFERENCIA),
            payment(money("50.00"), PaymentMethod.CHEQUE),
            // FORMA_COBRO_ID desconocido
            payment(money("77.00"), PaymentMethod.OTRO)
        )
        val ef = ReportAggregator.efectivo(ps)
        val tr = ReportAggregator.transferencia(ps)
        val ch = ReportAggregator.cheque(ps)
        val ot = ReportAggregator.otros(ps)

        // El pago OTRO NO se vuelve invisible: aparece como remanente...
        assertEquals(money("77.00"), ot.total)
        assertEquals(1, ot.count)
        // ...y sigue contando en el total.
        assertEquals(money("527.00"), ReportAggregator.total(ps))
        // Exhaustividad dinero + conteo con OTRO presente.
        assertEquals(ReportAggregator.total(ps), ef.total + tr.total + ch.total + ot.total)
        assertEquals(ReportAggregator.count(ps), ef.count + tr.count + ch.count + ot.count)
    }

    @Test
    fun `cheque NO entra en efectivo pero SI en el total`() {
        val ps = listOf(
            payment(money("100.00"), PaymentMethod.EFECTIVO),
            payment(money("50.00"), PaymentMethod.CHEQUE)
        )
        assertEquals(money("100.00"), ReportAggregator.efectivo(ps).total)
        assertEquals(money("100.00"), ReportAggregator.efectivoEnMano(ps))
        assertEquals(money("150.00"), ReportAggregator.total(ps)) // cheque SÍ suma al total
    }

    @Test
    fun `guarda anti-fuga - un pago con metodo condonacion NUNCA suma al total ni al conteo`() {
        val ps = listOf(
            payment(money("100.00"), PaymentMethod.EFECTIVO),
            // 137026 colado como pago: condonar no es cobrar.
            payment(money("999.00"), PaymentMethod.CONDONACION)
        )
        assertEquals(money("100.00"), ReportAggregator.total(ps)) // NO incluye los 999
        assertEquals(1, ReportAggregator.count(ps)) // solo el efectivo cuenta
        assertEquals(MethodBreakdown(Money.ZERO, 0), ReportAggregator.otros(ps)) // ni en remanente
        // Y la exhaustividad se mantiene sobre lo cobrado.
        val ef = ReportAggregator.efectivo(ps)
        val tr = ReportAggregator.transferencia(ps)
        val ch = ReportAggregator.cheque(ps)
        val ot = ReportAggregator.otros(ps)
        assertEquals(ReportAggregator.total(ps), ef.total + tr.total + ch.total + ot.total)
        assertEquals(ReportAggregator.count(ps), ef.count + tr.count + ch.count + ot.count)
    }

    @Test
    fun `condonado suma la lista de condonaciones y no toca el total cobrado`() {
        val forg = listOf(
            Forgiveness("Ana Ruiz", "saldo mínimo", money("600.00")),
            Forgiveness("Luis Gómez", "ajuste de intereses", money("500.00"))
        )
        val co = ReportAggregator.condonado(forg)
        assertEquals(money("1100.00"), co.total)
        assertEquals(2, co.count)
        assertEquals(MethodBreakdown(Money.ZERO, 0), ReportAggregator.condonado(emptyList()))
    }

    // region — ticket promedio -------------------------------------------------

    @Test
    fun `ticket promedio de lista vacia es cero sin dividir`() {
        assertEquals(Money.ZERO, ReportAggregator.ticketPromedio(emptyList()))
    }

    @Test
    fun `ticket promedio divide total entre conteo con HALF_UP`() {
        // 10.00 / 3 = 3.333... -> 3.33
        val ps = (1..3).map { payment(money("3.333333"), id = "t$it") }
        // total = 3.33 * 3 = 9.99 -> /3 = 3.33
        assertEquals(money("3.33"), ReportAggregator.ticketPromedio(ps))
    }

    @Test
    fun `ticket promedio redondea el medio centavo hacia arriba`() {
        // total 5.01 / 2 = 2.505 -> 2.51 (HALF_UP)
        val ps = listOf(payment(money("2.50")), payment(money("2.51")))
        assertEquals(money("2.51"), ReportAggregator.ticketPromedio(ps))
    }

    // region — timeline DÍA (por hora) ----------------------------------------

    @Test
    fun `timeline dia crea nueve barras 8h a 16h y agrupa por hora de negocio`() {
        val ps = listOf(
            // 8h
            payment(money("100.00"), paidAt = Instant.parse("2026-08-07T14:00:00Z")),
            // 9h
            payment(money("2500.00"), paidAt = Instant.parse("2026-08-07T15:00:00Z")),
            // 9h
            payment(money("1700.00"), paidAt = Instant.parse("2026-08-07T15:30:00Z")),
            // 16h
            payment(money("300.00"), paidAt = Instant.parse("2026-08-07T22:00:00Z"))
        )
        val tl = ReportAggregator.timeline(ps, ReportPeriod.DIA, cycle)

        assertEquals(9, tl.buckets.size)
        assertEquals(
            listOf("8h", "9h", "10h", "11h", "12h", "13h", "14h", "15h", "16h"),
            tl.buckets.map { it.label }
        )
        assertEquals(money("100.00"), tl.buckets[0].total)
        assertEquals(money("4200.00"), tl.buckets[1].total) // 9h = 2500 + 1700
        assertEquals(2, tl.buckets[1].count)
        assertEquals(money("300.00"), tl.buckets[8].total) // 16h
        assertEquals(1, tl.highlightIndex) // pico = 9h
    }

    @Test
    fun `timeline dia acota pagos fuera de la franja al borde mas cercano`() {
        val ps = listOf(
            // 6h -> acotado a 8h
            payment(money("50.00"), paidAt = Instant.parse("2026-08-07T12:00:00Z")),
            // 20h -> acotado a 16h
            payment(money("70.00"), paidAt = Instant.parse("2026-08-08T02:00:00Z"))
        )
        val tl = ReportAggregator.timeline(ps, ReportPeriod.DIA, cycle)
        assertEquals(money("50.00"), tl.buckets.first().total) // 8h
        assertEquals(money("70.00"), tl.buckets.last().total) // 16h
    }

    @Test
    fun `timeline dia vacio son nueve barras en cero sin crash y pico en cero`() {
        val tl = ReportAggregator.timeline(emptyList(), ReportPeriod.DIA, cycle)
        assertEquals(9, tl.buckets.size)
        assertTrue(tl.buckets.all { it.total == Money.ZERO && it.count == 0 })
        assertEquals(0, tl.highlightIndex)
    }

    // region — timeline SEMANA (por día del ciclo) ----------------------------

    @Test
    fun `timeline semana crea una barra por dia del ciclo y resalta hoy`() {
        val ps = listOf(
            // lun
            payment(money("1000.00"), paidAt = Instant.parse("2026-08-03T15:00:00Z")),
            // mié
            payment(money("3000.00"), paidAt = Instant.parse("2026-08-05T15:00:00Z")),
            // vie
            payment(money("500.00"), paidAt = Instant.parse("2026-08-07T15:00:00Z"))
        )
        val tl = ReportAggregator.timeline(ps, ReportPeriod.SEMANA, cycle)

        assertEquals(listOf("lun", "mar", "mié", "jue", "vie"), tl.buckets.map { it.label })
        assertEquals(money("1000.00"), tl.buckets[0].total)
        assertEquals(Money.ZERO, tl.buckets[1].total) // mar sin pagos, pero la barra existe
        assertEquals(money("3000.00"), tl.buckets[2].total)
        assertEquals(money("500.00"), tl.buckets[4].total)
        assertEquals(4, tl.highlightIndex) // "hoy" = vie (último día del ciclo)
    }

    @Test
    fun `timeline semana vacia son barras del ciclo en cero sin crash`() {
        val tl = ReportAggregator.timeline(emptyList(), ReportPeriod.SEMANA, cycle)
        assertEquals(5, tl.buckets.size) // una barra por día del ciclo, aun sin pagos
        assertEquals(listOf("lun", "mar", "mié", "jue", "vie"), tl.buckets.map { it.label })
        assertTrue(tl.buckets.all { it.total == Money.ZERO && it.count == 0 })
        assertEquals(4, tl.highlightIndex) // "hoy" sigue siendo el último día
    }

    // region — mejor momento ---------------------------------------------------

    @Test
    fun `mejor momento dia reetiqueta el pico a rango horario`() {
        val ps = listOf(
            payment(money("2500.00"), paidAt = Instant.parse("2026-08-07T15:00:00Z")),
            payment(money("1700.00"), paidAt = Instant.parse("2026-08-07T15:30:00Z"))
        )
        val tl = ReportAggregator.timeline(ps, ReportPeriod.DIA, cycle)
        val best = ReportAggregator.mejorMomento(tl, ReportPeriod.DIA)
        assertEquals("9–10 h", best?.label)
        assertEquals(money("4200.00"), best?.total)
    }

    @Test
    fun `mejor momento semana reetiqueta el pico a dia completo`() {
        val ps = listOf(
            payment(money("1000.00"), paidAt = Instant.parse("2026-08-03T15:00:00Z")),
            payment(money("3000.00"), paidAt = Instant.parse("2026-08-05T15:00:00Z"))
        )
        val tl = ReportAggregator.timeline(ps, ReportPeriod.SEMANA, cycle)
        val best = ReportAggregator.mejorMomento(tl, ReportPeriod.SEMANA)
        assertEquals("miércoles", best?.label)
        assertEquals(money("3000.00"), best?.total)
    }

    @Test
    fun `mejor momento sin dinero es null`() {
        val tl = ReportAggregator.timeline(emptyList(), ReportPeriod.DIA, cycle)
        assertNull(ReportAggregator.mejorMomento(tl, ReportPeriod.DIA))
    }

    @Test
    fun `mejor momento semana sin pagos en el ciclo es null`() {
        val tl = ReportAggregator.timeline(emptyList(), ReportPeriod.SEMANA, cycle)
        assertNull(ReportAggregator.mejorMomento(tl, ReportPeriod.SEMANA))
    }

    // region — resumen por día (dailyTrend) -----------------------------------

    @Test
    fun `dailyTrend produce una fila por dia con nombre iniciales y marca de hoy`() {
        val ps = listOf(
            payment(money("1000.00"), paidAt = Instant.parse("2026-08-03T15:00:00Z")),
            payment(money("3000.00"), paidAt = Instant.parse("2026-08-05T15:00:00Z")),
            payment(money("500.00"), paidAt = Instant.parse("2026-08-07T15:00:00Z"))
        )
        val trend = ReportAggregator.dailyTrend(ps, cycle, clock)

        assertEquals(5, trend.size)
        assertEquals("lun 3 ago", trend[0].label)
        assertEquals("L3", trend[0].initials)
        assertEquals(money("1000.00"), trend[0].total)
        assertEquals(1, trend[0].count)
        assertTrue(!trend[0].isToday)

        assertEquals("mié 5 ago", trend[2].label)
        assertEquals("M5", trend[2].initials)
        assertEquals(money("3000.00"), trend[2].total)

        // vie 7 = hoy
        assertEquals("vie 7 ago (hoy)", trend[4].label)
        assertEquals("V7", trend[4].initials)
        assertTrue(trend[4].isToday)
    }

    @Test
    fun `dailyTrend fecha cada fila con el dia de negocio que resume, no con la etiqueta`() {
        // La fecha es lo que identifica al renglón río abajo (llave de la lista perezosa del
        // detalle de Semana, ver `dayDetailItems`): tiene que ser el día exacto del ciclo, en
        // orden ascendente y sin huecos, no algo derivado de la etiqueta "EEE d MMM" — que se
        // repite en cuanto el ciclo abarca más de un año.
        val trend = ReportAggregator.dailyTrend(emptyList(), cycle, clock)

        assertEquals(
            listOf(
                LocalDate.parse("2026-08-03"),
                LocalDate.parse("2026-08-04"),
                LocalDate.parse("2026-08-05"),
                LocalDate.parse("2026-08-06"),
                LocalDate.parse("2026-08-07")
            ),
            trend.map { it.date }
        )
    }

    @Test
    fun `dailyTrend sin pagos produce una fila en cero por dia del ciclo sin crash`() {
        val trend = ReportAggregator.dailyTrend(emptyList(), cycle, clock)
        assertEquals(5, trend.size)
        assertTrue(trend.all { it.total == Money.ZERO && it.count == 0 })
        assertEquals("lun 3 ago", trend[0].label)
        assertEquals("vie 7 ago (hoy)", trend[4].label) // "hoy" se marca aun sin pagos
        assertTrue(trend[4].isToday)
    }

    // region — pagos por día (paymentsByDay) ----------------------------------

    @Test
    fun `paymentsByDay reparte los pagos por dia alineado 1 a 1 con dailyTrend`() {
        val ps = listOf(
            payment(money("1000.00"), paidAt = Instant.parse("2026-08-03T15:00:00Z"), id = "a"),
            payment(money("3000.00"), paidAt = Instant.parse("2026-08-05T15:00:00Z"), id = "b"),
            payment(money("500.00"), paidAt = Instant.parse("2026-08-05T18:00:00Z"), id = "c"),
            payment(money("700.00"), paidAt = Instant.parse("2026-08-07T15:00:00Z"), id = "d")
        )
        val byDay = ReportAggregator.paymentsByDay(ps, cycle)
        val trend = ReportAggregator.dailyTrend(ps, cycle, clock)

        // Un bucket por día del ciclo, mismo tamaño y orden que dailyTrend (índice == mismo día).
        assertEquals(5, byDay.size)
        assertEquals(trend.size, byDay.size)
        byDay.forEachIndexed { i, dayList ->
            assertEquals(
                "índice $i debe coincidir en conteo con dailyTrend",
                trend[i].count,
                dayList.size
            )
        }
        // lun 3: 1 pago; mié 5: 2 pagos; vie 7: 1 pago; días sin pagos: lista vacía.
        assertEquals(listOf("a"), byDay[0].map { it.id })
        assertTrue(byDay[1].isEmpty())
        assertEquals(2, byDay[2].size)
        assertTrue(byDay[3].isEmpty())
        assertEquals(listOf("d"), byDay[4].map { it.id })
    }

    @Test
    fun `paymentsByDay ordena los pagos de cada dia cronologicamente ascendente`() {
        val ps = listOf(
            payment(money("500.00"), paidAt = Instant.parse("2026-08-05T18:00:00Z"), id = "tarde"),
            payment(money("300.00"), paidAt = Instant.parse("2026-08-05T09:00:00Z"), id = "manana"),
            payment(money("400.00"), paidAt = Instant.parse("2026-08-05T13:00:00Z"), id = "medio")
        )
        val miercoles = ReportAggregator.paymentsByDay(ps, cycle)[2]
        assertEquals(listOf("manana", "medio", "tarde"), miercoles.map { it.id })
    }

    @Test
    fun `paymentsByDay excluye condonaciones que se cuelen`() {
        val ps = listOf(
            payment(money("1000.00"), paidAt = Instant.parse("2026-08-05T15:00:00Z"), id = "cobro"),
            payment(
                money("999.00"),
                method = PaymentMethod.CONDONACION,
                paidAt = Instant.parse("2026-08-05T16:00:00Z"),
                id = "condon"
            )
        )
        val miercoles = ReportAggregator.paymentsByDay(ps, cycle)[2]
        assertEquals(listOf("cobro"), miercoles.map { it.id })
    }

    @Test
    fun `paymentsByDay sin pagos produce una lista vacia por dia del ciclo sin crash`() {
        val byDay = ReportAggregator.paymentsByDay(emptyList(), cycle)
        assertEquals(5, byDay.size)
        assertTrue(byDay.all { it.isEmpty() })
    }

    // region — delta -----------------------------------------------------------

    @Test
    fun `delta al alza usa flecha arriba y sufijo del periodo`() {
        assertEquals(
            DeltaChip("▲ 12% vs ayer", DeltaDirection.UP),
            ReportAggregator.delta(money("112.00"), money("100.00"), ReportPeriod.DIA)
        )
        assertEquals(
            DeltaChip("▲ 6% vs semana", DeltaDirection.UP),
            ReportAggregator.delta(money("106.00"), money("100.00"), ReportPeriod.SEMANA)
        )
    }

    @Test
    fun `delta a la baja usa flecha abajo y porcentaje positivo`() {
        assertEquals(
            DeltaChip("▼ 10% vs ayer", DeltaDirection.DOWN),
            ReportAggregator.delta(money("90.00"), money("100.00"), ReportPeriod.DIA)
        )
    }

    @Test
    fun `delta sin cambio es cero por ciento y direccion FLAT`() {
        assertEquals(
            DeltaChip("0% vs ayer", DeltaDirection.FLAT),
            ReportAggregator.delta(money("100.00"), money("100.00"), ReportPeriod.DIA)
        )
    }

    @Test
    fun `delta contra prior cero degrada a guion sin dividir por cero`() {
        assertEquals(
            DeltaChip("—", DeltaDirection.NONE),
            ReportAggregator.delta(money("100.00"), Money.ZERO, ReportPeriod.DIA)
        )
    }

    @Test
    fun `delta contra prior negativo tambien degrada a guion`() {
        // prior <= 0 no tiene base de comparación válida en ningún periodo.
        assertEquals(
            DeltaChip("—", DeltaDirection.NONE),
            ReportAggregator.delta(money("100.00"), money("-5.00"), ReportPeriod.SEMANA)
        )
    }

    @Test
    fun `delta redondea el porcentaje HALF_UP`() {
        // 105.5 / 100 = 5.5% -> 6%
        assertEquals(
            DeltaChip("▲ 6% vs ayer", DeltaDirection.UP),
            ReportAggregator.delta(money("105.50"), money("100.00"), ReportPeriod.DIA)
        )
    }

    // region — progressFraction (clamp) ---------------------------------------

    @Test
    fun `progressFraction es la razon total sobre meta`() {
        assertEquals(
            0.915f,
            ReportAggregator.progressFraction(money("18300"), money("20000")),
            1e-4f
        )
    }

    @Test
    fun `progressFraction satura en uno cuando se supera la meta`() {
        assertEquals(1f, ReportAggregator.progressFraction(money("25000"), money("20000")), 0f)
    }

    @Test
    fun `progressFraction es cero con meta cero o negativa`() {
        assertEquals(0f, ReportAggregator.progressFraction(money("18300"), Money.ZERO), 0f)
        assertEquals(0f, ReportAggregator.progressFraction(money("18300"), money("-5")), 0f)
    }

    @Test
    fun `progressFraction no baja de cero con total negativo`() {
        assertEquals(0f, ReportAggregator.progressFraction(money("-100"), money("20000")), 0f)
    }

    // region — insight (datos estructurados, la UI formatea el dinero) --------

    @Test
    fun `insight dia expone conteo porcentaje y proyeccion en Money sin formatear`() {
        val progress = ReportAggregator.progressFraction(money("18300"), money("20000"))
        assertEquals(
            Insight.Daily(count = 32, progressPct = 91, projection = money("19800")),
            ReportAggregator.insight(ReportPeriod.DIA, 32, progress, money("19800"))
        )
    }

    @Test
    fun `insight dia sin proyeccion deja projection en null`() {
        assertEquals(
            Insight.Daily(count = 0, progressPct = 0, projection = null),
            ReportAggregator.insight(ReportPeriod.DIA, 0, 0f, projection = null)
        )
    }

    @Test
    fun `insight semana expone dia D de T del ciclo`() {
        val progress = ReportAggregator.progressFraction(money("118400"), money("130000"))
        assertEquals(
            Insight.Weekly(count = 214, progressPct = 91, cycleDay = 5, cycleDays = 5),
            ReportAggregator.insight(
                ReportPeriod.SEMANA,
                214,
                progress,
                projection = null,
                cycleDay = 5,
                cycleDays = 5
            )
        )
    }

    @Test
    fun `insight trunca el porcentaje al piso consistente con la barra`() {
        // 0.999 -> 99%, no 100%.
        val i = ReportAggregator.insight(ReportPeriod.DIA, 1, 0.999f, money("1"))
        assertEquals(99, (i as Insight.Daily).progressPct)
    }
}
