package com.example.msp_app.core.database.dao.tareas

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.example.msp_app.core.database.entities.TareaZonaEntity
import com.example.msp_app.core.database.entities.TareasSyncStateEntity

/**
 * El único escritor de `tarea_zona` y `tareas_sync_state` (spec visitas §6.2 y
 * §5.4.3; plan visitas P3, Tarea 10; E-APP-121). Misma forma que
 * `VisitaZonaDao`, para que la bajada de tareas (Tarea 11) use el motor común
 * de cursor par con las mismas garantías:
 *
 * - I6: la página y su posición se escriben en una sola transacción, con la
 *   posición al final; si algo revienta, la transacción lo deshace todo.
 * - Este DAO solo nombra `tarea_zona` y `tareas_sync_state`: ninguna tabla de
 *   salida ni de dinero.
 *
 * Sin `INSERT … ON CONFLICT DO UPDATE` (SQLite 3.24): `minSdk` 24 trae SQLite
 * 3.9. Se usa `INSERT OR IGNORE` + `UPDATE` dentro de la transacción.
 */
@Dao
abstract class TareaZonaDao {

    /**
     * Guarda [items] y la posición `(cursor, afterId)` de [zona] en la misma
     * transacción. La generación persistida no cambia.
     *
     * @throws IllegalArgumentException si [cursor] es nulo y [afterId] no:
     *   media posición. No escribe nada.
     */
    @Transaction
    open suspend fun aplicarPagina(
        zona: Int,
        items: List<TareaZonaEntity>,
        cursor: String?,
        afterId: String?
    ) {
        require(cursor != null || afterId == null) {
            "tareas_sync_state: AFTER_ID sin CURSOR es media posición (zona $zona)"
        }
        if (items.isNotEmpty()) upsertAll(items)
        guardarEstado(zona, cursor, afterId)
    }

    /**
     * Cambio de zona: borra lo bajado de cualquier zona distinta de
     * [zonaNueva] y deja `CURSOR` y `AFTER_ID` nulos en TODAS las zonas, en la
     * misma transacción. La generación se conserva.
     */
    @Transaction
    open suspend fun reiniciarZona(zonaNueva: Int) {
        borrarFueraDeZona(zonaNueva)
        sinPosicion()
        crearEstadoSiFalta(zonaNueva)
    }

    /**
     * Replay por generación: borra todo lo bajado y la posición, en la misma
     * transacción. La generación nueva NO se persiste aquí (`COBRANZA-SYNC.md`
     * §3): se guarda con [guardarEpoch] cuando el replay terminó.
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

    @Query("SELECT * FROM tareas_sync_state WHERE ZONA = :zona")
    abstract suspend fun estado(zona: Int): TareasSyncStateEntity?

    /**
     * Filas bajadas de otra zona más posiciones de otra zona con cursor.
     * Mayor que 0 = el teléfono cambió de zona. Solo lectura.
     */
    @Query(
        """
        SELECT (SELECT COUNT(*) FROM tarea_zona WHERE ZONA_CLIENTE_ID <> :zona)
             + (SELECT COUNT(*) FROM tareas_sync_state WHERE ZONA <> :zona AND CURSOR IS NOT NULL)
        """
    )
    abstract suspend fun restosDeOtraZona(zona: Int): Int

    /** Lo bajado de [zona], ordenado por id. Solo lectura. */
    @Query("SELECT * FROM tarea_zona WHERE ZONA_CLIENTE_ID = :zona ORDER BY ID")
    abstract suspend fun deZona(zona: Int): List<TareaZonaEntity>

    // ─── piezas: solo se llaman desde las operaciones de arriba ─────────────

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    protected abstract suspend fun upsertAll(items: List<TareaZonaEntity>)

    @Transaction
    protected open suspend fun guardarEstado(zona: Int, cursor: String?, afterId: String?) {
        crearEstadoSiFalta(zona)
        actualizarPosicion(zona, cursor, afterId)
    }

    @Query(
        """
        INSERT OR IGNORE INTO tareas_sync_state (ZONA, CURSOR, AFTER_ID, EPOCH)
        VALUES (:zona, NULL, NULL, NULL)
        """
    )
    protected abstract suspend fun crearEstadoSiFalta(zona: Int)

    @Query("UPDATE tareas_sync_state SET CURSOR = :cursor, AFTER_ID = :afterId WHERE ZONA = :zona")
    protected abstract suspend fun actualizarPosicion(zona: Int, cursor: String?, afterId: String?)

    @Query("UPDATE tareas_sync_state SET EPOCH = :epoch WHERE ZONA = :zona")
    protected abstract suspend fun actualizarEpoch(zona: Int, epoch: Int)

    @Query("UPDATE tareas_sync_state SET CURSOR = NULL, AFTER_ID = NULL")
    protected abstract suspend fun sinPosicion()

    @Query("DELETE FROM tarea_zona WHERE ZONA_CLIENTE_ID <> :zona")
    protected abstract suspend fun borrarFueraDeZona(zona: Int)

    @Query("DELETE FROM tarea_zona")
    protected abstract suspend fun borrarTodo()
}
