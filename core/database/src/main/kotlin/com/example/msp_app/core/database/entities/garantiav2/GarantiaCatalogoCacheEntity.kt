package com.example.msp_app.core.database.entities.garantiav2

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * La copia local del catálogo de acciones de garantías
 * (`GET /v2/garantias/catalogos`), para decidir qué botón mostrar sin señal.
 *
 * Una sola fila ([ID] siempre `1`): el catálogo es un documento, no una lista
 * de entidades independientes. Se guarda el cuerpo **tal como llegó** —sin
 * reinterpretarlo— porque el teléfono no calcula etapas ni transiciones: sólo
 * busca la acción que el servidor declaró válida para el estado actual.
 */
@Entity(tableName = "garantia_catalogos_cache")
data class GarantiaCatalogoCacheEntity(
    @PrimaryKey val ID: Int = FILA_UNICA,
    /** El JSON del catálogo, byte por byte como lo devolvió el servidor. */
    @ColumnInfo(name = "CUERPO") val CUERPO: String,
    /** Instante en que se guardó, formato de cable de `AppTime`. */
    @ColumnInfo(name = "ACTUALIZADO_EN") val ACTUALIZADO_EN: String
) {
    companion object {
        /** La única fila posible: el catálogo es un documento único. */
        const val FILA_UNICA: Int = 1
    }
}
