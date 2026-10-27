package com.example.msp_app.core.sync.zona

/**
 * Una página de un canal de bajada por zona con cursor par, ya traducida del
 * DTO de Retrofit. [syncEpoch] tal como llegó (el motor decide qué es válido).
 */
data class PaginaSync<Dto>(
    val items: List<Dto>,
    val cursor: String?,
    val afterId: String?,
    val hasMore: Boolean,
    val syncEpoch: Int?
)

/** La posición persistida de una zona: el par y la generación ya replicada. */
data class EstadoSync(
    val cursor: String?,
    val afterId: String?,
    val epoch: Int?
)

/** Lo que hizo una corrida de [CursorParSyncEngine.correr]. */
data class CorridaCursorPar(
    val paginas: Int,
    val aplicadas: Int,
    val descartadas: Int,
    val replayPorEpoch: Boolean,
    /**
     * La posición en la que CERRÓ la corrida (la misma que quedó guardada).
     * Solo la usa la telemetría de cursor atorado de la bajada de visitas
     * (E-APP-110).
     */
    val cursor: String? = null,
    val afterId: String? = null
)

/**
 * Motor común de los canales de bajada por zona con cursor par
 * `(UPDATED_AT, ID)` y generación (`sync_epoch`) (plan visitas P3, Tarea 10;
 * spec visitas §5.4.3 y §5.4.5; E-APP-120).
 *
 * Extraído **sin cambios de conducta** de la bajada de visitas de P2
 * (`VisitasZonaSyncManager`, E-APP-101, E-APP-102). Lo usan la bajada de
 * visitas y, desde la Tarea 11, la de tareas: una sola implementación de las
 * reglas, no dos copias que puedan separarse. No es el motor de cobranza
 * (`CobranzaSyncManager`), que sigue intacto.
 *
 * ## El cursor es un PAR (`COBRANZA-SYNC.md` §2)
 *
 * - La página y su posición se escriben juntas con [aplicar], que debe ser
 *   UNA transacción (I6).
 * - La posición solo sale de la respuesta del servidor y de [estado]: nunca
 *   de las filas guardadas (E-VIS-070, E-APP-102).
 * - Sin cursor no hay media posición: `afterId` nulo.
 * - Página vacía con el MISMO cursor: se conserva el `afterId` que se tenía.
 * - Cursor movido sin filas: `afterId` nulo (E-APP-101, R7).
 *
 * ## La generación (`COBRANZA-SYNC.md` §3)
 *
 * 1. Se persiste solo cuando el replay terminó (`hasMore == false`).
 * 2. Un solo reinicio por corrida.
 * 3. Ausente, nula, 0 o negativa = mecanismo apagado.
 * 4. Si retrocede también replica: es identidad, no orden.
 * 5. Si cambia a media paginación no se persiste ninguna.
 * 6. Sin cursor guardado no hay nada que reiniciar: la corrida ya es desde el
 *    inicio y la generación se guarda al terminarla.
 *
 * El motor no conoce tablas ni DAOs: todo lo que escribe lo escribe por
 * [aplicar], [reiniciar] y [guardarEpoch]. Lanza lo que lancen ellos o
 * [pagina]; quien lo usa decide cómo reportarlo.
 */
class CursorParSyncEngine<Dto>(
    /** Pide una página: `(cursor, afterId, desde)`. */
    private val pagina: suspend (
        cursor: String?,
        afterId: String?,
        desde: String
    ) -> PaginaSync<Dto>,
    /** El id de una fila, para la posición cuando el servidor no manda `after_id`. */
    private val idDe: (Dto) -> String?,
    /**
     * Guarda las filas de la página y la posición `(cursor, afterId)` en UNA
     * transacción. Devuelve cuántas filas guardó (las demás se cuentan como
     * descartadas).
     */
    private val aplicar: suspend (items: List<Dto>, cursor: String?, afterId: String?) -> Int,
    /** La posición y la generación persistidas. */
    private val estado: suspend () -> EstadoSync,
    /** Replay: borra lo bajado y deja cursor y `afterId` nulos, juntos. No toca la generación. */
    private val reiniciar: suspend () -> Unit,
    /** Persiste la generación sin mover la posición. */
    private val guardarEpoch: suspend (Int) -> Unit
) {

    suspend fun correr(desde: String): CorridaCursorPar {
        val inicial = estado()
        // Generación ya replicada por completo; constante durante la corrida.
        val epochAplicado = inicial.epoch
        var cursor: String? = inicial.cursor
        var afterId: String? = inicial.afterId
        var paginas = 0
        var aplicadas = 0
        var descartadas = 0
        var replayPorEpoch = false
        var primeraVista = false
        var epochCorrida: Int? = null
        var epochEstable = true
        while (true) {
            val r = pagina(cursor, afterId, desde)
            paginas++
            val epochServidor = r.syncEpoch?.takeIf { it > 0 }
            if (!primeraVista) {
                if (epochServidor != null && epochServidor != epochAplicado && cursor != null) {
                    // Los dos, juntos, y lo bajado con ellos, en una
                    // transacción. La generación nueva NO se guarda aquí.
                    reiniciar()
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
            val items = r.items
            val siguienteCursor = r.cursor
            val siguienteAfterId = when {
                // Sin cursor no hay media posición posible.
                siguienteCursor == null -> null
                // Página con filas: la posición nueva es la última fila.
                r.afterId != null -> r.afterId
                items.isNotEmpty() -> idDe(items.last())
                // Página vacía con el MISMO cursor: la posición dentro del grupo
                // empatado sigue valiendo. Ponerla en nulo aquí volvería a bajar
                // el grupo entero en la siguiente corrida.
                siguienteCursor == cursor -> afterId
                // Cursor movido sin filas: el afterId viejo es de OTRO cursor;
                // el grupo nuevo se pide desde su inicio (solo cuesta red).
                else -> null
            }
            // Filas y posición en la MISMA transacción (E-APP-102).
            val guardadas = aplicar(items, siguienteCursor, siguienteAfterId)
            cursor = siguienteCursor
            afterId = siguienteAfterId
            aplicadas += guardadas
            descartadas += items.size - guardadas
            if (!r.hasMore) {
                val epochNuevo = epochCorrida
                if (epochEstable && epochNuevo != null && epochNuevo != epochAplicado) {
                    guardarEpoch(epochNuevo)
                }
                break
            }
        }
        return CorridaCursorPar(
            paginas = paginas,
            aplicadas = aplicadas,
            descartadas = descartadas,
            replayPorEpoch = replayPorEpoch,
            cursor = cursor,
            afterId = afterId
        )
    }
}
