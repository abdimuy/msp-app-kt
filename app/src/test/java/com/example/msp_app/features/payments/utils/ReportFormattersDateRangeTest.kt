package com.example.msp_app.features.payments.utils

import com.example.msp_app.core.common.time.AppTime
import com.example.msp_app.core.utils.DateUtils
import java.time.Instant
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.util.TimeZone
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * TDD suite for [ReportFormatters.dateRangeFor] — Task 5 of the fechas/AppTime migration
 * ([MONEY] rangos de reporte de cobranza `[desde, hasta)`).
 *
 * **Consumption-path audit** (full detail in `task-5-report.md`): `startIso`/`endIso` produced
 * here feed `PaymentDao.getPaymentsByDate`/`getForgivenessByDate` and `VisitDao.getVisitsByDate`
 * (`core/database/.../dao/payment/PaymentDao.kt`, `dao/visit/VisitDao.kt`) — all local Room
 * queries comparing `FECHA_HORA_PAGO`/`FECHA BETWEEN :start AND :end` on TEXT columns that store
 * `AppTime` wire-format strings (`yyyy-MM-ddTHH:mm:ss[.SSS]Z`). None of these three call chains
 * ever reach `msp-api` directly (no Retrofit call in `getPaymentsByDate`/`getForgivenessByDate`/
 * `getVisitsByDate`) — this is a 100% local, Room-only range. This suite pins the intended
 * Kotlin-level half-open contract `[startOfDay(date), startOfNextDay(date))`.
 *
 * KNOWN CAVEAT (documented, not fixed here — out of Task 5's file scope): SQLite `BETWEEN` is
 * inclusive on BOTH ends. Passing `[startOfDay, startOfNextDay)` as `(:start, :end)` to a
 * `BETWEEN :start AND :end` query makes the SQL layer still treat `:end` as inclusive, so a
 * payment landing at the *exact* string value of `startOfNextDay` (to the same sub-second
 * precision) would still match. In practice this requires an exact lexicographic tie on a
 * TEXT column with variable fractional-second width (captured payments carry milliseconds via
 * `Instant.now().toString()`; the boundary carries none) — vanishingly unlikely, and strictly
 * narrower than the bug this task fixes (which shifted results by up to a device's full UTC
 * offset). Fixing the SQL layer itself (`BETWEEN` -> `>= AND <`, plus standardizing
 * `FECHA_HORA_PAGO` wire-format precision) is cataloged as follow-up work, not in this task's
 * file scope (`PaymentDao.kt`/`VisitDao.kt` are not listed in the Task 5 brief).
 */
class ReportFormattersDateRangeTest {

    private val originalDefaultTimeZone: TimeZone = TimeZone.getDefault()

    @After
    fun restoreTimeZone() {
        TimeZone.setDefault(originalDefaultTimeZone)
    }

    private fun isWithinRange(instant: Instant, range: ReportDateRange): Boolean {
        val start = AppTime.parseWireFormat(range.startIso)
        val end = AppTime.parseWireFormat(range.endIso)
        return !instant.isBefore(start) && instant.isBefore(end)
    }

    // region — Basic contract: wraps AppTime.startOfDay/startOfNextDay, business zone

    @Test
    fun `dateRangeFor produce startOfDay y startOfNextDay en zona de negocio`() {
        val date = LocalDate.of(2026, 4, 15)

        val range = ReportFormatters.dateRangeFor(date)

        assertEquals(AppTime.toWireFormat(AppTime.startOfDay(date)), range.startIso)
        assertEquals(AppTime.toWireFormat(AppTime.startOfNextDay(date)), range.endIso)
        // 15-abr-2026 medianoche CDMX = 06:00 UTC (Mexico sin DST nacional desde 2022).
        assertEquals("2026-04-15T06:00:00Z", range.startIso)
        assertEquals("2026-04-16T06:00:00Z", range.endIso)
    }

    // endregion

    // region — Half-open boundary (brief punto 1)

    @Test
    fun `pago a las 23-59-59-999 CDMX del dia D esta dentro del rango de D`() {
        val d = LocalDate.of(2026, 4, 15)
        val range = ReportFormatters.dateRangeFor(d)
        // 23:59:59.999 CDMX 15-abr == 05:59:59.999 UTC 16-abr.
        val lastInstantOfDay = AppTime.parseWireFormat("2026-04-16T05:59:59.999Z")

        assertTrue(isWithinRange(lastInstantOfDay, range))
    }

    @Test
    fun `pago a las 00-00-00-000 CDMX de D+1 NO entra en D y SI entra en D+1`() {
        val d = LocalDate.of(2026, 4, 15)
        val dPlus1 = d.plusDays(1)
        val rangeD = ReportFormatters.dateRangeFor(d)
        val rangeDPlus1 = ReportFormatters.dateRangeFor(dPlus1)
        // 00:00:00.000 CDMX 16-abr == 06:00:00.000 UTC 16-abr.
        val exactMidnightOfNextDay = AppTime.parseWireFormat("2026-04-16T06:00:00.000Z")

        assertFalse(
            "medianoche exacta de D+1 NO debe pertenecer al reporte de D (medio-abierto)",
            isWithinRange(exactMidnightOfNextDay, rangeD)
        )
        assertTrue(
            "medianoche exacta de D+1 SI debe pertenecer al reporte de D+1",
            isWithinRange(exactMidnightOfNextDay, rangeDPlus1)
        )
    }

    // endregion

    // region — Device-zone independence (brief punto 2)

    @Test
    fun `dateRangeFor no depende de la zona horaria del dispositivo`() {
        val date = LocalDate.of(2026, 4, 15)

        TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
        val rangeUtc = ReportFormatters.dateRangeFor(date)

        TimeZone.setDefault(TimeZone.getTimeZone("America/Tijuana"))
        val rangeTijuana = ReportFormatters.dateRangeFor(date)

        assertEquals(rangeUtc, rangeTijuana)
        assertEquals("2026-04-15T06:00:00Z", rangeUtc.startIso)
        assertEquals("2026-04-16T06:00:00Z", rangeUtc.endIso)
    }

    // endregion

    // region — Weekly half-open contract (brief punto 3): la misma pareja
    // startOfDay/startOfNextDay sostiene un rango semanal lunes-a-lunes.

    @Test
    fun `rango semanal lunes-a-lunes es medio-abierto en zona de negocio`() {
        val monday = LocalDate.of(2026, 4, 13) // lunes
        val nextMonday = monday.plusWeeks(1)

        val weekStart = AppTime.startOfDay(monday)
        val weekEndExclusive = AppTime.startOfDay(nextMonday)

        val mondayMidnight = AppTime.parseWireFormat("2026-04-13T06:00:00.000Z")
        val sundayLastInstant = AppTime.parseWireFormat("2026-04-20T05:59:59.999Z")
        val nextMondayMidnight = AppTime.parseWireFormat("2026-04-20T06:00:00.000Z")

        assertTrue(
            !mondayMidnight.isBefore(weekStart) && mondayMidnight.isBefore(weekEndExclusive)
        )
        assertTrue(
            !sundayLastInstant.isBefore(weekStart) && sundayLastInstant.isBefore(weekEndExclusive)
        )
        assertFalse(
            "el lunes siguiente a medianoche NO pertenece a la semana anterior (medio-abierto)",
            nextMondayMidnight.isBefore(weekEndExclusive)
        )
    }

    // endregion

    // region — Old vs new characterization (brief punto 5 / bug #3 + #1)

    @Test
    fun `caracterizacion old-vs-new - el patron viejo +1d-1s en zona dispositivo difiere del medio-abierto en zona negocio`() {
        val date = LocalDate.of(2026, 4, 15)

        // Comportamiento VIEJO (DailyReportScreen.prepareReportDate / RouteMapScreen antes del
        // fix): DateUtils.parseLocalDateToIso ancla en ZoneId.systemDefault() (zona del
        // DISPOSITIVO), y el fin de rango se calcula con addToIsoDate(+1 dia) seguido de
        // addToIsoDate(-1 segundo) — inclusive, y sujeto al bug #3 (round-trip por
        // LocalDateTime que descarta el offset original).
        TimeZone.setDefault(TimeZone.getTimeZone("America/Tijuana")) // siempre detras de CDMX
        val oldStartIso = DateUtils.parseLocalDateToIso(date)
        val oldEndIso = DateUtils.addToIsoDate(
            DateUtils.addToIsoDate(oldStartIso, 1, ChronoUnit.DAYS),
            -1,
            ChronoUnit.SECONDS
        )

        // Comportamiento NUEVO: medio-abierto, zona de NEGOCIO, independiente del dispositivo.
        val newRange = ReportFormatters.dateRangeFor(date)

        assertNotEquals(
            "el fin de rango viejo (zona dispositivo, inclusivo) debe diferir del nuevo " +
                "(zona negocio, exclusivo) para exponer el cambio de comportamiento consciente",
            oldEndIso,
            newRange.endIso
        )

        // Tijuana (UTC-7/-8) siempre va detras de CDMX (UTC-6 fijo): el fin de rango viejo
        // se extiende mas alla de la medianoche de NEGOCIO del dia siguiente, incluyendo por
        // error un pago que en zona de negocio ya pertenece a D+1 (bug #1 de zona).
        val exactBusinessMidnightOfNextDay = AppTime.parseWireFormat("2026-04-16T06:00:00.000Z")
        val oldEndInstant = AppTime.parseWireFormat(oldEndIso)

        assertTrue(
            "bajo el comportamiento VIEJO (zona dispositivo Tijuana), la medianoche de " +
                "NEGOCIO del dia siguiente caia dentro del rango viejo (bug de zona)",
            !exactBusinessMidnightOfNextDay.isAfter(oldEndInstant)
        )
        assertFalse(
            "bajo el comportamiento NUEVO medio-abierto, esa misma medianoche de negocio " +
                "queda correctamente fuera del rango de D",
            isWithinRange(exactBusinessMidnightOfNextDay, newRange)
        )
    }

    // endregion
}
