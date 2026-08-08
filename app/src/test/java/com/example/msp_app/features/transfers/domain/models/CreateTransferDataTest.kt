package com.example.msp_app.features.transfers.domain.models

import com.example.msp_app.core.common.time.AppTime
import com.example.msp_app.core.common.time.BUSINESS_ZONE
import com.example.msp_app.core.testing.time.FakeClock
import com.example.msp_app.features.transfers.data.mappers.toPendingEntity
import com.example.msp_app.features.transfers.data.mappers.toRequest
import java.time.Duration
import java.time.Instant
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Task 10 — `CreateTransferData.fecha`'s default moved off ambient `LocalDateTime.now()`
 * (device timezone, per `docs/standards/timezones.md` an explicit anti-pattern) to
 * `AppTime.nowInBusinessZone()` (fixed CDMX business zone via `AppClock.System`). The one
 * live construction site (`NewTransferViewModel.createTransfer()`) always passes `fecha`
 * explicitly using its own injected `AppClock`, so this default is a safety net, not the
 * production path — exercised here for completeness.
 */
class CreateTransferDataTest {

    private val producto = TransferProductItem(articuloId = 1, unidades = 2)
    private val dateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss")

    @Test
    fun `default fecha is the CDMX business-zone wall clock, not an untestable ambient now`() {
        val before = Instant.now()
        val data = CreateTransferData(
            almacenOrigenId = 1,
            almacenDestinoId = 2,
            productos = listOf(producto)
        )
        val after = Instant.now()

        val asInstant = data.fecha.atZone(BUSINESS_ZONE).toInstant()
        assertTrue(
            "default fecha must fall within the real call window",
            !asInstant.isBefore(before.minusSeconds(2)) && !asInstant.isAfter(after.plusSeconds(2))
        )
    }

    @Test
    fun `explicit fecha from an injected clock overrides the default`() {
        val fixed = Instant.parse("2026-08-08T18:30:00Z")
        val clock = FakeClock(fixed)

        val data = CreateTransferData(
            almacenOrigenId = 1,
            almacenDestinoId = 2,
            fecha = AppTime.nowInBusinessZone(clock),
            productos = listOf(producto)
        )

        assertEquals(AppTime.toBusinessDateTime(fixed), data.fecha)
        assertEquals(LocalDateTime.of(2026, 8, 8, 12, 30, 0), data.fecha)
    }

    // region — round-trip: the wire value this module actually emits (naive local pattern,
    // NOT RFC3339 `Z` — see task report on the legacy `sys_msp_backend` contract) survives a
    // parse back unchanged, for a clock-derived fecha.

    @Test
    fun `toRequest fecha round-trips through the module's naive wire format`() {
        val clock = FakeClock(Instant.parse("2026-08-08T18:30:45Z"))
        val data = CreateTransferData(
            almacenOrigenId = 1,
            almacenDestinoId = 2,
            fecha = AppTime.nowInBusinessZone(clock),
            productos = listOf(producto)
        )

        val wire = requireNotNull(data.toRequest().fecha)
        assertEquals("2026-08-08T12:30:45", wire)

        val parsedBack = LocalDateTime.parse(wire, dateTimeFormatter)
        assertEquals(data.fecha, parsedBack)
    }

    @Test
    fun `toPendingEntity fecha matches toRequest fecha exactly`() {
        val clock = FakeClock(Instant.parse("2026-08-08T18:30:45Z"))
        val data = CreateTransferData(
            almacenOrigenId = 1,
            almacenDestinoId = 2,
            fecha = AppTime.nowInBusinessZone(clock),
            productos = listOf(producto)
        )

        assertEquals(data.toRequest().fecha, data.toPendingEntity().fecha)
    }

    // endregion

    // region — midnight CDMX boundary

    @Test
    fun `fecha derived near UTC midnight lands on the previous CDMX calendar day`() {
        // 04:15 UTC on the 8th == 22:15 CDMX on the 7th.
        val clock = FakeClock(Instant.parse("2026-08-08T04:15:00Z"))

        val data = CreateTransferData(
            almacenOrigenId = 1,
            almacenDestinoId = 2,
            fecha = AppTime.nowInBusinessZone(clock),
            productos = listOf(producto)
        )

        assertEquals(LocalDateTime.of(2026, 8, 7, 22, 15, 0), data.fecha)
        assertFalse(data.toRequest().fecha!!.startsWith("2026-08-08"))
    }

    // endregion

    // region — clock advancement is honored, not a value fixed once

    @Test
    fun `fecha reflects clock advancement between two constructions`() {
        val clock = FakeClock(Instant.parse("2026-08-08T18:30:00Z"))

        val first = CreateTransferData(
            almacenOrigenId = 1,
            almacenDestinoId = 2,
            fecha = AppTime.nowInBusinessZone(clock),
            productos = listOf(producto)
        ).fecha

        clock.advance(Duration.ofHours(1))

        val second = CreateTransferData(
            almacenOrigenId = 1,
            almacenDestinoId = 2,
            fecha = AppTime.nowInBusinessZone(clock),
            productos = listOf(producto)
        ).fecha

        assertEquals(Duration.ofHours(1), Duration.between(first, second))
    }

    // endregion
}
