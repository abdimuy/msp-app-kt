package com.example.msp_app.core.common.time

import java.time.Instant
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Contract test: pins `AppTime`'s wire serialization/parsing against the REAL format that
 * `msp-api` (the Go backend, `/Volumes/M2-1TB/Developer/msp-api`) emits and accepts.
 *
 * This is deliberately separate from [AppTimeTest] (which exhaustively covers `AppTime`'s own
 * edge cases — DST, leap years, device-zone independence, etc.). `WireContractTest` exists so
 * that Tasks 3-7 of the date migration (the money-path call sites) can point at ONE file and
 * trust "the wire format is verified against the backend" without re-deriving it — every sample
 * string below is copied from, or computed from a rule documented in, a concrete Go source
 * location, not invented.
 *
 * Sources cross-checked (2026-08-08):
 *  - `docs/module-standards/DATETIME_HANDLING.md` (msp-api) — the 3 canonical rules: domain is
 *    always UTC, output DTOs use `t.UTC().Format(time.RFC3339Nano)` or `time.RFC3339`, input
 *    accepts `time.Parse(time.RFC3339, raw)` (any offset, liberal on fractional seconds).
 *  - `internal/cobranza/infra/cobranzahttp/dto_pago_recibido.go` — `FechaHoraPago`, `ReceivedAt`,
 *    `CreatedAt`, `UpdatedAt`, `AplicadoAt` all use `.UTC().Format(time.RFC3339)` (money-path
 *    payment timestamps — exactly what Task 6 will write to and Task 3's grouping will read).
 *  - `internal/ventas/infra/venthttp/dto_mapper.go` (`formatTime`) — uses
 *    `.UTC().Format(time.RFC3339Nano)` (fractional seconds included when non-zero).
 *  - `internal/ventas/infra/venthttp/dto.go:359-360` — range query params `desde`/`hasta`:
 *    "Filtra FECHA_VENTA >= desde" / "Filtra FECHA_VENTA < hasta" — the live proof of the
 *    `[desde, hasta)` half-open range semantics this file also pins for `AppTime`.
 *  - `internal/clientes/infra/clienteshttp/dto.go:89-90` — pure calendar-date range params
 *    (`YYYY-MM-DD`, no time component) — the shape `AppTime.toWireDate`/`parseWireDateOrNull`
 *    must match.
 *  - `docs/standards/timezones.md` (this repo, §"Contrato real con el backend", verified
 *    2026-04-22) — the one documented legacy exception: `FECHA_HORA_CREACION` comes back as
 *    `"2026-04-22T19:43:56.000-06:00"` (explicit offset, not `Z`) from an older Microsip-mirror
 *    endpoint, not the standard V2 contract above.
 *  - Live-verified against the real Go stdlib (`go run`, `go1.25.0`, 2026-08-08): confirmed that
 *    (a) `time.Parse(time.RFC3339, raw)` accepts fractional seconds in `raw` even though the
 *    `time.RFC3339` layout constant has none — Go's parser recognises a trailing `.NNN` before
 *    the zone regardless of the layout; (b) the `[desde, hasta)` boundary instants below land
 *    exactly where expected on the Go side too.
 */
class WireContractTest {

    // region — 1. Emission: what AppTime sends is what msp-api's `time.Parse(time.RFC3339, ..)` accepts

    @Test
    fun `toWireFormat emits Z-UTC without fraction, parseable by Go time RFC3339`() {
        // Mirrors a payment CreatedAt/UpdatedAt with whole-second precision.
        val instant = Instant.parse("2026-05-13T18:00:00Z")
        assertEquals("2026-05-13T18:00:00Z", AppTime.toWireFormat(instant))
    }

    @Test
    fun `toWireFormat emits Z-UTC with millis fraction, parseable by Go time RFC3339`() {
        // Go's time.Parse(time.RFC3339, raw) accepts a fractional-seconds component in the
        // input even though the RFC3339 layout constant itself has none (verified live against
        // the Go stdlib) — so this shape round-trips through msp-api's input parsing cleanly.
        val instant = Instant.parse("2026-05-13T18:05:23.142Z")
        assertEquals("2026-05-13T18:05:23.142Z", AppTime.toWireFormat(instant))
    }

    // endregion

    // region — 2. Standard V2 input: what msp-api actually emits (RFC3339Nano, variable fraction)

    @Test
    fun `parses the exact FechaHoraPago shape emitted by dto_pago_recibido go (whole seconds, no fraction)`() {
        // internal/cobranza/infra/cobranzahttp/dto_pago_recibido.go uses
        // p.FechaHoraPago().UTC().Format(time.RFC3339) — no fractional component ever.
        val i = AppTime.parseWireFormat("2026-05-13T18:00:00Z")
        assertEquals(Instant.parse("2026-05-13T18:00:00Z"), i)
    }

    @Test
    fun `parses the millis-fraction shape emitted by formatTime in ventas dto_mapper go`() {
        // internal/ventas/infra/venthttp/dto_mapper.go's formatTime uses
        // .UTC().Format(time.RFC3339Nano) — fraction present when the source instant has one.
        val i = AppTime.parseWireFormat("2026-05-13T18:05:23.142Z")
        assertEquals(Instant.parse("2026-05-13T18:05:23.142Z"), i)
    }

    @Test
    fun `parses a FECHA field at CDMX midnight (06 UTC) matching the calendar-date convention`() {
        // docs/standards/timezones.md: FECHA = "2026-04-22T06:00:00.000Z" represents 22-abr in
        // CDMX (06:00 UTC == 00:00 CDMX). This is how msp-api encodes a "calendar date" as a
        // UTC instant when the field is really a timestamp column, not a DATE column.
        val i = AppTime.parseWireFormat("2026-04-22T06:00:00.000Z")
        assertEquals(LocalDate.of(2026, 4, 22), AppTime.toBusinessDate(i))
    }

    // endregion

    // region — 3. Legacy non-Z input: the real FECHA_HORA_CREACION -06:00 case (the pinned fix)

    @Test
    fun `parses the real legacy FECHA_HORA_CREACION -06 00 shape to the correct instant`() {
        // docs/standards/timezones.md §"Contrato real con el backend" (verified 2026-04-22):
        // a legacy Microsip-mirror endpoint returns FECHA_HORA_CREACION with an explicit -06:00
        // offset instead of Z. Live-verified against the Go stdlib: time.Parse(time.RFC3339, ..)
        // on this exact string yields 2026-04-23T01:43:56Z (19:43:56 -06:00 == 01:43:56Z next day).
        //
        // BEFORE this migration, the legacy date util's `parseIsoToDateTime` THROWS
        // DateTimeParseException on this exact string (bug #2 in the audit) — callers like
        // `isAfterIso`/`isBeforeIso` crashed, and `formatIsoDate` silently degraded to the raw
        // string. This test pins the CORRECT behaviour: AppTime.parseWireFormat must accept it
        // and resolve to the right instant, never throw.
        val i = AppTime.parseWireFormat("2026-04-22T19:43:56.000-06:00")
        assertEquals(Instant.parse("2026-04-23T01:43:56Z"), i)
    }

    @Test
    fun `the legacy -06 00 shape lands on the correct CDMX business date, not the UTC date`() {
        // 19:43:56 -06:00 (22-abr local) becomes 01:43:56Z the NEXT calendar day in UTC
        // (23-abr). A naive "take the UTC date" reading would misfile this event on 23-abr.
        // AppTime.toBusinessDate must recover the correct local date, 22-abr.
        val i = AppTime.parseWireFormat("2026-04-22T19:43:56.000-06:00")
        assertEquals(LocalDate.of(2026, 4, 22), AppTime.toBusinessDate(i))
    }

    // endregion

    // region — 4. Pure calendar dates: toWireDate/parseWireDateOrNull vs Desde/Hasta query params

    @Test
    fun `toWireDate matches the YYYY-MM-DD shape documented for clientes Desde Hasta query params`() {
        // internal/clientes/infra/clienteshttp/dto.go:89-90 — `Desde`/`Hasta string query:"desde|hasta"`
        // doc:"... (YYYY-MM-DD) ...": a plain calendar date, no time component.
        val d = LocalDate.of(2026, 5, 13)
        assertEquals("2026-05-13", AppTime.toWireDate(d))
    }

    @Test
    fun `parseWireDateOrNull round-trips the same YYYY-MM-DD shape`() {
        assertEquals(LocalDate.of(2026, 5, 13), AppTime.parseWireDateOrNull("2026-05-13"))
    }

    // endregion

    // region — 5. [desde, hasta) half-open range semantics (ventas dto.go: ">= desde", "< hasta")

    @Test
    fun `startOfDay resolves to exactly 00-00-00 CDMX for the given business date`() {
        // internal/ventas/infra/venthttp/dto.go:359 doc:"Filtra FECHA_VENTA >= desde"
        // CDMX has a fixed UTC-06:00 offset (Mexico stopped observing DST nationally in 2022),
        // so midnight CDMX of 2026-04-15 is exactly 06:00 UTC. Pinned against a concrete
        // expected Instant (not against itself) so a wrong implementation — e.g. one that
        // resolves to a different day, or to noon instead of midnight — actually fails here.
        val d = LocalDate.of(2026, 4, 15)
        assertEquals(Instant.parse("2026-04-15T06:00:00Z"), AppTime.startOfDay(d))
    }

    @Test
    fun `startOfNextDay is exactly one CDMX calendar day after startOfDay, not the same instant`() {
        // internal/ventas/infra/venthttp/dto.go:360 doc:"Filtra FECHA_VENTA < hasta"
        // Pinned against the concrete expected Instant for 2026-04-16 CDMX midnight, and cross
        // checked against startOfDay(d + 1) — a wrong startOfNextDay (e.g. one that returns
        // startOfDay(d) unchanged, or jumps a month instead of a day) fails both assertions.
        val d = LocalDate.of(2026, 4, 15)
        val hasta = AppTime.startOfNextDay(d)
        assertEquals(Instant.parse("2026-04-16T06:00:00Z"), hasta)
        assertEquals(AppTime.startOfDay(d.plusDays(1)), hasta)
    }

    @Test
    fun `an instant one millisecond before hasta is inside the range, one at or after is not`() {
        val d = LocalDate.of(2026, 4, 15)
        val desde = AppTime.startOfDay(d)
        val hasta = AppTime.startOfNextDay(d)

        val lastInsideRange = hasta.minusMillis(1) // 23:59:59.999 CDMX of `d`
        val firstOutsideRange = hasta // 00:00:00.000 CDMX of `d + 1`

        assertTrue(!lastInsideRange.isBefore(desde) && lastInsideRange.isBefore(hasta))
        assertFalse(!firstOutsideRange.isBefore(desde) && firstOutsideRange.isBefore(hasta))
    }

    // endregion
}
