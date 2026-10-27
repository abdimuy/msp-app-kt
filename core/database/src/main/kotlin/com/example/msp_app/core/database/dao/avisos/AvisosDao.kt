package com.example.msp_app.core.database.dao.avisos

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.example.msp_app.core.database.entities.AvisoEmitidoEntity
import com.example.msp_app.core.database.entities.AvisoProgramadoEntity

/**
 * Avisos del teléfono (spec visitas §6.5 y §6.6; plan visitas P3, Tareas 10,
 * 12 y 13; E-APP-121):
 *
 * - `aviso_emitido`: qué notificaciones "Visita asignada" ya se mostraron.
 *   [marcarEmitido] es la llave de la idempotencia.
 * - `aviso_programado`: las alarmas que deben existir. Fuente de verdad; las
 *   de `AlarmManager` se reconstruyen desde aquí.
 *
 * Solo nombra esas dos tablas.
 */
@Dao
abstract class AvisosDao {

    /**
     * Sustituye TODO lo programado por [nuevos] en una transacción y devuelve
     * lo que había antes, para que quien proyecta a `AlarmManager` cancele lo
     * que ya no está.
     */
    @Transaction
    open suspend fun reemplazarProgramados(
        nuevos: List<AvisoProgramadoEntity>
    ): List<AvisoProgramadoEntity> {
        val anteriores = programados()
        borrarProgramados()
        if (nuevos.isNotEmpty()) insertarProgramados(nuevos)
        return anteriores
    }

    /** Lo programado, por instante y luego por id. */
    @Query("SELECT * FROM aviso_programado ORDER BY INSTANTE, ID")
    abstract suspend fun programados(): List<AvisoProgramadoEntity>

    /**
     * Marca [id] como notificado en [en] (RFC3339 UTC). `true` solo la primera
     * vez: si ya estaba, no cambia nada (ni la hora) y devuelve `false`.
     */
    open suspend fun marcarEmitido(id: String, en: String): Boolean =
        insertarEmitido(AvisoEmitidoEntity(ID = id, EMITIDO_EN = en)) != NO_INSERTADO

    @Query("SELECT EXISTS(SELECT 1 FROM aviso_emitido WHERE ID = :id)")
    abstract suspend fun emitido(id: String): Boolean

    /**
     * Olvida los avisos emitidos antes de [limite] (RFC3339 UTC, mismo formato
     * que `EMITIDO_EN`, así que compara como texto).
     */
    @Query("DELETE FROM aviso_emitido WHERE EMITIDO_EN < :limite")
    abstract suspend fun podarEmitidosAnterioresA(limite: String)

    // ─── piezas ─────────────────────────────────────────────────────────────

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    protected abstract suspend fun insertarEmitido(aviso: AvisoEmitidoEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    protected abstract suspend fun insertarProgramados(avisos: List<AvisoProgramadoEntity>)

    @Query("DELETE FROM aviso_programado")
    protected abstract suspend fun borrarProgramados()

    private companion object {
        /** Lo que devuelve un `INSERT OR IGNORE` que no insertó. */
        const val NO_INSERTADO = -1L
    }
}
