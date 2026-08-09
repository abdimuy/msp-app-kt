package com.example.msp_app.core.sync.cobranza

import androidx.test.core.app.ApplicationProvider
import com.example.msp_app.core.database.entities.PaymentEntity
import com.example.msp_app.core.network.ConnectivityMonitor
import com.example.msp_app.core.testing.RoomTestBase
import com.example.msp_app.data.api.services.cobranza.DigestResponse
import com.example.msp_app.data.api.services.cobranza.IdsResponse
import com.example.msp_app.data.api.services.cobranza.PagoDto
import com.example.msp_app.data.api.services.cobranza.SyncPagosResponse
import com.example.msp_app.data.api.services.cobranza.SyncVentasResponse
import com.example.msp_app.data.api.services.cobranza.V2CobranzaApi
import com.example.msp_app.data.api.services.cobranza.VentaDto
import com.example.msp_app.data.api.services.cobranza.toEntity
import java.time.Instant
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class CobranzaReconcilerTest : RoomTestBase() {

    @After
    fun resetByIdsFlag() {
        ByIdsChunker.byIdsAvailable.set(true)
    }

    // ─── Fakes ──────────────────────────────────────────────────────────────

    private class FakeConnectivity(private val online: Boolean) :
        ConnectivityMonitor(ApplicationProvider.getApplicationContext()) {
        override fun isNetworkAvailable(): Boolean = online
        override val isConnected: Flow<Boolean> = flowOf(online)
    }

    /**
     * Simple fake API for reconcile tests. Holds lists of pages per endpoint;
     * each call advances an index.
     *
     * Digest stubs default to DigestResponse(0, "0", "0", null) which always
     * mismatches local state (unless local is also empty), ensuring all existing
     * /ids-based tests continue to exercise the /ids path unchanged.
     */
    private inner class FakeV2CobranzaApi(
        var pagoIdPages: List<IdsResponse> = listOf(IdsResponse(emptyList(), false)),
        var saldoIdPages: List<IdsResponse> = listOf(IdsResponse(emptyList(), false)),
        var pagosDigestResponses: List<DigestResponse> = listOf(
            DigestResponse(count_activos = 0, ids_xor = "0", ids_sum = "0", max_updated_at = null)
        ),
        var saldosDigestResponses: List<DigestResponse> = listOf(
            DigestResponse(count_activos = 0, ids_xor = "0", ids_sum = "0", max_updated_at = null)
        ),
        var pagosByIdsResult: List<PagoDto> = emptyList(),
        var saldosByIdsResult: List<VentaDto> = emptyList()
    ) : V2CobranzaApi {
        private var pagoIdx = 0
        private var saldoIdx = 0
        private var pagosDigestIdx = 0
        private var saldosDigestIdx = 0

        var listPagoIdsCalled = 0
        var listSaldoIdsCalled = 0
        var pagosDigestCalled = 0
        var saldosDigestCalled = 0
        var pagosByIdsCalled = 0
        var saldosByIdsCalled = 0

        var lastPagosDigestDesde: String? = null
        var lastSaldosDigestDesde: String? = null

        override suspend fun pagosDigest(zonaId: Int, desde: String?): DigestResponse {
            pagosDigestCalled++
            lastPagosDigestDesde = desde
            val resp = pagosDigestResponses.getOrNull(pagosDigestIdx)
                ?: error("pagosDigest called too many times (idx=$pagosDigestIdx)")
            pagosDigestIdx++
            return resp
        }

        override suspend fun saldosDigest(zonaId: Int, desde: String?): DigestResponse {
            saldosDigestCalled++
            lastSaldosDigestDesde = desde
            val resp = saldosDigestResponses.getOrNull(saldosDigestIdx)
                ?: error("saldosDigest called too many times (idx=$saldosDigestIdx)")
            saldosDigestIdx++
            return resp
        }

        override suspend fun listPagoIds(
            zonaId: Int,
            after: Int,
            limit: Int,
            desde: String?
        ): IdsResponse {
            listPagoIdsCalled++
            val page = pagoIdPages.getOrNull(pagoIdx)
                ?: error("listPagoIds called too many times (idx=$pagoIdx)")
            pagoIdx++
            return page
        }

        override suspend fun listSaldoIds(
            zonaId: Int,
            after: Int,
            limit: Int,
            desde: String?
        ): IdsResponse {
            listSaldoIdsCalled++
            val page = saldoIdPages.getOrNull(saldoIdx)
                ?: error("listSaldoIds called too many times (idx=$saldoIdx)")
            saldoIdx++
            return page
        }

        override suspend fun syncVentas(
            zonaId: Int,
            cursor: String?,
            afterId: Int,
            limit: Int,
            desde: String?
        ): SyncVentasResponse {
            fail("syncVentas should not be called by reconciler")
            error("unreachable")
        }

        override suspend fun syncPagos(
            zonaId: Int,
            cursor: String?,
            afterId: Int,
            limit: Int,
            desde: String?
        ): SyncPagosResponse {
            fail("syncPagos should not be called by reconciler")
            error("unreachable")
        }

        override suspend fun pagosByIds(zonaId: Int, ids: String): List<PagoDto> {
            pagosByIdsCalled++
            return pagosByIdsResult
        }

        override suspend fun saldosByIds(zonaId: Int, ids: String): List<VentaDto> {
            saldosByIdsCalled++
            return saldosByIdsResult
        }
    }

    private fun newReconciler(
        api: V2CobranzaApi,
        online: Boolean = true,
        zona: Int? = 21,
        fechaCargaInicial: Instant? = null
    ): CobranzaReconciler = CobranzaReconciler(
        api = api,
        saleDao = db.saleDao(),
        paymentDao = db.paymentDao(),
        connectivity = FakeConnectivity(online),
        userContextFlow = MutableStateFlow(
            zona?.let { UserContext(zona = it, fechaCargaInicial = fechaCargaInicial) }
        ).asStateFlow(),
        // Mutex fresco por test para evitar dependencias entre tests al usar el singleton de proceso.
        cobranzaWriteMutex = CobranzaWriteMutex()
    )

    // ─── Helper fixtures ────────────────────────────────────────────────────

    private fun sampleVenta(doctoCcId: Int, zonaId: Int = 21): VentaDto = VentaDto(
        docto_cc_id = doctoCcId,
        docto_pv_id = null,
        cliente_id = 99,
        zona_cliente_id = zonaId,
        folio = "cv$doctoCcId",
        fecha_cargo = "2026-02-15T00:00:00Z",
        fecha_venta = null,
        precio_total = "5000.00",
        total_importe = "1000.00",
        impte_rest = "0.00",
        saldo = "4000.00",
        num_pagos = 4,
        fecha_ult_pago = null,
        cargo_cancelado = false,
        updated_at = "2026-05-30T18:25:13.456789Z",
        cliente_nombre = "JUAN PEREZ",
        limite_credito = null,
        cliente_notas = "",
        cobrador_id = null,
        nombre_cobrador = "",
        zona_nombre = "R/21",
        calle = "AV INDEPENDENCIA 123",
        ciudad = "TEHUACAN",
        estado = "PUEBLA",
        telefono = "",
        parcialidad = 200,
        enganche = "500.00",
        tiempo_corto_plazo_meses = null,
        monto_corto_plazo = null,
        precio_de_contado = null,
        aval_o_responsable = "",
        vendedor_1 = "",
        vendedor_2 = "",
        vendedor_3 = "",
        frec_pago = "SEMANAL"
    )

    private fun samplePayment(impteId: Int, doctoCcId: Int, zonaId: Int = 21) = PaymentEntity(
        ID = impteId.toString(),
        COBRADOR = "",
        DOCTO_CC_ACR_ID = doctoCcId,
        DOCTO_CC_ID = doctoCcId + 1,
        FECHA_HORA_PAGO = "2026-05-01T00:00:00Z",
        GUARDADO_EN_MICROSIP = true,
        IMPORTE = 200.0,
        LAT = null,
        LNG = null,
        CLIENTE_ID = 99,
        COBRADOR_ID = 0,
        FORMA_COBRO_ID = 1,
        ZONA_CLIENTE_ID = zonaId,
        NOMBRE_CLIENTE = ""
    )

    private fun uuidPayment(
        id: String,
        doctoCcId: Int,
        zonaId: Int = 21,
        guardado: Boolean = false
    ) = PaymentEntity(
        ID = id,
        COBRADOR = "",
        DOCTO_CC_ACR_ID = doctoCcId,
        DOCTO_CC_ID = doctoCcId + 1,
        FECHA_HORA_PAGO = "2026-05-01T00:00:00Z",
        GUARDADO_EN_MICROSIP = guardado,
        IMPORTE = 200.0,
        LAT = null,
        LNG = null,
        CLIENTE_ID = 99,
        COBRADOR_ID = 0,
        FORMA_COBRO_ID = 1,
        ZONA_CLIENTE_ID = zonaId,
        NOMBRE_CLIENTE = ""
    )

    // Helper: compute XOR of a list of ints as Long.
    private fun xorOf(vararg ids: Int): Long = ids.fold(0L) { acc, id -> acc xor id.toLong() }

    // Helper: compute SUM of a list of ints as Long.
    private fun sumOf(vararg ids: Int): Long = ids.fold(0L) { acc, id -> acc + id.toLong() }

    // ─── Tests ──────────────────────────────────────────────────────────────

    /**
     * Happy path: local set equals server set — no phantoms, no extras.
     * Digest mismatches (default stub) → falls through to /ids path.
     */
    @Test
    fun happyPathNoDriftReturnsOkZeroZero() = runTest {
        db.saleDao().insertAll(listOf(sampleVenta(101).toEntity(), sampleVenta(102).toEntity()))
        db.paymentDao().saveAll(listOf(samplePayment(201, 101), samplePayment(202, 102)))

        val api = FakeV2CobranzaApi(
            pagoIdPages = listOf(IdsResponse(listOf(201, 202), false)),
            saldoIdPages = listOf(IdsResponse(listOf(101, 102), false))
        )
        val outcome = newReconciler(api).reconcileNow()

        assertTrue(outcome is ReconcileOutcome.Ok)
        val ok = outcome as ReconcileOutcome.Ok
        assertEquals(0, ok.pagosPhantomsDeleted)
        assertEquals(0, ok.saldosPhantomsDeleted)
        assertEquals(0, ok.pagosExtrasOnServer)
        assertEquals(0, ok.saldosExtrasOnServer)
    }

    /**
     * Local has 3 pago IDs that server's /ids doesn't return — phantoms
     * get deleted, pagosPhantomsDeleted=3.
     */
    @Test
    fun phantomPagosAreDeleted() = runTest {
        db.paymentDao().saveAll(
            listOf(
                samplePayment(301, 101),
                samplePayment(302, 102),
                samplePayment(303, 103)
            )
        )

        val api = FakeV2CobranzaApi(
            pagoIdPages = listOf(IdsResponse(emptyList(), false)),
            saldoIdPages = listOf(IdsResponse(emptyList(), false))
        )
        val outcome = newReconciler(api).reconcileNow()

        assertTrue(outcome is ReconcileOutcome.Ok)
        assertEquals(3, (outcome as ReconcileOutcome.Ok).pagosPhantomsDeleted)

        assertTrue(db.paymentDao().getPaymentsBySaleId(101).isEmpty())
        assertTrue(db.paymentDao().getPaymentsBySaleId(102).isEmpty())
        assertTrue(db.paymentDao().getPaymentsBySaleId(103).isEmpty())
    }

    /**
     * Local has 3 saldo (venta) IDs that server's /ids doesn't return —
     * phantoms get deleted, saldosPhantomsDeleted=3.
     */
    @Test
    fun phantomSaldosAreDeleted() = runTest {
        db.saleDao().insertAll(
            listOf(
                sampleVenta(401).toEntity(),
                sampleVenta(402).toEntity(),
                sampleVenta(403).toEntity()
            )
        )

        val api = FakeV2CobranzaApi(
            pagoIdPages = listOf(IdsResponse(emptyList(), false)),
            saldoIdPages = listOf(IdsResponse(emptyList(), false))
        )
        val outcome = newReconciler(api).reconcileNow()

        assertTrue(outcome is ReconcileOutcome.Ok)
        assertEquals(3, (outcome as ReconcileOutcome.Ok).saldosPhantomsDeleted)

        assertNull(db.saleDao().findByDoctoCcId(401))
        assertNull(db.saleDao().findByDoctoCcId(402))
        assertNull(db.saleDao().findByDoctoCcId(403))
    }

    /**
     * Filter asymmetry case: server returns 5 IDs but local only has 2.
     * Extras are logged but NOT deleted. pagosExtrasOnServer=3, pagosPhantomsDeleted=0.
     */
    @Test
    fun extrasOnServerAreLoggedNotDeleted() = runTest {
        db.paymentDao().saveAll(
            listOf(samplePayment(501, 101), samplePayment(502, 102))
        )

        val api = FakeV2CobranzaApi(
            pagoIdPages = listOf(IdsResponse(listOf(501, 502, 503, 504, 505), false)),
            saldoIdPages = listOf(IdsResponse(emptyList(), false))
        )
        val outcome = newReconciler(api).reconcileNow()

        assertTrue(outcome is ReconcileOutcome.Ok)
        val ok = outcome as ReconcileOutcome.Ok
        assertEquals(0, ok.pagosPhantomsDeleted)
        assertEquals(3, ok.pagosExtrasOnServer)

        // Local rows must be untouched.
        assertEquals(1, db.paymentDao().getPaymentsBySaleId(101).size)
        assertEquals(1, db.paymentDao().getPaymentsBySaleId(102).size)
    }

    /**
     * Server returns 5000 IDs with has_more=true, then another 10. Reconciler
     * accumulates both pages before computing phantoms.
     */
    @Test
    fun paginationAccumulatesBothPages() = runTest {
        val firstPageIds = (1..5000).toList()
        val secondPageIds = (5001..5010).toList()

        // Seed two local pagos whose IDs fall within the server set.
        db.paymentDao().saveAll(
            listOf(samplePayment(1, 101), samplePayment(5000, 102))
        )

        val api = FakeV2CobranzaApi(
            pagoIdPages = listOf(
                IdsResponse(firstPageIds, has_more = true),
                IdsResponse(secondPageIds, has_more = false)
            ),
            saldoIdPages = listOf(IdsResponse(emptyList(), false))
        )
        val outcome = newReconciler(api).reconcileNow()

        assertTrue(outcome is ReconcileOutcome.Ok)
        val ok = outcome as ReconcileOutcome.Ok
        // IDs 1 and 5000 are in the server set — no phantoms.
        assertEquals(0, ok.pagosPhantomsDeleted)
        // Server has 5010 total, local has 2 — 5008 extras.
        assertEquals(5008, ok.pagosExtrasOnServer)
        assertEquals(2, api.listPagoIdsCalled)
    }

    /**
     * Offline → SkippedOffline, no API call made.
     */
    @Test
    fun skipOfflineNoApiCall() = runTest {
        val api = object : V2CobranzaApi {
            override suspend fun pagosDigest(zonaId: Int, desde: String?): DigestResponse {
                fail("API must not be called when offline")
                error("unreachable")
            }
            override suspend fun saldosDigest(zonaId: Int, desde: String?): DigestResponse {
                fail("API must not be called when offline")
                error("unreachable")
            }
            override suspend fun listPagoIds(
                zonaId: Int,
                after: Int,
                limit: Int,
                desde: String?
            ): IdsResponse {
                fail("API must not be called when offline")
                error("unreachable")
            }
            override suspend fun listSaldoIds(
                zonaId: Int,
                after: Int,
                limit: Int,
                desde: String?
            ): IdsResponse {
                fail("API must not be called when offline")
                error("unreachable")
            }
            override suspend fun syncVentas(
                zonaId: Int,
                cursor: String?,
                afterId: Int,
                limit: Int,
                desde: String?
            ): SyncVentasResponse {
                error("unreachable")
            }
            override suspend fun syncPagos(
                zonaId: Int,
                cursor: String?,
                afterId: Int,
                limit: Int,
                desde: String?
            ): SyncPagosResponse {
                error("unreachable")
            }
            override suspend fun pagosByIds(zonaId: Int, ids: String) = error("unreachable")
            override suspend fun saldosByIds(zonaId: Int, ids: String) = error("unreachable")
        }
        val outcome = newReconciler(api, online = false).reconcileNow()
        assertTrue(outcome is ReconcileOutcome.SkippedOffline)
    }

    /**
     * No zone (null user context) → SkippedNoZone, no API call made.
     */
    @Test
    fun skipNoZoneNoApiCall() = runTest {
        val api = object : V2CobranzaApi {
            override suspend fun pagosDigest(zonaId: Int, desde: String?): DigestResponse {
                fail("API must not be called without zone")
                error("unreachable")
            }
            override suspend fun saldosDigest(zonaId: Int, desde: String?): DigestResponse {
                fail("API must not be called without zone")
                error("unreachable")
            }
            override suspend fun listPagoIds(
                zonaId: Int,
                after: Int,
                limit: Int,
                desde: String?
            ): IdsResponse {
                fail("API must not be called without zone")
                error("unreachable")
            }
            override suspend fun listSaldoIds(
                zonaId: Int,
                after: Int,
                limit: Int,
                desde: String?
            ): IdsResponse {
                fail("API must not be called without zone")
                error("unreachable")
            }
            override suspend fun syncVentas(
                zonaId: Int,
                cursor: String?,
                afterId: Int,
                limit: Int,
                desde: String?
            ): SyncVentasResponse {
                error("unreachable")
            }
            override suspend fun syncPagos(
                zonaId: Int,
                cursor: String?,
                afterId: Int,
                limit: Int,
                desde: String?
            ): SyncPagosResponse {
                error("unreachable")
            }
            override suspend fun pagosByIds(zonaId: Int, ids: String) = error("unreachable")
            override suspend fun saldosByIds(zonaId: Int, ids: String) = error("unreachable")
        }
        val outcome = newReconciler(api, zona = null).reconcileNow()
        assertTrue(outcome is ReconcileOutcome.SkippedNoZone)
    }

    /**
     * Network error during /digest fetch → returns Error(cause), no rows deleted.
     */
    @Test
    fun networkErrorReturnsErrorOutcomeNoRowsDeleted() = runTest {
        db.saleDao().insertAll(listOf(sampleVenta(601).toEntity()))

        val api = object : V2CobranzaApi {
            override suspend fun pagosDigest(zonaId: Int, desde: String?): DigestResponse =
                throw RuntimeException("network down")
            override suspend fun saldosDigest(zonaId: Int, desde: String?): DigestResponse =
                throw RuntimeException("network down")
            override suspend fun listPagoIds(
                zonaId: Int,
                after: Int,
                limit: Int,
                desde: String?
            ): IdsResponse = throw RuntimeException("network down")
            override suspend fun listSaldoIds(
                zonaId: Int,
                after: Int,
                limit: Int,
                desde: String?
            ): IdsResponse = throw RuntimeException("network down")
            override suspend fun syncVentas(
                zonaId: Int,
                cursor: String?,
                afterId: Int,
                limit: Int,
                desde: String?
            ): SyncVentasResponse {
                error("unreachable")
            }
            override suspend fun syncPagos(
                zonaId: Int,
                cursor: String?,
                afterId: Int,
                limit: Int,
                desde: String?
            ): SyncPagosResponse {
                error("unreachable")
            }
            override suspend fun pagosByIds(zonaId: Int, ids: String) = error("unreachable")
            override suspend fun saldosByIds(zonaId: Int, ids: String) = error("unreachable")
        }
        val outcome = newReconciler(api).reconcileNow()

        assertTrue(outcome is ReconcileOutcome.Error)
        assertEquals("network down", (outcome as ReconcileOutcome.Error).cause.message)
        // Seeded venta must still be present.
        assertTrue(db.saleDao().findByDoctoCcId(601) != null)
    }

    // ─── New digest-first tests ──────────────────────────────────────────────

    /**
     * When both digests match, /ids is NOT called and outcome is Ok(0,0,0,0).
     */
    @Test
    fun digestMatchesSkipsIds() = runTest {
        // Seed Room: 3 pago IDs [10, 20, 30] in zona 21.
        db.paymentDao().saveAll(
            listOf(
                samplePayment(10, 100),
                samplePayment(20, 200),
                samplePayment(30, 300)
            )
        )
        // Seed Room: 3 saldo IDs [10, 20, 30] in zona 21.
        db.saleDao().insertAll(
            listOf(
                sampleVenta(10).toEntity(),
                sampleVenta(20).toEntity(),
                sampleVenta(30).toEntity()
            )
        )

        val pagoXor = xorOf(10, 20, 30)
        val pagoSum = sumOf(10, 20, 30)
        val saldoXor = xorOf(10, 20, 30)
        val saldoSum = sumOf(10, 20, 30)

        val api = FakeV2CobranzaApi(
            pagosDigestResponses = listOf(
                DigestResponse(
                    count_activos = 3,
                    ids_xor = pagoXor.toString(),
                    ids_sum = pagoSum.toString(),
                    max_updated_at = null
                )
            ),
            saldosDigestResponses = listOf(
                DigestResponse(
                    count_activos = 3,
                    ids_xor = saldoXor.toString(),
                    ids_sum = saldoSum.toString(),
                    max_updated_at = null
                )
            )
        )

        val outcome = newReconciler(api).reconcileNow()

        assertTrue(outcome is ReconcileOutcome.Ok)
        val ok = outcome as ReconcileOutcome.Ok
        assertEquals(0, ok.pagosPhantomsDeleted)
        assertEquals(0, ok.saldosPhantomsDeleted)
        assertEquals(0, ok.pagosExtrasOnServer)
        assertEquals(0, ok.saldosExtrasOnServer)

        // /ids must NOT have been called.
        assertEquals(0, api.listPagoIdsCalled)
        assertEquals(0, api.listSaldoIdsCalled)
    }

    /**
     * When pagos digest mismatches (server count=4), /ids is called for pagos
     * only. Saldos digest matches so saldos /ids is skipped.
     */
    @Test
    fun digestMismatchTriggersIds() = runTest {
        // Seed local: 3 pagos.
        db.paymentDao().saveAll(
            listOf(
                samplePayment(10, 100),
                samplePayment(20, 200),
                samplePayment(30, 300)
            )
        )
        // Saldos: empty local, empty server → digest matches (0,0,0).

        val api = FakeV2CobranzaApi(
            // Pagos digest: server says count=4 → mismatch.
            pagosDigestResponses = listOf(
                DigestResponse(
                    count_activos = 4,
                    ids_xor = "99",
                    ids_sum = "99",
                    max_updated_at = null
                )
            ),
            // Saldos digest: matches empty local (count=0, xor=0, sum=0).
            saldosDigestResponses = listOf(
                DigestResponse(
                    count_activos = 0,
                    ids_xor = "0",
                    ids_sum = "0",
                    max_updated_at = null
                )
            ),
            // /ids pagos: server has [10, 20, 30, 40] — 40 is extra.
            pagoIdPages = listOf(IdsResponse(listOf(10, 20, 30, 40), false)),
            saldoIdPages = listOf(IdsResponse(emptyList(), false))
        )

        val outcome = newReconciler(api).reconcileNow()

        assertTrue(outcome is ReconcileOutcome.Ok)
        val ok = outcome as ReconcileOutcome.Ok
        assertEquals(0, ok.pagosPhantomsDeleted)
        assertEquals(0, ok.saldosPhantomsDeleted)
        assertEquals(1, ok.pagosExtrasOnServer)
        assertEquals(0, ok.saldosExtrasOnServer)

        // Only pagos /ids was called; saldos /ids was skipped.
        assertEquals(1, api.listPagoIdsCalled)
        assertEquals(0, api.listSaldoIdsCalled)
    }

    /**
     * Pagos digest matches → pagos /ids skipped.
     * Saldos digest mismatches → saldos /ids called.
     */
    @Test
    fun digestMatchOnPagosSkipsPagosIds() = runTest {
        // Pagos: empty local, server digest also empty → match.
        // Saldos: empty local, server says count=2 → mismatch.

        val api = FakeV2CobranzaApi(
            pagosDigestResponses = listOf(
                DigestResponse(
                    count_activos = 0,
                    ids_xor = "0",
                    ids_sum = "0",
                    max_updated_at = null
                )
            ),
            saldosDigestResponses = listOf(
                DigestResponse(
                    count_activos = 2,
                    ids_xor = "99",
                    ids_sum = "99",
                    max_updated_at = null
                )
            ),
            pagoIdPages = listOf(IdsResponse(emptyList(), false)),
            saldoIdPages = listOf(IdsResponse(listOf(50, 51), false))
        )

        val outcome = newReconciler(api).reconcileNow()

        assertTrue(outcome is ReconcileOutcome.Ok)

        // Pagos /ids must NOT have been called; saldos /ids must have been called once.
        assertEquals(0, api.listPagoIdsCalled)
        assertEquals(1, api.listSaldoIdsCalled)
    }

    /**
     * When fechaCargaInicial is non-null, its toString() is forwarded as
     * `desde` to both /digest calls.
     */
    @Test
    fun desdeIsForwarded() = runTest {
        val ventana = Instant.parse("2026-04-01T00:00:00Z")

        // Both digests match empty local → early return, desde is still set.
        val api = FakeV2CobranzaApi(
            pagosDigestResponses = listOf(
                DigestResponse(
                    count_activos = 0,
                    ids_xor = "0",
                    ids_sum = "0",
                    max_updated_at = null
                )
            ),
            saldosDigestResponses = listOf(
                DigestResponse(
                    count_activos = 0,
                    ids_xor = "0",
                    ids_sum = "0",
                    max_updated_at = null
                )
            )
        )

        newReconciler(api, fechaCargaInicial = ventana).reconcileNow()

        assertEquals(ventana.toString(), api.lastPagosDigestDesde)
        assertEquals(ventana.toString(), api.lastSaldosDigestDesde)
    }

    // ─── Fetch missing via by-ids ────────────────────────────────────────────

    /**
     * IDs en servidor pero no en local (missing pagos) → se traen via pagosByIds
     * y se insertan en Room.
     */
    @Test
    fun missingPagosFetchedViaByIds() = runTest {
        ByIdsChunker.byIdsAvailable.set(true)

        // Local: sin pagos. Server /ids: [10, 20].
        val pagoDto = samplePayment(10, 100)
        val pagoDto2 = samplePayment(20, 200)

        val api = FakeV2CobranzaApi(
            // Digest mismatch para forzar el path /ids: server dice count=2.
            pagosDigestResponses = listOf(
                DigestResponse(
                    count_activos = 2,
                    ids_xor = "30",
                    ids_sum = "30",
                    max_updated_at = null
                )
            ),
            // Saldos digest: matches empty local (count=0).
            saldosDigestResponses = listOf(
                DigestResponse(
                    count_activos = 0,
                    ids_xor = "0",
                    ids_sum = "0",
                    max_updated_at = null
                )
            ),
            pagoIdPages = listOf(IdsResponse(listOf(10, 20), false)),
            saldoIdPages = listOf(IdsResponse(emptyList(), false)),
            pagosByIdsResult = listOf(
                PagoDto(
                    impte_docto_cc_id = 10,
                    docto_cc_id = 101,
                    docto_cc_acr_id = 100,
                    cliente_id = 99,
                    zona_cliente_id = 21,
                    folio = "abono",
                    concepto_cc_id = 87327,
                    fecha = "2026-05-20T14:30:00Z",
                    importe = "200.00",
                    impuesto = "0.00",
                    lat = null,
                    lon = null,
                    cancelado = false,
                    aplicado = true,
                    updated_at = "2026-05-30T18:25:13Z",
                    cobrador = "",
                    cobrador_id = null,
                    nombre_cliente = "",
                    forma_cobro_id = null
                )
            )
        )

        val outcome = newReconciler(api).reconcileNow()

        assertTrue(outcome is ReconcileOutcome.Ok)
        val ok = outcome as ReconcileOutcome.Ok
        assertEquals(0, ok.pagosPhantomsDeleted)
        assertEquals(2, ok.pagosExtrasOnServer)
        // El pago con id=10 debe haberse insertado en Room.
        assertEquals(1, api.pagosByIdsCalled)
        assertEquals(1, db.paymentDao().getPaymentsBySaleId(100).size)
    }

    /**
     * Con byIdsAvailable=false, los IDs missing en pagos se loguean pero
     * no se llama a pagosByIds.
     */
    @Test
    fun missingPagosNotFetchedWhenByIdsUnavailable() = runTest {
        ByIdsChunker.byIdsAvailable.set(false)

        val api = FakeV2CobranzaApi(
            // Digest mismatch para forzar el path /ids.
            pagosDigestResponses = listOf(
                DigestResponse(
                    count_activos = 2,
                    ids_xor = "30",
                    ids_sum = "30",
                    max_updated_at = null
                )
            ),
            saldosDigestResponses = listOf(
                DigestResponse(
                    count_activos = 0,
                    ids_xor = "0",
                    ids_sum = "0",
                    max_updated_at = null
                )
            ),
            pagoIdPages = listOf(IdsResponse(listOf(10, 20), false)),
            saldoIdPages = listOf(IdsResponse(emptyList(), false))
        )

        val outcome = newReconciler(api).reconcileNow()

        assertTrue(outcome is ReconcileOutcome.Ok)
        val ok = outcome as ReconcileOutcome.Ok
        assertEquals(0, ok.pagosPhantomsDeleted)
        assertEquals(2, ok.pagosExtrasOnServer)
        // pagosByIds NO debe haberse llamado.
        assertEquals(0, api.pagosByIdsCalled)
    }

    // ─── UUID rows must never crash the numeric reconcile ──────────────────

    /**
     * Bug: getActiveIDsByZona devuelve TODOS los ids de la zona, incluidos
     * los locales con ID=UUID (pagos cobrados offline aún no subidos, o
     * gemelos UUID pendientes de colapsar). El pre-check de digest hacía
     * `.map { it.toInt() }` sobre esa lista completa — cualquier UUID
     * lanzaba NumberFormatException y abortaba TODO el reconcile (incluido
     * saldos). El fix filtra con `.mapNotNull { it.toIntOrNull() }` para que
     * el digest se calcule solo sobre los ids numéricos.
     */
    @Test
    fun reconcilePreCheckIgnoraFilasUuidEnElDigestSinExplotar() = runTest {
        db.paymentDao().saveAll(
            listOf(
                samplePayment(10, 100),
                samplePayment(20, 200)
            )
        )
        // Fila UUID mezclada en la misma zona — antes del fix esto tronaba
        // el reconcile completo con NumberFormatException.
        db.paymentDao().saveAll(listOf(uuidPayment("uuid-pendiente-1", 300)))

        val pagoXor = xorOf(10, 20)
        val pagoSum = sumOf(10, 20)
        val api = FakeV2CobranzaApi(
            pagosDigestResponses = listOf(
                DigestResponse(
                    count_activos = 2,
                    ids_xor = pagoXor.toString(),
                    ids_sum = pagoSum.toString(),
                    max_updated_at = null
                )
            ),
            saldosDigestResponses = listOf(
                DigestResponse(
                    count_activos = 0,
                    ids_xor = "0",
                    ids_sum = "0",
                    max_updated_at = null
                )
            )
        )

        val outcome = newReconciler(api).reconcileNow()

        assertTrue("no debe lanzar ni devolver Error", outcome is ReconcileOutcome.Ok)
        val ok = outcome as ReconcileOutcome.Ok
        // Digest coincide (ignorando la fila UUID) → no se llama a /ids.
        assertEquals(0, ok.pagosPhantomsDeleted)
        assertEquals(0, api.listPagoIdsCalled)

        // La fila UUID sigue intacta — nunca se tocó.
        assertEquals(1, db.paymentDao().getPaymentsBySaleId(300).size)
    }

    /**
     * Con digest mismatch (fuerza el path /ids), la fila UUID local se
     * ignora al calcular localPagoIds (mapNotNull descarta el UUID) y por
     * lo tanto NUNCA se cuenta como phantom ni se borra — aunque el
     * servidor no la conozca. Los pagos numéricos se reconcilian
     * normalmente sin crashear.
     */
    @Test
    fun reconcileViaIdsIgnoraFilasUuidYNoLasBorraComoPhantom() = runTest {
        db.paymentDao().saveAll(listOf(samplePayment(301, 101)))
        db.paymentDao().saveAll(
            listOf(uuidPayment("uuid-pendiente-2", 999, guardado = false))
        )

        val api = FakeV2CobranzaApi(
            // Fuerza mismatch para caer al path /ids.
            pagosDigestResponses = listOf(
                DigestResponse(
                    count_activos = 99,
                    ids_xor = "0",
                    ids_sum = "0",
                    max_updated_at = null
                )
            ),
            saldosDigestResponses = listOf(
                DigestResponse(
                    count_activos = 0,
                    ids_xor = "0",
                    ids_sum = "0",
                    max_updated_at = null
                )
            ),
            // El servidor solo conoce el pago numérico 301.
            pagoIdPages = listOf(IdsResponse(listOf(301), false)),
            saldoIdPages = listOf(IdsResponse(emptyList(), false))
        )

        val outcome = newReconciler(api).reconcileNow()

        assertTrue("no debe lanzar NumberFormatException", outcome is ReconcileOutcome.Ok)
        val ok = outcome as ReconcileOutcome.Ok
        // 301 está en el servidor → no es phantom. La fila UUID se ignora
        // por completo (ni phantom ni extra).
        assertEquals(0, ok.pagosPhantomsDeleted)

        assertEquals(1, db.paymentDao().getPaymentsBySaleId(101).size)
        assertEquals(
            "la fila UUID pendiente jamás se borra como phantom",
            1,
            db.paymentDao().getPaymentsBySaleId(999).size
        )
    }

    /**
     * Prueba estructural del Mutex: la segunda llamada a reconcileNow() debe
     * BLOQUEAR mientras la primera tiene el lock. Para distinguir "se
     * serializaron" de "ambas corrieron y terminaron", el fake API bloquea
     * la primera llamada en una compuerta y verificamos que job2 no avanzó
     * antes de soltar la primera. La versión previa solo afirmaba
     * `callCount == 2` después de joinear las dos — eso prueba completitud,
     * no serialización.
     *
     * El bloqueo ocurre en pagosDigest (primer call del reconcileNow).
     */
    @Test
    fun mutexSerializesConcurrentCalls() = runTest {
        var callCount = 0
        val entered = CompletableDeferred<Unit>()
        val gate = CompletableDeferred<Unit>()

        val api = object : V2CobranzaApi {
            override suspend fun pagosDigest(zonaId: Int, desde: String?): DigestResponse {
                entered.complete(Unit)
                gate.await()
                callCount++
                // Return mismatch so it falls through to /ids.
                return DigestResponse(
                    count_activos = 99,
                    ids_xor = "0",
                    ids_sum = "0",
                    max_updated_at = null
                )
            }
            override suspend fun saldosDigest(zonaId: Int, desde: String?): DigestResponse =
                DigestResponse(
                    count_activos = 0,
                    ids_xor = "0",
                    ids_sum = "0",
                    max_updated_at = null
                )
            override suspend fun listPagoIds(
                zonaId: Int,
                after: Int,
                limit: Int,
                desde: String?
            ): IdsResponse = IdsResponse(emptyList(), false)
            override suspend fun listSaldoIds(
                zonaId: Int,
                after: Int,
                limit: Int,
                desde: String?
            ): IdsResponse = IdsResponse(emptyList(), false)
            override suspend fun syncVentas(
                zonaId: Int,
                cursor: String?,
                afterId: Int,
                limit: Int,
                desde: String?
            ): SyncVentasResponse {
                error("unreachable")
            }
            override suspend fun syncPagos(
                zonaId: Int,
                cursor: String?,
                afterId: Int,
                limit: Int,
                desde: String?
            ): SyncPagosResponse {
                error("unreachable")
            }
            override suspend fun pagosByIds(zonaId: Int, ids: String) = error("unreachable")
            override suspend fun saldosByIds(zonaId: Int, ids: String) = error("unreachable")
        }

        val reconciler = newReconciler(api)

        // Job 1 entra al Mutex y queda colgado en `gate.await()`.
        val job1 = launch { reconciler.reconcileNow() }
        entered.await()

        // Job 2 intenta entrar; debe quedar parqueado en mutex.lock().
        var job2EnteredApi = false
        val job2 = launch {
            reconciler.reconcileNow()
            // Si llegó aquí sin que liberáramos `gate`, callCount sería 1
            // por la suya en lugar de ambas — eso explota el assert de abajo.
            job2EnteredApi = true
        }
        yield() // dar oportunidad a job2 de avanzar

        assertFalse(
            "job2 debe estar bloqueado en el Mutex mientras job1 sostiene el lock",
            job2EnteredApi
        )
        assertEquals(
            "job1 todavía no completó su llamada al API porque sigue colgado en `gate`",
            0,
            callCount
        )

        // Soltamos a job1; job2 debe poder progresar después.
        gate.complete(Unit)
        job1.join()
        job2.join()

        assertEquals("ambas llamadas completaron al API", 2, callCount)
    }

    // ─── Self-heal del gemelo UUID (siempre corre, aun sin drift) ───────────

    /**
     * Bug: mergePagos solo colapsa el gemelo UUID de un tiro, en el instante
     * en que la numérica llega con `pago_recibido_id` y el UUID ya está
     * `GUARDADO_EN_MICROSIP=1`. Si esa ventana se pierde (carrera
     * pull-vs-markDone, o el `pago_recibido_id` llegó antes de que se
     * empezara a persistir), el gemelo queda huérfano para siempre — el
     * reconciliador ignoraba las filas UUID por completo. El self-heal corre
     * SIEMPRE al inicio de reconcileNow(), incluso cuando los digests
     * coinciden y /ids nunca se llama, para converger en ese caso también.
     */
    @Test
    fun selfHealColapsaGemeloUuidAunSinDriftEnDigest() = runTest {
        // Fila numérica ya confirmada, apuntando a su gemelo UUID original.
        db.paymentDao().saveAll(
            listOf(samplePayment(40, 100).copy(PAGO_RECIBIDO_ID = "uuid-twin"))
        )
        // El gemelo UUID, ya subido, quedó huérfano (el colapso de un tiro no ocurrió).
        db.paymentDao().saveAll(listOf(uuidPayment("uuid-twin", 100, guardado = true)))

        val pagoXor = xorOf(40)
        val pagoSum = sumOf(40)
        val api = FakeV2CobranzaApi(
            pagosDigestResponses = listOf(
                DigestResponse(
                    count_activos = 1,
                    ids_xor = pagoXor.toString(),
                    ids_sum = pagoSum.toString(),
                    max_updated_at = null
                )
            ),
            saldosDigestResponses = listOf(
                DigestResponse(
                    count_activos = 0,
                    ids_xor = "0",
                    ids_sum = "0",
                    max_updated_at = null
                )
            )
        )

        val outcome = newReconciler(api).reconcileNow()

        assertTrue(outcome is ReconcileOutcome.Ok)
        // Digest coincide (el UUID se ignora en el pre-check) → /ids nunca se llama;
        // el self-heal corrió de todas formas.
        assertEquals(0, api.listPagoIdsCalled)

        assertNull(
            "el gemelo UUID debe quedar colapsado aunque no hubiera drift de digest",
            db.paymentDao().getPaymentById("uuid-twin")
        )
        assertEquals(1, db.paymentDao().getPaymentsBySaleId(100).size)
    }
}
