package com.example.msp_app.core.sync.visitas

import android.util.Log
import com.example.msp_app.core.common.time.BUSINESS_ZONE
import com.example.msp_app.core.database.dao.visit.VisitaZonaDao
import com.example.msp_app.core.database.entities.VisitaZonaEntity
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
 * `VisitasZonaSyncManagerTest`:
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

    /** hoy − [VENTANA_DIAS] a las 00:00 de la zona del negocio, como instante RFC3339. */
    private fun desdeDeLaCorrida(): String {
        val hoy = reloj().atZone(BUSINESS_ZONE).toLocalDate()
        val inicio = hoy.minusDays(VENTANA_DIAS).atStartOfDay(BUSINESS_ZONE).toInstant()
        return DateTimeFormatter.ISO_INSTANT.format(inicio)
    }

    private suspend fun bajar(zona: Int, desde: String): ResultadoSync.Ok {
        val cambioDeZona = dao.restosDeOtraZona(zona) > 0
        if (cambioDeZona) {
            Log.i(TAG, "cambio de zona: se borra la agenda bajada de otras zonas (zona=$zona)")
            dao.reiniciarZona(zona)
        }
        val estado = dao.estado(zona)
        // Generación ya replicada por completo; constante durante la corrida.
        val epochAplicado = estado?.EPOCH
        // El par sale del estado persistido, que solo escribe la respuesta del
        // servidor (E-VIS-070): nunca de las filas de `visita_zona`.
        var cursor: String? = estado?.CURSOR
        var afterId: String? = estado?.AFTER_ID
        var paginas = 0
        var aplicadas = 0
        var descartadas = 0
        var replayPorEpoch = false
        var primeraVista = false
        var epochCorrida: Int? = null
        var epochEstable = true
        while (true) {
            val r = api.sync(zona, desde, cursor, afterId, VisitasZonaSyncApi.LIMITE_PAGINA)
            paginas++
            val epochServidor = r.sync_epoch?.takeIf { it > 0 }
            if (!primeraVista) {
                if (epochServidor != null && epochServidor != epochAplicado && cursor != null) {
                    Log.i(
                        TAG,
                        "generación $epochAplicado -> $epochServidor, replay desde el inicio"
                    )
                    // Los dos, juntos, y la agenda bajada con ellos, en una
                    // transacción. La generación nueva NO se guarda aquí.
                    dao.reiniciarPorEpoch(zona)
                    cursor = null
                    afterId = null
                    replayPorEpoch = true
                    continue
                }
                primeraVista = true
                epochCorrida = epochServidor
            } else if (epochServidor != epochCorrida) {
                epochEstable = false
            }
            val items = r.items.orEmpty()
            val filas = items.mapNotNull { it.aEntidad(zona) }
            descartadas += items.size - filas.size
            val siguienteCursor = r.cursor
            val siguienteAfterId = when {
                // Sin cursor no hay media posición posible.
                siguienteCursor == null -> null
                // Página con filas: la posición nueva es la última fila.
                r.after_id != null -> r.after_id
                items.isNotEmpty() -> items.last().id
                // Página vacía con el MISMO cursor: la posición dentro del grupo
                // empatado sigue valiendo. Ponerla en nulo aquí volvería a bajar
                // el grupo entero en la siguiente corrida.
                siguienteCursor == cursor -> afterId
                // Cursor movido sin filas: el after_id viejo es de OTRO cursor;
                // el grupo nuevo se pide desde su inicio (solo cuesta red).
                else -> null
            }
            // Filas y posición en la MISMA transacción (E-APP-102).
            dao.aplicarPagina(zona, filas, siguienteCursor, siguienteAfterId)
            cursor = siguienteCursor
            afterId = siguienteAfterId
            aplicadas += filas.size
            if (r.has_more != true) {
                val epochNuevo = epochCorrida
                if (epochEstable && epochNuevo != null && epochNuevo != epochAplicado) {
                    dao.guardarEpoch(zona, epochNuevo)
                }
                break
            }
        }
        if (descartadas > 0) Log.w(TAG, "bajada zona=$zona: $descartadas fila(s) sin datos minimos")
        return ResultadoSync.Ok(
            paginas = paginas,
            aplicadas = aplicadas,
            descartadas = descartadas,
            replayPorEpoch = replayPorEpoch,
            cambioDeZona = cambioDeZona
        )
    }

    companion object {
        /** V del servidor (`VISITAS_BAJADA_VENTANA_DIAS`, DA-3 = 92). */
        const val VENTANA_DIAS = 92L

        private const val TAG = "VisitasZonaSync"

        private val MUTEX_DEL_PROCESO = Mutex()
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

    data class Ok(
        val paginas: Int,
        val aplicadas: Int,
        val descartadas: Int,
        val replayPorEpoch: Boolean,
        val cambioDeZona: Boolean
    ) : ResultadoSync()

    data class Error(val causa: Throwable) : ResultadoSync()
}
