package com.example.msp_app.core.database.dao.garantiav2

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.msp_app.core.database.entities.garantiav2.GarantiaCatalogoCacheEntity

/** La copia local del catálogo de acciones (`garantia_catalogos_cache`). */
@Dao
interface GarantiaCatalogoDao {

    /** Una sola fila; reemplazarla es la actualización normal del catálogo. */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun guardar(catalogo: GarantiaCatalogoCacheEntity)

    @Query("SELECT * FROM garantia_catalogos_cache WHERE ID = 1")
    suspend fun leer(): GarantiaCatalogoCacheEntity?
}
