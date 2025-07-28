package com.example.msp_app.data.local.dao.guarantee

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.msp_app.data.local.entities.GuaranteeEntity
import com.example.msp_app.data.local.entities.GuaranteeEventEntity
import com.example.msp_app.data.local.entities.GuaranteeImageEntity

@Dao
interface GuaranteeDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertGuarantees(guarantee: GuaranteeEntity)

    @Query(
        """
        SELECT
            EXTERNAL_ID,
            DOCTO_CC_ID,
            ESTADO,
            DESCRIPCION,
            OBSERVACIONES,
            UPLOADED,
            FECHA_SOLICITUD,
            ID
        FROM garantias
        WHERE ID = :id
    """
    )
    suspend fun getGuaranteesById(id: Int): GuaranteeEntity?

    @Query(
        """
        SELECT
            EXTERNAL_ID,
            DOCTO_CC_ID,
            ESTADO,
            DESCRIPCION,
            OBSERVACIONES,
            UPLOADED,
            FECHA_SOLICITUD,
            ID
        FROM garantias
    """
    )
    suspend fun getAllGuarantees(): List<GuaranteeEntity>

    @Query(
        """
        UPDATE garantias
        SET UPLOADED = :uploaded
        WHERE ID = :id
    """
    )
    suspend fun updateUploadedStatus(id: Int, uploaded: Int)

    @Query("DELETE FROM garantias")
    suspend fun deleteAllGuarantees()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertGuaranteesImagen(image: GuaranteeImageEntity)

    @Query(
        """
        SELECT
            ID,
            GARANTIA_ID,
            IMG_PATH,
            IMG_MIME,
            IMG_DESC,
            FECHA_SUBIDA
        FROM garantia_imagenes
        WHERE GARANTIA_ID = :guaranteeId
    """
    )
    suspend fun getImagenesByGuaranteesId(guaranteeId: Int): List<GuaranteeImageEntity>

    @Query("DELETE FROM garantia_imagenes")
    suspend fun deleteAllGuaranteesImages()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEvento(event: GuaranteeEventEntity)

    @Query(
        """
        SELECT
            ID,
            GARANTIA_ID,
            TIPO_EVENTO,
            FECHA_EVENTO,
            COMENTARIO,
            ENVIADO
        FROM garantia_eventos
        WHERE GARANTIA_ID = :guaranteeId
    """
    )
    suspend fun getEventosByGuaranteesId(guaranteeId: String): List<GuaranteeEventEntity>

    @Query(
        """
        UPDATE garantia_eventos
        SET ENVIADO = :sent
        WHERE GARANTIA_ID = :id
    """
    )
    suspend fun updateEventoEnviado(id: String, sent: Int)

    @Query("DELETE FROM garantia_eventos")
    suspend fun deleteAllGuaranteesEvents()
}