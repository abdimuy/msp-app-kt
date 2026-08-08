package com.example.msp_app.core.sync.cobranza

import androidx.test.core.app.ApplicationProvider
import com.example.msp_app.core.common.sync.pendingwork.domain.models.SyncContext
import com.example.msp_app.core.common.sync.pendingwork.domain.models.SyncResult
import com.example.msp_app.core.common.sync.pendingwork.domain.ports.PaymentsWorkEnqueuer
import com.example.msp_app.core.network.ConnectivityMonitor
import com.example.msp_app.core.sync.pendingwork.data.synchronizers.PaymentsPendingSynchronizer
import com.example.msp_app.data.api.services.cobranza.DigestResponse
import com.example.msp_app.data.api.services.cobranza.PagoDto
import com.example.msp_app.data.api.services.cobranza.SyncPagosResponse
import com.example.msp_app.data.api.services.cobranza.SyncVentasResponse
import com.example.msp_app.data.api.services.cobranza.V2CobranzaApi
import com.example.msp_app.data.api.services.cobranza.VentaDto
import com.example.msp_app.data.local.entities.PaymentEntity
import com.example.msp_app.`test-fixtures`.RoomTestBase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Verifies the hand-off between the zone-change cleanup fix (B1/B2) and the
 * pending-work re-enqueue pipeline: once `syncNow()` finishes an online
 * zone-change and pending payments survive, [PaymentsPendingSynchronizer]
 * must be able to drain them all — this is what "defuses" the money-loss
 * race for real, since surviving in Room is worthless if nothing re-enqueues
 * the upload work afterward.
 */
class CobranzaSyncReenqueueTest : RoomTestBase() {

    private class FakeConnectivity(private val online: Boolean) :
        ConnectivityMonitor(ApplicationProvider.getApplicationContext()) {
        override fun isNetworkAvailable(): Boolean = online
        override val isConnected: Flow<Boolean> = flowOf(online)
    }

    private fun newManager(api: V2CobranzaApi, zona: Int): CobranzaSyncManager =
        CobranzaSyncManager(
            api = api,
            db = db,
            saleDao = db.saleDao(),
            paymentDao = db.paymentDao(),
            syncStateDao = db.cobranzaSyncStateDao(),
            connectivity = FakeConnectivity(online = true),
            userContextFlow = MutableStateFlow(
                UserContext(zona = zona, fechaCargaInicial = null)
            ).asStateFlow(),
            cobranzaWriteMutex = CobranzaWriteMutex()
        )

    private fun ventaDto(doctoCcId: Int, zonaId: Int) = VentaDto(
        docto_cc_id = doctoCcId,
        docto_pv_id = null,
        cliente_id = 99,
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
        updated_at = "2026-07-20T10:00:00Z",
        cliente_nombre = "CLIENTE DE PRUEBA",
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

    private fun fakeApi(ventaZona: Int, doctoCcId: Int): V2CobranzaApi = object : V2CobranzaApi {
        override suspend fun syncVentas(
            zonaId: Int,
            cursor: String?,
            afterId: Int,
            limit: Int,
            desde: String?
        ): SyncVentasResponse = SyncVentasResponse(
            items = listOf(ventaDto(doctoCcId, ventaZona)),
            max_updated_at = "2026-07-20T10:00:00Z",
            server_now = "2026-07-20T10:00:01Z",
            has_more = false
        )

        override suspend fun syncPagos(
            zonaId: Int,
            cursor: String?,
            afterId: Int,
            limit: Int,
            desde: String?
        ): SyncPagosResponse = SyncPagosResponse(
            items = emptyList(),
            max_updated_at = cursor.orEmpty(),
            server_now = "2026-07-20T10:00:01Z",
            has_more = false
        )

        override suspend fun pagosDigest(zonaId: Int, desde: String?): DigestResponse =
            DigestResponse(count_activos = 0, ids_xor = "0", ids_sum = "0", max_updated_at = null)

        override suspend fun saldosDigest(zonaId: Int, desde: String?): DigestResponse =
            DigestResponse(count_activos = 0, ids_xor = "0", ids_sum = "0", max_updated_at = null)

        override suspend fun listPagoIds(zonaId: Int, after: Int, limit: Int, desde: String?) =
            error("not used in this test")

        override suspend fun listSaldoIds(zonaId: Int, after: Int, limit: Int, desde: String?) =
            error("not used in this test")

        override suspend fun pagosByIds(zonaId: Int, ids: String): List<PagoDto> =
            error("not used in this test")

        override suspend fun saldosByIds(zonaId: Int, ids: String): List<VentaDto> =
            error("not used in this test")
    }

    private fun samplePayment(id: String, doctoCcId: Int, cobradorId: Int, zonaId: Int) =
        PaymentEntity(
            ID = id,
            COBRADOR = "Cobrador $cobradorId",
            DOCTO_CC_ACR_ID = doctoCcId,
            DOCTO_CC_ID = doctoCcId + 1,
            FECHA_HORA_PAGO = "2026-07-25T00:00:00Z",
            GUARDADO_EN_MICROSIP = false,
            IMPORTE = 250.0,
            LAT = null,
            LNG = null,
            CLIENTE_ID = 99,
            COBRADOR_ID = cobradorId,
            FORMA_COBRO_ID = 1,
            ZONA_CLIENTE_ID = zonaId,
            NOMBRE_CLIENTE = "Cliente de prueba"
        )

    private class RecordingEnqueuer : PaymentsWorkEnqueuer {
        val enqueuedIds: MutableList<String> = mutableListOf()

        override fun enqueue(paymentId: String, replace: Boolean) {
            enqueuedIds += paymentId
        }
    }

    @Test
    fun pendientesSobrevivientesTrasCambioDeZonaSeReencolan() = runTest {
        // Sync inicial en zona 21 — deja el state en zona 21.
        newManager(fakeApi(ventaZona = 21, doctoCcId = 601), zona = 21).syncNow()

        // Dos pagos pendientes (distinta atribución) más uno ya subido que
        // no debe sobrevivir el cleanup y por lo tanto tampoco debe
        // re-encolarse.
        val pendienteUno = samplePayment("pendiente-reenqueue-1", 602, cobradorId = 7, zonaId = 21)
        val pendienteDos = samplePayment("pendiente-reenqueue-2", 603, cobradorId = 9, zonaId = 30)
        val yaSubido = samplePayment("ya-subido-reenqueue", 604, cobradorId = 3, zonaId = 21)
            .copy(GUARDADO_EN_MICROSIP = true)
        db.paymentDao().saveAll(listOf(pendienteUno, pendienteDos, yaSubido))

        // Cambia de zona ONLINE → dispara el cleanup, conservando los
        // pendientes.
        val outcome = newManager(fakeApi(ventaZona = 42, doctoCcId = 605), zona = 42).syncNow()
        assertTrue(outcome is SyncOutcome.Ok)

        val survivors = db.paymentDao().getPendingPayments()
        assertEquals(2, survivors.size)
        val survivorIds = survivors.map { it.ID }.toSet()
        assertEquals(setOf("pendiente-reenqueue-1", "pendiente-reenqueue-2"), survivorIds)

        // Feed the survivors into the pending-work pipeline exactly as the
        // real synchronizer would: fetchPending reads straight from Room.
        val enqueuer = RecordingEnqueuer()
        val synchronizer = PaymentsPendingSynchronizer(
            fetchPending = { db.paymentDao().getPendingPayments() },
            enqueuer = enqueuer
        )

        val syncResult = synchronizer.sync(
            SyncContext(userId = "u1", userEmail = "u1@muebleriamsp.mx")
        )

        assertEquals(SyncResult.Enqueued(itemCount = 2, workRequestCount = 2), syncResult)
        assertEquals(survivorIds, enqueuer.enqueuedIds.toSet())
        // The uploaded payment must never reach the enqueuer — it was never
        // pending, and the cleanup already dropped it.
        assertTrue("ya-subido-reenqueue" !in enqueuer.enqueuedIds)
    }
}
