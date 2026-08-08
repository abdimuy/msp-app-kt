package com.example.msp_app.features.transfers.data.mappers

import com.example.msp_app.core.common.time.AppTime
import com.example.msp_app.core.common.time.BUSINESS_ZONE
import com.example.msp_app.core.testing.time.FakeClock
import com.example.msp_app.features.transfers.data.local.entities.TransferEntity
import java.time.Instant
import java.time.LocalDateTime
import java.util.TimeZone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Task 10 — `TransferEntity.toDomain()`'s `fecha` fallback (used only when every parse
 * attempt on the stored `fecha` string fails) moved off ambient `LocalDateTime.now()` to
 * an injectable [com.example.msp_app.core.common.time.AppClock], defaulted to
 * [com.example.msp_app.core.common.time.AppClock.System] so existing (currently absent —
 * see task report, this Room path is unwired dead code) call sites keep compiling.
 *
 * `createdAt`/`updatedAt` on [TransferEntity] are `Long` epoch-millis Room columns
 * (`System.currentTimeMillis()`), out of this task's scope — untouched here.
 */
class TransferMappersTest {

    private fun entity(fecha: String) = TransferEntity(
        doctoInId = 501,
        almacenOrigenId = 11,
        almacenDestinoId = 22,
        fecha = fecha,
        descripcion = "Traspaso de bodega a camioneta",
        folio = "T-501",
        usuario = "erika.paredes",
        aplicado = "S",
        almacenOrigenNombre = "Almacén Central",
        almacenDestinoNombre = "Camioneta 3",
        totalProductos = 4,
        costoTotal = 1200.0,
        sincronizado = true,
        createdAt = 1_754_000_000_000L,
        updatedAt = 1_754_000_000_000L
    )

    @Test
    fun `well-formed fecha parses on the happy path, clock never consulted`() {
        val clock = FakeClock(Instant.parse("2026-08-08T18:30:00Z"))

        val domain = entity(fecha = "2026-04-22T19:43:56").toDomain(clock)

        assertEquals(LocalDateTime.of(2026, 4, 22, 19, 43, 56), domain.fecha)
    }

    @Test
    fun `unparseable fecha falls back to the injected clock's business datetime`() {
        val fixed = Instant.parse("2026-08-08T18:30:00Z")
        val clock = FakeClock(fixed)

        val domain = entity(fecha = "no es una fecha").toDomain(clock)

        assertEquals(AppTime.toBusinessDateTime(fixed), domain.fecha)
    }

    @Test
    fun `fallback reflects clock advancement, not a value fixed at call time`() {
        val clock = FakeClock(Instant.parse("2026-08-08T18:30:00Z"))

        val before = entity(fecha = "garbage").toDomain(clock).fecha
        clock.advanceHours(5)
        val after = entity(fecha = "garbage").toDomain(clock).fecha

        assertEquals(AppTime.toBusinessDateTime(Instant.parse("2026-08-08T23:30:00Z")), after)
        assert(before != after)
    }

    @Test
    fun `default clock parameter falls back to the real business-zone wall clock`() {
        val before = Instant.now()
        val domain = entity(fecha = "garbage").toDomain()
        val after = Instant.now()

        // Real, not a placeholder: the fallback must land inside the actual call window,
        // measured in BUSINESS_ZONE (not whatever zone the JVM default happens to be).
        val stampedAsInstant = domain.fecha.atZone(BUSINESS_ZONE).toInstant()
        assertTrue(
            "default-clock fallback must fall within the real call window",
            !stampedAsInstant.isBefore(before.minusSeconds(2)) &&
                !stampedAsInstant.isAfter(after.plusSeconds(2))
        )
    }

    // region — device-zone independence (brief-mandated): the fallback must be identical
    // regardless of the JVM's default timezone, because it is always computed in
    // BUSINESS_ZONE via AppTime.toBusinessDateTime, never via the device/JVM default zone.

    @Test
    fun `toDomain fallback is identical under UTC and America-Tijuana device defaults`() {
        val originalDefault = TimeZone.getDefault()
        try {
            val fixed = Instant.parse("2026-08-08T18:30:00Z")
            val expected = AppTime.toBusinessDateTime(fixed)

            TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
            val underUtc = entity(fecha = "garbage").toDomain(FakeClock(fixed)).fecha

            TimeZone.setDefault(TimeZone.getTimeZone("America/Tijuana"))
            val underTijuana = entity(fecha = "garbage").toDomain(FakeClock(fixed)).fecha

            assertEquals(expected, underUtc)
            assertEquals(expected, underTijuana)
            assertEquals(underUtc, underTijuana)
        } finally {
            TimeZone.setDefault(originalDefault)
        }
    }

    // endregion

    @Test
    fun `list extension propagates the clock to every element's fallback`() {
        val fixed = Instant.parse("2026-08-08T18:30:00Z")
        val clock = FakeClock(fixed)
        val entities = listOf(entity(fecha = "garbage-1"), entity(fecha = "garbage-2"))

        val domains = entities.toDomainTransfers(clock)

        val expected = AppTime.toBusinessDateTime(fixed)
        assertEquals(listOf(expected, expected), domains.map { it.fecha })
    }
}
