package com.example.msp_app.core.database.dao.visit

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.example.msp_app.core.database.entities.VisitaZonaEntity
import com.example.msp_app.core.database.entities.VisitasSyncStateEntity

/** Un renglón del inventario local: id y `UPDATED_AT` tal como llegó. */
data class IdYUpdatedAt(val id: String, val updatedAt: String)

/**
 * El único escritor de `visita_zona` y `visitas_sync_state` (spec visitas
 * §5.4.8, E-APP-080).
 *
 * I8: este DAO no nombra la tabla de salida de las visitas capturadas en
 * ningún método, ni para leer ni para escribir; lo cobra
 * `VisitaZonaNoEscribeVisitTest`. Un reinicio de esta agenda nunca puede
 * borrar una captura pendiente.
 *
 * I6: la página y su posición se escriben en una sola transacción. Cada
 * operación compuesta deja la escritura de la posición al final, de modo que
 * si algo revienta, ya se habrá escrito lo demás y solo la transacción lo
 * deshace (lo prueba `VisitaZonaDaoTest` con un trigger que aborta).
 *
 * Sin `INSERT … ON CONFLICT DO UPDATE` (upsert de SQLite 3.24): `minSdk` 24
 * trae SQLite 3.9 y la sentencia no existiría en esos teléfonos aunque pase en
 * Robolectric. Se usa `INSERT OR IGNORE` + `UPDATE` dentro de la transacción.
 */
@Dao
abstract class VisitaZonaDao {

    /**
     * Aplica una página del canal de bajada: guarda [items] y la posición
     * `(cursor, afterId)` de [zona] en la misma transacción. Escribe la
     * posición que recibe, tal cual: decidir qué `afterId` corresponde a una
     * página vacía es del gestor del sync (Tarea 16). La generación persistida
     * no cambia.
     *
     * @throws IllegalArgumentException si [cursor] es nulo y [afterId] no:
     *   media posición. No escribe nada.
     */
    @Transaction
    open suspend fun aplicarPagina(
        zona: Int,
        items: List<VisitaZonaEntity>,
        cursor: String?,
        afterId: String?
    ) {
        require(cursor != null || afterId == null) {
            "visitas_sync_state: AFTER_ID sin CURSOR es media posición (zona $zona)"
        }
        if (items.isNotEmpty()) upsertAll(items)
        guardarEstado(zona, cursor, afterId)
    }

    /**
     * Cambio de zona: borra lo bajado de cualquier zona distinta de
     * [zonaNueva] y deja `CURSOR` y `AFTER_ID` nulos en la misma transacción.
     *
     * Deja sin posición a TODAS las zonas, no solo a [zonaNueva]: las filas de
     * la zona vieja se acaban de borrar, y si su cursor sobreviviera, al volver
     * a ella el teléfono creería tenerlas y no bajarían nunca. La generación
     * (`EPOCH`) se conserva.
     */
    @Transaction
    open suspend fun reiniciarZona(zonaNueva: Int) {
        borrarFueraDeZona(zonaNueva)
        sinPosicion()
        crearEstadoSiFalta(zonaNueva)
    }

    /**
     * Replay por generación: borra todo lo bajado y la posición, en la misma
     * transacción. La generación nueva NO se persiste aquí: se guarda con
     * [guardarEpoch] solo cuando el replay terminó (`COBRANZA-SYNC.md` §3).
     */
    @Transaction
    open suspend fun reiniciarPorEpoch(zona: Int) {
        borrarTodo()
        sinPosicion()
        crearEstadoSiFalta(zona)
    }

    /** Persiste la generación de [zona] sin mover su posición. */
    @Transaction
    open suspend fun guardarEpoch(zona: Int, epoch: Int) {
        crearEstadoSiFalta(zona)
        actualizarEpoch(zona, epoch)
    }

    @Query("SELECT * FROM visitas_sync_state WHERE ZONA = :zona")
    abstract suspend fun estado(zona: Int): VisitasSyncStateEntity?

    /**
     * Restos de una zona distinta de [zona]: filas bajadas de otra zona más
     * posiciones de otra zona que todavía tienen cursor. Mayor que 0 = el
     * teléfono cambió de zona y toca [reiniciarZona] (Tarea 16, E-APP-103).
     * Solo lectura: decide un reinicio, nunca reconstruye una posición
     * (E-VIS-070).
     */
    @Query(
        """
        SELECT (SELECT COUNT(*) FROM visita_zona WHERE ZONA_CLIENTE_ID <> :zona)
             + (SELECT COUNT(*) FROM visitas_sync_state WHERE ZONA <> :zona AND CURSOR IS NOT NULL)
        """
    )
    abstract suspend fun restosDeOtraZona(zona: Int): Int

    /** Lo bajado de [zona], ordenado por id. Solo lectura. */
    @Query("SELECT * FROM visita_zona WHERE ZONA_CLIENTE_ID = :zona ORDER BY ID")
    abstract suspend fun deZona(zona: Int): List<VisitaZonaEntity>

    /**
     * El inventario local de [zona] con `FECHA` en o después de [desde]
     * (`yyyy-MM-dd`; compara como texto contra el RFC3339 del servidor, así que
     * el día [desde] entero entra), ordenado por id.
     */
    @Query(
        """
        SELECT ID AS id, UPDATED_AT AS updatedAt FROM visita_zona
        WHERE ZONA_CLIENTE_ID = :zona AND FECHA >= :desde
        ORDER BY ID
        """
    )
    abstract suspend fun idsYUpdatedAt(zona: Int, desde: String): List<IdYUpdatedAt>

    /**
     * Borra [ids] de lo bajado, en tandas de [TANDA]: Android ≤ 11 admite 999
     * parámetros por sentencia (`CLAUDE.md` §1).
     */
    @Transaction
    open suspend fun borrar(ids: List<String>) {
        ids.chunked(TANDA).forEach { borrarTanda(it) }
    }

    /** Borra lo bajado con `FECHA` anterior a [desde] (`yyyy-MM-dd`). */
    @Query("DELETE FROM visita_zona WHERE FECHA < :desde")
    abstract suspend fun podarFueraDeVentana(desde: String)

    // ─── piezas: solo se llaman desde las operaciones de arriba ─────────────

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    protected abstract suspend fun upsertAll(items: List<VisitaZonaEntity>)

    @Transaction
    protected open suspend fun guardarEstado(zona: Int, cursor: String?, afterId: String?) {
        crearEstadoSiFalta(zona)
        actualizarPosicion(zona, cursor, afterId)
    }

    @Query(
        """
        INSERT OR IGNORE INTO visitas_sync_state (ZONA, CURSOR, AFTER_ID, EPOCH)
        VALUES (:zona, NULL, NULL, NULL)
        """
    )
    protected abstract suspend fun crearEstadoSiFalta(zona: Int)

    @Query("UPDATE visitas_sync_state SET CURSOR = :cursor, AFTER_ID = :afterId WHERE ZONA = :zona")
    protected abstract suspend fun actualizarPosicion(zona: Int, cursor: String?, afterId: String?)

    @Query("UPDATE visitas_sync_state SET EPOCH = :epoch WHERE ZONA = :zona")
    protected abstract suspend fun actualizarEpoch(zona: Int, epoch: Int)

    @Query("UPDATE visitas_sync_state SET CURSOR = NULL, AFTER_ID = NULL")
    protected abstract suspend fun sinPosicion()

    @Query("DELETE FROM visita_zona WHERE ZONA_CLIENTE_ID <> :zona")
    protected abstract suspend fun borrarFueraDeZona(zona: Int)

    @Query("DELETE FROM visita_zona")
    protected abstract suspend fun borrarTodo()

    @Query("DELETE FROM visita_zona WHERE ID IN (:ids)")
    protected abstract suspend fun borrarTanda(ids: List<String>)

    private companion object {
        const val TANDA = 900
    }
}
