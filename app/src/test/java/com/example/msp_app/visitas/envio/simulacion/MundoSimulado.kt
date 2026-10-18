package com.example.msp_app.visitas.envio.simulacion

import android.content.Context
import com.example.msp_app.core.common.sync.pendingwork.domain.ports.SyncErrorReporter
import com.example.msp_app.core.common.sync.pendingwork.domain.ports.VisitsWorkEnqueuer
import com.example.msp_app.core.common.sync.pendingwork.domain.usecases.ReconcileDeliveredVisitsUseCase
import com.example.msp_app.core.common.sync.pendingwork.domain.usecases.ReconcileVisitsUseCase
import com.example.msp_app.core.common.time.AppTime
import com.example.msp_app.core.database.AppDatabase
import com.example.msp_app.core.database.dao.visit.VisitDao
import com.example.msp_app.core.database.dao.visit.VisitImageDao
import com.example.msp_app.core.database.entities.VisitImageEntity
import com.example.msp_app.core.network.diagnostico.EnvioDiagnosticoSink
import com.example.msp_app.core.sync.pendingwork.data.visits.RoomDeliveredVisitsStore
import com.example.msp_app.core.sync.pendingwork.data.visits.RoomPendingVisitsStore
import com.example.msp_app.core.sync.pendingwork.data.visits.V2VisitCustodyRegistry
import com.example.msp_app.core.testing.time.FakeClock
import com.example.msp_app.core.upload.HEADER_INTENT_CAPTURED
import com.example.msp_app.core.visitas.envio.ModoEnvioVisitas
import com.example.msp_app.data.api.services.visits.CrearVisitaBody
import com.example.msp_app.data.api.services.visits.V2VisitsApi
import com.example.msp_app.data.api.services.visits.VisitaDTO
import com.example.msp_app.data.api.services.visits.VisitaDetalleDTO
import com.example.msp_app.data.local.datasource.visit.MemoriaDePodaDeResguardadas
import com.example.msp_app.data.local.datasource.visit.VisitsLocalDataSource
import com.example.msp_app.workers.construirPendingVisitsWorker
import com.example.msp_app.workers.httpError
import com.example.msp_app.workers.httpErrorConCabecera
import com.example.msp_app.workers.pendingVisit
import com.example.msp_app.workers.visitaDetalle
import com.example.msp_app.workers.visitaDto
import java.io.File
import java.io.IOException
import java.time.Instant
import java.util.UUID
import kotlin.random.Random
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.job
import kotlinx.coroutines.supervisorScope
import okhttp3.MultipartBody
import okhttp3.RequestBody
import okio.Buffer

/**
 * Fallas que siembra el [MundoSimulado]. Mismo catálogo de red que la
 * simulación Go de la Tarea 16 (msp-api `internal/visitas/sim/red_test.go`),
 * menos las que solo existen en el servidor (retenidas, candados, commit
 * ambiguo): el servidor aquí es un mapa y esas ya las cubre la simulación Go
 * con el `app.Service` real.
 */
enum class Falla(val nombre: String) {
    PERDER_PETICION("perder_peticion"),
    PERDER_RESPUESTA("perder_respuesta_tras_commit"),
    DUPLICAR("duplicar"),
    CUERPO_200_TRUNCADO("cuerpo_200_truncado"),
    PROXY_200_AJENO("proxy_200_ajeno"),
    STATUS_503("status_503"),
    GET_PERDIDO("get_perdido"),
    GET_503("get_503"),
    BY_IDS_FALLA("by_ids_falla")
}

/**
 * El mundo de la simulación Kotlin del envío de visitas (plan visitas P1,
 * Tarea 17; E-APP-076). Lo que corre es código de producción:
 *  - el worker REAL ([com.example.msp_app.workers.PendingVisitsWorker]) vía
 *    `TestListenableWorkerBuilder`, con sus DAO reales de Room en memoria;
 *  - la reconciliación de pendientes real ([ReconcileVisitsUseCase] +
 *    [RoomPendingVisitsStore]) y la inversa real
 *    ([ReconcileDeliveredVisitsUseCase] + [RoomDeliveredVisitsStore]), las
 *    dos sobre [V2VisitCustodyRegistry];
 *  - la poda real (`VisitsLocalDataSource.deleteUploadedVisits`) con
 *    [FakeClock] (nunca el reloj del sistema).
 *
 * Lo falso: el servidor (un mapa con la semántica que mide la Tarea 16:
 * reenvío idempotente que repara fotos faltantes, `imagen_ids` = fila Y
 * archivo, 422 con o sin `X-Intent-Captured`), la red con fallas sembradas y
 * las COSTURAS de los DAO, que delegan en el DAO real y solo agregan ganchos
 * para las carreras:
 *  - foto tardía ANTES de la relectura: `insertAll` justo después de la
 *    primera lectura de pendientes;
 *  - foto tardía ENTRE la relectura y el UPDATE: `insertAll` justo antes de
 *    `confirmarTrasSubidaSiNoHayFotosNuevas` (la ventana que solo cierra el
 *    `NOT EXISTS`);
 *  - "ya confirmada por otro camino": la reconciliación de pendientes real
 *    corre en esa misma ventana y luego entra una foto;
 *  - matar el proceso entre el 2xx y la marca: el API falso cancela la
 *    corrutina del worker en cuanto el servidor aplicó.
 *
 * Las marcas del historial NO se escriben desde el worker: se DEDUCEN
 * mirando Room después de cada acción (y dentro de los ganchos). Las fotos
 * estampadas (`SUBIDA_EN` que pasó de nulo a lleno) viajan en el
 * `estampadas=` de la siguiente `marca_confirmada`, aunque se hayan
 * estampado antes de que la visita se confirmara. La falla
 * `foto_sin_archivo_local` la emite el INYECTOR ([perderFotoLocal]) en el
 * momento en que borra el archivo; nada la deduce de que el archivo falte.
 */
@Suppress("TooManyFunctions", "LargeClass", "LongParameterList")
class MundoSimulado(
    val semilla: Long,
    private val db: AppDatabase,
    private val context: Context,
    private val carpeta: File,
    private val modo: ModoEnvioVisitas = ModoEnvioVisitas.V3,
    probabilidades: Map<Falla, Double> = PROBABILIDADES_POR_OMISION,
    /** Fracción de las visitas rechazables cuya captura del 422 falla SIEMPRE (sin fila ni cabecera). */
    private val capturaFalla: Double = 0.2
) {
    private val rnd = Random(semilla)
    private var prob: Map<Falla, Double> = probabilidades
    val clock = FakeClock(Instant.parse("2026-10-17T18:00:00Z"))
    private val h = mutableListOf<Evento>()
    private var paso = 0

    private class Local(
        val id: String,
        val rechazable: Boolean,
        val incapturable: Boolean,
        var legada: Boolean,
        var estado: String = "pendiente",
        var intentos: Int = 0,
        var ultimo2xx: Boolean = false,
        val estampadasVistas: MutableSet<String> = mutableSetOf(),
        val porReportar: MutableSet<String> = mutableSetOf(),
        var podada: Boolean = false,
        var perdida: Boolean = false
    )

    private val locales = linkedMapOf<String, Local>()

    // Servidor: visita -> fotos con fila Y archivo en disco.
    private val servidor = linkedMapOf<String, MutableSet<String>>()
    private val bandeja = linkedMapOf<String, Int>()
    private val ultimoSrv = mutableMapOf<String, String>()

    // Ganchos armados para el próximo intento.
    private var fotoTardia = false
    private var fotoTardiaTrasRelectura = false
    private var otroCamino: Boolean? = null // null = no armado; true/false = la foto trae archivo
    private var matar = false

    /** Códigos de diagnóstico que el worker anotó (E-APP-065), por cobertura. */
    val diagnosticos = mutableListOf<String>()

    private val sinkDiagnostico = EnvioDiagnosticoSink { obs ->
        obs.fallo?.codigo?.let {
            diagnosticos += it
            evento(TipoEvento.FALLA, obs.filaId, "diag=$it")
        }
    }

    private val reporter = object : SyncErrorReporter {
        override fun report(code: String, message: String, props: Map<String, String>) = Unit
    }

    private val memoriaDePoda = object : MemoriaDePodaDeResguardadas {
        private var hasta: String? = null
        override fun reportadasHasta(): String? = hasta
        override fun anotarReportadasHasta(limite: String) {
            hasta = limite
        }
    }

    private val sinEncolar = object : VisitsWorkEnqueuer {
        override fun enqueue(visitId: String) = Unit
    }

    val historial: List<Evento> get() = h

    // ─── historial ─────────────────────────────────────────────────────────

    private fun evento(tipo: String, visita: String, detalle: String) {
        paso++
        h += Evento(paso, tipo, visita, detalle)
    }

    private fun nuevoUuid(): String = UUID(rnd.nextLong(), rnd.nextLong()).toString()

    private fun ahora(): String = AppTime.toWireFormat(clock.now())

    private fun pasa(f: Falla): Boolean = rnd.nextDouble() < (prob[f] ?: 0.0)

    fun finales(): List<Evento> {
        var p = paso
        return h + locales.values.map { l ->
            p++
            Evento(p, TipoEvento.FINAL, l.id, if (l.perdida) "perdida" else l.estado)
        }
    }

    /**
     * I1–I3 e I3-foto sobre lo que va del historial. A media corrida se
     * permite la DEUDA de E-APP-079 (una foto con archivo agregada a una
     * visita ya confirmada): la cobra la siguiente reconciliación inversa.
     * [permitirDeuda] = false al final, después de drenar: ahí ya no hay
     * excusa.
     */
    fun verificarInvariantes(cuando: String, permitirDeuda: Boolean = true) {
        if (h.isEmpty()) return
        verificar(
            finales(),
            permitirDeuda = permitirDeuda
        )?.let { throw AssertionError("semilla=$semilla $cuando: ${it.message}", it) }
    }

    // ─── observación ───────────────────────────────────────────────────────

    private fun observarServidor() {
        locales.keys.forEach { id ->
            val fotos = servidor[id]
            val d = "filas=${if (fotos == null) 0 else 1} fotos=${fotos.orEmpty().sorted().joinToString(
                ","
            )}"
            if (ultimoSrv[id] != d) {
                ultimoSrv[id] = d
                evento(TipoEvento.SERVIDOR, id, d)
            }
        }
    }

    /** Deduce las marcas de Room. Ver el KDoc de la clase. */
    @Suppress("CyclomaticComplexMethod")
    suspend fun observarTelefono() {
        val dao = db.visitDao()
        locales.values.forEach { l ->
            db.visitImageDao().getByVisitaId(l.id).forEach { img ->
                if (img.SUBIDA_EN != null && l.estampadasVistas.add(img.ID)) l.porReportar += img.ID
            }
            val fila = dao.findVisitById(l.id)
            if (fila == null) {
                if (!l.podada) {
                    l.podada = true
                    if (l.estado == "pendiente") l.perdida = true
                }
                return@forEach
            }
            val nuevo = when {
                fila.CONFIRMADA_EN != null -> "confirmada"
                fila.RESGUARDADA_EN != null -> "resguardada"
                fila.GUARDADO_EN_MICROSIP == 1 && !l.legada ->
                    // Soltada SIN marca (el modo v2_actual, o un defecto): el
                    // teléfono la da por entregada según lo último que oyó.
                    if (l.ultimo2xx) "confirmada" else "resguardada"
                else -> "pendiente"
            }
            if (fila.GUARDADO_EN_MICROSIP == 0 && l.legada) {
                // La inversa devolvió a la cola una legada que el servidor no tiene.
                l.legada = false
                evento(TipoEvento.DEVUELTA, l.id, "legada")
            }
            when {
                nuevo == "confirmada" && (l.estado != "confirmada" || l.porReportar.isNotEmpty()) -> {
                    evento(
                        TipoEvento.CONFIRMADA,
                        l.id,
                        "estampadas=" + l.porReportar.sorted().joinToString(",")
                    )
                    l.porReportar.clear()
                }
                nuevo == "resguardada" && l.estado != "resguardada" -> evento(
                    TipoEvento.RESGUARDADA,
                    l.id,
                    ""
                )
                nuevo == "pendiente" && l.estado != "pendiente" -> evento(
                    TipoEvento.DEVUELTA,
                    l.id,
                    ""
                )
            }
            l.estado = nuevo
        }
    }

    // ─── acciones del teléfono ─────────────────────────────────────────────

    private suspend fun insertarFoto(visita: String, conArchivo: Boolean, orden: Int): String {
        val id = nuevoUuid()
        val archivo = File(carpeta, "$id.jpg")
        if (conArchivo) {
            archivo.writeBytes(
                byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0x00)
            )
        }
        db.visitImageDao().insertAll(
            listOf(
                VisitImageEntity(
                    ID = id,
                    VISITA_ID = visita,
                    URI = archivo.absolutePath,
                    MIME = "image/jpeg",
                    ORDEN = orden,
                    CREADA_EN = ahora()
                )
            )
        )
        return id
    }

    suspend fun capturar(fotos: Int, sinArchivo: Int, rechazable: Boolean): String {
        val id = nuevoUuid()
        val l = Local(
            id = id,
            rechazable = rechazable,
            incapturable = rechazable && rnd.nextDouble() < capturaFalla,
            legada = false
        )
        db.visitDao().insertVisit(pendingVisit(id).copy(FECHA = ahora()))
        val con = (0 until fotos).map { insertarFoto(id, true, it) }
        val sin = (0 until sinArchivo).map { insertarFoto(id, false, fotos + it) }
        locales[id] = l
        ultimoSrv[id] = "filas=0 fotos="
        evento(
            TipoEvento.CAPTURA,
            id,
            "fotos=${(con + sin).sorted().joinToString(
                ","
            )} sin_archivo=${sin.sorted().joinToString(",")}"
        )
        return id
    }

    /**
     * Visita legada de antes de v35: `GUARDADO_EN_MICROSIP = 1` con las dos
     * marcas nulas (E-VIS-001). [enServidor] = el envío viejo sí llegó.
     */
    suspend fun capturarLegada(enServidor: Boolean): String {
        val id = nuevoUuid()
        db.visitDao().insertVisit(pendingVisit(id, guardado = 1).copy(FECHA = ahora()))
        locales[id] = Local(id = id, rechazable = false, incapturable = false, legada = true)
        ultimoSrv[id] = "filas=0 fotos="
        evento(TipoEvento.CAPTURA, id, "fotos= sin_archivo=")
        if (enServidor) servidor[id] = mutableSetOf()
        observarServidor()
        return id
    }

    private fun pendientesNoLegadas(): List<Local> = locales.values.filter {
        it.estado == "pendiente" && !it.legada && !it.podada
    }

    suspend fun agregarFoto(cual: Int, conArchivo: Boolean) {
        val pend = pendientesNoLegadas()
        if (pend.isEmpty()) return
        val v = pend[cual % pend.size]
        val f = insertarFoto(v.id, conArchivo, ORDEN_TARDIA)
        evento(TipoEvento.FOTO_AGREGADA, v.id, "foto=$f archivo=${if (conArchivo) 1 else 0}")
    }

    /** INYECTOR de `foto_sin_archivo_local`: borra el archivo y lo anota en el mismo paso. */
    suspend fun perderFotoLocal(cual: Int) {
        val cs = locales.values.filter { !it.podada }.flatMap { l ->
            db.visitImageDao().getPendientesDe(
                l.id
            ).filter { File(it.URI).exists() }.map { l.id to it }
        }
        if (cs.isEmpty()) return
        val (visita, img) = cs[cual % cs.size]
        File(img.URI).delete()
        evento(TipoEvento.FALLA, visita, "$FALLA_FOTO_SIN_ARCHIVO foto=${img.ID}")
    }

    fun armarFotoTardia() {
        fotoTardia = true
    }

    fun armarFotoTardiaTrasRelectura() {
        fotoTardiaTrasRelectura = true
    }

    /** Arma "ya confirmada por otro camino": ver [VisitDaoConCostura]. */
    fun armarOtroCamino(fotoConArchivo: Boolean) {
        otroCamino = fotoConArchivo
    }

    fun armarMatar() {
        matar = true
    }

    /** Corre el worker REAL sobre la pendiente número [cual] (orden de `pendientesOrdenadas`). */
    suspend fun intentar(cual: Int) {
        val ids = db.visitDao().pendientesOrdenadas().filter { it in locales }
        if (ids.isEmpty()) return
        intentarId(ids[cual % ids.size])
    }

    suspend fun intentarId(id: String) {
        val l = locales.getValue(id)
        l.intentos++
        val worker = construirPendingVisitsWorker(
            context = context,
            imagenes = ImagenesConCostura(db.visitImageDao(), id),
            clock = clock,
            modo = modo,
            visitId = id,
            api = api,
            maxAttempts = MAX_INTENTOS_V2,
            runAttemptCount = l.intentos - 1,
            visitDao = VisitDaoConCostura(db.visitDao(), id),
            diagnostico = sinkDiagnostico
        )
        val muerto = supervisorScope {
            val trabajo = async { worker.doWork() }
            try {
                trabajo.await()
                false
            } catch (_: CancellationException) {
                true
            }
        }
        if (muerto) evento(TipoEvento.FALLA, id, "proceso_muerto_antes_de_marcar")
        fotoTardia = false
        fotoTardiaTrasRelectura = false
        otroCamino = null
        matar = false
        observarTelefono()
    }

    /** La reconciliación de pendientes de cada 15 min, real (by-ids). */
    suspend fun reconciliarPendientes() {
        ReconcileVisitsUseCase(
            RoomPendingVisitsStore(db.visitDao()),
            V2VisitCustodyRegistry(api),
            reporter
        ).execute()
        observarTelefono()
    }

    /** La reconciliación inversa diaria, real (Tarea 13). */
    suspend fun reconciliacionInversa() {
        val r = ReconcileDeliveredVisitsUseCase(
            RoomDeliveredVisitsStore(db.visitDao(), db.visitImageDao()),
            V2VisitCustodyRegistry(api),
            reporter
        ) { clock.now() }.execute()
        // Cobertura del barrido de E-APP-079 (no lo mira el verificador).
        if (r.devueltasPorFoto > 0) {
            evento(
                TipoEvento.FALLA,
                "",
                "barrido_devuelve n=${r.devueltasPorFoto}"
            )
        }
        if (r.fotosAbandonadas > 0) {
            evento(
                TipoEvento.FALLA,
                "",
                "barrido_abandona n=${r.fotosAbandonadas}"
            )
        }
        observarTelefono()
    }

    /** Avanza el reloj falso y corre la poda real (Tarea 12). */
    /**
     * Avanza el reloj falso [dias] días, en saltos de a lo más
     * [DIAS_POR_SALTO]: en cada salto corre la reconciliación inversa (el
     * trabajo diario de un teléfono que se usa) y la poda real (Tarea 12).
     * Sin la inversa en el camino, una foto sin enviar envejecería más allá
     * de la ventana del barrido sin que nadie la mirara (E-APP-079).
     */
    suspend fun avanzarYPodar(dias: Long) {
        var resta = dias
        while (resta > 0) {
            val salto = minOf(resta, DIAS_POR_SALTO)
            resta -= salto
            clock.advanceDays(salto)
            reconciliacionInversa()
            VisitsLocalDataSource(
                db.visitDao(),
                db.saleDao(),
                sinEncolar,
                clock,
                memoriaDePoda
            ).deleteUploadedVisits()
        }
        evento(TipoEvento.FALLA, "", "reloj_avanza dias=$dias")
        observarTelefono()
    }

    /** El archivo de una foto desaparece en el servidor (solo de visitas que el teléfono no confirmó). */
    fun borrarArchivoServidor(cual: Int) {
        val cs = locales.values.filter { it.estado != "confirmada" && !it.podada }
            .flatMap { l -> servidor[l.id].orEmpty().map { l.id to it } }
        if (cs.isEmpty()) return
        val (visita, foto) = cs[cual % cs.size]
        servidor.getValue(visita).remove(foto)
        evento(TipoEvento.FALLA, visita, "archivo_borrado")
        observarServidor()
    }

    // ─── la máquina aleatoria ──────────────────────────────────────────────

    @Suppress("MagicNumber", "CyclomaticComplexMethod")
    suspend fun accionAleatoria() {
        clock.advanceMinutes(1)
        when (rnd.nextInt(100)) {
            in 0 until 14 -> capturar(
                rnd.nextInt(3),
                if (rnd.nextInt(4) == 0) 1 else 0,
                rnd.nextInt(10) == 0
            )
            in 14 until 17 -> capturarLegada(rnd.nextBoolean())
            in 17 until 22 -> agregarFoto(rnd.nextInt(1000), rnd.nextInt(10) < 7)
            in 22 until 26 -> perderFotoLocal(rnd.nextInt(1000))
            in 26 until 30 -> armarFotoTardia()
            in 30 until 34 -> armarFotoTardiaTrasRelectura()
            // Con y sin archivo: con archivo es el HALLAZGO de E-APP-076, que
            // cierra el barrido de E-APP-079.
            in 34 until 37 -> armarOtroCamino(fotoConArchivo = rnd.nextBoolean())
            in 37 until 41 -> armarMatar()
            in 41 until 46 -> reconciliacionInversa()
            in 46 until 50 -> reconciliarPendientes()
            in 50 until 53 -> borrarArchivoServidor(rnd.nextInt(1000))
            in 53 until 57 -> avanzarYPodar(1L + rnd.nextInt(40))
            else -> intentar(rnd.nextInt(1000))
        }
    }

    /**
     * Red perfecta y sin ganchos; inversa + drenado hasta que nada cambie.
     * Lo que sigue pendiente después solo puede ser una rechazable
     * incapturable (sin fila en la bandeja, el teléfono la conserva: I1).
     */
    suspend fun repararRedYDrenar() {
        prob = emptyMap()
        repeat(RONDAS_DE_DRENADO) {
            reconciliacionInversa()
            val pend = db.visitDao().pendientesOrdenadas().filter { it in locales }
            if (pend.isEmpty()) return
            pend.forEach { intentarId(it) }
        }
    }

    suspend fun verificarFinal() {
        verificarInvariantes("final", permitirDeuda = false)
        val sinDecidir = locales.values.filter {
            !it.podada && it.estado == "pendiente" && !it.incapturable
        }
        if (sinDecidir.isNotEmpty()) {
            throw AssertionError(
                "semilla=$semilla vivacidad: con la red reparada siguen pendientes " +
                    sinDecidir.map { "${it.id} legada=${it.legada}" }
            )
        }
        val soltadasSinMarca = db.visitDao().entregadasDesde("").filter { id ->
            db.visitDao().findVisitById(id)?.CONFIRMADA_EN == null
        }
        if (soltadasSinMarca.isNotEmpty()) {
            throw AssertionError(
                "semilla=$semilla soltadas sin marca tras drenar: $soltadasSinMarca"
            )
        }
    }

    fun violaciones(): InvarianteError? = if (h.isEmpty()) {
        null
    } else {
        verificar(
            finales(),
            permitirDeuda = false
        )
    }

    fun escribirHistorial(archivo: File) = escribirJsonl(archivo, finales())

    // ─── el servidor y la red ──────────────────────────────────────────────

    private fun idsDeFotos(partes: List<MultipartBody.Part>): List<String> =
        partes.mapNotNull { p ->
            val disp = p.headers?.get("Content-Disposition").orEmpty()
            if (!Regex("name=\"id_\\d+\"").containsMatchIn(disp)) return@mapNotNull null
            Buffer().also { p.body.writeTo(it) }.readUtf8()
        }

    private fun aplicar(id: String, fotos: List<String>) {
        // Reenvío idempotente por id que repara las fotos faltantes (planImagenes, E-VIS-004).
        servidor.getOrPut(id) { mutableSetOf() }.addAll(fotos)
    }

    @Suppress("ThrowsCount")
    private suspend fun post(id: String, fotos: List<String>): VisitaDTO {
        currentCoroutineContext().ensureActive()
        val l = locales.getValue(id)
        l.ultimo2xx = false
        evento(
            TipoEvento.ENVIO,
            id,
            "intento=${l.intentos} multipart=${if (fotos.isEmpty()) 0 else 1}"
        )
        if (pasa(Falla.PERDER_PETICION)) {
            evento(TipoEvento.FALLA, id, Falla.PERDER_PETICION.nombre)
            evento(TipoEvento.RESPUESTA, id, "io")
            throw IOException("sim: petición perdida")
        }
        if (pasa(Falla.STATUS_503)) {
            evento(TipoEvento.FALLA, id, Falla.STATUS_503.nombre)
            evento(TipoEvento.RESPUESTA, id, "http=503 eco=- cabecera=0")
            throw httpError(HTTP_503)
        }
        if (l.rechazable) {
            if (l.incapturable) {
                evento(TipoEvento.RESPUESTA, id, "http=422 eco=- cabecera=0")
                throw httpError(HTTP_422)
            }
            bandeja[id] = paso
            evento(TipoEvento.BANDEJA, id, "http=422 entera=1 cabecera=1")
            evento(TipoEvento.RESPUESTA, id, "http=422 eco=- cabecera=1")
            throw httpErrorConCabecera(HTTP_422, HEADER_INTENT_CAPTURED, nuevoUuid())
        }
        aplicar(id, fotos)
        if (pasa(Falla.DUPLICAR)) {
            evento(TipoEvento.FALLA, id, Falla.DUPLICAR.nombre)
            aplicar(id, fotos)
        }
        observarServidor()
        if (matar) {
            matar = false
            currentCoroutineContext().job.cancel()
        }
        if (pasa(Falla.PERDER_RESPUESTA)) {
            evento(TipoEvento.FALLA, id, Falla.PERDER_RESPUESTA.nombre)
            evento(TipoEvento.RESPUESTA, id, "io")
            throw IOException("sim: respuesta perdida tras el commit")
        }
        val eco = when {
            pasa(Falla.CUERPO_200_TRUNCADO) -> null.also {
                evento(TipoEvento.FALLA, id, Falla.CUERPO_200_TRUNCADO.nombre)
            }
            pasa(Falla.PROXY_200_AJENO) -> nuevoUuid().also {
                evento(TipoEvento.FALLA, id, Falla.PROXY_200_AJENO.nombre)
            }
            else -> id
        }
        evento(TipoEvento.RESPUESTA, id, "http=201 eco=${eco ?: "-"} cabecera=0")
        l.ultimo2xx = true
        return visitaDto(eco)
    }

    private val api: V2VisitsApi = object : V2VisitsApi {
        override suspend fun crearVisita(idempotencyKey: String, body: CrearVisitaBody): VisitaDTO =
            post(body.id, emptyList())

        override suspend fun crearVisitaConImagenes(
            idempotencyKey: String,
            datos: RequestBody,
            imagenes: List<MultipartBody.Part>
        ): VisitaDTO = post(idempotencyKey, idsDeFotos(imagenes))

        override suspend fun visitasExistentesPorIds(ids: String): List<String> {
            currentCoroutineContext().ensureActive()
            if (pasa(Falla.BY_IDS_FALLA)) {
                evento(TipoEvento.FALLA, "", Falla.BY_IDS_FALLA.nombre)
                throw IOException("sim: by-ids falló")
            }
            return ids.split(',').filter { it in servidor }
        }

        override suspend fun obtenerVisita(id: String): VisitaDetalleDTO {
            currentCoroutineContext().ensureActive()
            if (pasa(Falla.GET_PERDIDO)) {
                evento(TipoEvento.FALLA, id, Falla.GET_PERDIDO.nombre)
                evento(TipoEvento.GET, id, "http=-")
                throw IOException("sim: GET perdido")
            }
            if (pasa(Falla.GET_503)) {
                evento(TipoEvento.FALLA, id, Falla.GET_503.nombre)
                evento(TipoEvento.GET, id, "http=503")
                throw httpError(HTTP_503)
            }
            val fotos = servidor[id] ?: run {
                evento(TipoEvento.GET, id, "http=404")
                throw httpError(HTTP_404)
            }
            val imgs = fotos.sorted()
            evento(
                TipoEvento.GET,
                id,
                "http=200 id=$id imagen_ids=${imgs.joinToString(",").ifEmpty { "-" }}"
            )
            return visitaDetalle(id, imgs)
        }
    }

    // ─── costuras de los DAO ───────────────────────────────────────────────

    /** DAO real de fotos; la PRIMERA lectura de pendientes de [visita] dispara la foto tardía. */
    private inner class ImagenesConCostura(
        private val real: VisitImageDao,
        private val visita: String
    ) :
        VisitImageDao by real {
        private var lecturas = 0
        override suspend fun getPendientesDe(visitaId: String): List<VisitImageEntity> {
            val r = real.getPendientesDe(visitaId)
            lecturas++
            if (lecturas == 1 && visitaId == visita && fotoTardia) {
                fotoTardia = false
                evento(TipoEvento.FALLA, visita, "foto_tardia")
                val f = insertarFoto(visita, true, ORDEN_TARDIA)
                evento(TipoEvento.FOTO_AGREGADA, visita, "foto=$f archivo=1")
            }
            return r
        }
    }

    /**
     * DAO real de visitas. Justo ANTES del UPDATE condicional (después de la
     * relectura) corre la foto tardía o "otro camino"; si el UPDATE da 0
     * filas lo anota (`marca_condicional_sin_filas`).
     */
    private inner class VisitDaoConCostura(private val real: VisitDao, private val visita: String) :
        VisitDao by real {
        override suspend fun confirmarTrasSubidaSiNoHayFotosNuevas(
            id: String,
            ahora: String,
            conocidas: List<String>
        ): Int {
            if (id == visita && fotoTardiaTrasRelectura) {
                fotoTardiaTrasRelectura = false
                evento(TipoEvento.FALLA, id, "foto_tardia_tras_relectura")
                val f = insertarFoto(id, true, ORDEN_TARDIA)
                evento(TipoEvento.FOTO_AGREGADA, id, "foto=$f archivo=1")
            }
            val otro = otroCamino
            if (id == visita && otro != null) {
                otroCamino = null
                evento(
                    TipoEvento.FALLA,
                    id,
                    if (otro) "otro_camino_confirma_con_archivo" else "otro_camino_confirma"
                )
                ReconcileVisitsUseCase(
                    RoomPendingVisitsStore(real),
                    V2VisitCustodyRegistry(api),
                    reporter
                ).execute()
                observarTelefono()
                val f = insertarFoto(id, otro, ORDEN_TARDIA)
                evento(TipoEvento.FOTO_AGREGADA, id, "foto=$f archivo=${if (otro) 1 else 0}")
            }
            val n = real.confirmarTrasSubidaSiNoHayFotosNuevas(id, ahora, conocidas)
            if (n == 0) evento(TipoEvento.FALLA, id, "marca_condicional_sin_filas")
            return n
        }
    }

    companion object {
        private const val HTTP_404 = 404
        private const val HTTP_422 = 422
        private const val HTTP_503 = 503
        private const val ORDEN_TARDIA = 50
        private const val RONDAS_DE_DRENADO = 6
        private const val DIAS_POR_SALTO = 3L

        /** El modo v2_actual suelta el 5xx en el intento 10 (E-VIS-001). */
        const val MAX_INTENTOS_V2 = 10

        val PROBABILIDADES_POR_OMISION: Map<Falla, Double> = mapOf(
            Falla.PERDER_PETICION to 0.08,
            Falla.PERDER_RESPUESTA to 0.08,
            Falla.DUPLICAR to 0.05,
            Falla.CUERPO_200_TRUNCADO to 0.05,
            Falla.PROXY_200_AJENO to 0.04,
            Falla.STATUS_503 to 0.08,
            Falla.GET_PERDIDO to 0.08,
            Falla.GET_503 to 0.06,
            Falla.BY_IDS_FALLA to 0.15
        )
    }
}
