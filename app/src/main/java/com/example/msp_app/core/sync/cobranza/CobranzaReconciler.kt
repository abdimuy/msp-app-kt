package com.example.msp_app.core.sync.cobranza

import android.util.Log
import com.example.msp_app.core.network.ConnectivityMonitor
import com.example.msp_app.data.api.services.cobranza.IdsResponse
import com.example.msp_app.data.api.services.cobranza.V2CobranzaApi
import com.example.msp_app.data.local.dao.payment.PaymentDao
import com.example.msp_app.data.local.dao.sale.SaleDao
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Reconcile defensivo que limpia phantoms del cache local de cobranza
 * (rows que el cobrador ve en la app pero ya no existen activas en
 * Microsip). Se diseñó como contramedida ante tres modos de falla:
 *
 *  1. Outage del listener FbEvent: el server deja de notificar y el
 *     cliente acumula rows que se cancelaron sin que el cursor las
 *     repropagase (TombstoneRetentionDays=30 del server las recoge
 *     después, pero el cobrador trabaja con esas semanas).
 *  2. Snapshot restore de Microsip que retrocede UPDATED_AT y deja el
 *     cliente con IDs huérfanos que el sync incremental nunca volverá
 *     a tocar.
 *  3. Bugs locales que hayan dejado state inconsistente.
 *
 * --- Por qué NO se llama al endpoint /digest ---
 * Server-side (commit a1ceffb del repo msp-api) el endpoint /digest
 * filtra sólo por `CANCELADO='N' AND ZONA_CLIENTE_ID=?`, mientras que
 * /sync también filtra por `CONCEPTO_CC_ID IN (87327, 27969)` y
 * `s.SALDO > 0`. El set del digest es estructuralmente un superset del
 * que el sync entrega, así que un digest local nunca igualaría al del
 * server en steady state — y el call al /digest sería siempre ruido.
 *
 * TODO(server): cuando el endpoint /digest acepte un `desde=` y aplique
 * los mismos filtros de saldo/concepto que /sync, podemos llamarlo
 * primero como pre-check barato (matching server-vs-local digest sin
 * pedir todos los IDs). Por ahora vamos directo a /ids.
 *
 * --- Semántica del reconcile ---
 * Se baja el set completo de IDs activos del server (zona-scoped, via
 * /ids paginado), se compara con los IDs locales y se borra
 * `local - server` (phantoms). El delta `server - local` es esperado
 * en steady state por la asimetría arriba; se loguea para
 * observabilidad pero no se actúa — el sync incremental los traerá si
 * caen dentro del filtro server-side (saldo/concepto/desde).
 *
 * Idempotente: corre tantas veces como quieras, el resultado converge.
 * Mutex-protegido: dos llamadas concurrentes serializan.
 */
class CobranzaReconciler(
    private val api: V2CobranzaApi,
    private val saleDao: SaleDao,
    private val paymentDao: PaymentDao,
    private val connectivity: ConnectivityMonitor,
    private val userContextFlow: StateFlow<UserContext?>
) {
    private val mutex = Mutex()

    suspend fun reconcileNow(): ReconcileOutcome {
        return mutex.withLock {
            if (!connectivity.isNetworkAvailable()) {
                Log.i(TAG, "reconcile skip: offline")
                return ReconcileOutcome.SkippedOffline
            }
            val ctx = userContextFlow.value ?: run {
                Log.i(TAG, "reconcile skip: sin contexto de zona")
                return ReconcileOutcome.SkippedNoZone
            }
            val zona = ctx.zona

            try {
                Log.i(TAG, "reconcileNow start zona=$zona")

                // Collect full server-side ID sets via paginated /ids endpoints.
                val serverPagoIds = fetchAllServerIds { after ->
                    api.listPagoIds(zona, after = after, limit = PAGE_LIMIT)
                }
                val serverSaldoIds = fetchAllServerIds { after ->
                    api.listSaldoIds(zona, after = after, limit = PAGE_LIMIT)
                }

                // Read local ID sets.
                val localPagoIds = paymentDao.getActiveIDsByZona(zona).map { it.toInt() }.toSet()
                val localSaldoIds = saleDao.getActiveIdsByZona(zona).toSet()

                // Phantoms = local − server: rows the client has but server no longer serves.
                val pagoPhantoms = localPagoIds - serverPagoIds
                val saldoPhantoms = localSaldoIds - serverSaldoIds

                // Extras = server − local: expected in steady state due to filter asymmetry.
                // Do NOT fetch — the incremental sync will bring them when they fall within
                // the saldo/concepto/desde filters. Log for observability only.
                val pagosExtras = serverPagoIds - localPagoIds
                val saldosExtras = serverSaldoIds - localSaldoIds

                if (pagosExtras.isNotEmpty()) {
                    Log.i(
                        TAG,
                        "reconcile: ${pagosExtras.size} pago IDs on server but not local " +
                            "(expected — server /ids superset vs /sync filters)"
                    )
                }
                if (saldosExtras.isNotEmpty()) {
                    Log.i(
                        TAG,
                        "reconcile: ${saldosExtras.size} saldo IDs on server but not local " +
                            "(expected — server /ids superset vs /sync filters)"
                    )
                }

                // Bulk-delete phantoms in one round-trip each.
                if (pagoPhantoms.isNotEmpty()) {
                    Log.i(TAG, "reconcile: deleting ${pagoPhantoms.size} phantom pagos")
                    paymentDao.deleteByIDs(pagoPhantoms.map { it.toString() })
                }
                if (saldoPhantoms.isNotEmpty()) {
                    Log.i(TAG, "reconcile: deleting ${saldoPhantoms.size} phantom saldos")
                    saleDao.deleteByDoctoCcIds(saldoPhantoms.toList())
                }

                Log.i(
                    TAG,
                    "reconcileNow ok zona=$zona " +
                        "pagoPhantoms=${pagoPhantoms.size} saldoPhantoms=${saldoPhantoms.size} " +
                        "pagosExtras=${pagosExtras.size} saldosExtras=${saldosExtras.size}"
                )
                ReconcileOutcome.Ok(
                    pagosPhantomsDeleted = pagoPhantoms.size,
                    saldosPhantomsDeleted = saldoPhantoms.size,
                    pagosExtrasOnServer = pagosExtras.size,
                    saldosExtrasOnServer = saldosExtras.size
                )
            } catch (e: Exception) {
                Log.w(TAG, "reconcile failed: ${e.message}", e)
                ReconcileOutcome.Error(e)
            }
        }
    }

    /**
     * Pages through a `/ids` endpoint until `has_more=false`, accumulating
     * all IDs into a single set. Starts from `after=0` on every call —
     * reconcile is stateless across runs by design.
     */
    private suspend fun fetchAllServerIds(
        fetchPage: suspend (after: Int) -> IdsResponse
    ): Set<Int> {
        val result = mutableSetOf<Int>()
        var after = 0
        while (true) {
            val page = fetchPage(after)
            result.addAll(page.ids)
            // Defensa: si el server dice has_more=true pero manda una página
            // vacía (bug del server), `last()` lanzaría NoSuchElementException
            // y el outer try/catch lo convertiría en Error silencioso. Cortar
            // el loop es safer — el siguiente reconcile reintentará desde 0.
            if (!page.has_more || page.ids.isEmpty()) break
            after = page.ids.last()
        }
        return result
    }

    companion object {
        const val PAGE_LIMIT = 5000
        const val RECONCILE_INTERVAL_MS = 5 * 60_000L
        private const val TAG = "CobranzaReconciler"
    }
}

sealed class ReconcileOutcome {
    object SkippedOffline : ReconcileOutcome()
    object SkippedNoZone : ReconcileOutcome()
    data class Ok(
        val pagosPhantomsDeleted: Int,
        val saldosPhantomsDeleted: Int,
        val pagosExtrasOnServer: Int,
        val saldosExtrasOnServer: Int
    ) : ReconcileOutcome()
    data class Error(val cause: Throwable) : ReconcileOutcome()
}
