package com.example.msp_app.core.sync.cobranza

import android.util.Log
import androidx.room.withTransaction
import com.example.msp_app.core.database.AppDatabase
import com.example.msp_app.core.database.dao.payment.PaymentDao
import com.example.msp_app.core.database.dao.sale.SaleDao
import com.example.msp_app.core.logging.Logger
import com.example.msp_app.core.network.ConnectivityMonitor
import com.example.msp_app.data.api.services.cobranza.DigestResponse
import com.example.msp_app.data.api.services.cobranza.IdsResponse
import com.example.msp_app.data.api.services.cobranza.PagoDto
import com.example.msp_app.data.api.services.cobranza.V2CobranzaApi
import com.example.msp_app.data.api.services.cobranza.toEntity
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
    /**
     * Se inyecta solo para abrir transacciones. El camino by-ids inserta la
     * fila numérica y colapsa su gemelo UUID en el mismo `withTransaction`,
     * de modo que ningún lector alcance a ver las dos filas juntas — el
     * mismo criterio (y la misma razón) que [CobranzaSyncManager.mergePagos].
     */
    private val db: AppDatabase,
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

                // Self-heal del gemelo UUID: colapsa toda captura local cuya
                // fila numérica ya llegó (PAGO_RECIBIDO_ID persistido). Corre
                // SIEMPRE porque mergePagos solo colapsa de un tiro; si esa
                // falló (carrera / histórico), aquí converge. Idempotente.
                collapseUuidTwins(FASE_INICIO)

                // Pre-check: compare server vs local digest. Saves the /ids round-trip
                // when there's no drift, which should be the common case.
                val pagoMatched = checkDigestMatch(
                    server = api.pagosDigest(zona, desde = desdeIso),
                    // mapNotNull: getActiveIDsByZona incluye filas locales con
                    // ID=UUID (pagos offline aún no subidos, o gemelos UUID
                    // pendientes de colapsar por mergePagos). Un `.map { it.toInt() }`
                    // lanza NumberFormatException ante cualquier UUID y aborta
                    // TODO el reconcile — el digest solo debe considerar ids
                    // numéricos, que son los únicos que el server conoce.
                    localIds = paymentDao.getActiveIDsByZona(zona).mapNotNull { it.toIntOrNull() }
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

    /**
     * Colapsa (borra) las filas UUID de captura local cuyo gemelo numérico ya
     * está en Room — el criterio canónico vive en
     * [PaymentDao.findCollapsibleUuidTwins]: existe OTRA fila que las nombra
     * por `PAGO_RECIBIDO_ID`, lo que prueba que el servidor recibió esa
     * captura y le asignó su id de Microsip.
     *
     * Extraído a función porque `reconcileNow` lo necesita en DOS momentos y
     * duplicar el bloque invitaba a que los dos criterios divergieran:
     *
     *  - [FASE_INICIO]: converge lo histórico y las carreras de ticks previos.
     *  - [FASE_POST_BY_IDS]: la fila numérica que ACABA de insertar el camino
     *    by-ids trae `PAGO_RECIBIDO_ID` y por definición es colapsable; sin
     *    esta segunda pasada el gemelo sobrevivía hasta el siguiente
     *    `mergePagos` o el siguiente tick (5 min), que es el defecto medido en
     *    campo: dos filas del mismo pago conviviendo más de tres minutos.
     *
     * Idempotente y silenciosa: si no hay nada colapsable no escribe ni
     * loguea. Cada llamada consulta el estado fresco de Room, así que la
     * segunda sólo puede encontrar gemelos que la primera no podía ver
     * todavía — los conteos que reporta nunca se solapan ni cuentan doble.
     * Por eso se loguea una línea por fase en vez de un total agregado: un
     * único número no podría distinguir "colapsé 1 al inicio" de "colapsé 1
     * después de insertar", que son eventos distintos.
     *
     * Devuelve cuántas filas colapsó.
     */
    private suspend fun collapseUuidTwins(fase: String): Int {
        val collapsed = paymentDao.findCollapsibleUuidTwins()
        if (collapsed.isEmpty()) return 0
        paymentDao.deleteByIDs(collapsed)
        Log.i(TAG, "reconcile[$fase]: colapsados ${collapsed.size} gemelo(s) UUID")
        runCatching {
            Logger.get().info(
                module = "COBRANZA",
                action = "COLLAPSE_TWIN",
                message = "Colapsados ${collapsed.size} gemelos UUID de pago",
                data = mapOf(
                    "count" to collapsed.size,
                    "ids" to collapsed,
                    "fase" to fase
                )
            )
        }
        return collapsed.size
    }

    /**
     * Colapsa el **gemelo legacy**: la fila que dejó el sync Node para el mismo
     * abono que [alive] trae por el canal v2. Los dos canales guardan el pago
     * con llaves distintas (`MSP_PAGOS_RECIBIDOS.ID` — UUID de captura o
     * `"<DOCTO_CC_ID>-<IMPTE_DOCTO_CC_ID>"` de oficina — contra
     * `IMPTE_DOCTO_CC_ID` puro), y como `Payment.ID` es la PK, Room los trata
     * como dos pagos distintos: los totales del cobrador salen al doble. Es el
     * mismo incidente del cutover Node→Go, no un escenario teórico.
     *
     * Es un mecanismo DISTINTO del gemelo UUID de [collapseUuidTwins] y no se
     * puede sustituir por él: aquí no hay `PAGO_RECIBIDO_ID` con el cual el
     * servidor nombre a la fila (es NULL en todo el histórico pre-cutover,
     * porque el Node nunca escribió `MSP_PAGOS_RECIBIDOS.IMPTE_DOCTO_CC_ID`).
     * El match es por `DOCTO_CC_ID`, que identifica el mismo abono en ambos
     * canales.
     *
     * **Los tres cerrojos viven en la consulta y se portan intactos** — ver
     * [PaymentDao.deleteLegacyTwinsByDoctoCcIds]:
     *  1. `GUARDADO_EN_MICROSIP = 1` — una captura pendiente de subir jamás se
     *     toca. Este es el equivalente exacto, para este camino, de la regla
     *     "nunca borrar lo que el servidor no nombró": la bandera solo la
     *     escriben `PendingPaymentsWorker.markDone` (tras una subida exitosa)
     *     y `PagoDto.toEntity()` (filas que vienen del servidor), así que
     *     `= 1` prueba que el servidor recibió el pago. NO se relaja aquí,
     *     aunque el gemelo UUID sí lo hizo: allá existe evidencia más fuerte
     *     (`PAGO_RECIBIDO_ID`) que la reemplaza; acá no hay ninguna, y el
     *     `DOCTO_CC_ID` solo no distingue "mismo pago, markDone perdido" de
     *     "captura pendiente con docto_cc_id ya anotado" — el worker persiste
     *     `DOCTO_CC_ID` ANTES de marcar la bandera, así que esa ventana
     *     existe de verdad. Borrar ahí sería destruir dinero.
     *  2. `ID LIKE '%-%'` — solo formatos legacy; la fila canónica v2 es
     *     numérica pura y nunca se borra a sí misma.
     *  3. `DOCTO_CC_ID > 0` — el 0 es el centinela de "aún sin documento",
     *     reforzado aquí con el `filter { it > 0 }` sobre los candidatos.
     *
     * Se calcula sobre [alive] y no sobre todo lo traído por by-ids: es
     * exactamente el conjunto que se va a insertar, o sea el único que puede
     * crear un duplicado. `mergePagos` sí incluye sus tombstones, pero ahí el
     * borrado acompaña a un pago cancelado; acá un tombstone no inserta nada,
     * así que barrer por él sería borrar una fila sin reponer ninguna. En la
     * práctica el conjunto es el mismo: el servidor no publica cancelados en
     * `/ids`, y el `filter { !it.cancelado }` de este camino es pura defensa.
     *
     * Devuelve cuántas filas legacy borró.
     */
    private suspend fun collapseLegacyTwins(alive: List<PagoDto>, fase: String): Int {
        val doctoCcIds = alive.map { it.docto_cc_id }.filter { it > 0 }.distinct()
        if (doctoCcIds.isEmpty()) return 0
        // Chunking obligatorio, NO cosmético: `mergePagos` corre sobre una
        // página acotada (`limit=1000`), pero acá `alive` es el set completo
        // de pagos faltantes — ByIdsChunker acota las llamadas HTTP, no el
        // resultado acumulado. Con minSdk 24 y Room sobre el SQLite del
        // framework, todo dispositivo por debajo de API 31 tiene
        // SQLITE_MAX_VARIABLE_NUMBER = 999, así que un `IN (:ids)` sin trocear
        // reventaría con "too many SQL variables" justo en el caso que más
        // importa: el teléfono muy desfasado, que es el que más gemelos tiene.
        var borrados = 0
        doctoCcIds.chunked(SQLITE_MAX_IN_PARAMS).forEach { chunk ->
            borrados += paymentDao.deleteLegacyTwinsByDoctoCcIds(chunk)
        }
        if (borrados == 0) return 0
        Log.i(TAG, "reconcile[$fase]: colapsados $borrados gemelo(s) legacy")
        runCatching {
            Logger.get().info(
                module = "COBRANZA",
                action = "COLLAPSE_LEGACY_TWIN",
                message = "Colapsados $borrados gemelos legacy de pago",
                data = mapOf(
                    "count" to borrados,
                    "doctos_evaluados" to doctoCcIds.size,
                    "fase" to fase
                )
            )
        }
        return borrados
    }

    private suspend fun reconcilePagosViaIds(zona: Int, desdeIso: String?): Pair<Int, Int> {
        val serverPagoIds = fetchAllServerIds { after ->
            api.listPagoIds(zona, after = after, limit = PAGE_LIMIT, desde = desdeIso)
        }
        // mapNotNull por la misma razón que en el pre-check de digest: ids
        // UUID locales (offline / gemelos pendientes) se ignoran, nunca se
        // cuentan como phantom ni se borran.
        val localPagoIds = paymentDao.getActiveIDsByZona(
            zona
        ).mapNotNull { it.toIntOrNull() }.toSet()

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
                    // Insertar y colapsar en la MISMA transacción. La fila que
                    // entra aquí trae `PAGO_RECIBIDO_ID`, así que en el instante
                    // en que se escribe su gemelo UUID pasa a ser un duplicado
                    // visible para el cobrador. El colapso del inicio de
                    // `reconcileNow` ya pasó y no la alcanza; sin esta pasada el
                    // duplicado vive hasta el próximo `mergePagos` o el próximo
                    // tick. Y sin la transacción quedaría una ventana —corta,
                    // pero real— en la que un lector ve las dos filas.
                    db.withTransaction {
                        // El legacy va ANTES del insert, igual que en
                        // mergePagos: así ningún cerrojo tiene que sostener el
                        // caso "la fila que acabo de escribir se borra a sí
                        // misma". El UUID va DESPUÉS porque su gemelo solo
                        // nace colapsable cuando la fila numérica ya está
                        // escrita — ese es justamente el defecto que se
                        // arregla. Los dos son idempotentes y no se pisan: si
                        // ambos apuntan a la misma fila, el primero la borra y
                        // el segundo no la encuentra.
                        collapseLegacyTwins(alive, FASE_POST_BY_IDS)
                        paymentDao.saveAll(alive.map { it.toEntity() })
                        collapseUuidTwins(FASE_POST_BY_IDS)
                    }
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

        /** Barrido auto-sanable al entrar a `reconcileNow` (histórico / carreras previas). */
        private const val FASE_INICIO = "inicio"

        /** Barrido tras insertar filas numéricas traídas por by-ids en este mismo tick. */
        private const val FASE_POST_BY_IDS = "post-by-ids"

        /**
         * Tope de parámetros por `IN (...)`. SQLITE_MAX_VARIABLE_NUMBER es 999
         * en el SQLite del framework para todo Android por debajo de API 31, y
         * este módulo declara `minSdk = 24`. Se deja margen por debajo de 999.
         */
        private const val SQLITE_MAX_IN_PARAMS = 900
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
