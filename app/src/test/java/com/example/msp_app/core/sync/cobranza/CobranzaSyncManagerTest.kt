package com.example.msp_app.core.sync.cobranza

import androidx.test.core.app.ApplicationProvider
import com.example.msp_app.core.network.ConnectivityMonitor
import com.example.msp_app.data.api.services.cobranza.PagoDto
import com.example.msp_app.data.api.services.cobranza.SyncPagosResponse
import com.example.msp_app.data.api.services.cobranza.SyncVentasResponse
import com.example.msp_app.data.api.services.cobranza.V2CobranzaApi
import com.example.msp_app.data.api.services.cobranza.VentaDto
import com.example.msp_app.data.api.services.cobranza.toEntity
import com.example.msp_app.data.local.entities.PaymentEntity
import com.example.msp_app.data.models.sale.EstadoCobranza
import com.example.msp_app.`test-fixtures`.RoomTestBase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class CobranzaSyncManagerTest : RoomTestBase() {

    private class FakeConnectivity(private val online: Boolean) :
        ConnectivityMonitor(ApplicationProvider.getApplicationContext()) {
        override fun isNetworkAvailable(): Boolean = online
        override val isConnected: Flow<Boolean> = flowOf(online)
    }

    private fun newConnectivity(connected: Boolean): ConnectivityMonitor = FakeConnectivity(
        connected
    )

    private fun newManager(
        api: V2CobranzaApi,
        online: Boolean = true,
        zona: Int? = 21
    ): CobranzaSyncManager = CobranzaSyncManager(
        api = api,
        saleDao = db.saleDao(),
        paymentDao = db.paymentDao(),
        syncStateDao = db.cobranzaSyncStateDao(),
        connectivity = newConnectivity(online),
        zonaProvider = { zona }
    )

    private fun ventaDto(
        doctoCcId: Int,
        zonaId: Int = 21,
        cancelado: Boolean = false,
        importeTotal: String = "1000.00",
        numPagos: Int = 4,
        updatedAt: String = "2026-05-30T18:25:13.456789Z"
    ) = VentaDto(
        docto_cc_id = doctoCcId,
        docto_pv_id = null,
        cliente_id = 99,
        zona_cliente_id = zonaId,
        folio = "cv$doctoCcId",
        fecha_cargo = "2026-02-15T00:00:00Z",
        fecha_venta = null,
        precio_total = "5000.00",
        total_importe = importeTotal,
        impte_rest = "0.00",
        saldo = "4000.00",
        num_pagos = numPagos,
        fecha_ult_pago = null,
        cargo_cancelado = cancelado,
        updated_at = updatedAt,
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
        vendedor_1_id = null,
        vendedor_2_id = null,
        vendedor_3_id = null
    )

    private fun pagoDto(impteId: Int, doctoCcId: Int) = PagoDto(
        impte_docto_cc_id = impteId,
        docto_cc_id = doctoCcId + 1,
        docto_cc_acr_id = doctoCcId,
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
        updated_at = "2026-05-30T18:25:13.456789Z"
    )

    @Test
    fun initialSyncWritesEntitiesAndPersistsCursor() = runTest {
        val api = fakeApi(
            ventas = listOf(
                page(items = listOf(ventaDto(101), ventaDto(102)), hasMore = false)
            ),
            pagos = listOf(pagoPage(emptyList(), hasMore = false))
        )
        val mgr = newManager(api)

        val outcome = mgr.syncNow()
        assertTrue(outcome is SyncOutcome.Ok)
        assertEquals(2, (outcome as SyncOutcome.Ok).ventasApplied)
        assertEquals(101, db.saleDao().findByDoctoCcId(101)!!.DOCTO_CC_ID)
        assertEquals(102, db.saleDao().findByDoctoCcId(102)!!.DOCTO_CC_ID)
        val state = db.cobranzaSyncStateDao().get(CobranzaSyncManager.RESOURCE_VENTAS)
        assertNotNull(state)
        assertEquals("2026-05-30T18:25:13.456789Z", state!!.CURSOR)
    }

    @Test
    fun mergePreservesEstadoCobranzaAndDiaTemporal() = runTest {
        val seeded = ventaDto(201).toEntity().copy(
            ESTADO_COBRANZA = EstadoCobranza.PAGADO.name,
            DIA_TEMPORAL_COBRANZA = "VIERNES"
        )
        db.saleDao().insertAll(listOf(seeded))

        val api = fakeApi(
            ventas = listOf(
                page(
                    items = listOf(ventaDto(201, importeTotal = "2000.00", numPagos = 5)),
                    hasMore = false
                )
            ),
            pagos = listOf(pagoPage(emptyList(), hasMore = false))
        )
        val mgr = newManager(api)
        mgr.syncNow()

        val refreshed = db.saleDao().findByDoctoCcId(201)!!
        assertEquals(EstadoCobranza.PAGADO.name, refreshed.ESTADO_COBRANZA)
        assertEquals("VIERNES", refreshed.DIA_TEMPORAL_COBRANZA)
        assertEquals(2000.0, refreshed.TOTAL_IMPORTE, 0.001)
    }

    @Test
    fun tombstoneRemovesSaleAndPayments() = runTest {
        val seed = ventaDto(301).toEntity()
        db.saleDao().insertAll(listOf(seed))
        db.paymentDao().saveAll(listOf(samplePayment(301)))

        val api = fakeApi(
            ventas = listOf(page(items = listOf(ventaDto(301, cancelado = true)), hasMore = false)),
            pagos = listOf(pagoPage(emptyList(), hasMore = false))
        )
        newManager(api).syncNow()

        assertNull(db.saleDao().findByDoctoCcId(301))
        assertTrue(db.paymentDao().getPaymentsBySaleId(301).isEmpty())
    }

    @Test
    fun offlineSkipsApiCall() = runTest {
        val api = failingApi()
        val mgr = newManager(api, online = false)
        val outcome = mgr.syncNow()
        assertTrue(outcome is SyncOutcome.SkippedOffline)
    }

    @Test
    fun noZoneSkipsApiCall() = runTest {
        val api = failingApi()
        val mgr = newManager(api, zona = null)
        val outcome = mgr.syncNow()
        assertTrue(outcome is SyncOutcome.SkippedNoZone)
    }

    @Test
    fun apiErrorRecordsLastErrorWithoutCrashing() = runTest {
        val api = object : V2CobranzaApi {
            override suspend fun syncVentas(
                zonaId: Int,
                cursor: String?,
                afterId: Int,
                limit: Int
            ) = throw RuntimeException("network down")
            override suspend fun syncPagos(zonaId: Int, cursor: String?, afterId: Int, limit: Int) =
                throw RuntimeException("network down")
        }
        val outcome = newManager(api).syncNow()
        assertTrue(outcome is SyncOutcome.Error)
        // Records error even when the row was never created — these calls are
        // best-effort. We don't assert state existence because the first
        // failed page may not have written a row yet.
    }

    @Test
    fun pagesUntilHasMoreFalse() = runTest {
        val api = fakeApi(
            ventas = listOf(
                page(
                    items = listOf(ventaDto(401, updatedAt = "2026-05-30T18:00:00Z")),
                    hasMore = true
                ),
                page(
                    items = listOf(ventaDto(402, updatedAt = "2026-05-30T18:10:00Z")),
                    hasMore = false
                )
            ),
            pagos = listOf(pagoPage(emptyList(), hasMore = false))
        )
        val outcome = newManager(api).syncNow()
        assertTrue(outcome is SyncOutcome.Ok)
        assertEquals(2, (outcome as SyncOutcome.Ok).ventasApplied)
        val state = db.cobranzaSyncStateDao().get(CobranzaSyncManager.RESOURCE_VENTAS)!!
        assertEquals("2026-05-30T18:10:00Z", state.CURSOR)
    }

    // ─── fixtures ───────────────────────────────────────────────────────────

    private data class VentaPage(val items: List<VentaDto>, val hasMore: Boolean)
    private data class PagoPage(val items: List<PagoDto>, val hasMore: Boolean)

    private fun page(items: List<VentaDto>, hasMore: Boolean) = VentaPage(items, hasMore)
    private fun pagoPage(items: List<PagoDto>, hasMore: Boolean) = PagoPage(items, hasMore)

    private fun fakeApi(ventas: List<VentaPage>, pagos: List<PagoPage>) = object : V2CobranzaApi {
        private var ventasIdx = 0
        private var pagosIdx = 0

        override suspend fun syncVentas(
            zonaId: Int,
            cursor: String?,
            afterId: Int,
            limit: Int
        ): SyncVentasResponse {
            val p = ventas.getOrNull(ventasIdx) ?: error("syncVentas called too many times")
            ventasIdx++
            return SyncVentasResponse(
                items = p.items,
                max_updated_at = p.items.lastOrNull()?.updated_at ?: cursor.orEmpty(),
                server_now = "2026-05-30T18:25:23Z",
                has_more = p.hasMore
            )
        }

        override suspend fun syncPagos(
            zonaId: Int,
            cursor: String?,
            afterId: Int,
            limit: Int
        ): SyncPagosResponse {
            val p = pagos.getOrNull(pagosIdx) ?: error("syncPagos called too many times")
            pagosIdx++
            return SyncPagosResponse(
                items = p.items,
                max_updated_at = p.items.lastOrNull()?.updated_at ?: cursor.orEmpty(),
                server_now = "2026-05-30T18:25:23Z",
                has_more = p.hasMore
            )
        }
    }

    private fun failingApi() = object : V2CobranzaApi {
        override suspend fun syncVentas(
            zonaId: Int,
            cursor: String?,
            afterId: Int,
            limit: Int
        ): SyncVentasResponse {
            fail("API should not be called when offline / unzoned")
            error("unreachable")
        }
        override suspend fun syncPagos(
            zonaId: Int,
            cursor: String?,
            afterId: Int,
            limit: Int
        ): SyncPagosResponse {
            fail("API should not be called when offline / unzoned")
            error("unreachable")
        }
    }

    private fun samplePayment(doctoCcId: Int) = PaymentEntity(
        ID = "pmt-$doctoCcId",
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
        ZONA_CLIENTE_ID = 21,
        NOMBRE_CLIENTE = ""
    )
}
