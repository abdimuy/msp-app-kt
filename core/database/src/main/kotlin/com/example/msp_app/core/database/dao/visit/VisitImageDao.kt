package com.example.msp_app.core.database.dao.visit

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.msp_app.core.database.entities.VisitImageEntity

/**
 * Los comprobantes de una visita (`visita_imagenes`, Task 26 / consumida por la
 * Task 23).
 *
 * ## Las dos columnas que NO son intercambiables
 *
 * [VisitImageEntity.ID] es el UUID de **la imagen** —el que viaja como `id_<n>`
 * en el multipart de `POST /v2/visitas`— y [VisitImageEntity.VISITA_ID] es el de
 * **la visita**. Cada consulta dice en su nombre por cuál filtra, justamente
 * porque este plan ya lleva siete defectos de la familia "un id donde iba el
 * otro".
 *
 * El `id_<n>` aquí es **obligatorio** del lado del servidor
 * (`parsePositionalImagenID`: sin él, 422 `imagen_id_requerido` para la visita
 * entera), a diferencia de cobranza, que acuña uno cuando falta. O sea que este
 * UUID no es solo la clave de idempotencia: es un campo requerido del contrato.
 *
 * ## Por qué no hay un `marcarSubidas(ids)` con `IN`
 *
 * `CLAUDE.md:52-56` documenta el caso que costó horas: un `IN (...)` sin trocear
 * lanza *"too many SQL variables"* y un `try/catch` de arriba lo vuelve un error
 * silencioso. El marcado va **de una en una**: son a lo más un puñado de filas
 * por visita (el tope lo pone la pantalla), el costo es despreciable, y así no
 * existe el borde que trocear.
 */
@Dao
interface VisitImageDao {

    /**
     * `REPLACE` y no `IGNORE`: la PK es el UUID que acuñó el teléfono, así que
     * volver a escribir la MISMA captura reescribe su propia fila en vez de
     * duplicarla. Es la misma idempotencia que el servidor aplica del otro lado
     * con la clave de storage `visitas/<visita_id>/<imagen_id><ext>`.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(imagenes: List<VisitImageEntity>)

    /** Todos los comprobantes de la visita [visitaId], **en el orden de captura**. */
    @Query("SELECT * FROM visita_imagenes WHERE VISITA_ID = :visitaId ORDER BY ORDEN ASC, ID ASC")
    suspend fun getByVisitaId(visitaId: String): List<VisitImageEntity>

    /**
     * Los que todavía no subieron, de la visita [visitaId]. `SUBIDA_EN IS NULL`
     * es la definición de "pendiente" y tiene índice: barrerlo es barato.
     *
     * El orden es el de captura porque es el que decide la posición `n` de
     * `id_<n>` en el multipart.
     */
    @Query(
        "SELECT * FROM visita_imagenes WHERE VISITA_ID = :visitaId AND SUBIDA_EN IS NULL " +
            "ORDER BY ORDEN ASC, ID ASC"
    )
    suspend fun getPendientesDe(visitaId: String): List<VisitImageEntity>

    /**
     * Marca UNA imagen como subida. Filtra por `ID` —el de la imagen— y no por
     * `VISITA_ID`: marcar por visita estamparía también las que el servidor no
     * recibió (un archivo que ya no estaba en disco, un tipo no permitido), y
     * esas tienen que quedarse pendientes para que se vean.
     */
    @Query("UPDATE visita_imagenes SET SUBIDA_EN = :subidaEn WHERE ID = :imagenId")
    suspend fun marcarSubida(imagenId: String, subidaEn: String)

    /** Las rutas de archivo que alguna fila todavía referencia. Tabla chica. */
    @Query("SELECT URI FROM visita_imagenes")
    suspend fun rutasVivas(): List<String>

    /**
     * Las filas **huérfanas y viejas**: su `VISITA_ID` ya no existe en `Visit` y
     * se crearon antes de [limite] (formato de cable de `AppTime`, comparable
     * como texto porque `ISO_INSTANT` está siempre en UTC y con ancho fijo).
     *
     * Las dos condiciones son necesarias. `VisitImageEntity` no tiene llave
     * foránea a propósito —una cascada sobre el `INSERT OR REPLACE` de
     * `VisitDao.insertVisit` borraría fotos en silencio—, así que una visita
     * reinsertada deja a sus comprobantes un instante sin padre visible.
     * Barrer en ese momento borraría comprobantes de visitas vivas; barrer por
     * antigüedad no puede.
     */
    @Query(
        "SELECT * FROM visita_imagenes WHERE CREADA_EN < :limite " +
            "AND VISITA_ID NOT IN (SELECT ID FROM Visit)"
    )
    suspend fun huerfanasAnterioresA(limite: String): List<VisitImageEntity>

    /** Borra UNA fila por el id de **la imagen**. */
    @Query("DELETE FROM visita_imagenes WHERE ID = :imagenId")
    suspend fun eliminar(imagenId: String)
}
