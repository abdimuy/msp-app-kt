package com.example.msp_app.core.database.dao.ubicacion

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.msp_app.core.database.entities.UbicacionDeCapturaEntity

/**
 * `ubicacion_de_captura` (E-APP-145). Una fila por captura: el insert es
 * `IGNORE`, asi que una ubicacion ya anotada nunca se reemplaza ni se escribe
 * dos veces. Las transiciones de estado son condicionales a `pendiente`: un
 * reintento tardio nunca hace retroceder una fila ya final.
 */
@Dao
interface UbicacionDeCapturaDao {

    /** Devuelve el rowid, o -1 si ya habia una fila con ese id (no se toca). */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun anotar(fila: UbicacionDeCapturaEntity): Long

    @Query("SELECT * FROM ubicacion_de_captura WHERE id = :id")
    suspend fun obtener(id: String): UbicacionDeCapturaEntity?

    @Query("SELECT * FROM ubicacion_de_captura WHERE estado_envio = 'pendiente' AND tipo = :tipo")
    suspend fun pendientes(tipo: String): List<UbicacionDeCapturaEntity>

    @Query(
        "UPDATE ubicacion_de_captura SET estado_envio = 'enviada' " +
            "WHERE id = :id AND estado_envio = 'pendiente'"
    )
    suspend fun marcarEnviada(id: String): Int

    @Query(
        "UPDATE ubicacion_de_captura SET estado_envio = 'descartada' " +
            "WHERE id = :id AND estado_envio = 'pendiente'"
    )
    suspend fun marcarDescartada(id: String): Int

    @Query(
        "UPDATE ubicacion_de_captura SET intentos = intentos + 1 " +
            "WHERE id = :id AND estado_envio = 'pendiente'"
    )
    suspend fun sumarIntento(id: String): Int
}
