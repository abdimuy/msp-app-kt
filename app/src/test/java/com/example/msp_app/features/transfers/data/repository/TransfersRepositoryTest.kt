package com.example.msp_app.features.transfers.data.repository

import com.example.msp_app.core.common.time.AppTime
import com.example.msp_app.core.testing.time.FakeClock
import com.example.msp_app.features.transfers.data.api.TransfersApiService
import com.example.msp_app.features.transfers.data.api.dto.CostPreviewRequest
import com.example.msp_app.features.transfers.data.api.dto.CreateTransferRequest
import com.example.msp_app.features.transfers.data.api.dto.CreateTransferResponse
import com.example.msp_app.features.transfers.data.api.dto.ProductCostDto
import com.example.msp_app.features.transfers.data.api.dto.TransferDetailResponse
import com.example.msp_app.features.transfers.data.api.dto.TransferListItemDto
import com.example.msp_app.features.transfers.data.api.dto.TransferListResponse
import java.time.Instant
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.TimeZone
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Test
import retrofit2.Response

/**
 * Task 10 — timestamp generation in [TransfersRepository] moved from ambient
 * `LocalDateTime.now()` (device timezone, untestable) to an injected [com.example.msp_app.core.common.time.AppClock]
 * routed through [AppTime.toBusinessDateTime]. `createdAt`/`updatedAt` on the domain
 * `Transfer` are materialization metadata fabricated client-side (the backend does not
 * send them for this shape) — they were, and remain, never persisted and never displayed
 * (see audit note in the task report). The `fecha` field is the real business date and IS
 * displayed; its happy-path parsing pattern is untouched (see report — the legacy Node
 * `sys_msp_backend` contract for this field could not be verified from this repo), only the
 * "everything failed to parse" fallback moves off ambient `now()`.
 */
class TransfersRepositoryTest {

    private val dateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss")

    private fun listItem(fecha: String) = TransferListItemDto(
        doctoInId = 501,
        almacenId = 11,
        almacenDestinoId = 22,
        fecha = fecha,
        descripcion = "Traspaso a camioneta de Erika Paredes",
        folio = "T-501",
        usuario = "erika.paredes",
        aplicado = "S",
        almacen = "Almacén Central",
        almacenDestino = "Camioneta 3",
        totalProductos = 4,
        costoTotal = 1200.0
    )

    private fun detail(fecha: String) = TransferDetailResponse(
        doctoInId = 501,
        almacenId = 11,
        almacenDestinoId = 22,
        fecha = fecha,
        descripcion = "Traspaso a camioneta de Erika Paredes",
        folio = "T-501",
        usuario = "erika.paredes",
        aplicado = "S",
        almacen = "Almacén Central",
        almacenDestino = "Camioneta 3",
        salidas = emptyList(),
        entradas = emptyList(),
        detallesCompletos = emptyList()
    )

    private class FakeTransfersApiService(
        private val listResponse: Response<TransferListResponse>? = null,
        private val detailResponse: Response<TransferDetailResponse>? = null
    ) : TransfersApiService {
        override suspend fun createTransfer(
            request: CreateTransferRequest
        ): Response<CreateTransferResponse> = error("not needed for these tests")

        override suspend fun getTransfers(
            fechaInicio: String?,
            fechaFin: String?,
            almacenOrigenId: Int?,
            almacenDestinoId: Int?
        ): Response<TransferListResponse> = listResponse ?: error("not stubbed")

        override suspend fun getTransferDetail(doctoInId: Int): Response<TransferDetailResponse> =
            detailResponse ?: error("not stubbed")

        override suspend fun getProductCosts(
            request: CostPreviewRequest
        ): Response<List<ProductCostDto>> = error("not needed for these tests")
    }

    // region — 1. FakeClock fixed instant -> generated timestamp is exact AppTime.toBusinessDateTime output

    @Test
    fun `getTransferDetail stamps createdAt and updatedAt from the injected clock, business zone`() =
        runTest {
            // 18:30 CDMX, well inside the same UTC calendar day.
            val fixed = Instant.parse("2026-08-08T18:30:00Z") // 12:30 CDMX 08-ago
            val clock = FakeClock(fixed)
            val repo = TransfersRepository(
                apiService = FakeTransfersApiService(
                    detailResponse = Response.success(detail(fecha = "2026-08-08T09:00:00"))
                ),
                clock = clock
            )

            val result = repo.getTransferDetail(501).getOrThrow()

            val expected = AppTime.toBusinessDateTime(fixed)
            assertEquals(expected, result.transfer.createdAt)
            assertEquals(expected, result.transfer.updatedAt)
        }

    @Test
    fun `getTransfers stamps createdAt and updatedAt from the injected clock, business zone`() =
        runTest {
            val fixed = Instant.parse("2026-08-08T18:30:00Z")
            val clock = FakeClock(fixed)
            val repo = TransfersRepository(
                apiService = FakeTransfersApiService(
                    listResponse = Response.success(
                        TransferListResponse(
                            error = null,
                            body = listOf(listItem("2026-08-08T09:00:00"))
                        )
                    )
                ),
                clock = clock
            )

            val result = repo.getTransfers().getOrThrow()

            val expected = AppTime.toBusinessDateTime(fixed)
            assertEquals(expected, result.single().createdAt)
            assertEquals(expected, result.single().updatedAt)
        }

    @Test
    fun `stamped timestamp reflects clock advancement, not a value captured at construction`() =
        runTest {
            val clock = FakeClock(Instant.parse("2026-08-08T18:30:00Z"))
            val repo = TransfersRepository(
                apiService = FakeTransfersApiService(
                    detailResponse = Response.success(detail(fecha = "2026-08-08T09:00:00"))
                ),
                clock = clock
            )

            val before = repo.getTransferDetail(501).getOrThrow().transfer.createdAt

            clock.advanceHours(3)
            val after = repo.getTransferDetail(501).getOrThrow().transfer.createdAt

            assertNotEquals(before, after)
            assertEquals(AppTime.toBusinessDateTime(Instant.parse("2026-08-08T21:30:00Z")), after)
        }

    // endregion

    // region — 1b. Device-zone independence (brief-mandated): the injected clock's instant
    // must produce the SAME business-zone timestamp regardless of the device/JVM default
    // timezone, exercised through the actual generation path this repository uses
    // (getTransferDetail's createdAt/updatedAt, and the `fecha` clock-fallback), not just
    // AppTime directly.

    @Test
    fun `stamped createdAt is identical under UTC and America-Tijuana device defaults`() = runTest {
        val originalDefault = TimeZone.getDefault()
        try {
            val fixed = Instant.parse("2026-08-08T18:30:00Z")
            val expected = AppTime.toBusinessDateTime(fixed)

            TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
            val repoUnderUtc = TransfersRepository(
                apiService = FakeTransfersApiService(
                    detailResponse = Response.success(detail(fecha = "2026-08-08T09:00:00"))
                ),
                clock = FakeClock(fixed)
            )
            val underUtc = repoUnderUtc.getTransferDetail(501).getOrThrow().transfer.createdAt

            TimeZone.setDefault(TimeZone.getTimeZone("America/Tijuana"))
            val repoUnderTijuana = TransfersRepository(
                apiService = FakeTransfersApiService(
                    detailResponse = Response.success(detail(fecha = "2026-08-08T09:00:00"))
                ),
                clock = FakeClock(fixed)
            )
            val underTijuana = repoUnderTijuana.getTransferDetail(
                501
            ).getOrThrow().transfer.createdAt

            assertEquals(expected, underUtc)
            assertEquals(expected, underTijuana)
            assertEquals(underUtc, underTijuana)
        } finally {
            TimeZone.setDefault(originalDefault)
        }
    }

    @Test
    fun `unparseable-fecha clock fallback is identical under UTC and America-Tijuana device defaults`() =
        runTest {
            val originalDefault = TimeZone.getDefault()
            try {
                val fixed = Instant.parse("2026-08-08T18:30:00Z")
                val expected = AppTime.toBusinessDateTime(fixed)

                TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
                val repoUnderUtc = TransfersRepository(
                    apiService = FakeTransfersApiService(
                        detailResponse = Response.success(detail(fecha = "no es una fecha"))
                    ),
                    clock = FakeClock(fixed)
                )
                val underUtc = repoUnderUtc.getTransferDetail(501).getOrThrow().transfer.fecha

                TimeZone.setDefault(TimeZone.getTimeZone("America/Tijuana"))
                val repoUnderTijuana = TransfersRepository(
                    apiService = FakeTransfersApiService(
                        detailResponse = Response.success(detail(fecha = "no es una fecha"))
                    ),
                    clock = FakeClock(fixed)
                )
                val underTijuana = repoUnderTijuana.getTransferDetail(
                    501
                ).getOrThrow().transfer.fecha

                assertEquals(expected, underUtc)
                assertEquals(expected, underTijuana)
                assertEquals(underUtc, underTijuana)
            } finally {
                TimeZone.setDefault(originalDefault)
            }
        }

    // endregion

    // region — 2. Midnight CDMX boundary: a UTC instant on one calendar day can be the
    // previous business day in CDMX (UTC-6, no DST since 2023). The old `LocalDateTime.now()`
    // used the DEVICE zone (ambiguous); the new path is always CDMX-correct regardless of
    // device settings.

    @Test
    fun `stamped createdAt crosses back to the previous CDMX calendar day near UTC midnight`() =
        runTest {
            // 05:30 UTC on the 8th == 23:30 CDMX on the 7th.
            val fixed = Instant.parse("2026-08-08T05:30:00Z")
            val clock = FakeClock(fixed)
            val repo = TransfersRepository(
                apiService = FakeTransfersApiService(
                    detailResponse = Response.success(detail(fecha = "2026-08-07T23:00:00"))
                ),
                clock = clock
            )

            val stamped = repo.getTransferDetail(501).getOrThrow().transfer.createdAt

            assertEquals(LocalDateTime.of(2026, 8, 7, 23, 30, 0), stamped)
            assertFalse(
                "must not leak the UTC calendar day for a business-zone-crossing instant",
                stamped.toLocalDate().dayOfMonth == 8
            )
        }

    // endregion

    // region — 3. `fecha` (the real, displayed business date): happy-path parsing is
    // unchanged by this migration — only the "every parse attempt failed" fallback moves
    // off ambient `now()`. This proves existing/legacy wire values keep working exactly as
    // before (backward compatibility for the one format this endpoint is confirmed to use).

    @Test
    fun `well-formed fecha parses on the happy path, untouched by the clock migration`() = runTest {
        val clock = FakeClock(Instant.parse("2026-08-08T18:30:00Z"))
        val repo = TransfersRepository(
            apiService = FakeTransfersApiService(
                detailResponse = Response.success(detail(fecha = "2026-04-22T19:43:56"))
            ),
            clock = clock
        )

        val fecha = repo.getTransferDetail(501).getOrThrow().transfer.fecha

        // Exactly the parsed value, NOT the clock's fallback value — proves the happy path
        // never touches the clock at all.
        assertEquals(LocalDateTime.of(2026, 4, 22, 19, 43, 56), fecha)
    }

    @Test
    fun `date-only fecha parses at start of day, unaffected by the clock migration`() = runTest {
        val clock = FakeClock(Instant.parse("2026-08-08T18:30:00Z"))
        val repo = TransfersRepository(
            apiService = FakeTransfersApiService(
                detailResponse = Response.success(detail(fecha = "2026-04-22"))
            ),
            clock = clock
        )

        val fecha = repo.getTransferDetail(501).getOrThrow().transfer.fecha

        assertEquals(LocalDateTime.of(2026, 4, 22, 0, 0, 0), fecha)
    }

    @Test
    fun `unparseable fecha falls back to the injected clock's business datetime, not ambient now`() =
        runTest {
            val fixed = Instant.parse("2026-08-08T18:30:00Z")
            val clock = FakeClock(fixed)
            val repo = TransfersRepository(
                apiService = FakeTransfersApiService(
                    detailResponse = Response.success(detail(fecha = "no es una fecha"))
                ),
                clock = clock
            )

            val fecha = repo.getTransferDetail(501).getOrThrow().transfer.fecha

            assertEquals(AppTime.toBusinessDateTime(fixed), fecha)
        }

    // endregion

    // region — 4. Round-trip stability of what this module actually emits on the wire.
    // This endpoint's contract (legacy Node `sys_msp_backend`, not msp-api — see report) is a
    // naive local pattern with no zone/offset, NOT RFC3339 `Z`. The clock-derived value must
    // still format/parse cleanly through that exact contract without precision loss.

    @Test
    fun `clock-derived business datetime round-trips through the module's naive wire format`() {
        val fixed = Instant.parse("2026-08-08T18:30:45Z")
        val clock = FakeClock(fixed)

        val businessDateTime = AppTime.toBusinessDateTime(clock.now())
        val wire = businessDateTime.format(dateTimeFormatter)
        val parsedBack = LocalDateTime.parse(wire, dateTimeFormatter)

        assertEquals(businessDateTime, parsedBack)
        assertEquals("2026-08-08T12:30:45", wire)
    }

    // endregion
}
