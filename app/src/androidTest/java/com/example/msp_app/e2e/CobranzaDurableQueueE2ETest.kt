package com.example.msp_app.e2e

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.WorkInfo
import com.example.msp_app.core.common.sync.pendingwork.domain.models.SyncContext
import com.example.msp_app.core.common.sync.pendingwork.domain.models.SyncResult
import com.example.msp_app.core.database.entities.PaymentEntity
import com.example.msp_app.core.network.ConnectivityMonitor
import com.example.msp_app.core.sync.cobranza.CobranzaSyncManager
import com.example.msp_app.core.sync.cobranza.CobranzaWriteMutex
import com.example.msp_app.core.sync.cobranza.SyncOutcome
import com.example.msp_app.core.sync.cobranza.UserContext
import com.example.msp_app.core.sync.pendingwork.data.enqueuers.PaymentsWorkManagerEnqueuer
import com.example.msp_app.core.sync.pendingwork.data.synchronizers.PaymentsPendingSynchronizer
import com.example.msp_app.data.api.services.cobranza.DigestResponse
import com.example.msp_app.data.api.services.cobranza.IdsResponse
import com.example.msp_app.data.api.services.cobranza.PagoDto
import com.example.msp_app.data.api.services.cobranza.SyncPagosResponse
import com.example.msp_app.data.api.services.cobranza.SyncVentasResponse
import com.example.msp_app.data.api.services.cobranza.V2CobranzaApi
import com.example.msp_app.data.api.services.cobranza.VentaDto
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * B7 — highest-fidelity money-loss-scenario e2e without Firebase.
 *
 * Reproduces the regression [CobranzaSyncManager] guards against
 * (`cambioDeZonaPreservaPagosMultiCobrador` in the JVM suite, mirrored here
 * on-device): a cobrador changes zone mid-session while carrying pagos
 * registered offline. The zone-change cache cleanup must wipe the stale
 * cache and already-uploaded pagos WITHOUT ever touching pending
 * (`GUARDADO_EN_MICROSIP=false`) rows, regardless of which cobrador/zone
 * they belong to. It then drains the durable queue for real: the same
 * [PaymentsPendingSynchronizer] + [PaymentsWorkManagerEnqueuer] production
 * wiring feeds each survivor into the real WorkManager scheduler, which
 * uploads it to a [okhttp3.mockwebserver.MockWebServer] via the real
 * [com.example.msp_app.workers.PendingPaymentsWorker].
 */
@RunWith(AndroidJUnit4::class)
class CobranzaDurableQueueE2ETest : PagosE2ETestBase() {

    // ── fixtures ─────────────────────────────────────────────────────────

    private fun samplePayment(
        id: String,
        doctoCcAcrId: Int,
        cobrador: String,
        cobradorId: Int,
        zonaClienteId: Int,
        guardado: Boolean
    ) = PaymentEntity(
        ID = id,
        COBRADOR = cobrador,
        DOCTO_CC_ACR_ID = doctoCcAcrId,
        DOCTO_CC_ID = doctoCcAcrId + 1,
        FECHA_HORA_PAGO = "2026-07-18T11:00:00Z",
        GUARDADO_EN_MICROSIP = guardado,
        IMPORTE = 620.0,
        LAT = null,
        LNG = null,
        CLIENTE_ID = 8801,
        COBRADOR_ID = cobradorId,
        FORMA_COBRO_ID = 87327,
        ZONA_CLIENTE_ID = zonaClienteId,
        NOMBRE_CLIENTE = "Cliente de prueba $id"
    )

    private fun ventaDto(doctoCcId: Int, zonaId: Int) = VentaDto(
        docto_cc_id = doctoCcId,
        docto_pv_id = null,
        cliente_id = 8801,
        zona_cliente_id = zonaId,
        folio = "cv$doctoCcId",
        fecha_cargo = "2026-02-15T00:00:00Z",
        fecha_venta = null,
        precio_total = "5000.00",
        total_importe = "1000.00",
        impte_rest = "4000.00",
        saldo = "4000.00",
        num_pagos = 4,
        fecha_ult_pago = null,
        cargo_cancelado = false,
        updated_at = "2026-07-18T11:00:00Z",
        cliente_nombre = "Cliente de prueba",
        limite_credito = null,
        cliente_notas = "",
        cobrador_id = null,
        nombre_cobrador = "",
        zona_nombre = "R/$zonaId",
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

    /** Minimal fake — only `syncVentas`/`syncPagos` are exercised by `syncNow()`. */
    private class FakeV2CobranzaApi(
        private val ventaItem: VentaDto
    ) : V2CobranzaApi {
        override suspend fun syncVentas(
            zonaId: Int,
            cursor: String?,
            afterId: Int,
            limit: Int,
            desde: String?
        ) = SyncVentasResponse(
            items = if (cursor == null) listOf(ventaItem) else emptyList(),
            max_updated_at = ventaItem.updated_at,
            server_now = "2026-07-18T12:00:00Z",
            has_more = false
        )

        override suspend fun syncPagos(
            zonaId: Int,
            cursor: String?,
            afterId: Int,
            limit: Int,
            desde: String?
        ) = SyncPagosResponse(
            items = emptyList<PagoDto>(),
            max_updated_at = cursor.orEmpty(),
            server_now = "2026-07-18T12:00:00Z",
            has_more = false
        )

        override suspend fun pagosDigest(zonaId: Int, desde: String?) =
            DigestResponse(count_activos = 0, ids_xor = "0", ids_sum = "0", max_updated_at = null)

        override suspend fun saldosDigest(zonaId: Int, desde: String?) =
            DigestResponse(count_activos = 0, ids_xor = "0", ids_sum = "0", max_updated_at = null)

        override suspend fun listPagoIds(zonaId: Int, after: Int, limit: Int, desde: String?) =
            IdsResponse(ids = emptyList(), has_more = false)

        override suspend fun listSaldoIds(zonaId: Int, after: Int, limit: Int, desde: String?) =
            IdsResponse(ids = emptyList(), has_more = false)

        override suspend fun pagosByIds(zonaId: Int, ids: String) = emptyList<PagoDto>()

        override suspend fun saldosByIds(zonaId: Int, ids: String) = emptyList<VentaDto>()
    }

    private class FakeConnectivity(context: android.content.Context) : ConnectivityMonitor(
        context
    ) {
        override fun isNetworkAvailable(): Boolean = true
        override val isConnected: Flow<Boolean> = flowOf(true)
    }

    private fun newManager(api: V2CobranzaApi, zona: Int): CobranzaSyncManager =
        CobranzaSyncManager(
            api = api,
            db = db,
            saleDao = db.saleDao(),
            paymentDao = db.paymentDao(),
            syncStateDao = db.cobranzaSyncStateDao(),
            connectivity = FakeConnectivity(context),
            userContextFlow = MutableStateFlow(
                UserContext(zona = zona, fechaCargaInicial = null)
            ).asStateFlow(),
            cobranzaWriteMutex = CobranzaWriteMutex()
        )

    // ── the test ─────────────────────────────────────────────────────────

    @Test
    fun durableQueueSurvivesZoneChangeThenDrainsToServer() = runBlocking {
        // 1) Initial sync at zona 21 — establishes the sync-state baseline
        //    that the second sync (below) will detect as a zone change.
        val initialOutcome = newManager(
            FakeV2CobranzaApi(ventaDto(9001, zonaId = 21)),
            zona = 21
        ).syncNow()
        assertTrue(initialOutcome is SyncOutcome.Ok)
        assertTrue(db.saleDao().findByDoctoCcId(9001) != null)

        // 2) Seed the durable queue AFTER the baseline sync: two pending
        //    pagos from two different cobradores/zones (multi-cobrador
        //    device), plus one already-confirmed pago that must be
        //    discarded with the stale cache.
        val pendienteCobrador7 = samplePayment(
            id = "pendiente-cobrador-7",
            doctoCcAcrId = 9101,
            cobrador = "Ricardo Flores Mendoza",
            cobradorId = 7,
            zonaClienteId = 21,
            guardado = false
        )
        val pendienteCobrador9 = samplePayment(
            id = "pendiente-cobrador-9",
            doctoCcAcrId = 9102,
            cobrador = "Sandra Patricia Gomez Ruiz",
            cobradorId = 9,
            zonaClienteId = 30,
            guardado = false
        )
        val yaSubido = samplePayment(
            id = "ya-subido-multi",
            doctoCcAcrId = 9103,
            cobrador = "Carlos Ivan Mendez Soto",
            cobradorId = 3,
            zonaClienteId = 21,
            guardado = true
        )
        db.paymentDao().saveAll(listOf(pendienteCobrador7, pendienteCobrador9, yaSubido))

        // 3) The cobrador's zone changes to 42 — this must trigger the
        //    zone-change cleanup inside syncNow().
        val zoneChangeOutcome = newManager(
            FakeV2CobranzaApi(ventaDto(9002, zonaId = 42)),
            zona = 42
        ).syncNow()
        assertTrue(zoneChangeOutcome is SyncOutcome.Ok)

        // Stale zona-21 sale is gone; the new zona-42 sale is present.
        assertNull(db.saleDao().findByDoctoCcId(9001))
        assertTrue(db.saleDao().findByDoctoCcId(9002) != null)

        // The confirmed pago is discarded with the stale cache...
        assertTrue(db.paymentDao().getPaymentsBySaleId(9103).isEmpty())

        // ...but BOTH pending pagos — from two different cobradores/zones —
        // survive intact, with their attribution untouched. This is the
        // money-loss invariant: unsynced work is never lost on a zone
        // change, no matter how many cobradores share the device/cache.
        val survivors = db.paymentDao().getPendingPayments()
        assertEquals(2, survivors.size)
        val byId = survivors.associateBy { it.ID }

        val survivor7 = byId.getValue("pendiente-cobrador-7")
        assertEquals(7, survivor7.COBRADOR_ID)
        assertEquals(21, survivor7.ZONA_CLIENTE_ID)
        assertEquals("Ricardo Flores Mendoza", survivor7.COBRADOR)
        assertFalse(survivor7.GUARDADO_EN_MICROSIP)

        val survivor9 = byId.getValue("pendiente-cobrador-9")
        assertEquals(9, survivor9.COBRADOR_ID)
        assertEquals(30, survivor9.ZONA_CLIENTE_ID)
        assertEquals("Sandra Patricia Gomez Ruiz", survivor9.COBRADOR)
        assertFalse(survivor9.GUARDADO_EN_MICROSIP)

        // 4) Drain the durable queue through the REAL production wiring:
        //    PaymentsPendingSynchronizer -> PaymentsWorkManagerEnqueuer ->
        //    WorkManager -> PendingPaymentsWorker -> V2PaymentsApi.
        mockWebServer.enqueue(MockResponse().setResponseCode(200).setBody("""{"estado":"ok"}"""))
        mockWebServer.enqueue(MockResponse().setResponseCode(200).setBody("""{"estado":"ok"}"""))

        val synchronizer = PaymentsPendingSynchronizer(
            fetchPending = { db.paymentDao().getPendingPayments() },
            enqueuer = PaymentsWorkManagerEnqueuer(context)
        )
        val syncResult = synchronizer.sync(
            SyncContext(userId = "cobrador-multi-1", userEmail = "ricardo.flores@muebleriamsp.mx")
        )
        assertTrue(syncResult is SyncResult.Enqueued)
        assertEquals(2, (syncResult as SyncResult.Enqueued).workRequestCount)

        // Trigger constraints for each enqueued work so the real worker runs.
        val survivorIds = listOf("pendiente-cobrador-7", "pendiente-cobrador-9")
        survivorIds.forEach { id -> testDriver.setAllConstraintsMet(currentWorkId(id)) }

        survivorIds.forEach { id ->
            val info = awaitWorkInfo(id) {
                it.state == WorkInfo.State.SUCCEEDED || it.state == WorkInfo.State.FAILED
            }
            assertEquals("payment $id must finish SUCCEEDED", WorkInfo.State.SUCCEEDED, info.state)
        }

        assertEquals(2, mockWebServer.requestCount)

        val finalPending = db.paymentDao().getPendingPayments()
        assertTrue("no survivor should remain pending after the drain", finalPending.isEmpty())
        assertTrue(db.paymentDao().getPaymentById("pendiente-cobrador-7")!!.GUARDADO_EN_MICROSIP)
        assertTrue(db.paymentDao().getPaymentById("pendiente-cobrador-9")!!.GUARDADO_EN_MICROSIP)
    }
}
