package com.example.msp_app.core.sync.cobranza

import android.util.Log
import com.example.msp_app.core.network.ConnectivityMonitor
import com.example.msp_app.data.api.services.cobranza.DigestResponse
import com.example.msp_app.data.api.services.cobranza.IdsResponse
import com.example.msp_app.data.api.services.cobranza.V2CobranzaApi
import com.example.msp_app.data.api.services.cobranza.toEntity
import com.example.msp_app.data.local.dao.payment.PaymentDao
import com.example.msp_app.data.local.dao.sale.SaleDao
import kotlinx.coroutines.flow.StateFlow
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
 * --- Protocolo digest-first ---
 * Antes de bajar el set completo de IDs vía /ids (operación cara), el
 * reconciler llama a /digest para pagos y saldos en paralelo. El digest
 * del server es un triplete (count, XOR, SUM) sobre los IDs activos de
 * la zona con los mismos filtros que /sync aplica:
 *
 *   - Pagos:  CANCELADO='N' + CONCEPTO_CC_ID IN (87327, 27969) + SALDO > 0
 *             (o FECHA >= desde si se pasa `desde`).
 *   - Saldos: CARGO_CANCELADO='N' + SALDO > 0
 *             (o FECHA_ULT_PAGO >= desde si se pasa `desde`).
 *
 * Se computa el mismo triplete sobre los IDs locales en Room. Si coinciden
 * → no hay drift → se devuelve Ok(0, 0, 0, 0) sin llamar a /ids. Si
 * alguno no coincide → se cae al path /ids SOLO PARA ESE KIND. Esto hace
 * que el caso steady-state (sin drift) cueste dos llamadas HTTP ligeras
 * en lugar de descargar todos los IDs paginados.
 *
 * --- Steady-state y extras ---
 * Tras la alineación de filtros en el server (commit 5b42b55 de msp-api),
 * /digest y /ids aplican exactamente los mismos predicados que /sync. En
 * steady state `extras` debe converger a 0. Si persiste no-cero a lo
 * largo de muchos runs, indica una divergencia de filtros en el server
 * (regresión). Se loguea para observabilidad.
 *
 * Idempotente: corre tantas veces como quieras, el resultado converge.
 * Mutex-protegido: dos llamadas concurrentes serializan.
 */
class CobranzaReconciler(
    private val api: V2CobranzaApi,
    private val saleDao: SaleDao,
    private val paymentDao: PaymentDao,
    private val connectivity: ConnectivityMonitor,
    private val userContextFlow: StateFlow<UserContext?>,
    /**
     * Mutex compartido con [CobranzaSyncManager] para serializar escrituras a
     * Room. Inyectado para que el reconciler y el sync nunca solapen sus
     * operaciones de escritura y evitar la race condition phantom-delete vs
     * SSE-apply.
     * Default: mutex de proceso gestionado por [CobranzaWriteMutexProvider].
     */
    private val cobranzaWriteMutex: CobranzaWriteMutex = CobranzaWriteMutexProvider.get()
) {
    private val mutex get() = cobranzaWriteMutex.mutex

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
            val desdeIso = ctx.fechaCargaInicial?.toString()

            try {
                Log.i(TAG, "reconcileNow start zona=$zona")

                // Pre-check: compare server vs local digest. Saves the /ids round-trip
                // when there's no drift, which should be the common case.
                val pagoMatched = checkDigestMatch(
                    server = api.pagosDigest(zona, desde = desdeIso),
                    localIds = paymentDao.getActiveIDsByZona(zona).map { it.toInt() }
                )
                val saldoMatched = checkDigestMatch(
                    server = api.saldosDigest(zona, desde = desdeIso),
                    localIds = saleDao.getActiveIdsByZona(zona)
                )

                if (pagoMatched && saldoMatched) {
                    Log.i(TAG, "reconcileNow zona=$zona — both digests match, skip ids")
                    return@withLock ReconcileOutcome.Ok(0, 0, 0, 0)
                }

                // Mismatch → /ids reconcile FOR THAT KIND ONLY.
                val pagoStats = if (pagoMatched) {
                    Pair(
                        0,
                        0
                    )
                } else {
                    reconcilePagosViaIds(zona, desdeIso)
                }
                val saldoStats = if (saldoMatched) {
                    Pair(
                        0,
                        0
                    )
                } else {
                    reconcileSaldosViaIds(zona, desdeIso)
                }

                Log.i(
                    TAG,
                    "reconcileNow ok zona=$zona " +
                        "pagoPhantoms=${pagoStats.first} saldoPhantoms=${saldoStats.first} " +
                        "pagosExtras=${pagoStats.second} saldosExtras=${saldoStats.second}"
                )
                ReconcileOutcome.Ok(
                    pagosPhantomsDeleted = pagoStats.first,
                    saldosPhantomsDeleted = saldoStats.first,
                    pagosExtrasOnServer = pagoStats.second,
                    saldosExtrasOnServer = saldoStats.second
                )
            } catch (e: Exception) {
                Log.w(TAG, "reconcile failed: ${e.message}", e)
                ReconcileOutcome.Error(e)
            }
        }
    }

    private suspend fun reconcilePagosViaIds(zona: Int, desdeIso: String?): Pair<Int, Int> {
        val serverPagoIds = fetchAllServerIds { after ->
            api.listPagoIds(zona, after = after, limit = PAGE_LIMIT, desde = desdeIso)
        }
        val localPagoIds = paymentDao.getActiveIDsByZona(zona).map { it.toInt() }.toSet()

        val pagoPhantoms = localPagoIds - serverPagoIds
        val pagosMissing = serverPagoIds - localPagoIds

        // Phantoms: en local pero no en servidor → borrar.
        if (pagoPhantoms.isNotEmpty()) {
            Log.i(TAG, "reconcile: deleting ${pagoPhantoms.size} phantom pagos")
            paymentDao.deleteByIDs(pagoPhantoms.map { it.toString() })
        }

        // Missing: en servidor pero no en local → traer quirúrgicamente si
        // byIdsAvailable; de lo contrario loguear como smoke signal (el cursor-
        // sync del próximo tick los incorporará).
        if (pagosMissing.isNotEmpty()) {
            if (ByIdsChunker.byIdsAvailable.get()) {
                Log.i(TAG, "reconcile: fetching ${pagosMissing.size} missing pagos via by-ids")
                val fetched = ByIdsChunker.fetchInChunks(pagosMissing.toList()) { chunk ->
                    api.pagosByIds(zona, chunk)
                }
                // Merge: upsert solo vivos (cancelado=false). Los tombstones en este
                // path son raros (el server no los incluye en /ids activos), pero por
                // defensividad respetamos el flag.
                val alive = fetched.filter { !it.cancelado }
                if (alive.isNotEmpty()) {
                    paymentDao.saveAll(alive.map { it.toEntity() })
                }
            } else {
                Log.i(
                    TAG,
                    "reconcile: ${pagosMissing.size} pago IDs on server but not local " +
                        "(byIds no disponible — el cursor-sync los incorporará en el próximo tick)"
                )
            }
        }

        return Pair(pagoPhantoms.size, pagosMissing.size)
    }

    private suspend fun reconcileSaldosViaIds(zona: Int, desdeIso: String?): Pair<Int, Int> {
        val serverSaldoIds = fetchAllServerIds { after ->
            api.listSaldoIds(zona, after = after, limit = PAGE_LIMIT, desde = desdeIso)
        }
        val localSaldoIds = saleDao.getActiveIdsByZona(zona).toSet()

        val saldoPhantoms = localSaldoIds - serverSaldoIds
        val saldosMissing = serverSaldoIds - localSaldoIds

        // Phantoms: en local pero no en servidor → borrar.
        if (saldoPhantoms.isNotEmpty()) {
            Log.i(TAG, "reconcile: deleting ${saldoPhantoms.size} phantom saldos")
            saleDao.deleteByDoctoCcIds(saldoPhantoms.toList())
        }

        // Missing: en servidor pero no en local → traer quirúrgicamente.
        if (saldosMissing.isNotEmpty()) {
            if (ByIdsChunker.byIdsAvailable.get()) {
                Log.i(TAG, "reconcile: fetching ${saldosMissing.size} missing saldos via by-ids")
                val fetched = ByIdsChunker.fetchInChunks(saldosMissing.toList()) { chunk ->
                    api.saldosByIds(zona, chunk)
                }
                // Merge: solo activos (cargo_cancelado=false, saldo>0 ó sin ventana).
                val alive = fetched.filter { !it.cargo_cancelado }
                if (alive.isNotEmpty()) {
                    saleDao.insertAll(alive.map { it.toEntity() })
                }
            } else {
                Log.i(
                    TAG,
                    "reconcile: ${saldosMissing.size} saldo IDs on server but not local " +
                        "(byIds no disponible — el cursor-sync los incorporará en el próximo tick)"
                )
            }
        }

        return Pair(saldoPhantoms.size, saldosMissing.size)
    }

    /**
     * Computes the local digest triplet over [localIds] and compares it
     * against the server's [DigestResponse]. Returns true when all three
     * fields (count, XOR, SUM) agree — meaning there is no drift for this
     * kind and /ids can be skipped.
     *
     * Uses Long (int64) arithmetic for XOR and SUM to match server semantics
     * and avoid 32-bit overflow on the SUM (worst case: 50k rows × 100M IDs
     * ≈ 5e15 — fits in int64, blows int32).
     */
    private fun checkDigestMatch(server: DigestResponse, localIds: List<Int>): Boolean {
        val localCount = localIds.size
        val localXor = localIds.fold(0L) { acc, id -> acc xor id.toLong() }
        val localSum = localIds.fold(0L) { acc, id -> acc + id.toLong() }
        val serverXor = server.ids_xor.toLong()
        val serverSum = server.ids_sum.toLong()
        val matched = server.count_activos == localCount && serverXor == localXor && serverSum == localSum
        if (!matched) {
            Log.i(
                TAG,
                "digest mismatch: server(count=${server.count_activos}, xor=$serverXor, sum=$serverSum) " +
                    "local(count=$localCount, xor=$localXor, sum=$localSum)"
            )
        }
        return matched
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
