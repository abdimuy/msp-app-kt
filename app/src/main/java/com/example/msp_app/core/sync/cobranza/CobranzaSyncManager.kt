package com.example.msp_app.core.sync.cobranza

import android.util.Log
import com.example.msp_app.core.network.ConnectivityMonitor
import com.example.msp_app.data.api.services.cobranza.PagoDto
import com.example.msp_app.data.api.services.cobranza.V2CobranzaApi
import com.example.msp_app.data.api.services.cobranza.VentaDto
import com.example.msp_app.data.api.services.cobranza.toEntity
import com.example.msp_app.data.local.dao.cobranzasync.CobranzaSyncStateDao
import com.example.msp_app.data.local.dao.payment.PaymentDao
import com.example.msp_app.data.local.dao.sale.SaleDao
import com.example.msp_app.data.local.entities.CobranzaSyncStateEntity
import com.example.msp_app.data.local.entities.PaymentEntity
import java.time.Instant
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Drives the per-zone incremental sync of cobranza data from the v2 Go
 * backend into the local Room store. One instance lives at the
 * application scope; the navigation graph mounts a
 * [CobranzaSyncObserver] which calls [start] / [stop] in tandem with the
 * lifecycle of the authenticated UI.
 *
 * The manager polls every [TICK_INTERVAL_MILLIS] and additionally fires a
 * sync immediately when connectivity returns after a drop. Both behaviors
 * share a [Mutex] so concurrent syncs are impossible.
 */
class CobranzaSyncManager(
    private val api: V2CobranzaApi,
    private val saleDao: SaleDao,
    private val paymentDao: PaymentDao,
    private val syncStateDao: CobranzaSyncStateDao,
    private val connectivity: ConnectivityMonitor,
    private val zonaProvider: suspend () -> Int?
) {

    private val mutex = Mutex()
    private var tickJob: Job? = null
    private var connectivityJob: Job? = null

    fun start(scope: CoroutineScope) {
        stop()
        tickJob = scope.launch(Dispatchers.IO) { tickLoop() }
        connectivityJob = scope.launch(Dispatchers.IO) { connectivityObserver() }
    }

    fun stop() {
        tickJob?.cancel()
        tickJob = null
        connectivityJob?.cancel()
        connectivityJob = null
    }

    suspend fun syncNow(): SyncOutcome {
        return mutex.withLock {
            val zona = zonaProvider() ?: return SyncOutcome.SkippedNoZone
            if (!connectivity.isNetworkAvailable()) return SyncOutcome.SkippedOffline
            try {
                val ventas = syncResource(RESOURCE_VENTAS, zona) { cursor ->
                    val response = api.syncVentas(zona, cursor)
                    SyncPage(
                        cursor = response.max_updated_at,
                        hasMore = response.has_more,
                        afterId = response.items.lastOrNull()?.docto_cc_id ?: 0,
                        size = response.items.size,
                        apply = { mergeVentas(response.items) }
                    )
                }
                val pagos = syncResource(RESOURCE_PAGOS, zona) { cursor ->
                    val response = api.syncPagos(zona, cursor)
                    SyncPage(
                        cursor = response.max_updated_at,
                        hasMore = response.has_more,
                        afterId = response.items.lastOrNull()?.impte_docto_cc_id ?: 0,
                        size = response.items.size,
                        apply = { mergePagos(response.items) }
                    )
                }
                SyncOutcome.Ok(ventasApplied = ventas, pagosApplied = pagos)
            } catch (e: Exception) {
                Log.w(TAG, "sync failed: ${e.message}", e)
                runCatching {
                    syncStateDao.recordError(
                        RESOURCE_VENTAS,
                        e.message.orEmpty(),
                        Instant.now().toString()
                    )
                    syncStateDao.recordError(
                        RESOURCE_PAGOS,
                        e.message.orEmpty(),
                        Instant.now().toString()
                    )
                }
                SyncOutcome.Error(e)
            }
        }
    }

    private suspend fun syncResource(
        resource: String,
        zona: Int,
        fetchPage: suspend (cursor: String?) -> SyncPage
    ): Int {
        var applied = 0
        var cursor: String? = syncStateDao.get(resource)?.CURSOR
        while (true) {
            val page = fetchPage(cursor)
            page.apply.invoke()
            applied += page.size
            cursor = page.cursor
            syncStateDao.upsert(
                CobranzaSyncStateEntity(
                    RESOURCE = resource,
                    ZONA_CLIENTE_ID = zona,
                    CURSOR = cursor,
                    LAST_SYNCED_AT = Instant.now().toString(),
                    LAST_ERROR = null
                )
            )
            if (!page.hasMore) break
        }
        return applied
    }

    /**
     * Merge contract: tombstones delete; otherwise the row from the wire
     * overwrites server-owned fields while preserving the cobrador's local
     * status (`ESTADO_COBRANZA`, `DIA_TEMPORAL_COBRANZA`).
     */
    private suspend fun mergeVentas(items: List<VentaDto>) {
        for (dto in items) {
            if (dto.cargo_cancelado) {
                saleDao.deleteByDoctoCcId(dto.docto_cc_id)
                paymentDao.deleteByDoctoCcAcrId(dto.docto_cc_id)
                continue
            }
            val existing = saleDao.findByDoctoCcId(dto.docto_cc_id)
            val incoming = dto.toEntity()
            val merged = if (existing == null) {
                incoming
            } else {
                incoming.copy(
                    ESTADO_COBRANZA = existing.ESTADO_COBRANZA,
                    DIA_TEMPORAL_COBRANZA = existing.DIA_TEMPORAL_COBRANZA
                )
            }
            saleDao.insertAll(listOf(merged))
        }
    }

    private suspend fun mergePagos(items: List<PagoDto>) {
        if (items.isEmpty()) return
        val entities: List<PaymentEntity> = items.map { it.toEntity() }
        paymentDao.saveAll(entities)
    }

    private suspend fun tickLoop() = coroutineScope {
        while (currentCoroutineContext()[Job]?.isActive == true) {
            try {
                syncNow()
            } catch (e: Exception) {
                Log.w(TAG, "tick failed: ${e.message}")
            }
            delay(TICK_INTERVAL_MILLIS)
        }
    }

    private suspend fun connectivityObserver() {
        // Skip the initial replay value emitted by ConnectivityMonitor on
        // subscribe — the tick loop is already racing the first sync.
        connectivity.isConnected
            .drop(1)
            .onEach { online -> if (online) syncNow() }
            .collect()
    }

    companion object {
        const val RESOURCE_VENTAS = "ventas"
        const val RESOURCE_PAGOS = "pagos"
        const val TICK_INTERVAL_MILLIS = 30_000L
        private const val TAG = "CobranzaSyncManager"
    }
}

/**
 * Per-resource page descriptor. `apply` is invoked once `cursor` is the
 * server's `max_updated_at`, allowing the caller to advance even on the
 * empty page so we don't replay history forever.
 */
private data class SyncPage(
    val cursor: String,
    val hasMore: Boolean,
    val afterId: Int,
    val size: Int,
    val apply: suspend () -> Unit
)

sealed class SyncOutcome {
    object SkippedOffline : SyncOutcome()
    object SkippedNoZone : SyncOutcome()
    data class Ok(val ventasApplied: Int, val pagosApplied: Int) : SyncOutcome()
    data class Error(val cause: Throwable) : SyncOutcome()
}
