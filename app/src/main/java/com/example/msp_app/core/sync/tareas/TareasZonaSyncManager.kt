package com.example.msp_app.core.sync.tareas

import android.util.Log
import com.example.msp_app.core.common.time.BUSINESS_ZONE
import com.example.msp_app.core.database.dao.tareas.TareaZonaDao
import com.example.msp_app.core.database.entities.TareaZonaEntity
import com.example.msp_app.core.sync.visitas.ResultadoSync
import com.example.msp_app.core.sync.visitas.VisitasZonaSyncManager
import com.example.msp_app.core.sync.zona.CursorParSyncEngine
import com.example.msp_app.core.sync.zona.EstadoSync
import com.example.msp_app.core.sync.zona.PaginaSync
import com.example.msp_app.data.api.services.visits.TareaZonaDto
import com.example.msp_app.data.api.services.visits.TareasZonaSyncApi
import java.time.Instant
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Baja las tareas de oficina de la zona
 * (`/v2/visitas/sync/tareas/zona/{zona_id}`) a `tarea_zona` (plan visitas P3,
 * Tarea 11; spec §6.2; E-APP-122).
 *
 * Es el segundo consumidor de [CursorParSyncEngine] (E-APP-120): las reglas del
 * par `(UPDATED_AT, ID)` y de la generación (`sync_epoch`) NO están copiadas
 * aquí, viven en el motor y las comparte con [VisitasZonaSyncManager]. Esta
 * clase solo dice de dónde sale la página y dónde se escribe:
 *
 * - Siempre `TareaZonaDao.aplicarPagina`: la página y su posición en UNA
 *   transacción (I6). Nunca `Visit` ni ninguna tabla de pagos o ventas.
 * - El cursor solo sale de la respuesta del servidor y de `tareas_sync_state`,
 *   nunca de las filas de `tarea_zona` (E-VIS-070, E-APP-102).
 * - `desde` se calcula una vez por corrida con la misma ventana que las
 *   visitas ([VisitasZonaSyncManager.VENTANA_DIAS]); el servidor lo aplica a
 *   la FECHA civil de la tarea.
 * - Una tarea cancelada llega como un cambio (`cancelada_at`), no como lápida:
 *   el upsert reemplaza la fila. El estado (atendida, no atendida…) no se
 *   guarda: se deriva (`derivarEstadoTarea`).
 * - La nota de la tarea SÍ se guarda y viaja a los teléfonos de la zona, hasta
 *   500 caracteres (decisión del dueño, 2026-10-19). La nota de una visita no.
 *
 * ## [alAplicar]
 *
 * Recibe las filas de cada página YA guardada (el aviso inmediato "Visita
 * asignada" de la Tarea 12 cuelga de aquí). Va después de la transacción: si
 * revienta, se registra y el sync sigue — la posición ya avanzó y un aviso
 * perdido no debe tumbar la bajada ni repetirla. La idempotencia del aviso es
 * de quien lo consume (`aviso_emitido`).
 *
 * ## Kill-switch
 *
 * [killSwitch] `false` = no baja nada: ni red ni escrituras.
 *
 * Nunca lanza (salvo cancelación): devuelve [ResultadoSync.Error] y la
 * siguiente corrida retoma desde la última página aplicada.
 */
class TareasZonaSyncManager(
    private val api: TareasZonaSyncApi,
    private val dao: TareaZonaDao,
    private val zonaActual: () -> Int?,
    private val killSwitch: () -> Boolean,
    private val alAplicar: suspend (List<TareaZonaEntity>) -> Unit = {},
    /** Solo reporta; no decide nada. Si revienta, la corrida sigue. */
    private val telemetria: (ResultadoSync) -> Unit = {},
    private val reloj: () -> Instant = { Instant.now() },
    /** Serializa el disparo periódico y el del despertador FCM. */
    private val mutex: Mutex = MUTEX_DEL_PROCESO
) {

    suspend fun sincronizar(): ResultadoSync {
        val resultado = mutex.withLock { sincronizarBajoCandado() }
        try {
            telemetria(resultado)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "telemetria de la bajada de tareas fallo; el sync no se afecta", e)
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
            Log.w(TAG, "bajada de tareas zona=$zona fallo: ${e.message}", e)
            ResultadoSync.Error(e)
        }
    }

    /** hoy − [VisitasZonaSyncManager.VENTANA_DIAS] a las 00:00 de la zona del negocio, como instante RFC3339. */
    private fun desdeDeLaCorrida(): String {
        val hoy = reloj().atZone(BUSINESS_ZONE).toLocalDate()
        val inicio = hoy.minusDays(VisitasZonaSyncManager.VENTANA_DIAS)
            .atStartOfDay(BUSINESS_ZONE)
            .toInstant()
        return DateTimeFormatter.ISO_INSTANT.format(inicio)
    }

    private suspend fun bajar(zona: Int, desde: String): ResultadoSync.Ok {
        val cambioDeZona = dao.restosDeOtraZona(zona) > 0
        if (cambioDeZona) {
            Log.i(TAG, "cambio de zona: se borran las tareas bajadas de otras zonas (zona=$zona)")
            dao.reiniciarZona(zona)
        }
        val corrida = motorDe(zona).correr(desde)
        if (corrida.descartadas > 0) {
            Log.w(
                TAG,
                "bajada de tareas zona=$zona: ${corrida.descartadas} fila(s) sin datos minimos"
            )
        }
        return ResultadoSync.Ok(
            paginas = corrida.paginas,
            aplicadas = corrida.aplicadas,
            descartadas = corrida.descartadas,
            replayPorEpoch = corrida.replayPorEpoch,
            cambioDeZona = cambioDeZona
        )
    }

    private fun motorDe(zona: Int) = CursorParSyncEngine<TareaZonaDto>(
        pagina = { cursor, afterId, desde ->
            val r = api.sync(zona, desde, cursor, afterId, TareasZonaSyncApi.LIMITE_PAGINA)
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
            avisarSinRiesgo(filas)
            filas.size
        },
        estado = {
            val e = dao.estado(zona)
            EstadoSync(cursor = e?.CURSOR, afterId = e?.AFTER_ID, epoch = e?.EPOCH)
        },
        reiniciar = {
            Log.i(TAG, "generación distinta, replay desde el inicio (zona=$zona)")
            dao.reiniciarPorEpoch(zona)
        },
        guardarEpoch = { dao.guardarEpoch(zona, it) }
    )

    @Suppress("TooGenericExceptionCaught")
    private suspend fun avisarSinRiesgo(filas: List<TareaZonaEntity>) {
        if (filas.isEmpty()) return
        try {
            alAplicar(filas)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "el aviso de ${filas.size} tarea(s) fallo; la bajada sigue", e)
        }
    }

    private companion object {
        const val TAG = "TareasZonaSync"

        val MUTEX_DEL_PROCESO = Mutex()
    }
}

/**
 * La fila tal como la guarda `tarea_zona`, o `null` si le falta lo mínimo (id,
 * venta, cliente, fecha, `created_at` o `updated_at`): se descarta y se cuenta,
 * sin frenar la página.
 *
 * `ZONA_CLIENTE_ID` = la zona que se pidió: el servidor solo entrega filas de
 * esa zona, y una fila guardada con otra zona dispararía un cambio de zona en
 * cada corrida.
 */
internal fun TareaZonaDto.aEntidad(zona: Int): TareaZonaEntity? {
    val id = id?.takeIf { it.isNotBlank() } ?: return null
    return TareaZonaEntity(
        ID = id,
        VENTA_ID = venta_id ?: return null,
        CLIENTE_ID = cliente_id ?: return null,
        ZONA_CLIENTE_ID = zona,
        FECHA = fecha?.takeIf { it.isNotBlank() } ?: return null,
        HORA = hora?.takeIf { it.isNotBlank() },
        NOTA = nota,
        REEMPLAZA_A = reemplaza_a?.takeIf { it.isNotBlank() },
        CREATED_AT = created_at?.takeIf { it.isNotBlank() } ?: return null,
        UPDATED_AT = updated_at?.takeIf { it.isNotBlank() } ?: return null,
        CANCELADA_AT = cancelada_at?.takeIf { it.isNotBlank() }
    )
}
