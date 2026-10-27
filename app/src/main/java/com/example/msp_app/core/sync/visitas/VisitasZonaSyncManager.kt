package com.example.msp_app.core.sync.visitas

import android.util.Log
import com.example.msp_app.core.common.time.BUSINESS_ZONE
import com.example.msp_app.core.database.dao.visit.VisitaZonaDao
import com.example.msp_app.core.database.entities.VisitaZonaEntity
import com.example.msp_app.core.sync.zona.CursorParSyncEngine
import com.example.msp_app.core.sync.zona.EstadoSync
import com.example.msp_app.core.sync.zona.PaginaSync
import com.example.msp_app.data.api.services.visits.VisitaZonaDto
import com.example.msp_app.data.api.services.visits.VisitasZonaSyncApi
import java.time.Instant
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Baja la agenda de visitas de la zona (`/v2/visitas/sync/zona/{zona_id}`) a
 * `visita_zona` (plan visitas P2, Tarea 16; spec §5.4.8; E-APP-101).
 *
 * Copia deliberada de las reglas de `CobranzaSyncManager.syncResource`
 * (`COBRANZA-SYNC.md` §2 y §3), cada una con la prueba del mismo nombre en
 * `VisitasZonaSyncManagerTest`. Desde visitas P3 (Tarea 10, E-APP-120) las
 * reglas del par y de la generación viven en el motor común
 * [CursorParSyncEngine], que comparte con la bajada de tareas; esta clase
 * conserva el cambio de zona, el `desde` y la traducción a `visita_zona`:
 *
 * - **El cursor es un PAR** `(UPDATED_AT, ID)`. La página y su posición se
 *   escriben juntas con `VisitaZonaDao.aplicarPagina`, en UNA transacción
 *   (I6). Una página vacía conserva el `AFTER_ID` guardado: el servidor
 *   devuelve el cursor recibido y `after_id = null` ("sin posición nueva").
 * - **El cursor solo sale de la respuesta del servidor** (E-VIS-070,
 *   E-APP-102): nunca se reconstruye de lo que hay en `visita_zona`, ni del
 *   máximo `UPDATED_AT` de las filas. Si no hay posición guardada se pide
 *   desde el inicio, aunque haya filas.
 * - **La generación** (`sync_epoch`) se persiste solo cuando el replay terminó
 *   (`has_more == false`) y fue la misma en todas las páginas; un solo
 *   reinicio por corrida; ausente, nula, 0 o negativa = apagada; si retrocede
 *   también replica.
 * - **`desde` se calcula una vez por corrida** y viaja en todas las páginas:
 *   hoy (zona del negocio) − [VENTANA_DIAS] a las 00:00 de
 *   America/Mexico_City, el mismo default que el servidor
 *   (`domain.DesdeBajadaPorOmision`). Si el servidor cambia la ventana, sube
 *   el epoch `visitas`.
 *
 * ## I8: nunca `Visit` (E-APP-103)
 *
 * Esta clase no recibe el DAO de `Visit` ni la base entera: no tiene con qué
 * escribir lo capturado. El cambio de zona y el replay borran solo
 * `visita_zona` (`reiniciarZona`, `reiniciarPorEpoch`).
 *
 * ## La nota no se guarda (E-VIS-066)
 *
 * `VisitaZonaDto` no declara la nota y [aEntidad] escribe `NOTA = null`
 * siempre, aunque la columna exista.
 *
 * ## Kill-switch
 *
 * [killSwitch] `false` = no baja nada: ni red ni escrituras. El de producción
 * (`KillSwitchAgenda`) está apagado por omisión.
 *
 * Nunca lanza (salvo cancelación): devuelve [ResultadoSync.Error] y la
 * siguiente corrida retoma desde la última página aplicada.
 */
class VisitasZonaSyncManager(
    private val api: VisitasZonaSyncApi,
    private val dao: VisitaZonaDao,
    private val zonaActual: () -> Int?,
    private val killSwitch: () -> Boolean,
    /** Solo reporta; no decide nada. Si revienta, la corrida sigue. */
    private val telemetria: (ResultadoSync) -> Unit = {},
    private val reloj: () -> Instant = { Instant.now() },
    /** Serializa el disparo periódico y el del aviso SSE. */
    private val mutex: Mutex = MUTEX_DEL_PROCESO
) {

    suspend fun sincronizar(): ResultadoSync {
        val resultado = mutex.withLock { sincronizarBajoCandado() }
        try {
            telemetria(resultado)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "telemetria de la bajada fallo; el sync no se afecta", e)
        }
        return resultado
    }

    @Suppress("TooGenericExceptionCaught")
    private suspend fun sincronizarBajoCandado(): ResultadoSync {
        if (!killSwitch()) return ResultadoSync.Apagado
        val zona = zonaActual() ?: return ResultadoSync.SinZona
        return try {
            bajar(zona, desdeDeLaCorrida())
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "bajada de visitas zona=$zona fallo: ${e.message}", e)
            ResultadoSync.Error(e)
        }
    }

    private fun desdeDeLaCorrida(): String = desdeDeLaVentana(reloj())

    private suspend fun bajar(zona: Int, desde: String): ResultadoSync.Ok {
        val cambioDeZona = dao.restosDeOtraZona(zona) > 0
        if (cambioDeZona) {
            Log.i(TAG, "cambio de zona: se borra la agenda bajada de otras zonas (zona=$zona)")
            dao.reiniciarZona(zona)
        }
        val corrida = motorDe(zona).correr(desde)
        if (corrida.descartadas > 0) {
            Log.w(TAG, "bajada zona=$zona: ${corrida.descartadas} fila(s) sin datos minimos")
        }
        return ResultadoSync.Ok(
            paginas = corrida.paginas,
            aplicadas = corrida.aplicadas,
            descartadas = corrida.descartadas,
            replayPorEpoch = corrida.replayPorEpoch,
            cambioDeZona = cambioDeZona,
            cursor = corrida.cursor,
            afterId = corrida.afterId
        )
    }

    /**
     * Las reglas del par y de la generación viven en [CursorParSyncEngine]
     * (E-APP-120); aquí solo se dice de dónde sale la página y dónde se
     * escribe: siempre `VisitaZonaDao`, nunca `Visit` (I8).
     */
    private fun motorDe(zona: Int) = CursorParSyncEngine<VisitaZonaDto>(
        pagina = { cursor, afterId, desde ->
            val r = api.sync(zona, desde, cursor, afterId, VisitasZonaSyncApi.LIMITE_PAGINA)
            PaginaSync(
                items = r.items.orEmpty(),
                cursor = r.cursor,
                afterId = r.after_id,
                hasMore = r.has_more == true,
                syncEpoch = r.sync_epoch
            )
        },
        idDe = { it.id },
        aplicar = { items, cursor, afterId ->
            val filas = items.mapNotNull { it.aEntidad(zona) }
            // Filas y posición en la MISMA transacción (E-APP-102).
            dao.aplicarPagina(zona, filas, cursor, afterId)
            filas.size
        },
        estado = {
            // El par sale del estado persistido, que solo escribe la respuesta
            // del servidor (E-VIS-070): nunca de las filas de `visita_zona`.
            val e = dao.estado(zona)
            EstadoSync(cursor = e?.CURSOR, afterId = e?.AFTER_ID, epoch = e?.EPOCH)
        },
        reiniciar = {
            Log.i(TAG, "generación distinta, replay desde el inicio (zona=$zona)")
            // Cursor, after_id y la agenda bajada, juntos, en una transacción.
            dao.reiniciarPorEpoch(zona)
        },
        guardarEpoch = { dao.guardarEpoch(zona, it) }
    )

    companion object {
        /** V del servidor (`VISITAS_BAJADA_VENTANA_DIAS`, DA-3 = 92). */
        const val VENTANA_DIAS = 92L

        private const val TAG = "VisitasZonaSync"

        /**
         * Serializa todo lo que escribe `visita_zona` en el proceso: la bajada
         * y el inventario de la Tarea 18 (E-APP-109). Si el inventario corriera
         * a media bajada, borraría una fila que el sync acaba de aplicar y que el
         * servidor no listó en sus `ids` porque llegó después, y el cursor ya
         * estaría más allá de ella.
         */
        internal val MUTEX_DEL_PROCESO = Mutex()

        /**
         * hoy − [VENTANA_DIAS] a las 00:00 de la zona del negocio, como instante
         * RFC3339: el `desde` de los cuatro canales (I4). La bajada y el
         * inventario lo sacan de aquí para mandar exactamente el mismo.
         */
        fun desdeDeLaVentana(ahora: Instant): String {
            val hoy = ahora.atZone(BUSINESS_ZONE).toLocalDate()
            val inicio = hoy.minusDays(VENTANA_DIAS).atStartOfDay(BUSINESS_ZONE).toInstant()
            return DateTimeFormatter.ISO_INSTANT.format(inicio)
        }
    }
}

/**
 * La fila tal como la guarda `visita_zona`, o `null` si le falta lo mínimo
 * (id, cliente, fecha, tipo o `updated_at`): se descarta y se cuenta, sin
 * frenar la página.
 *
 * - `ZONA_CLIENTE_ID` = la zona que se pidió: el servidor solo entrega filas de
 *   esa zona, y una fila guardada con otra zona dispararía un cambio de zona
 *   en cada corrida.
 * - `NOTA` = `null` siempre (E-VIS-066, E-APP-103).
 */
internal fun VisitaZonaDto.aEntidad(zona: Int): VisitaZonaEntity? {
    val id = id?.takeIf { it.isNotBlank() } ?: return null
    return VisitaZonaEntity(
        ID = id,
        CLIENTE_ID = cliente_id ?: return null,
        COBRADOR_ID = cobrador_id,
        COBRADOR = cobrador,
        FECHA = fecha ?: return null,
        TIPO_VISITA = tipo_visita ?: return null,
        ZONA_CLIENTE_ID = zona,
        IMPTE_DOCTO_CC_ID = impte_docto_cc_id,
        NOTA = null,
        PROMESA_VENTA_ID = promesa_venta_id,
        PROMESA_FECHA = promesa_fecha,
        PROMESA_MONTO_CENTAVOS = promesa_monto_centavos,
        CITA_FECHA = cita_fecha,
        CITA_HORA = cita_hora,
        UPDATED_AT = updated_at ?: return null
    )
}

/** Lo que hizo una corrida de [VisitasZonaSyncManager.sincronizar]. */
sealed class ResultadoSync {
    /** Kill-switch apagado: no se bajó nada. */
    object Apagado : ResultadoSync()

    /** Sin zona del cobrador todavía. */
    object SinZona : ResultadoSync()

    /**
     * [cursor] y [afterId]: la posición en la que CERRÓ la corrida, la misma
     * que quedó guardada. Solo la usa la telemetría de cursor atorado
     * (E-APP-110), que compara cierre contra cierre entre corridas.
     */
    data class Ok(
        val paginas: Int,
        val aplicadas: Int,
        val descartadas: Int,
        val replayPorEpoch: Boolean,
        val cambioDeZona: Boolean,
        val cursor: String? = null,
        val afterId: String? = null
    ) : ResultadoSync()

    data class Error(val causa: Throwable) : ResultadoSync()
}
