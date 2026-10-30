package com.example.msp_app.core.database.dao.ubicacion

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.msp_app.core.database.entities.PlazoDeEnvioEntity

/**
 * `plazo_de_envio` (E-APP-145). El insert es `IGNORE` y NUNCA lanza por un id repetido: se escribe
 * dentro de la transaccion del dinero y un fallo aqui no puede revertirlo.
 */
@Dao
interface PlazoDeEnvioDao {

    /** Devuelve el rowid, o -1 si ya habia una fila con ese id (no se toca). */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun anotar(fila: PlazoDeEnvioEntity): Long

    @Query("SELECT * FROM plazo_de_envio WHERE id = :id")
    suspend fun obtener(id: String): PlazoDeEnvioEntity?
}
