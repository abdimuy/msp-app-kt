package com.example.msp_app.core.database.dao.garantiav2

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.example.msp_app.core.database.entities.garantiav2.GarantiaComandoPendienteEntity

/**
 * La cola de comandos de garantías (`garantia_comandos_pendientes`).
 *
 * ## Lo que estas consultas NO hacen
 *
 * No hay ningún `UPDATE` que toque `CLAVE`, `CUERPO`, `CONTENT_TYPE`,
 * `DEVICE_CREATED_AT` ni `RUTA`. Es la garantía de D3: un comando encolado no
 * se edita. Las únicas escrituras son de estado y de resolución de ids
 * (`FOLIO_ID`, `ARTICULO_ID`, `EVENTO_ID`), que el servidor devuelve después.
 *
 * ## Por qué el orden es por [GarantiaComandoPendienteEntity.ID]
 *
 * Es el autoincremental local y refleja el orden en que el cobrador guardó.
 * [pendientesDelFolio] lo usa para que un comando no adelante a su anterior
 * en el mismo folio.
 */
@Dao
interface GarantiaComandoDao {

    /** Devuelve el id local con el que después se resuelven sus dependientes. */
    @Insert
    suspend fun insertar(comando: GarantiaComandoPendienteEntity): Long

    /** El comando y sus fotos se guardan juntos en una transacción (D3). */
    @Insert
    suspend fun insertarTodos(comandos: List<GarantiaComandoPendienteEntity>): List<Long>

    /** Todo lo que todavía puede viajar, en orden global de encolado. */
    @Query("SELECT * FROM garantia_comandos_pendientes WHERE ESTADO = 'pendiente' ORDER BY ID ASC")
    suspend fun pendientes(): List<GarantiaComandoPendienteEntity>

    /** Los comandos de un folio, en el orden en que se guardaron. */
    @Query(
        "SELECT * FROM garantia_comandos_pendientes " +
            "WHERE FOLIO_REF = :folioRef ORDER BY ID ASC"
    )
    suspend fun delFolio(folioRef: String): List<GarantiaComandoPendienteEntity>

    @Query("SELECT * FROM garantia_comandos_pendientes WHERE CLAVE = :clave")
    suspend fun porClave(clave: String): GarantiaComandoPendienteEntity?

    @Query("SELECT COUNT(*) FROM garantia_comandos_pendientes WHERE ESTADO = 'pendiente'")
    suspend fun pendientesCount(): Int

    @Query(
        "UPDATE garantia_comandos_pendientes " +
            "SET ESTADO = 'enviado', ULTIMO_ERROR = NULL, ACTUALIZADO_EN = :ahora " +
            "WHERE ID = :id"
    )
    suspend fun marcarEnviado(id: Long, ahora: String)

    @Query(
        "UPDATE garantia_comandos_pendientes " +
            "SET ESTADO = 'rechazado', ULTIMO_ERROR = :error, ACTUALIZADO_EN = :ahora " +
            "WHERE ID = :id"
    )
    suspend fun marcarRechazado(id: Long, error: String, ahora: String)

    @Query(
        "UPDATE garantia_comandos_pendientes " +
            "SET INTENTOS = INTENTOS + 1, ULTIMO_ERROR = :error, ACTUALIZADO_EN = :ahora " +
            "WHERE ID = :id"
    )
    suspend fun anotarIntento(id: Long, error: String, ahora: String)

    /**
     * Propaga el `id` del servidor a todos los comandos que esperaban el folio
     * local [folioRef]. Sólo escribe donde todavía está en `NULL`, para que un
     * reintento no pueda cambiar un id ya resuelto.
     */
    @Query(
        "UPDATE garantia_comandos_pendientes " +
            "SET FOLIO_ID = :folioId, ACTUALIZADO_EN = :ahora " +
            "WHERE FOLIO_REF = :folioRef AND FOLIO_ID IS NULL"
    )
    suspend fun resolverFolio(folioRef: String, folioId: String, ahora: String): Int

    /**
     * El `articulo_ref` del servidor se busca por `clave_idempotencia` en el
     * detalle (D4), nunca por orden ni por fecha. Se propaga a todo comando que
     * use esa misma referencia local.
     */
    @Query(
        "UPDATE garantia_comandos_pendientes " +
            "SET ARTICULO_ID = :articuloId, ACTUALIZADO_EN = :ahora " +
            "WHERE ARTICULO_REF = :articuloRef AND ARTICULO_ID IS NULL"
    )
    suspend fun resolverArticulo(articuloRef: String, articuloId: String, ahora: String): Int

    /** Igual que [resolverArticulo], para el evento que creó una recolección. */
    @Query(
        "UPDATE garantia_comandos_pendientes " +
            "SET EVENTO_ID = :eventoId, ACTUALIZADO_EN = :ahora " +
            "WHERE EVENTO_REF = :eventoRef AND EVENTO_ID IS NULL"
    )
    suspend fun resolverEvento(eventoRef: String, eventoId: String, ahora: String): Int
}
