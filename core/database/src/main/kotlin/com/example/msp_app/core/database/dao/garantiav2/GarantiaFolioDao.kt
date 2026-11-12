package com.example.msp_app.core.database.dao.garantiav2

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.msp_app.core.database.entities.garantiav2.GarantiaFolioSeguidoEntity

/**
 * Los folios que este teléfono abrió o tocó (`garantia_folios_seguidos`).
 *
 * [marcarAbierto] es la bisagra de D4: mientras `FOLIO_ID` esté en `NULL`, los
 * comandos del folio no pueden salir; cuando `abrir_folio` responde, esta fila
 * guarda el `id` del servidor y [GarantiaComandoDao.resolverFolio] se lo pasa a
 * todos los comandos que lo esperaban.
 */
@Dao
interface GarantiaFolioDao {

    /** `REPLACE`: la referencia local es estable, así que reescribir no duplica. */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun guardar(folio: GarantiaFolioSeguidoEntity)

    /** La bandeja de "mis folios", del más reciente al más viejo. */
    @Query("SELECT * FROM garantia_folios_seguidos ORDER BY ABIERTO_EN DESC")
    suspend fun todos(): List<GarantiaFolioSeguidoEntity>

    @Query("SELECT * FROM garantia_folios_seguidos WHERE ID = :id")
    suspend fun porId(id: String): GarantiaFolioSeguidoEntity?

    @Query("SELECT * FROM garantia_folios_seguidos WHERE FOLIO_ID = :folioId")
    suspend fun porFolioId(folioId: String): GarantiaFolioSeguidoEntity?

    /**
     * El folio ya existe en el servidor. No toca [GarantiaFolioSeguidoEntity.ORIGEN]
     * ni los datos del cliente: sólo resuelve el id y el estado local.
     */
    @Query(
        "UPDATE garantia_folios_seguidos " +
            "SET FOLIO_ID = :folioId, ESTADO_LOCAL = 'abierto', " +
            "ULTIMO_ERROR = NULL, ACTUALIZADO_EN = :ahora " +
            "WHERE ID = :id"
    )
    suspend fun marcarAbierto(id: String, folioId: String, ahora: String)

    /** `pendiente_envio`, `abierto` o `rechazado`, con su último error. */
    @Query(
        "UPDATE garantia_folios_seguidos " +
            "SET ESTADO_LOCAL = :estado, ULTIMO_ERROR = :error, ACTUALIZADO_EN = :ahora " +
            "WHERE ID = :id"
    )
    suspend fun marcarEstado(id: String, estado: String, error: String?, ahora: String)

    @Query("SELECT COUNT(*) FROM garantia_folios_seguidos WHERE ESTADO_LOCAL = 'pendiente_envio'")
    suspend fun pendientesDeEnvio(): Int
}
