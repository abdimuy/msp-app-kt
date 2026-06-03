package com.example.msp_app.core.sync.cobranza

import androidx.test.core.app.ApplicationProvider
import com.example.msp_app.core.network.ConnectivityMonitor
import com.example.msp_app.data.api.services.cobranza.IdsResponse
import com.example.msp_app.data.api.services.cobranza.SyncPagosResponse
import com.example.msp_app.data.api.services.cobranza.SyncVentasResponse
import com.example.msp_app.data.api.services.cobranza.V2CobranzaApi
import com.example.msp_app.data.api.services.cobranza.VentaDto
import com.example.msp_app.data.api.services.cobranza.toEntity
import com.example.msp_app.data.local.entities.PaymentEntity
import com.example.msp_app.`test-fixtures`.RoomTestBase
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class CobranzaReconcilerTest : RoomTestBase() {

    // ─── Fakes ──────────────────────────────────────────────────────────────

    private class FakeConnectivity(private val online: Boolean) :
        ConnectivityMonitor(ApplicationProvider.getApplicationContext()) {
        override fun isNetworkAvailable(): Boolean = online
        override val isConnected: Flow<Boolean> = flowOf(online)
    }

    /**
     * Simple fake API for reconcile tests. Holds lists of pages per endpoint;
     * each call advances an index.
     */
    private inner class FakeV2CobranzaApi(
        var pagoIdPages: List<IdsResponse> = listOf(IdsResponse(emptyList(), false)),
        var saldoIdPages: List<IdsResponse> = listOf(IdsResponse(emptyList(), false))
    ) : V2CobranzaApi {
        private var pagoIdx = 0
        private var saldoIdx = 0

        var listPagoIdsCalled = 0
        var listSaldoIdsCalled = 0

        override suspend fun listPagoIds(zonaId: Int, after: Int, limit: Int): IdsResponse {
            listPagoIdsCalled++
            val page = pagoIdPages.getOrNull(pagoIdx)
                ?: error("listPagoIds called too many times (idx=$pagoIdx)")
            pagoIdx++
            return page
        }

        override suspend fun listSaldoIds(zonaId: Int, after: Int, limit: Int): IdsResponse {
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
    }

    private fun newReconciler(
        api: V2CobranzaApi,
        online: Boolean = true,
        zona: Int? = 21
    ): CobranzaReconciler = CobranzaReconciler(
        api = api,
        saleDao = db.saleDao(),
        paymentDao = db.paymentDao(),
        connectivity = FakeConnectivity(online),
        userContextFlow = MutableStateFlow(
            zona?.let { UserContext(zona = it, fechaCargaInicial = null) }
        ).asStateFlow()
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

    // ─── Tests ──────────────────────────────────────────────────────────────

    /**
     * Happy path: local set equals server set — no phantoms, no extras.
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
            override suspend fun listPagoIds(zonaId: Int, after: Int, limit: Int): IdsResponse {
                fail("API must not be called when offline")
                error("unreachable")
            }
            override suspend fun listSaldoIds(zonaId: Int, after: Int, limit: Int): IdsResponse {
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
            override suspend fun listPagoIds(zonaId: Int, after: Int, limit: Int): IdsResponse {
                fail("API must not be called without zone")
                error("unreachable")
            }
            override suspend fun listSaldoIds(zonaId: Int, after: Int, limit: Int): IdsResponse {
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
        }
        val outcome = newReconciler(api, zona = null).reconcileNow()
        assertTrue(outcome is ReconcileOutcome.SkippedNoZone)
    }

    /**
     * Network error during /ids fetch → returns Error(cause), no rows deleted.
     */
    @Test
    fun networkErrorReturnsErrorOutcomeNoRowsDeleted() = runTest {
        db.saleDao().insertAll(listOf(sampleVenta(601).toEntity()))

        val api = object : V2CobranzaApi {
            override suspend fun listPagoIds(zonaId: Int, after: Int, limit: Int): IdsResponse =
                throw RuntimeException("network down")
            override suspend fun listSaldoIds(zonaId: Int, after: Int, limit: Int): IdsResponse =
                throw RuntimeException("network down")
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
        }
        val outcome = newReconciler(api).reconcileNow()

        assertTrue(outcome is ReconcileOutcome.Error)
        assertEquals("network down", (outcome as ReconcileOutcome.Error).cause.message)
        // Seeded venta must still be present.
        assertTrue(db.saleDao().findByDoctoCcId(601) != null)
    }

    /**
     * Prueba estructural del Mutex: la segunda llamada a reconcileNow() debe
     * BLOQUEAR mientras la primera tiene el lock. Para distinguir "se
     * serializaron" de "ambas corrieron y terminaron", el fake API bloquea
     * la primera llamada en una compuerta y verificamos que job2 no avanzó
     * antes de soltar la primera. La versión previa solo afirmaba
     * `callCount == 2` después de joinear las dos — eso prueba completitud,
     * no serialización.
     */
    @Test
    fun mutexSerializesConcurrentCalls() = runTest {
        var callCount = 0
        val entered = CompletableDeferred<Unit>()
        val gate = CompletableDeferred<Unit>()

        val api = object : V2CobranzaApi {
            override suspend fun listPagoIds(zonaId: Int, after: Int, limit: Int): IdsResponse {
                entered.complete(Unit)
                gate.await()
                callCount++
                return IdsResponse(emptyList(), false)
            }
            override suspend fun listSaldoIds(zonaId: Int, after: Int, limit: Int): IdsResponse =
                IdsResponse(emptyList(), false)
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
}
