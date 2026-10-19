package com.example.msp_app.core.database.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Posición del canal de bajada de visitas por zona (spec visitas §5.4.8,
 * E-APP-080). Una fila por zona.
 *
 * ## El cursor es un PAR (`COBRANZA-SYNC.md` §2)
 *
 * El servidor pagina por `(UPDATED_AT, ID)`. [CURSOR] es el `UPDATED_AT` y
 * [AFTER_ID] el id (UUID) de la última fila de la última página aplicada.
 * Donde se escribe uno se escribe el otro, en la misma transacción que la
 * página (`VisitaZonaDao.aplicarPagina`), y todo camino que deje [CURSOR] en
 * nulo deja [AFTER_ID] en nulo (`reiniciarZona`, `reiniciarPorEpoch`).
 *
 * A diferencia de `cobranza_sync_state.AFTER_ID` (entero, 0 = "desde el inicio
 * del grupo"), aquí el id es texto y "sin posición" es `NULL`.
 *
 * [EPOCH] es la generación del servidor que este teléfono YA terminó de
 * replicar; `NULL` = nunca terminó una. Se persiste solo al terminar el replay
 * (`COBRANZA-SYNC.md` §3), por eso los reinicios no la tocan.
 */
@Entity(tableName = "visitas_sync_state")
data class VisitasSyncStateEntity(
    @PrimaryKey val ZONA: Int,
    val CURSOR: String?,
    val AFTER_ID: String?,
    val EPOCH: Int?
)
