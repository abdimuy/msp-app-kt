package com.example.msp_app.core.sync.cobranza

import android.util.Log
import androidx.room.withTransaction
import com.example.msp_app.core.database.AppDatabase
import com.example.msp_app.core.database.dao.cobranzasync.CobranzaSyncStateDao
import com.example.msp_app.core.database.dao.payment.PaymentDao
import com.example.msp_app.core.database.dao.sale.SaleDao
import com.example.msp_app.core.database.entities.CobranzaSyncStateEntity
import com.example.msp_app.core.network.ConnectivityMonitor
import com.example.msp_app.data.api.services.cobranza.PagoDto
import com.example.msp_app.data.api.services.cobranza.V2CobranzaApi
import com.example.msp_app.data.api.services.cobranza.VentaDto
import com.example.msp_app.data.api.services.cobranza.toEntity
import java.time.Instant
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.withLock

/**
 * Drives the per-zone incremental sync of cobranza data from the v2 Go
 * backend into the local Room store. One instance lives at the
 * application scope; the navigation graph mounts a
 * [CobranzaSyncObserver] which calls [start] / [stop] in tandem with the
 * lifecycle of the authenticated UI.
 *
 * The manager polls every [TICK_INTERVAL_MILLIS] and fires `syncNow`
 * inmediato cuando:
 *   - vuelve la conectividad después de un drop, y
 *   - el [userContextFlow] transiciona a un valor non-null o cambia
 *     (cobrador recién autenticado, zona reasignada, FECHA_CARGA_INICIAL
 *     adelantada). Esto evita que tras el login el primer tick se salte
 *     por contexto null y el cobrador espere 30 s hasta la siguiente
 *     iteración del loop.
 * Todos los disparadores comparten un [Mutex] así que dos syncs
 * concurrentes son imposibles.
 */
class CobranzaSyncManager(
    private val api: V2CobranzaApi,
    private val db: AppDatabase,
    private val saleDao: SaleDao,
    private val paymentDao: PaymentDao,
    private val syncStateDao: CobranzaSyncStateDao,
    private val connectivity: ConnectivityMonitor,
    private val userContextFlow: StateFlow<UserContext?>,
    /**
     * Mutex compartido con [CobranzaReconciler] para serializar escrituras a
     * Room. Inyectado para que el reconciler y el sync nunca solapen sus
     * operaciones de escritura y evitar la race condition SSE vs phantom-delete.
     * Default: mutex de proceso gestionado por [CobranzaWriteMutexProvider].
     */
    private val cobranzaWriteMutex: CobranzaWriteMutex = CobranzaWriteMutexProvider.get(),
    /**
     * Dispatcher en el que corren los tres jobs background del manager
     * (tick / connectivity / context). Default `Dispatchers.IO` para
     * producción (no bloquea Main). Los tests inyectan el dispatcher del
     * scheduler virtual de `runTest` para coordinar con `advanceUntilIdle`.
     */
    private val backgroundDispatcher: CoroutineDispatcher = Dispatchers.IO
) {

    private val mutex get() = cobranzaWriteMutex.mutex
    private var tickJob: Job? = null
    private var connectivityJob: Job? = null
    private var contextJob: Job? = null

    fun start(scope: CoroutineScope) {
        stop()
        tickJob = scope.launch(backgroundDispatcher) { tickLoop() }
        connectivityJob = scope.launch(backgroundDispatcher) { connectivityObserver() }
        contextJob = scope.launch(backgroundDispatcher) { contextObserver() }
    }

    fun stop() {
        tickJob?.cancel()
        tickJob = null
        connectivityJob?.cancel()
        connectivityJob = null
        contextJob?.cancel()
        contextJob = null
    }

    suspend fun syncNow(): SyncOutcome {
        return mutex.withLock {
            val ctx = userContextFlow.value ?: run {
                Log.i(TAG, "skip: usuario sin contexto (zona/FECHA_CARGA_INICIAL no disponibles)")
                return SyncOutcome.SkippedNoZone
            }
            val zona = ctx.zona
            val desdeIso = ctx.fechaCargaInicial?.toString()
            if (!connectivity.isNetworkAvailable()) {
                Log.i(TAG, "skip: offline (zona=$zona)")
                return SyncOutcome.SkippedOffline
            }
            // Detección de cambio de zona: el cache local sigue al cobrador
            // y la zona es su namespace efectivo. Cuando cambia, lo local de
            // la zona anterior se descarta para evitar mezclar dos rutas.
            // Patrón estándar offline-first / multi-tenant: full clear +
            // resync — ver Android Developers, "Build an offline-first app".
            zonaChangeCleanupIfNeeded(zona)
            resetPagosCursorForPagoRecibidoIdMigrationIfNeeded()
            // Segundo replay one-time: los dispositivos que ya corrieron el
            // build af750fe consumieron el marcador de arriba, pero ese build
            // nunca persistía `pago_recibido_id` en la fila numérica (recién
            // se agregó PaymentEntity.PAGO_RECIBIDO_ID / mig 26->27 en este
            // build). Resultado: sus filas numéricas viejas quedan con
            // PAGO_RECIBIDO_ID=NULL para siempre y el barrido auto-sanable
            // del reconciler nunca las ve, así que el gemelo UUID sigue
            // atorado (solo "borrar datos" lo quitaba). Forzar UN replay más
            // en ESTE build re-baja esos pagos con toEntity ya poblando
            // pago_recibido_id, sin afectar instalaciones frescas (que
            // también lo corren, sin costo extra real más que red).
            resetPagosCursorOnce(MIGRATION_PAGO_RECIBIDO_ID_PERSIST)
            Log.i(TAG, "syncNow start zona=$zona desde=$desdeIso")
            try {
                // ORDEN INTENCIONAL: pagos antes que ventas.
                // mergeVentas consulta paymentDao.countPagosDesde() para decidir
                // si una venta saldada se conserva o se elimina. Si los pagos
                // se sincronizan después, el conteo siempre da 0 y todas las
                // saldadas con pago en ventana se borran por error — en el
                // siguiente sync incremental el backend no las re-envía
                // (UPDATED_AT < cursor) y quedan perdidas hasta el próximo
                // cleanup full. Sincronizar pagos primero garantiza que
                // mergeVentas vea el set completo de pagos al evaluar.
                val pagos = syncResource(RESOURCE_PAGOS, zona) { cursor, afterId ->
                    Log.i(
                        TAG,
                        "GET /sync/pagos zona=$zona cursor=$cursor after_id=$afterId desde=$desdeIso"
                    )
                    val response = api.syncPagos(zona, cursor, afterId, desde = desdeIso)
                    Log.i(
                        TAG,
                        "pagos page: items=${response.items.size} " +
                            "has_more=${response.has_more} max=${response.max_updated_at}"
                    )
                    SyncPage(
                        cursor = response.max_updated_at,
                        hasMore = response.has_more,
                        afterId = response.items.lastOrNull()?.impte_docto_cc_id ?: 0,
                        size = response.items.size,
                        apply = { mergePagos(response.items) }
                    )
                }
                val ventas = syncResource(RESOURCE_VENTAS, zona) { cursor, afterId ->
                    Log.i(
                        TAG,
                        "GET /sync/ventas zona=$zona cursor=$cursor after_id=$afterId desde=$desdeIso"
                    )
                    val response = api.syncVentas(zona, cursor, afterId, desde = desdeIso)
                    Log.i(
                        TAG,
                        "ventas page: items=${response.items.size} " +
                            "has_more=${response.has_more} max=${response.max_updated_at}"
                    )
                    SyncPage(
                        cursor = response.max_updated_at,
                        hasMore = response.has_more,
                        afterId = response.items.lastOrNull()?.docto_cc_id ?: 0,
                        size = response.items.size,
                        apply = { mergeVentas(response.items, desdeIso) }
                    )
                }
                // Defensa: si la ventana del cobrador avanzó entre runs, las
                // saldadas cuyos pagos quedaron fuera deben evictarse aunque
                // el backend ya no las mande (sync incremental no propaga
                // bajas silenciosas). El costo es despreciable: una sola
                // sentencia DELETE con un WHERE indexable.
                if (desdeIso != null) {
                    val pruned = pruneSaldadasFueraDeVentana(desdeIso)
                    if (pruned > 0) {
                        Log.i(TAG, "prune: $pruned saldadas fuera de ventana ($desdeIso)")
                    }
                }
                Log.i(TAG, "syncNow ok ventas=$ventas pagos=$pagos")
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

    /**
     * Path optimista SSE: en lugar de re-sincronizar todo el cursor, trae
     * solo los registros afectados por [ids] usando el endpoint by-ids.
     *
     * Adquiere el mutex compartido antes de escribir a Room para serializar
     * con el reconciler (evita la race condition phantom-delete vs apply).
     *
     * @param kind  Stream de origen (PAGOS o SALDOS).
     * @param ids   Lista de IDs notificados por el servidor via SSE.
     */
    suspend fun applyByIds(kind: SseKind, ids: List<Int>) {
        if (ids.isEmpty()) return
        val ctx = userContextFlow.value ?: run {
            Log.i(TAG, "applyByIds: skip — sin contexto de zona")
            return
        }
        val zona = ctx.zona
        Log.i(TAG, "applyByIds kind=$kind ids=${ids.size} zona=$zona")
        mutex.withLock {
            try {
                if (kind == SseKind.PAGOS) {
                    val pagos = ByIdsChunker.fetchInChunks(ids) { chunk ->
                        api.pagosByIds(zona, chunk)
                    }
                    mergePagos(pagos)
                    Log.i(TAG, "applyByIds PAGOS: merged ${pagos.size} registros")
                } else {
                    val ventas = ByIdsChunker.fetchInChunks(ids) { chunk ->
                        api.saldosByIds(zona, chunk)
                    }
                    val desdeIso = ctx.fechaCargaInicial?.toString()
                    mergeVentas(ventas, desdeIso)
                    Log.i(TAG, "applyByIds SALDOS: merged ${ventas.size} registros")
                }
            } catch (e: Exception) {
                Log.w(TAG, "applyByIds kind=$kind falló: ${e.message}", e)
                throw e
            }
        }
    }

    /**
     * Detecta si la zona efectiva del cobrador cambió y limpia el cache
     * local en transacción atómica para que el próximo sync arranque desde
     * cero. Dispara cuando se cumple cualquiera de las dos condiciones:
     *
     *  - El state de sync (`cobranza_sync_state.ZONA_CLIENTE_ID`) apunta a
     *    una zona distinta a la actual (caso normal de cambio).
     *  - Existen ventas en local pertenecientes a otra zona — residuos de
     *    una transición pasada donde el state ya quedó actualizado pero
     *    rows huérfanos se quedaron (defensa contra historia).
     *
     * Solo en el primer arranque (estado vacío y sin rows) no toca nada.
     */
    private suspend fun zonaChangeCleanupIfNeeded(zonaActual: Int) {
        val previa = syncStateDao.get(RESOURCE_VENTAS)?.ZONA_CLIENTE_ID
            ?: syncStateDao.get(RESOURCE_PAGOS)?.ZONA_CLIENTE_ID
        val residuosOtraZona = saleDao.countByZonaIdNot(zonaActual)

        val cambioPorState = previa != null && previa != zonaActual
        val cambioPorResiduos = residuosOtraZona > 0
        if (!cambioPorState && !cambioPorResiduos) return

        Log.i(
            TAG,
            "limpieza por cambio de zona: previa=$previa actual=$zonaActual residuos=$residuosOtraZona"
        )
        db.withTransaction {
            saleDao.deleteAll()
            // NO borrar los pagos pendientes de subir (GUARDADO_EN_MICROSIP=0):
            // son trabajo del cobrador aún no sincronizado y deben sobrevivir el
            // cambio de zona/cobrador para no perder dinero. Un cobrador que
            // registró pagos offline y luego cambió de sesión (o volvió el
            // internet en otra zona) los conserva y se suben después con su
            // propia atribución. Solo se descarta el cache ya confirmado (=1).
            paymentDao.deleteUploaded()
            syncStateDao.clear(RESOURCE_VENTAS)
            syncStateDao.clear(RESOURCE_PAGOS)
        }
    }

    /**
     * Borra las ventas saldadas cuyos pagos quedaron todos fuera de la
     * ventana que arranca en `fechaIso`. Útil cuando FECHA_CARGA_INICIAL
     * avanza (típicamente al cambiar de semana) y el cliente debe limpiar
     * lo que ya no entra en el set visible.
     *
     * Se llama automáticamente al final de cada `syncNow()` exitoso; el
     * AppNavigation también puede dispararla manualmente cuando observe un
     * cambio de `userData.FECHA_CARGA_INICIAL` sin esperar al próximo tick.
     */
    suspend fun pruneSaldadasFueraDeVentana(fechaIso: String): Int =
        saleDao.deleteSaldadasFueraDeVentana(fechaIso)

    /**
     * Migración one-time: los dispositivos que ya sincronizaron pagos antes
     * de que el backend agregara `pago_recibido_id` tienen el cursor de
     * pagos avanzado — el server jamás volvería a mandar esos pagos viejos
     * (su `UPDATED_AT` no cambió), así que el gemelo UUID local que quedó
     * en Room NUNCA se colapsaría porque el pago numérico correspondiente
     * no se vuelve a bajar. Limpiar el cursor de pagos fuerza un replay
     * completo en el próximo `syncResource(RESOURCE_PAGOS, ...)`: cada pago
     * activo vuelve a bajar trayendo `pago_recibido_id`, y [mergePagos]
     * colapsa cualquier gemelo UUID que siga en local. Las escrituras del
     * replay son idempotentes (UPSERT por PK / DELETE por PK), así que el
     * único costo real es red.
     *
     * Guardado por una fila marcador en `cobranza_sync_state`
     * (RESOURCE=[MIGRATION_PAGO_RECIBIDO_ID]) para que esto corra
     * exactamente una vez por instalación, sin importar cuántos
     * ticks/syncs corran después.
     */
    private suspend fun resetPagosCursorForPagoRecibidoIdMigrationIfNeeded() {
        resetPagosCursorOnce(MIGRATION_PAGO_RECIBIDO_ID)
    }

    /**
     * Helper compartido por las migraciones one-time de resync de pagos
     * ([MIGRATION_PAGO_RECIBIDO_ID] y [MIGRATION_PAGO_RECIBIDO_ID_PERSIST]):
     * limpia el cursor de [RESOURCE_PAGOS] para forzar un replay completo en
     * el próximo `syncResource`, y marca [marker] para que esto corra
     * exactamente una vez por instalación.
     *
     * IMPORTANTE: el marcador se persiste ANTES de que el replay realmente
     * termine (no espera a que `syncResource(RESOURCE_PAGOS, ...)` aplique
     * las páginas). Si el proceso se interrumpe a mitad del replay, esta
     * migración NO reintenta — pero el barrido auto-sanable del reconciler
     * (idempotente, corre en cada tick usando `pago_recibido_id` ya
     * persistido) es la red que cubre ese caso: el cursor limpiado retoma
     * desde el inicio en los siguientes `syncNow()` hasta traer de vuelta
     * todos los pagos activos, y el reconciler colapsa lo que vaya llegando.
     */
    private suspend fun resetPagosCursorOnce(marker: String) {
        if (syncStateDao.get(marker) != null) return
        Log.i(TAG, "migración $marker: limpiando cursor de pagos para full resync")
        syncStateDao.clear(RESOURCE_PAGOS)
        syncStateDao.upsert(
            CobranzaSyncStateEntity(
                RESOURCE = marker,
                ZONA_CLIENTE_ID = 0,
                CURSOR = null,
                LAST_SYNCED_AT = Instant.now().toString(),
                LAST_ERROR = null
            )
        )
    }

    private suspend fun syncResource(
        resource: String,
        zona: Int,
        fetchPage: suspend (cursor: String?, afterId: Int) -> SyncPage
    ): Int {
        var applied = 0
        var cursor: String? = syncStateDao.get(resource)?.CURSOR
        // afterId no se persiste entre runs: si la app se mata a media
        // corrida, el siguiente arranque retoma desde el cursor con
        // afterId=0 y vuelve a procesar el inicio del cursor — las
        // escrituras son idempotentes (UPSERT por PK), solo gasta red.
        var afterId = 0
        while (true) {
            val page = fetchPage(cursor, afterId)
            page.apply.invoke()
            applied += page.size
            cursor = page.cursor
            afterId = page.afterId
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
     * Merge contract:
     *
     *  - `cargo_cancelado` → tombstone real: borra venta y todos sus pagos
     *    locales (cancelación en Microsip es definitiva).
     *
     *  - `saldo > 0` o sin `desdeIso` → upsert normal preservando el estado
     *    local del cobrador (ESTADO_COBRANZA, DIA_TEMPORAL_COBRANZA).
     *
     *  - `saldo <= 0` con `desdeIso` → mantener la venta solo si tiene al
     *    menos un pago dentro de la ventana del cobrador. Sin pagos en
     *    ventana, la venta se borra (los pagos se conservan por SAT y para
     *    los reportes históricos).
     */
    private suspend fun mergeVentas(items: List<VentaDto>, desdeIso: String?) {
        for (dto in items) {
            if (dto.cargo_cancelado) {
                saleDao.deleteByDoctoCcId(dto.docto_cc_id)
                paymentDao.deleteByDoctoCcAcrId(dto.docto_cc_id)
                continue
            }
            val saldo = dto.saldo.toDoubleOrNull() ?: 0.0
            if (saldo <= 0.0 && desdeIso != null) {
                val pagosEnVentana = paymentDao.countPagosDesde(dto.docto_cc_id, desdeIso)
                if (pagosEnVentana <= 0) {
                    // Saldada fuera de la ventana del cobrador — la quito
                    // de la lista visible. Los Payment quedan intactos.
                    saleDao.deleteByDoctoCcId(dto.docto_cc_id)
                    continue
                }
                // Saldada con pagos en ventana → cae al upsert normal.
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

    /**
     * Merge contract for pagos:
     *
     *  - `cancelado=true` → tombstone: borra el pago local por su PK
     *    (`IMPTE_DOCTO_CC_ID`). El backend mantiene la fila en
     *    `MSP_PAGOS_VENTAS` con `IMPORTE=0` para propagar la cancelación
     *    por el cursor incremental; aquí la quitamos para que el cobrador
     *    no vea un pago fantasma de $0. Cubre ambos casos del server:
     *    `CANCELADO='S'` por flag de Microsip y DELETE físico en
     *    `IMPORTES_DOCTOS_CC` (mig 20 los unifica como tombstone).
     *
     *  - `cancelado=false` → upsert normal por PK (idempotente).
     *
     *  - `pago_recibido_id` non-null → colapso de gemelo UUID: el pago
     *    entrante es la versión numérica (IMPTE_DOCTO_CC_ID) de un pago que
     *    el app capturó offline con un UUID local. Se borra la fila UUID
     *    local (si sigue existiendo Y ya está confirmada,
     *    `GUARDADO_EN_MICROSIP=1`) para que "Historial de pagos" no
     *    muestre el mismo pago dos veces. Solo se borra por el
     *    `pago_recibido_id` exacto — nunca por contenido/monto — y jamás
     *    un pago pendiente de subir (ver [PaymentDao.filterUploadedIDs]).
     *
     * La partición evita una segunda pasada y mantiene la ergonomía del
     * `paymentDao.saveAll` actual (un solo UPSERT batch). Los DELETE se
     * ejecutan en secuencia por simplicidad — el volumen por página
     * (`limit=1000`) hace que el costo de N statements sea despreciable
     * comparado con un único `DELETE ... WHERE ID IN (...)`. Si crece la
     * presión, [PaymentDao.deleteByIDs] está disponible para bulk.
     *
     * Todo el merge corre en una sola transacción para que el colapso del
     * gemelo UUID y el upsert/delete de la fila numérica sean atómicos: si
     * algo falla a medias, Room revierte y no queda un estado intermedio
     * (por ejemplo, la UUID borrada sin que la numérica haya entrado).
     */
    private suspend fun mergePagos(items: List<PagoDto>) {
        if (items.isEmpty()) return
        val (tombstones, alive) = items.partition { it.cancelado }
        val pagoRecibidoIds = items.mapNotNull { it.pago_recibido_id }.distinct()
        db.withTransaction {
            if (pagoRecibidoIds.isNotEmpty()) {
                val uuidTwins = paymentDao.filterUploadedIDs(pagoRecibidoIds)
                if (uuidTwins.isNotEmpty()) {
                    Log.i(TAG, "mergePagos: colapsando ${uuidTwins.size} gemelo(s) UUID")
                    paymentDao.deleteByIDs(uuidTwins)
                }
            }
            for (t in tombstones) {
                paymentDao.deleteByID(t.impte_docto_cc_id.toString())
            }
            if (alive.isNotEmpty()) {
                paymentDao.saveAll(alive.map { it.toEntity() })
            }
        }
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

    /**
     * Dispara `syncNow()` cuando el [userContextFlow] cambia a un valor
     * non-null (cobrador recién autenticado) o a un valor distinto
     * (zona reasignada / FECHA_CARGA_INICIAL adelantada).
     *
     * Sin este observer la app puede entrar en una ventana muerta de
     * 30 s tras el login: el tick loop arranca con el lifecycle
     * `ON_START`, pero el `LaunchedEffect` que invoca
     * `CobranzaSyncProvider.setContext(...)` corre asíncrono y aún no ha
     * hidratado el flow. El primer tick lee `value == null`, registra
     * `skip: usuario sin contexto` y duerme hasta el siguiente tick.
     *
     * No usamos `drop(1)`: hay una race condition entre cuándo el
     * `LaunchedEffect` de AppNavigation llama `setContext(non-null)` y
     * cuándo el coroutine del observer alcanza el `.collect()`. Si la
     * suscripción gana, el observer recibe primero null (consumido por
     * `filterNotNull`) y luego el non-null (dispara syncNow). Si pierde,
     * el StateFlow replaya solo el non-null actual al suscribir — sin
     * `drop(1)` también dispara syncNow. Con `drop(1)` el non-null
     * replay se comería y el sync esperaría hasta el próximo tick (30s).
     *
     * `StateFlow` ya deduplica por igualdad (operator fusion), así que
     * no se dispara dos veces para la misma combinación zona/fecha.
     * `filterNotNull` ignora el caso de logout que vuelve el flow a
     * null (no hay nada que sincronizar). El tick loop arranca con su
     * propio `syncNow()` inmediato — el mutex serializa el posible
     * duplicado si context ya está set al arrancar.
     */
    private suspend fun contextObserver() {
        userContextFlow
            .filterNotNull()
            .onEach { syncNow() }
            .collect()
    }

    companion object {
        const val RESOURCE_VENTAS = "ventas"
        const val RESOURCE_PAGOS = "pagos"
        const val TICK_INTERVAL_MILLIS = 30_000L

        /**
         * RESOURCE marcador (en `cobranza_sync_state`) que registra que ya
         * corrió la migración one-time de [resetPagosCursorForPagoRecibidoIdMigrationIfNeeded].
         * No es un cursor real de sync — su fila solo existe para el check
         * de idempotencia.
         */
        const val MIGRATION_PAGO_RECIBIDO_ID = "migration_pago_recibido_id_v1"

        /**
         * Segundo marcador one-time, para dispositivos que ya consumieron
         * [MIGRATION_PAGO_RECIBIDO_ID] en un build (af750fe) que todavía no
         * persistía `pago_recibido_id` en `Payment.PAGO_RECIBIDO_ID` (esa
         * columna llegó en mig 26->27, en este mismo fix). Sin este segundo
         * replay, esos dispositivos quedarían con filas numéricas viejas en
         * NULL para siempre y el barrido auto-sanable del reconciler nunca
         * vería el gemelo UUID a colapsar. Mismo patrón que el marcador
         * original — ver [resetPagosCursorOnce].
         */
        const val MIGRATION_PAGO_RECIBIDO_ID_PERSIST = "migration_pago_recibido_id_v1_persist"
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
