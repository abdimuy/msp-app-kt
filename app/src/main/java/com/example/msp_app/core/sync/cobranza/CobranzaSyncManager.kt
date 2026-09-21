package com.example.msp_app.core.sync.cobranza

import android.util.Log
import androidx.room.withTransaction
import com.example.msp_app.core.database.AppDatabase
import com.example.msp_app.core.database.dao.cobranzasync.CobranzaSyncStateDao
import com.example.msp_app.core.database.dao.payment.PaymentDao
import com.example.msp_app.core.database.dao.product.ProductDao
import com.example.msp_app.core.database.dao.sale.SaleDao
import com.example.msp_app.core.database.entities.CobranzaSyncStateEntity
import com.example.msp_app.core.network.ConnectivityMonitor
import com.example.msp_app.core.telemetry.Telemetry
import com.example.msp_app.data.api.services.cobranza.PagoDto
import com.example.msp_app.data.api.services.cobranza.V2CobranzaApi
import com.example.msp_app.data.api.services.cobranza.VentaDto
import com.example.msp_app.data.api.services.cobranza.conSaldoAjustadoPorPagosEnVuelo
import com.example.msp_app.data.api.services.cobranza.toEntity
import com.example.msp_app.data.models.product.toEntity
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
    private val productDao: ProductDao,
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
    private val backgroundDispatcher: CoroutineDispatcher = Dispatchers.IO,
    /**
     * Puerto de telemetría (`:core:telemetry`). Default [NoOpTelemetry] para
     * que cualquier call site que no la quiera siga construyendo el manager
     * sin cambios; producción lo cablea en [CobranzaSyncProvider].
     *
     * Ver [CobranzaSyncTelemetry] para QUÉ se emite, por qué esos campos, y
     * las dos garantías duras (cero PII / no degradar el sync).
     */
    telemetry: Telemetry = NoOpTelemetry,
    /**
     * Reloj MONÓTONO en nanosegundos para medir cuánto dura una corrida
     * (`duration_ms` del evento `cobranza_sync.run`). Inyectable sólo para que
     * las pruebas puedan afirmar una duración exacta; producción usa
     * `System.nanoTime()`. No es un reloj de pared — no sirve ni pretende
     * servir para fechar nada, sólo para restar.
     */
    private val nanoTime: () -> Long = System::nanoTime
) {

    private val syncTelemetry = CobranzaSyncTelemetry(telemetry)

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
        val startedAtNanos = nanoTime()
        return mutex.withLock {
            val ctx = userContextFlow.value ?: run {
                Log.i(TAG, "skip: usuario sin contexto (zona/FECHA_CARGA_INICIAL no disponibles)")
                syncTelemetry.runFinished(
                    zona = null,
                    outcome = SyncRunOutcome.SKIPPED_NO_ZONE,
                    pages = 0,
                    rows = 0,
                    durationMillis = elapsedMillisSince(startedAtNanos),
                    advanced = null
                )
                return SyncOutcome.SkippedNoZone
            }
            val zona = ctx.zona
            val desdeIso = ctx.fechaCargaInicial?.toString()
            // Antes del check de conectividad a propósito: es una limpieza
            // puramente local, y un cobrador sin señal también merece ver sus
            // totales bien.
            purgeLegacyTwinsOnce()
            if (!connectivity.isNetworkAvailable()) {
                Log.i(TAG, "skip: offline (zona=$zona)")
                syncTelemetry.runFinished(
                    zona = zona,
                    outcome = SyncRunOutcome.SKIPPED_OFFLINE,
                    pages = 0,
                    rows = 0,
                    durationMillis = elapsedMillisSince(startedAtNanos),
                    advanced = null
                )
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
            // Acumuladores de la corrida para el evento `cobranza_sync.run`.
            // Se declaran FUERA del try para que el camino de error también
            // pueda reportar lo que alcanzó a bajar antes de reventar.
            var runPages = 0
            var runRows = 0
            var runAdvanced: Boolean? = null
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
                val pagosRun = syncResource(RESOURCE_PAGOS, zona) { cursor, afterId ->
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
                        // null (no 0) cuando la página viene vacía: "sin filas
                        // no hay posición nueva". Ver [SyncPage.afterId].
                        afterId = response.items.lastOrNull()?.impte_docto_cc_id,
                        size = response.items.size,
                        epoch = response.sync_epoch,
                        apply = { mergePagos(response.items) }
                    )
                }
                val pagos = pagosRun.applied
                runPages += pagosRun.pages
                runRows += pagosRun.applied
                runAdvanced = syncTelemetry.resourceSynced(
                    zona = zona,
                    resource = RESOURCE_PAGOS,
                    pages = pagosRun.pages,
                    rows = pagosRun.applied,
                    before = pagosRun.before,
                    after = pagosRun.after,
                    epochReplayed = pagosRun.epochReplayed
                )
                val ventasRun = syncResource(RESOURCE_VENTAS, zona) { cursor, afterId ->
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
                        // Ídem pagos: null = página vacía, la posición no se
                        // mueve. Ver [SyncPage.afterId].
                        afterId = response.items.lastOrNull()?.docto_cc_id,
                        size = response.items.size,
                        epoch = response.sync_epoch,
                        apply = { mergeVentas(response.items, desdeIso) }
                    )
                }
                val ventas = ventasRun.applied
                runPages += ventasRun.pages
                runRows += ventasRun.applied
                val ventasAdvanced = syncTelemetry.resourceSynced(
                    zona = zona,
                    resource = RESOURCE_VENTAS,
                    pages = ventasRun.pages,
                    rows = ventasRun.applied,
                    before = ventasRun.before,
                    after = ventasRun.after,
                    epochReplayed = ventasRun.epochReplayed
                )
                // La corrida "avanzó" si CUALQUIERA de los dos recursos movió su
                // posición: un solo recurso atorado ya no queda tapado por el
                // otro, porque el evento por recurso lo reporta aparte. `null`
                // (la emisión falló) se trata como "no aportó avance", nunca
                // como avance — un dato ausente no puede parecer salud.
                runAdvanced = (runAdvanced ?: false) || (ventasAdvanced ?: false)
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
                syncTelemetry.runFinished(
                    zona = zona,
                    outcome = SyncRunOutcome.OK,
                    pages = runPages,
                    rows = runRows,
                    durationMillis = elapsedMillisSince(startedAtNanos),
                    advanced = runAdvanced
                )
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
                syncTelemetry.runFinished(
                    zona = zona,
                    outcome = SyncRunOutcome.ERROR,
                    pages = runPages,
                    rows = runRows,
                    durationMillis = elapsedMillisSince(startedAtNanos),
                    advanced = runAdvanced
                )
                SyncOutcome.Error(e)
            }
        }
    }

    /** Milisegundos transcurridos desde [startNanos] según el reloj monótono. */
    private fun elapsedMillisSince(startNanos: Long): Long =
        (nanoTime() - startNanos) / NANOS_PER_MILLI

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
     * Purga one-time del histórico que dejó el sync legacy (Node) y que el
     * canal v2 duplicó al bajar el mismo pago con otra llave — la causa de
     * que el cobrador viera todos sus números exactamente al doble tras la
     * migración del 2026-08-13.
     *
     * [mergePagos] colapsa el gemelo de cada pago que baja, pero el sync
     * incremental jamás vuelve a mandar el histórico (su `UPDATED_AT` no
     * cambió), así que esas filas nunca pasarían por ahí. Se limpian una vez
     * por dispositivo con [PaymentDao.deleteLegacyTwins], que solo borra la
     * fila legacy cuando su gemelo numérico ya está en local.
     *
     * A diferencia de [resetPagosCursorOnce], el marcador se persiste
     * DESPUÉS del borrado: es una sola sentencia atómica, así que si el
     * proceso muere antes, la purga simplemente se reintenta en el próximo
     * arranque. Es idempotente de todas formas.
     */
    private suspend fun purgeLegacyTwinsOnce() {
        if (syncStateDao.get(MIGRATION_PURGE_LEGACY_PAGO_IDS) != null) return
        val borrados = paymentDao.deleteLegacyTwins()
        Log.i(
            TAG,
            "migración $MIGRATION_PURGE_LEGACY_PAGO_IDS: $borrados gemelo(s) legacy purgados"
        )
        syncStateDao.upsert(
            CobranzaSyncStateEntity(
                RESOURCE = MIGRATION_PURGE_LEGACY_PAGO_IDS,
                ZONA_CLIENTE_ID = 0,
                CURSOR = null,
                LAST_SYNCED_AT = Instant.now().toString(),
                LAST_ERROR = null
            )
        )
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

    /**
     * Pagina un recurso por `(cursor, after_id)` aplicando cada página, con
     * **resync por generación**: si el `sync_epoch` que trae la respuesta no
     * coincide con el que este dispositivo tiene aplicado
     * ([CobranzaSyncStateEntity.EPOCH]), el cursor se descarta y el recurso se
     * replica completo desde el inicio.
     *
     * Por qué existe: el sync es incremental por cursor, así que cuando cambia
     * lo que el SERVIDOR proyecta (no el dato de origen), las filas ya
     * guardadas no vuelven a bajar — su `UPDATED_AT` no cambió. Antes cada
     * incidente de ese tipo se arreglaba con un marcador nuevo hardcodeado y
     * un APK por incidente; la generación lo resuelve sin tocar la app.
     *
     * Reglas del mecanismo, todas deliberadas:
     *
     *  - **El epoch se persiste SOLO cuando el replay terminó** (última
     *    página, `has_more == false`). Si el proceso muere a media descarga,
     *    la generación guardada sigue siendo la vieja, las generaciones siguen
     *    difiriendo y el próximo arranque replica otra vez. El costo de
     *    equivocarse por ese lado es ancho de banda; por el otro sería un
     *    replay a medias congelado para siempre — que es justo el defecto que
     *    los marcadores `MIGRATION_*` documentan de sí mismos.
     *  - **Un solo reinicio por corrida**: el reinicio exige `cursor != null`
     *    y lo deja en null, así que la primera página del replay ya no puede
     *    volver a disparar otro. Sin ese cerrojo, un epoch distinto reiniciaría
     *    la paginación en cada página.
     *  - **Epoch inválido (ausente, nulo, 0 o negativo) = mecanismo apagado**:
     *    no limpia el cursor y no sobrescribe el epoch guardado. Un servidor
     *    viejo que no manda el campo se comporta exactamente como antes, y
     *    ninguna ruta que produzca un cero por default puede meter al cliente
     *    en un bucle de replays. Un epoch que RETROCEDE sí es una generación
     *    distinta: replica una vez y guarda el valor nuevo — la generación es
     *    identidad, no orden.
     *  - **Si el epoch cambia a media paginación** (el servidor subió de
     *    generación mientras descargábamos), no se persiste ninguno: el
     *    resultado es una mezcla de dos generaciones y el próximo arranque
     *    vuelve a replicar desde cero.
     */
    private suspend fun syncResource(
        resource: String,
        zona: Int,
        fetchPage: suspend (cursor: String?, afterId: Int) -> SyncPage
    ): ResourceSyncRun {
        var applied = 0
        var pagesFetched = 0
        var epochReplayed = false
        val state = syncStateDao.get(resource)
        // Generación ya replicada por completo. Constante durante toda la
        // corrida: es lo que se sigue escribiendo en cada página intermedia.
        val appliedEpoch = state?.EPOCH
        var cursor: String? = state?.CURSOR
        // `cursor` y `afterId` son UN cursor partido en dos columnas: el
        // servidor pagina por el par `(UPDATED_AT, PK)` y sin la segunda
        // mitad no hay forma de distinguir entre las filas empatadas en el
        // mismo `UPDATED_AT`. Por eso se lee del estado guardado y se
        // reescribe junto al cursor en cada página (ver el upsert de abajo).
        //
        // Antes solo se persistía el cursor y cada corrida arrancaba en
        // `afterId = 0`, con el argumento de que reprocesar el inicio del
        // grupo empatado solo gastaba red. El argumento asumía un grupo
        // chico: el backfill de migración dejó 1,835,734 de 2,173,422 filas
        // compartiendo un único `UPDATED_AT`, así que el grupo empatado es el
        // historial completo, la paginación nunca sale de él y el ciclo se
        // repite para siempre (medido en un teléfono: 2,057 pagos
        // re-descargados cada ~76 s).
        //
        // Invariante que hay que sostener: donde se escribe uno se escribe el
        // otro, y todo camino que deje el cursor en null debe dejar
        // `afterId = 0` en la misma operación — el replay por generación de
        // abajo lo hace explícito; los caminos de limpieza
        // (`syncStateDao.clear`) borran la fila entera, que lo cumple por
        // construcción.
        var afterId = state?.AFTER_ID ?: 0
        // Fotografía de la posición con la que ARRANCA la corrida. Es el otro
        // extremo de la comparación que hace visible el defecto D1: si al
        // terminar la corrida la posición es la misma y aun así se aplicaron
        // filas, el recurso está re-bajando el mismo lote (ver
        // [CobranzaSyncTelemetry]).
        val positionBefore = SyncCursorPosition(cursor, afterId)
        var firstPageSeen = false
        var runEpoch: Int? = null
        var epochStable = true
        while (true) {
            val page = fetchPage(cursor, afterId)
            pagesFetched++
            val serverEpoch = page.epoch?.takeIf { it > 0 }
            if (!firstPageSeen) {
                if (serverEpoch != null && serverEpoch != appliedEpoch && cursor != null) {
                    Log.i(
                        TAG,
                        "$resource: generación $appliedEpoch -> $serverEpoch, replay desde el inicio"
                    )
                    // Los dos, juntos: un replay que arrancara sin cursor
                    // pero con el `afterId` de la corrida anterior se
                    // saltaría el principio del grupo empatado.
                    cursor = null
                    afterId = 0
                    epochReplayed = true
                    continue
                }
                firstPageSeen = true
                runEpoch = serverEpoch
            } else if (serverEpoch != runEpoch) {
                epochStable = false
            }
            page.apply.invoke()
            applied += page.size
            val nextCursor = page.cursor
            afterId = when {
                // Página con filas: la posición nueva es la última fila, y va
                // con el cursor que esa misma fila define.
                page.afterId != null -> page.afterId
                // Página vacía con el MISMO cursor (lo que el servidor
                // garantiza: sin filas devuelve el cursor recibido) — la
                // posición dentro del grupo empatado sigue siendo válida y
                // tiene que conservarse. Ponerla en 0 aquí reintroduciría el
                // defecto un tick después: el siguiente arranque volvería a
                // bajar el grupo entero.
                nextCursor == cursor -> afterId
                // Cursor movido sin filas: no debería pasar, pero si pasa el
                // `afterId` viejo pertenece a OTRO cursor y aplicarlo saltaría
                // filas — un hueco en el cache que ningún sync posterior
                // repone. Arrancar el grupo nuevo desde el principio solo
                // cuesta red.
                else -> 0
            }
            cursor = nextCursor
            val lastPage = !page.hasMore
            val epochToPersist = if (lastPage && epochStable) {
                runEpoch ?: appliedEpoch
            } else {
                appliedEpoch
            }
            syncStateDao.upsert(
                CobranzaSyncStateEntity(
                    RESOURCE = resource,
                    ZONA_CLIENTE_ID = zona,
                    CURSOR = cursor,
                    LAST_SYNCED_AT = Instant.now().toString(),
                    LAST_ERROR = null,
                    EPOCH = epochToPersist,
                    // La otra mitad del cursor, en la MISMA escritura: si se
                    // guardara aparte (o no se guardara), un arranque a media
                    // corrida retomaría el grupo empatado desde el inicio.
                    AFTER_ID = afterId
                )
            )
            if (lastPage) break
        }
        return ResourceSyncRun(
            applied = applied,
            pages = pagesFetched,
            before = positionBefore,
            after = SyncCursorPosition(cursor, afterId),
            epochReplayed = epochReplayed
        )
    }

    /**
     * Merge contract:
     *
     *  - `cargo_cancelado` → tombstone real: borra la venta y los pagos del
     *    cargo YA confirmados (cancelación en Microsip es definitiva). Las
     *    capturas del cobrador aún sin subir sobreviven — ver
     *    [PaymentDao.deleteByDoctoCcAcrId]: fallar al subirlas contra un cargo
     *    cancelado es recuperable desde el escritorio, perderlas no.
     *
     *  - `saldo > 0` o sin `desdeIso` → upsert normal preservando el estado
     *    local del cobrador (ESTADO_COBRANZA, DIA_TEMPORAL_COBRANZA).
     *
     *  - `saldo <= 0` con `desdeIso` → mantener la venta solo si tiene al
     *    menos un pago dentro de la ventana del cobrador. Sin pagos en
     *    ventana, la venta se borra (los pagos se conservan por SAT y para
     *    los reportes históricos).
     *
     *  - Productos: cada rama que borra o upsertea la venta reemplaza sus
     *    productos por folio (`productDao.deleteByFolio` + `saveAll`), así
     *    el backend Go queda como única fuente — ya no se descargan por el
     *    endpoint Node legacy.
     */
    private suspend fun mergeVentas(items: List<VentaDto>, desdeIso: String?) {
        for (dto in items) {
            if (dto.cargo_cancelado) {
                saleDao.deleteByDoctoCcId(dto.docto_cc_id)
                paymentDao.deleteByDoctoCcAcrId(dto.docto_cc_id)
                productDao.deleteByFolio(dto.folio)
                continue
            }
            val saldo = dto.saldo.toDoubleOrNull() ?: 0.0
            if (saldo <= 0.0 && desdeIso != null) {
                val pagosEnVentana = paymentDao.countPagosDesde(dto.docto_cc_id, desdeIso)
                if (pagosEnVentana <= 0) {
                    // Saldada fuera de la ventana del cobrador — la quito
                    // de la lista visible. Los Payment quedan intactos.
                    saleDao.deleteByDoctoCcId(dto.docto_cc_id)
                    productDao.deleteByFolio(dto.folio)
                    continue
                }
                // Saldada con pagos en ventana → cae al upsert normal.
            }
            val existing = saleDao.findByDoctoCcId(dto.docto_cc_id)
            // El saldo del servidor menos lo que el servidor todavía no ha
            // visto. NO es preservar el valor local: se parte SIEMPRE del que
            // llega, así que una cancelación de oficina, el pago de otro
            // cobrador o una condonación entran en el mismo tick. Ver
            // [PaymentDao.sumImporteNoReconocidoPorElServidor].
            val incoming = dto.toEntity().conSaldoAjustadoPorPagosEnVuelo(
                paymentDao.sumImporteNoReconocidoPorElServidor(dto.docto_cc_id)
            )
            val merged = if (existing == null) {
                incoming
            } else {
                incoming.copy(
                    // ESTADO_COBRANZA y DIA_TEMPORAL_COBRANZA sí se preservan
                    // tal cual: son del cobrador, el servidor no los posee.
                    ESTADO_COBRANZA = existing.ESTADO_COBRANZA,
                    DIA_TEMPORAL_COBRANZA = existing.DIA_TEMPORAL_COBRANZA
                )
            }
            saleDao.insertAll(listOf(merged))
            productDao.deleteByFolio(dto.folio)
            productDao.saveAll(dto.productos.map { it.toEntity() })
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
     *  - Gemelo legacy → colapso por `docto_cc_id`: el sync Node guardaba el
     *    mismo abono con otra llave (`MSP_PAGOS_RECIBIDOS.ID` si venía de la
     *    app, `"<DOCTO_CC_ID>-<IMPTE_DOCTO_CC_ID>"` si se capturó en
     *    oficina), y como `Payment.ID` es la PK, Room lo trata como un pago
     *    distinto del que baja por v2 con `IMPTE_DOCTO_CC_ID`. Se borra la
     *    fila legacy del mismo documento de pago
     *    ([PaymentDao.deleteLegacyTwinsByDoctoCcIds]). No se apoya en
     *    `pago_recibido_id` porque es NULL para todo el histórico anterior al
     *    cutover: el Node nunca escribió
     *    `MSP_PAGOS_RECIBIDOS.IMPTE_DOCTO_CC_ID`, que es por donde el backend
     *    Go lo resuelve.
     *
     *  - `pago_recibido_id` non-null → colapso de gemelo UUID: el pago
     *    entrante es la versión numérica (IMPTE_DOCTO_CC_ID) de un pago que
     *    el app capturó offline con un UUID local. Se borra la fila UUID
     *    local si sigue existiendo, para que "Historial de pagos" no muestre
     *    el mismo pago dos veces. Solo se borra por el `pago_recibido_id`
     *    exacto — nunca por contenido/monto.
     *
     *    El colapso **ya no exige `GUARDADO_EN_MICROSIP = 1`** en la fila
     *    UUID. Esa bandera dice lo que este teléfono alcanzó a anotar; el
     *    `pago_recibido_id` dice lo que el servidor efectivamente hizo, y es
     *    la evidencia más fuerte de las dos: prueba que el pago ya está en
     *    Microsip con su id asignado. La carrera es estructural, no un caso
     *    raro — el aviso del servidor sale dentro de la misma transacción que
     *    escribe el pago, así que puede llegar antes de que
     *    `PendingPaymentsWorker.markDone` marque la bandera; y si la respuesta
     *    HTTP nunca llega (timeout), la bandera se queda en 0 para siempre y
     *    el duplicado se vuelve permanente. Lo que protege a una captura que
     *    NUNCA subió es que el servidor no puede nombrar un UUID que no
     *    recibió (ver [PaymentDao.filterExistingIDs]).
     *
     *    La auto-referencia (un DTO que trajera su propio
     *    `impte_docto_cc_id` como `pago_recibido_id`) no necesita cerrojo
     *    aquí: todos los DELETE corren antes del `saveAll`, así que la fila
     *    canónica se vuelve a escribir en la misma transacción. El cerrojo sí
     *    existe donde importa, en [PaymentDao.findCollapsibleUuidTwins], que
     *    borra sin reponer nada.
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
        // Documentos de pago que trae esta página: la llave con la que se
        // localiza la fila que dejó el sync legacy para el mismo abono.
        val doctoCcIds = items.map { it.docto_cc_id }.filter { it > 0 }.distinct()
        db.withTransaction {
            // Gemelo legacy (UUID de captura o "<docto>-<impte>" de oficina):
            // el canal Node guardaba el pago con una llave distinta a la del
            // canal v2 (IMPTE_DOCTO_CC_ID puro), así que sin este borrado el
            // mismo pago queda dos veces en Room y el cobrador ve todos sus
            // totales al doble. Aplica a `items` completo — vivos y
            // tombstones: si el cargo se canceló, el gemelo viejo también se
            // va. Un solo DELETE por página, no uno por fila.
            if (doctoCcIds.isNotEmpty()) {
                val legacyTwins = paymentDao.deleteLegacyTwinsByDoctoCcIds(doctoCcIds)
                if (legacyTwins > 0) {
                    Log.i(TAG, "mergePagos: colapsando $legacyTwins gemelo(s) legacy")
                }
            }
            if (pagoRecibidoIds.isNotEmpty()) {
                val uuidTwins = paymentDao.filterExistingIDs(pagoRecibidoIds)
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

        /*
         * Los tres marcadores de abajo son el mecanismo VIEJO de resync
         * one-time: un marcador hardcodeado (y por tanto un APK) por
         * incidente. El resync por generación de [syncResource] los sustituye
         * para todo lo que venga; se conservan porque siguen sin consumir en
         * los dispositivos que aún no instalan este build y su costo es una
         * fila centinela. No agregues marcadores nuevos aquí: sube el
         * `sync_epoch` del servidor.
         */

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

        /**
         * Marcador one-time de [purgeLegacyTwinsOnce]: la purga del histórico
         * que el sync legacy guardó con llave propia (UUID de captura o
         * `"<DOCTO_CC_ID>-<IMPTE_DOCTO_CC_ID>"`) y que el canal v2 duplicó al
         * re-bajarlo como `IMPTE_DOCTO_CC_ID` numérico.
         */
        const val MIGRATION_PURGE_LEGACY_PAGO_IDS = "migration_purge_legacy_pago_ids_v1"
        private const val TAG = "CobranzaSyncManager"
        private const val NANOS_PER_MILLI = 1_000_000L
    }
}

/**
 * Lo que dejó una corrida de [CobranzaSyncManager.syncResource] sobre UN
 * recurso. Antes esta función devolvía sólo `Int` (filas aplicadas); ahora
 * devuelve además lo que hace falta para responder "¿avanzó?" sin inferirlo:
 * las posiciones de arranque y de cierre, cuántas páginas costó y si hubo
 * replay por generación.
 *
 * OJO: "¿avanzó?" NO se decide comparando [before] con [after] — eso sería
 * ciego al defecto D1, donde cada corrida arrancaba en `afterId = 0` y cerraba
 * en el final del lote, así que la posición "cambiaba" dentro de la corrida
 * mientras el dispositivo re-bajaba lo mismo eternamente. Lo decide
 * [CobranzaSyncTelemetry] comparando cierre contra cierre; acá sólo se
 * transportan los dos extremos.
 */
private data class ResourceSyncRun(
    val applied: Int,
    val pages: Int,
    val before: SyncCursorPosition,
    val after: SyncCursorPosition,
    val epochReplayed: Boolean
)

/**
 * Per-resource page descriptor. `apply` is invoked once `cursor` is the
 * server's `max_updated_at`, allowing the caller to advance even on the
 * empty page so we don't replay history forever.
 */
private data class SyncPage(
    val cursor: String,
    val hasMore: Boolean,
    /**
     * PK de la última fila de la página: la segunda mitad del cursor
     * `(UPDATED_AT, PK)` con el que se pide la página siguiente.
     *
     * `null` cuando la página vino vacía, que NO es lo mismo que 0: el
     * servidor devuelve el cursor recibido tal cual cuando no hay filas (ver
     * `SyncPage.MaxUpdatedAt` en el backend), así que la posición dentro del
     * grupo de filas empatadas en ese `UPDATED_AT` sigue siendo la misma.
     * Traducir "vacía" a 0 haría que el siguiente arranque volviera a bajar el
     * grupo empatado completo — el mismo bucle que persistir `after_id` viene
     * a cerrar, solo que un tick después.
     */
    val afterId: Int?,
    val size: Int,
    /**
     * Generación de la proyección del servidor para este recurso, tal como
     * llegó en la respuesta. Null cuando el servidor no la manda (versión
     * previa al campo) — ver [CobranzaSyncManager.syncResource] para las
     * reglas de replay.
     */
    val epoch: Int?,
    val apply: suspend () -> Unit
)

sealed class SyncOutcome {
    object SkippedOffline : SyncOutcome()
    object SkippedNoZone : SyncOutcome()
    data class Ok(val ventasApplied: Int, val pagosApplied: Int) : SyncOutcome()
    data class Error(val cause: Throwable) : SyncOutcome()
}
