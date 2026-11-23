package com.example.msp_app.core.database.dao.garantiav2

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.msp_app.core.database.entities.garantiav2.GarantiaFotoPendienteEntity

/**
 * Las fotos en espera de subir (`garantia_fotos_pendientes`).
 *
 * La fila se borra **sólo** con [eliminar], y el código que la llama lo hace
 * únicamente después de [marcarSubida]: una subida que no respondió 2xx deja
 * la foto en disco y en la cola. Ése es el mutante A7 del brief.
 */
@Dao
interface GarantiaFotoDao {

    /** La clave es el UUID de la foto: reescribir la misma no duplica. */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun guardar(foto: GarantiaFotoPendienteEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun guardarTodas(fotos: List<GarantiaFotoPendienteEntity>)

    /** Todas las pendientes, en orden de captura. */
    @Query(
        "SELECT * FROM garantia_fotos_pendientes " +
            "WHERE ESTADO = 'pendiente' ORDER BY CREADO_EN ASC, CLAVE ASC"
    )
    suspend fun pendientes(): List<GarantiaFotoPendienteEntity>

    /**
     * Las que ya tienen el `id` del evento y por tanto pueden subir. Sin
     * [GarantiaFotoPendienteEntity.EVENTO_ID] la ruta no existe todavía.
     */
    @Query(
        "SELECT * FROM garantia_fotos_pendientes " +
            "WHERE ESTADO = 'pendiente' AND EVENTO_ID IS NOT NULL " +
            "ORDER BY CREADO_EN ASC, CLAVE ASC"
    )
    suspend fun listasParaSubir(): List<GarantiaFotoPendienteEntity>

    @Query("SELECT * FROM garantia_fotos_pendientes WHERE CLAVE = :clave")
    suspend fun porClave(clave: String): GarantiaFotoPendienteEntity?

    @Query("SELECT COUNT(*) FROM garantia_fotos_pendientes WHERE ESTADO = 'pendiente'")
    suspend fun pendientesCount(): Int

    /** El servidor confirmó la imagen: recién ahora puede borrarse el archivo. */
    @Query(
        "UPDATE garantia_fotos_pendientes " +
            "SET ESTADO = 'enviada', SUBIDA_EN = :ahora, ULTIMO_ERROR = NULL, " +
            "ACTUALIZADO_EN = :ahora WHERE CLAVE = :clave"
    )
    suspend fun marcarSubida(clave: String, ahora: String)

    @Query(
        "UPDATE garantia_fotos_pendientes " +
            "SET ESTADO = 'rechazada', ULTIMO_ERROR = :error, ACTUALIZADO_EN = :ahora " +
            "WHERE CLAVE = :clave"
    )
    suspend fun marcarRechazada(clave: String, error: String, ahora: String)

    @Query(
        "UPDATE garantia_fotos_pendientes " +
            "SET INTENTOS = INTENTOS + 1, ULTIMO_ERROR = :error, ACTUALIZADO_EN = :ahora " +
            "WHERE CLAVE = :clave"
    )
    suspend fun anotarIntento(clave: String, error: String, ahora: String)

    /**
     * El evento ya existe en el servidor: se propaga a todas las fotos que
     * esperaban esa referencia local, junto con el folio.
     */
    @Query(
        "UPDATE garantia_fotos_pendientes " +
            "SET EVENTO_ID = :eventoId, FOLIO_ID = :folioId, ACTUALIZADO_EN = :ahora " +
            "WHERE EVENTO_REF = :eventoRef AND EVENTO_ID IS NULL"
    )
    suspend fun resolverEvento(
        eventoRef: String,
        eventoId: String,
        folioId: String,
        ahora: String
    ): Int

    /** Las rutas que alguna fila todavía referencia; el barrido no las toca. */
    @Query("SELECT RUTA_ARCHIVO FROM garantia_fotos_pendientes")
    suspend fun rutasVivas(): List<String>

    /** Sólo después de [marcarSubida]: la fila y su archivo se van juntos. */
    @Query("DELETE FROM garantia_fotos_pendientes WHERE CLAVE = :clave")
    suspend fun eliminar(clave: String)
}
