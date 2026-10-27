package com.example.msp_app.visitas.envio.simulacion

import android.content.Context
import androidx.room.withTransaction
import com.example.msp_app.core.common.time.AppTime
import com.example.msp_app.core.common.time.BUSINESS_ZONE
import com.example.msp_app.core.database.AppDatabase
import com.example.msp_app.core.database.dao.visit.IdYUpdatedAt
import com.example.msp_app.core.database.dao.visit.VisitaZonaDao
import com.example.msp_app.core.database.entities.VisitaZonaEntity
import com.example.msp_app.core.database.entities.VisitasSyncStateEntity
import com.example.msp_app.core.sync.visitas.ResultadoSync
import com.example.msp_app.core.sync.visitas.VisitasZonaSyncManager
import com.example.msp_app.core.testing.time.FakeClock
import com.example.msp_app.core.upload.HEADER_INTENT_CAPTURED
import com.example.msp_app.core.visitas.capacidades.Capacidades
import com.example.msp_app.core.visitas.envio.ModoEnvioVisitas
import com.example.msp_app.data.api.services.visits.ByIdsVisitasZonaResponse
import com.example.msp_app.data.api.services.visits.CapacidadesDTO
import com.example.msp_app.data.api.services.visits.CrearVisitaBody
import com.example.msp_app.data.api.services.visits.DigestVisitasZonaResponse
import com.example.msp_app.data.api.services.visits.IdsVisitasZonaResponse
import com.example.msp_app.data.api.services.visits.SyncVisitasZonaResponse
import com.example.msp_app.data.api.services.visits.V2VisitsApi
import com.example.msp_app.data.api.services.visits.VisitaDTO
import com.example.msp_app.data.api.services.visits.VisitaDetalleDTO
import com.example.msp_app.data.api.services.visits.VisitaExistenteDTO
import com.example.msp_app.data.api.services.visits.VisitaZonaDto
import com.example.msp_app.data.api.services.visits.VisitasZonaSyncApi
import com.example.msp_app.workers.construirPendingVisitsWorker
import com.example.msp_app.workers.httpError
import com.example.msp_app.workers.httpErrorConCabecera
import com.example.msp_app.workers.pendingVisit
import java.io.IOException
import java.security.MessageDigest
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.UUID
import kotlin.random.Random
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import okhttp3.MultipartBody
import okhttp3.RequestBody

/**
 * Defectos que solo existen para los controles positivos (plan visitas P2,
 * Tarea 19, paso 2): cada uno rompe UNA regla y la simulación tiene que
 * cazarlo. Son la versión Kotlin de `opcionesBajada` de la simulación Go
 * (msp-api `internal/visitas/sim/bajada_mundo_test.go`, Tarea 12).
 */
data class DefectosBajada(
    /** El teléfono no persiste `AFTER_ID` (lo deja nulo con cursor). Rompe I6. */
    val afterIdCero: Boolean = false,
    /** La bajada hace `INSERT REPLACE` en `Visit` de lo que ya existe ahí. Rompe I8. */
    val bajadaEscribeSalida: Boolean = false,
    /** El servidor pisa la fila con el reenvío, nulos incluidos. Rompe I10 o 5.2.3. */
    val reenvioBorra: Boolean = false,
    /** El servidor entrega sin marca segura (`upper = ahora`). Rompe I-bajada. */
    val sinMarca: Boolean = false,
    /** El servidor compara solo `UPDATED_AT > cursor` (sin el id). Rompe I4. */
    val cursorSoloTiempo: Boolean = false
)

/** Fallas sembradas de la red en este mundo. */
enum class FallaBajada(val nombre: String) {
    PERDER_PETICION("perder_peticion"),
    PERDER_RESPUESTA("perder_respuesta_tras_commit"),
    STATUS_503("status_503"),
    SYNC_FALLA("sync_falla")
}

/** La muerte del proceso a mitad de una página: dentro de la transacción de Room. */
class MuerteSimulada : CancellationException(
    "sim: proceso muerto entre aplicar la página y guardar el cursor"
)

/**
 * El mundo de la simulación Kotlin de la BAJADA de visitas (plan visitas P2,
 * Tarea 19; E-APP-116). Extiende la simulación del envío de P1
 * ([MundoSimulado], Tarea 17) con lo que P2 agrega encima: la agenda de la
 * zona baja a `visita_zona` con cursor par y generación, mientras el teléfono
 * sigue capturando y mandando visitas con compromiso (contrato 3, relleno).
 *
 * Lo que corre es código de producción:
 *  - [VisitasZonaSyncManager] (T16) sobre el [VisitaZonaDao] real de Room en
 *    memoria, a través de una costura ([DaoConCostura]) que solo observa,
 *    mata el proceso dentro de la transacción y siembra los defectos del
 *    teléfono;
 *  - el worker REAL de envío (`PendingVisitsWorker` v3) con la capacidad que
 *    decida el mundo (contrato 2 o 3), incluido el relleno del compromiso.
 *
 * Lo falso, dicho en voz alta: el servidor. Es un mapa de versiones con
 * tiempo virtual que MODELA (no ejecuta) las reglas de Go:
 *  - un solo predicado (`zona`, `UPDATED_AT` no nulo, `FECHA >= desde`, y la
 *    marca segura) para sync, digest, ids y by-ids (I4);
 *  - la marca segura por tiempo: `upper = min(ahora − 1 s, inicio de la
 *    escritura en vuelo más vieja − 1 s)` (spec §5.4.4);
 *  - la tabla 5.2.3 de reenvío: bloque ausente se conserva, nulo se rellena,
 *    igual no cambia, distinto responde 422 con custodia;
 *  - la generación `visitas` y el relleno de `UPDATED_AT` de filas del binario
 *    P1 (que necesita subirla).
 * Que el servidor de Go cumpla esas reglas lo prueba la simulación Go (T12,
 * E-VIS-070) con el `app.Service` real; esta prueba el TELÉFONO contra ellas.
 *
 * I5 (la derivación del teléfono igual a la agenda del servidor) NO está
 * aquí: necesita `Compromisos.kt`/`AgendaLocal` de la Tarea 17, que no está
 * integrada en esta base (6d9e4c1e), y `AgendaLocal` lee pagos.
 */
@Suppress("TooManyFunctions", "LargeClass", "LongParameterList")
class MundoBajada(
    val semilla: Long,
    private val db: AppDatabase,
    private val context: Context,
    private val defectos: DefectosBajada = DefectosBajada(),
    probabilidades: Map<FallaBajada, Double> = PROBABILIDADES_POR_OMISION,
    /** Filas por página del servidor (el teléfono pide 500; el servidor puede dar menos). */
    private val paginaServidor: Int = 2
) {
    private val rnd = Random(semilla)
    private var prob = probabilidades
    val clock = FakeClock(Instant.parse("2026-10-17T18:00:00Z"))
    private val h = mutableListOf<Evento>()
    private var paso = 0
    private var orden = 0

    var zonaTel: Int = ZONA_A
        private set
    private var contrato3 = false

    /** La primera violación que detectó una acción (5.2.3, media página). */
    private var errAccion: InvarianteError? = null

    /** Lo que se recorrió: lo exige el control de cobertura de la prueba. */
    val cobertura = mutableMapOf<String, Int>()

    private fun cubrir(k: String) {
        cobertura[k] = (cobertura[k] ?: 0) + 1
    }

    // ─── historial ─────────────────────────────────────────────────────────

    val historial: List<Evento> get() = h

    private fun evento(tipo: String, visita: String, detalle: String) {
        paso++
        h += Evento(paso, tipo, visita, detalle)
    }

    private fun accion(detalle: String) {
        evento(TipoEvento.ACCION, "", detalle)
        cubrir(detalle.substringBefore(' '))
    }

    private fun nuevoUuid(): String = UUID(rnd.nextLong(), rnd.nextLong()).toString()

    private fun pasa(f: FallaBajada): Boolean = rnd.nextDouble() < (prob[f] ?: 0.0)

    // ─── el servidor modelado ──────────────────────────────────────────────

    data class Promesa(val ventaId: Int?, val fecha: String, val montoCentavos: Long?)
    data class Cita(val fecha: String, val hora: String?)

    private data class FilaSrv(
        val id: String,
        val clienteId: Int,
        val zona: Int,
        val fecha: String,
        val tipo: String,
        val promesa: Promesa?,
        val cita: Cita?
    )

    /**
     * Una versión de una fila de `MSP_VISITAS`. [updatedAt] nulo = la escribió
     * el binario P1 (E-VIS-060): no existe para la bajada. Entre [inicio] y
     * [visible] la escritura está en vuelo (su COMMIT no aterrizó).
     */
    private class VersionSrv(
        val fila: FilaSrv,
        val updatedAt: Instant?,
        val inicio: Instant,
        val visible: Instant,
        val orden: Int
    )

    private val srv = linkedMapOf<String, MutableList<VersionSrv>>()
    private var epochSrv = 1
    private val propias = linkedSetOf<String>()

    private fun actual(id: String, t: Instant = clock.now()): VersionSrv? =
        srv[id].orEmpty().filter { !it.visible.isAfter(t) }
            .maxWithOrNull(compareBy<VersionSrv>({ it.visible }, { it.orden }))

    private fun enVuelo(id: String, t: Instant = clock.now()): Boolean =
        srv[id].orEmpty().any { !it.inicio.isAfter(t) && it.visible.isAfter(t) }

    private fun asentada(id: String, t: Instant = clock.now()): Boolean =
        srv[id].orEmpty().none { it.visible.isAfter(t) }

    /** La marca segura por tiempo (spec §5.4.4). */
    private fun marca(t: Instant = clock.now()): Instant {
        if (defectos.sinMarca) return t
        val masVieja = srv.values.flatten()
            .filter { !it.inicio.isAfter(t) && it.visible.isAfter(t) }
            .minOfOrNull { it.inicio }
        val porReloj = t.minusSeconds(1)
        return if (masVieja == null) porReloj else minOf(porReloj, masVieja.minusSeconds(1))
    }

    /** `hoy − 92` a las 00:00 de la zona del negocio: el mismo default que el teléfono. */
    fun desde(t: Instant = clock.now()): Instant = t.atZone(BUSINESS_ZONE).toLocalDate()
        .minusDays(VisitasZonaSyncManager.VENTANA_DIAS).atStartOfDay(BUSINESS_ZONE).toInstant()

    /** El ÚNICO predicado de la bajada: lo usan los cuatro canales (I4). */
    private fun conjunto(
        zona: Int,
        desde: Instant,
        t: Instant = clock.now()
    ): Map<String, Instant> {
        val upper = marca(t)
        val out = sortedMapOf<String, Instant>()
        srv.keys.forEach { id ->
            val v = actual(id, t) ?: return@forEach
            val u = v.updatedAt ?: return@forEach
            if (v.fila.zona == zona && !Instant.parse(v.fila.fecha).isBefore(desde) && !u.isAfter(upper)) {
                out[id] = u
            }
        }
        return out
    }

    private fun ordenBajada(conj: Map<String, Instant>): List<Pair<String, Instant>> =
        conj.entries.map { it.key to it.value }
            .sortedWith(compareBy<Pair<String, Instant>>({ it.second }, { it.first }))

    private fun dto(id: String, u: Instant): VisitaZonaDto {
        val f = actual(id)!!.fila
        return VisitaZonaDto(
            id = id,
            cliente_id = f.clienteId,
            cobrador_id = COBRADOR,
            cobrador = "Ramirez Ortiz, Fernando",
            forma_cobro_id = 0,
            tipo_visita = f.tipo,
            zona_cliente_id = f.zona,
            fecha = f.fecha,
            promesa_venta_id = f.promesa?.ventaId,
            promesa_fecha = f.promesa?.fecha,
            promesa_monto_centavos = f.promesa?.montoCentavos,
            cita_fecha = f.cita?.fecha,
            cita_hora = f.cita?.hora,
            updated_at = fmt(u)
        )
    }

    private var ultimaPagina: SyncVisitasZonaResponse? = null

    @Suppress("CyclomaticComplexMethod")
    private fun srvSync(
        zona: Int,
        desde: String,
        cursor: String?,
        afterId: String?,
        limit: Int?,
        contar: Boolean = true
    ): SyncVisitasZonaResponse {
        val conj = ordenBajada(conjunto(zona, Instant.parse(desde)))
        val c = cursor?.let { Instant.parse(it) }
        val resto = conj.filter { (id, u) ->
            when {
                c == null -> true
                defectos.cursorSoloTiempo -> u.isAfter(c)
                afterId == null -> !u.isBefore(c)
                else -> u.isAfter(c) || (u == c && id > afterId)
            }
        }
        val n = minOf(limit ?: paginaServidor, paginaServidor)
        val pagina = resto.take(n)
        if (contar && pagina.isNotEmpty() && resto.size > n && resto[n].second == pagina.last().second) {
            cubrir("empate_cruza_pagina")
        }
        // La marca retiene una fila que ya se ve: hay una escritura en vuelo.
        val upper = marca()
        if (contar && srv.keys.any { id ->
                val v = actual(id)
                val u = v?.updatedAt
                v != null && u != null && v.fila.zona == zona && u.isAfter(upper) && !u.isAfter(clock.now().minusSeconds(1))
            }
        ) {
            cubrir("marca_retiene")
        }
        return SyncVisitasZonaResponse(
            items = pagina.map { (id, u) -> dto(id, u) },
            cursor = if (pagina.isEmpty()) cursor else fmt(pagina.last().second),
            after_id = pagina.lastOrNull()?.first,
            has_more = resto.size > n,
            sync_epoch = epochSrv,
            server_now = fmt(clock.now())
        )
    }

    private fun srvIds(
        zona: Int,
        desde: Instant,
        after: String?,
        limit: Int
    ): Pair<List<String>, Boolean> {
        val todos = conjunto(zona, desde).keys.filter { after == null || it > after }
        return todos.take(limit) to (todos.size > limit)
    }

    private fun srvByIds(zona: Int, desde: Instant, ids: List<String>): Map<String, Instant> {
        val conj = conjunto(zona, desde)
        return ids.filter { it in conj }.associateWith { conj.getValue(it) }
    }

    private fun srvDigest(zona: Int, desde: Instant): Pair<Int, String> {
        val conj = conjunto(zona, desde)
        return conj.size to hashDe(conj)
    }

    private fun hashDe(conj: Map<String, Instant>): String = sha256(
        conj.toSortedMap().entries.joinToString("\n") { "${it.key}|${fmt(it.value)}" }
    )

    /** Resultado de una escritura con la tabla 5.2.3. */
    private sealed class Escritura {
        data class Ok(val fila: FilaSrv, val nueva: Boolean) : Escritura()
        object Distinto : Escritura()
    }

    private fun <T> bloque(enviado: T?, vigente: T?): Pair<T?, Boolean> = when {
        enviado == null -> vigente to true // ausente: se conserva
        vigente == null -> enviado to true // relleno
        enviado == vigente -> vigente to true
        else -> null to false // distinto
    }

    /**
     * Escribe [nueva] con la tabla 5.2.3 sobre la vigente. Solo una versión
     * nueva si algo cambia (un reenvío igual no reestampa). [latencia]: cuánto
     * tarda en aterrizar el COMMIT; [estampa] = `null` es el binario P1.
     */
    private fun escribir(
        nueva: FilaSrv,
        latencia: Duration,
        estampa: Instant? = clock.now()
    ): Escritura {
        val vig = actual(nueva.id)?.fila
        val fila = when {
            vig == null -> nueva
            defectos.reenvioBorra -> vig.copy(promesa = nueva.promesa, cita = nueva.cita)
            else -> {
                val (p, okP) = bloque(nueva.promesa, vig.promesa)
                val (c, okC) = bloque(nueva.cita, vig.cita)
                if (!okP || !okC) return Escritura.Distinto
                vig.copy(promesa = p, cita = c)
            }
        }
        if (fila == vig) return Escritura.Ok(vig, nueva = false)
        val t = clock.now()
        srv.getOrPut(nueva.id) {
            mutableListOf()
        } += VersionSrv(fila, estampa, t, t.plus(latencia), ++orden)
        return Escritura.Ok(fila, nueva = true)
    }

    private fun filaDe(body: CrearVisitaBody) = FilaSrv(
        id = body.id,
        clienteId = body.cliente_id,
        zona = body.zona_cliente_id,
        fecha = body.fecha,
        tipo = body.tipo_visita,
        promesa = body.promesa_fecha?.let {
            Promesa(body.promesa_venta_id, it, body.promesa_monto_centavos)
        },
        cita = body.cita_fecha?.let { Cita(it, body.cita_hora) }
    )

    private val servidorVisto = mutableSetOf<String>()

    private fun observarServidor(id: String) {
        if (id in propias && actual(id) != null && servidorVisto.add(id)) {
            evento(TipoEvento.SERVIDOR, id, "filas=1 fotos=")
        }
    }

    private fun respuestaDe(f: FilaSrv) = VisitaDTO(
        id = f.id,
        cliente_id = f.clienteId,
        cobrador_id = COBRADOR,
        tipo_visita = f.tipo,
        zona_cliente_id = f.zona,
        fecha = f.fecha,
        promesa_venta_id = f.promesa?.ventaId,
        promesa_fecha = f.promesa?.fecha,
        promesa_monto_centavos = f.promesa?.montoCentavos,
        cita_fecha = f.cita?.fecha,
        cita_hora = f.cita?.hora
    )

    private suspend fun post(body: CrearVisitaBody): VisitaDTO {
        currentCoroutineContext().ensureActive()
        val id = body.id
        val conBloques = body.promesa_fecha != null || body.cita_fecha != null
        evento(TipoEvento.ENVIO, id, "intento=1 multipart=0 bloques=${if (conBloques) 1 else 0}")
        if (pasa(FallaBajada.PERDER_PETICION)) {
            cubrir(FallaBajada.PERDER_PETICION.nombre)
            evento(TipoEvento.RESPUESTA, id, "io")
            throw IOException("sim: petición perdida")
        }
        if (pasa(FallaBajada.STATUS_503)) {
            cubrir(FallaBajada.STATUS_503.nombre)
            evento(TipoEvento.RESPUESTA, id, "http=503 eco=- cabecera=0")
            throw httpError(HTTP_503)
        }
        when (val r = escribir(filaDe(body), Duration.ZERO)) {
            Escritura.Distinto -> {
                accion("reenvio_distinto_422 propia=1")
                evento(TipoEvento.RESPUESTA, id, "http=422 eco=- cabecera=1")
                throw httpErrorConCabecera(HTTP_422, HEADER_INTENT_CAPTURED, nuevoUuid())
            }
            is Escritura.Ok -> {
                observarServidor(id)
                if (pasa(FallaBajada.PERDER_RESPUESTA)) {
                    cubrir(FallaBajada.PERDER_RESPUESTA.nombre)
                    evento(TipoEvento.RESPUESTA, id, "io")
                    throw IOException("sim: respuesta perdida tras el commit")
                }
                evento(TipoEvento.RESPUESTA, id, "http=201 eco=$id cabecera=0")
                return respuestaDe(r.fila)
            }
        }
    }

    private val api: V2VisitsApi = object : V2VisitsApi {
        override suspend fun crearVisita(idempotencyKey: String, body: CrearVisitaBody): VisitaDTO =
            post(body)

        override suspend fun crearVisitaConImagenes(
            idempotencyKey: String,
            datos: RequestBody,
            imagenes: List<MultipartBody.Part>
        ): VisitaDTO = throw AssertionError("sim: en este mundo las visitas no llevan fotos")

        override suspend fun visitasExistentesPorIds(ids: String): List<String> =
            ids.split(',').filter { actual(it) != null }

        override suspend fun obtenerVisita(id: String): VisitaDetalleDTO {
            currentCoroutineContext().ensureActive()
            val f = actual(id)?.fila ?: run {
                evento(TipoEvento.GET, id, "http=404")
                throw httpError(HTTP_404)
            }
            evento(TipoEvento.GET, id, "http=200 id=$id imagen_ids=-")
            return VisitaDetalleDTO(
                id = id,
                cliente_id = f.clienteId,
                cobrador_id = COBRADOR,
                cobrador = "Ramirez Ortiz, Fernando",
                forma_cobro_id = 0,
                lat = 0.0,
                lng = 0.0,
                nota = null,
                tipo_visita = f.tipo,
                zona_cliente_id = f.zona,
                impte_docto_cc_id = null,
                fecha = f.fecha,
                created_at = f.fecha,
                imagen_ids = emptyList(),
                promesa_venta_id = f.promesa?.ventaId,
                promesa_fecha = f.promesa?.fecha,
                promesa_monto_centavos = f.promesa?.montoCentavos,
                cita_fecha = f.cita?.fecha,
                cita_hora = f.cita?.hora
            )
        }

        override suspend fun capacidades(): CapacidadesDTO =
            throw AssertionError("sim: el worker de subida no negocia capacidades")

        override suspend fun visitasExistentesPorIdsDetalle(
            ids: String,
            detalle: Int
        ): List<VisitaExistenteDTO> =
            throw AssertionError("sim: el worker de subida no usa by-ids con detalle")
    }

    private val apiBajada: VisitasZonaSyncApi = object : VisitasZonaSyncApi {
        override suspend fun sync(
            zona: Int,
            desde: String,
            cursor: String?,
            afterId: String?,
            limit: Int?
        ): SyncVisitasZonaResponse {
            currentCoroutineContext().ensureActive()
            if (pasa(FallaBajada.SYNC_FALLA)) {
                cubrir(FallaBajada.SYNC_FALLA.nombre)
                throw IOException("sim: página de la bajada perdida")
            }
            return srvSync(zona, desde, cursor, afterId, limit).also { ultimaPagina = it }
        }

        override suspend fun digest(zona: Int, desde: String): DigestVisitasZonaResponse {
            val (n, hash) = srvDigest(zona, Instant.parse(desde))
            return DigestVisitasZonaResponse(n, hash, null)
        }

        override suspend fun ids(
            zona: Int,
            desde: String,
            after: String?,
            limit: Int?
        ): IdsVisitasZonaResponse {
            val (ids, mas) = srvIds(zona, Instant.parse(desde), after, limit ?: LIMITE_IDS)
            return IdsVisitasZonaResponse(ids, mas)
        }

        override suspend fun byIds(
            zona: Int,
            desde: String,
            ids: String
        ): ByIdsVisitasZonaResponse {
            val m = srvByIds(zona, Instant.parse(desde), ids.split(','))
            return ByIdsVisitasZonaResponse(m.map { (id, u) -> dto(id, u) })
        }
    }

    // ─── el teléfono: la costura del DAO de la bajada ──────────────────────

    private val dao = DaoConCostura(db.visitaZonaDao())

    private var matarEnPagina: Int? = null
    private var paginasAplicadas = 0

    /**
     * El [VisitaZonaDao] real con ganchos. Solo DELEGA: las piezas protegidas
     * del DAO real no se llaman desde aquí (lanzan si alguien lo intenta).
     * Después de cada operación confirmada anota en el historial lo que
     * QUEDÓ persistido (lo lee de Room, no de los argumentos).
     */
    private inner class DaoConCostura(private val real: VisitaZonaDao) : VisitaZonaDao() {

        override suspend fun aplicarPagina(
            zona: Int,
            items: List<VisitaZonaEntity>,
            cursor: String?,
            afterId: String?
        ) {
            val after = if (defectos.afterIdCero && cursor != null) null else afterId
            val n = paginasAplicadas++
            if (matarEnPagina == n) {
                matarEnPagina = null
                val antes = foto()
                try {
                    db.withTransaction {
                        real.aplicarPagina(zona, items, cursor, after)
                        throw MuerteSimulada()
                    }
                } finally {
                    if (foto() != antes) {
                        errAccion = InvarianteError(
                            "I6",
                            paso,
                            "",
                            "la muerte a mitad de la página dejó filas o cursor persistidos"
                        )
                    }
                }
            }
            real.aplicarPagina(zona, items, cursor, after)
            if (defectos.bajadaEscribeSalida) {
                items.forEach { z ->
                    db.visitDao().findVisitById(z.ID)?.let { v ->
                        db.visitDao().insertVisit(
                            v.copy(
                                NOTA = z.NOTA,
                                PROMESA_FECHA = z.PROMESA_FECHA,
                                CITA_FECHA = z.CITA_FECHA
                            )
                        )
                    }
                }
            }
            val pagina = ultimaPagina
            evento(
                TipoEvento.PULL,
                "",
                "zona=$zona filas=${items.joinToString(",") { "${it.ID}|${it.UPDATED_AT}" }} " +
                    "has_more=${if (pagina?.has_more == true) 1 else 0} epoch=${pagina?.sync_epoch ?: "-"}"
            )
            anotarCursor(zona, MotivoCursor.PAGINA)
        }

        override suspend fun reiniciarZona(zonaNueva: Int) {
            real.reiniciarZona(zonaNueva)
            cubrir(MotivoCursor.REINICIO_ZONA)
            anotarCursor(zonaNueva, MotivoCursor.REINICIO_ZONA)
        }

        override suspend fun reiniciarPorEpoch(zona: Int) {
            real.reiniciarPorEpoch(zona)
            cubrir(MotivoCursor.REINICIO_EPOCH)
            anotarCursor(zona, MotivoCursor.REINICIO_EPOCH)
        }

        override suspend fun guardarEpoch(zona: Int, epoch: Int) {
            real.guardarEpoch(zona, epoch)
            evento(TipoEvento.EPOCH, "", "zona=$zona epoch=${real.estado(zona)?.EPOCH ?: "-"}")
        }

        override suspend fun estado(zona: Int): VisitasSyncStateEntity? = real.estado(zona)
        override suspend fun restosDeOtraZona(zona: Int): Int = real.restosDeOtraZona(zona)
        override suspend fun deZona(zona: Int): List<VisitaZonaEntity> = real.deZona(zona)
        override suspend fun idsYUpdatedAt(zona: Int, desde: String): List<IdYUpdatedAt> =
            real.idsYUpdatedAt(zona, desde)

        override suspend fun borrar(ids: List<String>) = real.borrar(ids)
        override suspend fun podarFueraDeVentana(desde: String) = real.podarFueraDeVentana(desde)

        private fun noSeUsa(): Nothing = throw AssertionError("sim: pieza protegida del DAO real")
        override suspend fun upsertAll(items: List<VisitaZonaEntity>) = noSeUsa()
        override suspend fun crearEstadoSiFalta(zona: Int) = noSeUsa()
        override suspend fun actualizarPosicion(zona: Int, cursor: String?, afterId: String?) =
            noSeUsa()
        override suspend fun actualizarEpoch(zona: Int, epoch: Int) = noSeUsa()
        override suspend fun sinPosicion() = noSeUsa()
        override suspend fun borrarFueraDeZona(zona: Int) = noSeUsa()
        override suspend fun borrarTodo() = noSeUsa()
        override suspend fun borrarTanda(ids: List<String>) = noSeUsa()

        private suspend fun anotarCursor(zona: Int, motivo: String) {
            val e = real.estado(zona)
            evento(
                TipoEvento.CURSOR,
                "",
                "zona=$zona cursor=${e?.CURSOR ?: "-"} after_id=${e?.AFTER_ID ?: "-"} motivo=$motivo"
            )
        }
    }

    /** Lo bajado y las posiciones, para comparar antes y después de una muerte. */
    private fun foto(): String = consulta("SELECT * FROM visita_zona ORDER BY ID") +
        consulta("SELECT * FROM visitas_sync_state ORDER BY ZONA")

    /** Huella de la tabla de salida `Visit` completa (I8). */
    private fun huellaSalida(): String = sha256(consulta("SELECT * FROM Visit ORDER BY ID"))

    private fun consulta(sql: String): String {
        val sb = StringBuilder()
        db.openHelper.readableDatabase.query(sql).use { c ->
            while (c.moveToNext()) {
                (0 until c.columnCount).forEach { i -> sb.append(c.getString(i)).append('\u001f') }
                sb.append('\n')
            }
        }
        return sb.toString()
    }

    private val manager = VisitasZonaSyncManager(
        api = apiBajada,
        dao = dao,
        zonaActual = { zonaTel },
        killSwitch = { true },
        reloj = { clock.now() },
        mutex = Mutex()
    )

    // ─── acciones del teléfono ─────────────────────────────────────────────

    private fun hoyMas(dias: Int): String =
        clock.now().atZone(BUSINESS_ZONE).toLocalDate().plusDays(dias.toLong()).toString()

    private fun promesaAlAzar() = Promesa(
        ventaId = VENTAS[rnd.nextInt(VENTAS.size)],
        fecha = hoyMas(1 + rnd.nextInt(DIAS_COMPROMISO)),
        montoCentavos = MONTOS[rnd.nextInt(MONTOS.size)]
    )

    private fun citaAlAzar() = Cita(
        fecha = hoyMas(1 + rnd.nextInt(DIAS_COMPROMISO)),
        hora = if (rnd.nextBoolean()) HORAS[rnd.nextInt(HORAS.size)] else null
    )

    /** Captura en el teléfono una visita de su zona con o sin compromiso. */
    suspend fun capturar(promesa: Boolean, cita: Boolean): String {
        val id = nuevoUuid()
        val p = if (promesa) promesaAlAzar() else null
        val c = if (cita) citaAlAzar() else null
        db.visitDao().insertVisit(
            pendingVisit(id).copy(
                FECHA = AppTime.toWireFormat(clock.now()),
                CLIENTE_ID = CLIENTES[rnd.nextInt(CLIENTES.size)],
                ZONA_CLIENTE_ID = zonaTel,
                TIPO_VISITA = if (p != null) "PROMESA_PAGO" else "SIN_PAGO",
                PROMESA_VENTA_ID = p?.ventaId,
                PROMESA_FECHA = p?.fecha,
                PROMESA_MONTO_CENTAVOS = p?.montoCentavos,
                CITA_FECHA = c?.fecha,
                CITA_HORA = c?.hora
            )
        )
        propias += id
        evento(TipoEvento.CAPTURA, id, "fotos= sin_archivo=")
        return id
    }

    private fun capacidades() = if (contrato3) Capacidades(3, true) else Capacidades.CONTRATO_2

    fun cambiarCapacidad(contrato3: Boolean) {
        this.contrato3 = contrato3
        accion("capacidad contrato=${if (contrato3) 3 else 2}")
    }

    private val intentos = mutableMapOf<String, Int>()

    /** Corre el worker REAL sobre una visita (envío o relleno del compromiso). */
    suspend fun correrWorker(id: String) {
        val n = (intentos[id] ?: 0) + 1
        intentos[id] = n
        val antes = db.visitDao().findVisitById(id)
        construirPendingVisitsWorker(
            context = context,
            imagenes = db.visitImageDao(),
            clock = clock,
            modo = ModoEnvioVisitas.V3,
            visitId = id,
            api = api,
            runAttemptCount = n - 1,
            visitDao = db.visitDao(),
            capacidades = capacidades()
        ).doWork()
        val despues = db.visitDao().findVisitById(id)
        if (antes?.COMPROMISO_CONFIRMADO_EN == null && despues?.COMPROMISO_CONFIRMADO_EN != null &&
            antes?.CONFIRMADA_EN != null
        ) {
            cubrir("relleno_confirma")
        }
        observarTelefono()
    }

    suspend fun intentar(cual: Int) {
        val ids = db.visitDao().pendientesOrdenadas().filter { it in propias }
        if (ids.isEmpty()) return
        correrWorker(ids[cual % ids.size])
    }

    suspend fun rellenar(cual: Int) {
        val ids = db.visitDao().compromisosPorRellenar().filter { it in propias }
        if (ids.isEmpty()) return
        correrWorker(ids[cual % ids.size])
    }

    private val estadoTel = mutableMapOf<String, String>()

    private suspend fun observarTelefono() {
        propias.forEach { id ->
            val v = db.visitDao().findVisitById(id) ?: return@forEach
            val nuevo = when {
                v.CONFIRMADA_EN != null -> "confirmada"
                v.RESGUARDADA_EN != null -> "resguardada"
                else -> "pendiente"
            }
            if (nuevo != (estadoTel[id] ?: "pendiente")) {
                when (nuevo) {
                    "confirmada" -> evento(TipoEvento.CONFIRMADA, id, "estampadas=")
                    "resguardada" -> evento(TipoEvento.RESGUARDADA, id, "")
                    else -> evento(TipoEvento.DEVUELTA, id, "")
                }
                estadoTel[id] = nuevo
            }
        }
    }

    /**
     * Una corrida del sync REAL. [matarEn] = índice de la página (desde 0,
     * contando todas las páginas aplicadas en este mundo a partir de ahora) en
     * la que el proceso muere dentro de la transacción.
     */
    suspend fun bajar(matarEn: Int? = null): ResultadoSync? {
        evento(TipoEvento.SALIDA, "", "fase=antes huella=${huellaSalida()}")
        paginasAplicadas = 0
        matarEnPagina = matarEn
        val r = try {
            manager.sincronizar()
        } catch (_: MuerteSimulada) {
            evento(TipoEvento.FALLA, "", "proceso_muerto_entre_pagina_y_cursor")
            cubrir("proceso_muerto_entre_pagina_y_cursor")
            null
        }
        matarEnPagina = null
        evento(TipoEvento.SALIDA, "", "fase=despues huella=${huellaSalida()}")
        if (r is ResultadoSync.Error) accion("bajada_error")
        return r
    }

    fun cambiarZona() {
        zonaTel = if (zonaTel == ZONA_A) ZONA_B else ZONA_A
        accion("cambiar_zona zona=$zonaTel")
    }

    // ─── acciones del servidor ─────────────────────────────────────────────

    private fun zonaAlAzar() = if (rnd.nextInt(3) == 0) ZONA_B else ZONA_A

    /** Otra persona escribe una visita en [zona]; su COMMIT tarda [latencia]. */
    fun ajena(
        zona: Int = zonaAlAzar(),
        latencia: Duration = latenciaAlAzar(),
        fueraDeVentana: Boolean = rnd.nextInt(8) == 0
    ) {
        val id = nuevoUuid()
        val dias = if (fueraDeVentana) VENTANA_FUERA + rnd.nextInt(10) else rnd.nextInt(30)
        val fecha = clock.now().minus(
            Duration.ofDays(dias.toLong())
        ).truncatedTo(java.time.temporal.ChronoUnit.SECONDS)
        escribir(
            FilaSrv(
                id = id,
                clienteId = CLIENTES[rnd.nextInt(CLIENTES.size)],
                zona = zona,
                fecha = AppTime.toWireFormat(fecha),
                tipo = "SIN_PAGO",
                promesa = if (rnd.nextInt(3) == 0) promesaAlAzar() else null,
                cita = if (rnd.nextInt(4) == 0) citaAlAzar() else null
            ),
            latencia
        )
        accion(
            "ajena zona=$zona latencia_ms=${latencia.toMillis()}${if (fueraDeVentana) " fuera" else ""}"
        )
    }

    /** Varias escrituras en el MISMO instante: un grupo empatado de `UPDATED_AT`. */
    fun rafaga(n: Int = 2 + rnd.nextInt(4), zona: Int = zonaAlAzar()) {
        repeat(n) { ajena(zona, Duration.ZERO, fueraDeVentana = false) }
        accion("rafaga n=$n zona=$zona")
    }

    private fun latenciaAlAzar(): Duration =
        if (rnd.nextInt(3) == 0) Duration.ofMillis(rnd.nextLong(LATENCIA_MAX_MS)) else Duration.ZERO

    /** Otro teléfono rellena un bloque nulo de una fila ajena (versión nueva). */
    fun ajenaRellena(cual: Int) {
        val ids = srv.keys.filter { it !in propias && !enVuelo(it) && actual(it)?.updatedAt != null }
        if (ids.isEmpty()) return
        val f = actual(ids[cual % ids.size])!!.fila
        val nueva = if (f.cita == null) {
            f.copy(
                cita = citaAlAzar()
            )
        } else {
            f.copy(promesa = f.promesa ?: promesaAlAzar())
        }
        escribir(nueva, latenciaAlAzar())
        accion("ajena_rellena")
    }

    /**
     * Un APK viejo (contrato 2) reenvía una fila que YA tiene un bloque, sin
     * los campos de compromiso: el bloque se queda (I10).
     */
    fun reenvioApkViejo(cual: Int) {
        val ids = srv.keys.filter { id ->
            !enVuelo(id) && actual(id)?.fila?.let { it.promesa != null || it.cita != null } == true
        }
        if (ids.isEmpty()) return
        val f = actual(ids[cual % ids.size])!!.fila
        escribir(f.copy(promesa = null, cita = null), Duration.ZERO)
        accion("apk_viejo")
        observarServidor(f.id)
    }

    /** Un reenvío con una promesa DISTINTA: 422 y la fila sin cambios (tabla 5.2.3). */
    fun reenvioDistinto(cual: Int) {
        val ids = srv.keys.filter { !enVuelo(it) && actual(it)?.fila?.promesa != null }
        if (ids.isEmpty()) return
        val id = ids[cual % ids.size]
        val antes = actual(id)!!.fila
        val p = antes.promesa!!
        val r =
            escribir(
                antes.copy(
                    promesa = p.copy(montoCentavos = (p.montoCentavos ?: 0L) + CENTAVOS_DE_MAS)
                ),
                Duration.ZERO
            )
        if (r != Escritura.Distinto || actual(id)!!.fila != antes) {
            errAccion = errAccion ?: InvarianteError("5.2.3", paso, id, "un reenvío con promesa distinta no respondió 422 o cambió la fila")
        }
        accion("reenvio_distinto")
    }

    /** El binario P1 escribe una visita sin `UPDATED_AT` (E-VIS-060). */
    fun binarioP1(zona: Int = zonaAlAzar()) {
        val id = nuevoUuid()
        val fecha = clock.now().minus(
            Duration.ofDays(rnd.nextLong(20))
        ).truncatedTo(java.time.temporal.ChronoUnit.SECONDS)
        escribir(
            FilaSrv(
                id,
                CLIENTES[rnd.nextInt(CLIENTES.size)],
                zona,
                AppTime.toWireFormat(fecha),
                "SIN_PAGO",
                promesaAlAzar(),
                null
            ),
            Duration.ZERO,
            estampa = null
        )
        accion("binario_p1 zona=$zona")
    }

    /**
     * El relleno de `UPDATED_AT` (T6) estampa las filas del binario P1 con su
     * `FECHA`, por DEBAJO de cualquier cursor: cambia lo que entrega el
     * `WHERE`, así que sube la generación `visitas` (`COBRANZA-SYNC.md` §3).
     */
    fun rellenoT6() {
        val nulas = srv.keys.filter { id ->
            !enVuelo(id) && actual(id)?.let {
                it.updatedAt == null
            } == true
        }
        if (nulas.isEmpty()) return
        nulas.forEach { id ->
            val f = actual(id)!!.fila
            val t = clock.now()
            srv.getValue(id) += VersionSrv(f, Instant.parse(f.fecha), t, t, ++orden)
        }
        epochSrv++
        accion("relleno_t6 filas=${nulas.size} epoch=$epochSrv")
    }

    fun subirEpoch() {
        epochSrv++
        accion("subir_epoch epoch=$epochSrv")
    }

    fun avanzar(ms: Long) {
        clock.advance(Duration.ofMillis(ms))
    }

    // ─── la máquina aleatoria ──────────────────────────────────────────────

    @Suppress("MagicNumber", "CyclomaticComplexMethod")
    suspend fun accionAleatoria() {
        avanzar(rnd.nextLong(1500))
        when (rnd.nextInt(100)) {
            in 0 until 12 -> capturar(rnd.nextInt(3) == 0, rnd.nextInt(4) == 0)
            in 12 until 24 -> intentar(rnd.nextInt(1000))
            in 24 until 29 -> rellenar(rnd.nextInt(1000))
            in 29 until 32 -> cambiarCapacidad(rnd.nextInt(4) != 0)
            in 32 until 46 -> ajena()
            in 46 until 50 -> rafaga()
            in 50 until 54 -> ajenaRellena(rnd.nextInt(1000))
            in 54 until 57 -> reenvioApkViejo(rnd.nextInt(1000))
            in 57 until 59 -> reenvioDistinto(rnd.nextInt(1000))
            in 59 until 61 -> binarioP1()
            in 61 until 63 -> rellenoT6()
            in 63 until 64 -> subirEpoch()
            in 64 until 66 -> cambiarZona()
            in 66 until 70 -> bajar(matarEn = rnd.nextInt(3))
            in 70 until 74 -> avanzar(rnd.nextLong(LATENCIA_MAX_MS * 2))
            else -> bajar()
        }
    }

    // ─── invariantes ───────────────────────────────────────────────────────

    /** Lo que va del historial y del estado, tras CADA acción. */
    suspend fun verificarInvariantes(cuando: String) {
        violacion()?.let { throw AssertionError("semilla=$semilla $cuando: ${it.message}", it) }
    }

    /** La primera violación, o `null`. */
    suspend fun violacion(): InvarianteError? {
        errAccion?.let { return it }
        if (h.isNotEmpty()) {
            verificar(conFinales(), permitirDeuda = true)?.let { return it }
        }
        return verificarI10() ?: verificarI4() ?: verificarEntrega()
    }

    private fun conFinales(): List<Evento> {
        var p = paso
        return h + propias.map { id ->
            p++
            Evento(p, TipoEvento.FINAL, id, estadoTel[id] ?: "pendiente")
        }
    }

    /** I10: en el orden en que se VEN, ninguna versión pierde un bloque que la anterior tenía. */
    private fun verificarI10(): InvarianteError? {
        srv.forEach { (id, vs) ->
            val vistas = vs.sortedWith(compareBy<VersionSrv>({ it.visible }, { it.orden }))
            vistas.zipWithNext().forEach { (a, b) ->
                if ((a.fila.promesa != null && b.fila.promesa == null) || (a.fila.cita != null && b.fila.cita == null)) {
                    return InvarianteError(
                        "I10",
                        paso,
                        id,
                        "un reenvío dejó nulo un bloque que la fila tenía"
                    )
                }
            }
        }
        return null
    }

    /** I4: sync drenado, ids, by-ids y digest dan el mismo conjunto por zona. */
    private fun verificarI4(): InvarianteError? {
        val d = desde()
        listOf(ZONA_A, ZONA_B).forEach { zona ->
            val sync = linkedMapOf<String, Instant>()
            var cursor: String? = null
            var after: String? = null
            var paginas = 0
            while (paginas++ < MAX_PAGINAS) {
                val p = srvSync(zona, fmt(d), cursor, after, paginaServidor, contar = false)
                p.items.orEmpty().forEach { sync[it.id!!] = Instant.parse(it.updated_at!!) }
                if (p.after_id != null) {
                    cursor = p.cursor
                    after = p.after_id
                }
                if (p.has_more != true) break
            }
            val ids = mutableListOf<String>()
            var a: String? = null
            while (true) {
                val (pag, mas) = srvIds(zona, d, a, LIMITE_IDS_I4)
                ids += pag
                if (!mas || pag.isEmpty()) break
                a = pag.last()
            }
            val byIds = srv.keys.chunked(VisitasZonaSyncApi.MAX_IDS_BY_IDS)
                .flatMap { srvByIds(zona, d, it).entries }.associate { it.key to it.value }
            val (n, hash) = srvDigest(zona, d)
            if (sync.keys.sorted() != ids.sorted() || sync != byIds || n != sync.size || hash != hashDe(sync)) {
                return InvarianteError(
                    "I4",
                    paso,
                    "",
                    "zona $zona: sync=${sync.size} ids=${ids.size} by-ids=${byIds.size} digest=$n"
                )
            }
        }
        return null
    }

    /**
     * I-bajada: toda fila asentada de la zona del teléfono, dentro de la
     * ventana y en o por debajo del cursor par que el teléfono GUARDÓ (con la
     * generación vigente), está en `visita_zona` con esa misma versión.
     */
    private suspend fun verificarEntrega(): InvarianteError? {
        val e = dao.estado(zonaTel) ?: return null
        val c = e.CURSOR?.let { Instant.parse(it) } ?: return null
        if (e.EPOCH != epochSrv) return null
        val after = e.AFTER_ID ?: ""
        val locales = dao.deZona(zonaTel).associate { it.ID to it.UPDATED_AT }
        val d = desde()
        srv.keys.forEach { id ->
            if (!asentada(id)) return@forEach
            val v = actual(id) ?: return@forEach
            val u = v.updatedAt ?: return@forEach
            if (v.fila.zona != zonaTel || Instant.parse(v.fila.fecha).isBefore(d)) return@forEach
            val bajoCursor = u.isBefore(c) || (u == c && id <= after)
            if (bajoCursor && locales[id]?.let { Instant.parse(it) } != u) {
                return InvarianteError(
                    "I-bajada",
                    paso,
                    id,
                    "confirmada con UPDATED_AT ${fmt(
                        u
                    )} bajo el cursor guardado ${e.CURSOR} y el teléfono no la tiene"
                )
            }
        }
        return null
    }

    /**
     * Red perfecta, todo aterriza, contrato 3; se manda y rellena todo lo
     * propio y se baja hasta agotar.
     */
    suspend fun repararYDrenar() {
        prob = emptyMap()
        contrato3 = true
        repeat(RONDAS_DE_DRENADO) {
            val pend = db.visitDao().pendientesOrdenadas().filter { it in propias } +
                db.visitDao().compromisosPorRellenar().filter { it in propias }
            if (pend.isEmpty()) return@repeat
            pend.distinct().forEach { correrWorker(it) }
        }
        val ultimo = srv.values.flatten().maxOfOrNull { it.visible } ?: clock.now()
        if (ultimo.isAfter(clock.now())) clock.advance(Duration.between(clock.now(), ultimo))
        avanzar(DRENAR_MS)
        accion("drenar")
        bajar()
        bajar()
    }

    /** Tras [repararYDrenar]: todo lo anterior más vivacidad. */
    suspend fun verificarFinal() {
        verificarInvariantes("final")
        val sinMandar = db.visitDao().pendientesOrdenadas().filter { it in propias } +
            db.visitDao().compromisosPorRellenar().filter { it in propias }
        if (sinMandar.isNotEmpty()) {
            throw AssertionError(
                "semilla=$semilla vivacidad: con la red reparada siguen sin mandar $sinMandar"
            )
        }
        val d = desde()
        val canales = conjunto(zonaTel, d)
        val tel = dao.deZona(zonaTel).filter { !Instant.parse(it.FECHA).isBefore(d) }
            .associate { it.ID to Instant.parse(it.UPDATED_AT) }
        if (tel != canales) {
            throw AssertionError(
                "semilla=$semilla vivacidad: tras drenar el teléfono tiene ${tel.size} filas y los canales " +
                    "${canales.size} (o con otra versión): faltan ${canales.keys - tel.keys}"
            )
        }
    }

    fun historialConFinales(): List<Evento> = conFinales()

    companion object {
        const val ZONA_A = 21550
        const val ZONA_B = 21563
        private const val COBRADOR = 200
        private const val HTTP_404 = 404
        private const val HTTP_422 = 422
        private const val HTTP_503 = 503
        private const val LIMITE_IDS = 1000
        private const val LIMITE_IDS_I4 = 3
        private const val MAX_PAGINAS = 100_000
        private const val LATENCIA_MAX_MS = 3000L
        private const val VENTANA_FUERA = 93
        private const val DIAS_COMPROMISO = 7
        private const val CENTAVOS_DE_MAS = 100L
        private const val RONDAS_DE_DRENADO = 6
        private const val DRENAR_MS = 5000L
        private val CLIENTES = listOf(11486, 11502, 12077, 13310, 14825)
        private val VENTAS = listOf(5000, 5013, 6120, 7302)
        private val MONTOS = listOf(10000L, 15000L, 20000L)
        private val HORAS = listOf("09:00", "12:30", "17:00")

        val PROBABILIDADES_POR_OMISION: Map<FallaBajada, Double> = mapOf(
            FallaBajada.PERDER_PETICION to 0.06,
            FallaBajada.PERDER_RESPUESTA to 0.08,
            FallaBajada.STATUS_503 to 0.05,
            FallaBajada.SYNC_FALLA to 0.06
        )

        private val FMT: DateTimeFormatter =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'").withZone(ZoneOffset.UTC)

        /** RFC3339 UTC con milisegundos fijos (como `RFC3339Nano` de Go, sin variar el largo). */
        fun fmt(t: Instant): String = FMT.format(t)

        private fun sha256(s: String): String = MessageDigest.getInstance(
            "SHA-256"
        ).digest(s.toByteArray()).joinToString("") { "%02x".format(it) }
    }
}
