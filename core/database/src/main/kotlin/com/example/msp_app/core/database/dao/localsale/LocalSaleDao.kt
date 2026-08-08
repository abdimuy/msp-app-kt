package com.example.msp_app.core.database.dao.localsale

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.msp_app.core.database.entities.LocalSaleEntity
import com.example.msp_app.core.database.entities.LocalSaleImageEntity

@Dao
interface LocalSaleDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSale(localsale: LocalSaleEntity)

    @Query(
        """
        SELECT * FROM local_sale
        WHERE FECHA_VENTA >= datetime('now', '-7 days')
        ORDER BY FECHA_VENTA DESC
        """
    )
    suspend fun getAllSales(): List<LocalSaleEntity>

    @Query("SELECT * FROM local_sale WHERE LOCAL_SALE_ID = :saleId")
    suspend fun getSaleById(saleId: String): LocalSaleEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSaleImage(saleImage: LocalSaleImageEntity)

    @Query(
        "SELECT LOCAL_SALE_IMAGE_ID, LOCAL_SALE_ID, IMAGE_URI, FECHA_SUBIDA, SERVER_UUID FROM sale_image WHERE LOCAL_SALE_ID = :saleId ORDER BY FECHA_SUBIDA"
    )
    suspend fun getImagesForSale(saleId: String): List<LocalSaleImageEntity>

    @Query("DELETE FROM sale_image WHERE LOCAL_SALE_ID = :saleId")
    suspend fun deleteImagesForSale(saleId: String)

    @Query("UPDATE local_sale SET ENVIADO = :enviado WHERE LOCAL_SALE_ID = :saleId")
    suspend fun updateSaleStatus(saleId: String, enviado: Boolean)

    /**
     * Persists the upload failure on the local sale. Called from the worker's
     * error-handling path. The first informative error wins over a later
     * less-informative one (see UploadFailureRepository for the precedence
     * rule); this query is the bare write — precedence logic is upstream.
     */
    @Query(
        """
        UPDATE local_sale SET
            LAST_UPLOAD_HTTP_CODE = :httpCode,
            LAST_UPLOAD_ERROR_CODE = :errorCode,
            LAST_UPLOAD_ERROR_MESSAGE = :errorMessage,
            LAST_UPLOAD_AT = :at,
            LAST_UPLOAD_PERMANENT = :permanent
        WHERE LOCAL_SALE_ID = :saleId
        """
    )
    suspend fun updateUploadFailure(
        saleId: String,
        httpCode: Int,
        errorCode: String?,
        errorMessage: String?,
        at: Long,
        permanent: Boolean
    )

    /**
     * Clears upload-failure tracking — called when the worker succeeds, or
     * when the user edits a failed sale (so the UI doesn't show a stale
     * error after a corrected resubmit).
     */
    @Query(
        """
        UPDATE local_sale SET
            LAST_UPLOAD_HTTP_CODE = NULL,
            LAST_UPLOAD_ERROR_CODE = NULL,
            LAST_UPLOAD_ERROR_MESSAGE = NULL,
            LAST_UPLOAD_AT = NULL,
            LAST_UPLOAD_PERMANENT = NULL
        WHERE LOCAL_SALE_ID = :saleId
        """
    )
    suspend fun clearUploadFailure(saleId: String)

    /**
     * Sets a fresh Idempotency-Key on the sale. Used by edit-and-retry so the
     * corrected body doesn't collide with a cached 2xx (which would otherwise
     * return 422 idempotency_key_mismatch from the server middleware).
     */
    @Query("UPDATE local_sale SET IDEMPOTENCY_KEY = :key WHERE LOCAL_SALE_ID = :saleId")
    suspend fun updateIdempotencyKey(saleId: String, key: String)

    @Query(
        """
        SELECT * FROM local_sale
        WHERE ENVIADO = :enviado
        ORDER BY FECHA_VENTA DESC
        """
    )
    suspend fun getSalesByStatus(enviado: Boolean): List<LocalSaleEntity>

    @Query("DELETE FROM sale_image WHERE LOCAL_SALE_IMAGE_ID = :imageId")
    suspend fun deleteImageById(imageId: String)

    @Query("DELETE FROM sale_image WHERE LOCAL_SALE_IMAGE_ID IN (:imageIds)")
    suspend fun deleteImagesByIds(imageIds: List<String>)

    @Query("UPDATE sale_image SET SERVER_UUID = :serverUuid WHERE LOCAL_SALE_IMAGE_ID = :imageId")
    suspend fun updateImageServerUuid(imageId: String, serverUuid: String)

    // Un parámetro por columna editable de `local_sale` — Room enlaza cada
    // `:nombreParametro` de la query por NOMBRE de parámetro Kotlin, así que
    // envolver esto en un objeto (para bajar el conteo de LongParameterList)
    // exigiría el binding "entity parcial" de Room (`@Update(entity = ...)`
    // con una data class de columnas), que es un cambio de forma de
    // persistencia — no un simple refactor de estilo — para una query de
    // edición de venta ya en uso. Se prefiere suprimir con esta nota a
    // arriesgar el comportamiento de escritura.
    @Suppress("LongParameterList")
    @Query(
        """
        UPDATE local_sale SET
            NOMBRE_CLIENTE = :nombreCliente,
            FECHA_VENTA = :fechaVenta,
            LATITUD = :latitud,
            LONGITUD = :longitud,
            DIRECCION = :direccion,
            PARCIALIDAD = :parcialidad,
            ENGANCHE = :enganche,
            TELEFONO = :telefono,
            FREC_PAGO = :frecPago,
            AVAL_O_RESPONSABLE = :avalOResponsable,
            NOTA = :nota,
            DIA_COBRANZA = :diaCobranza,
            PRECIO_TOTAL = :precioTotal,
            TIEMPO_A_CORTO_PLAZOMESES = :tiempoACortoPlazoMeses,
            MONTO_A_CORTO_PLAZO = :montoACortoPlazo,
            MONTO_DE_CONTADO = :montoDeContado,
            ENVIADO = :enviado,
            NUMERO = :numero,
            COLONIA = :colonia,
            POBLACION = :poblacion,
            CIUDAD = :ciudad,
            TIPO_VENTA = :tipoVenta,
            ZONA_CLIENTE_ID = :zonaClienteId,
            ZONA_CLIENTE = :zonaCliente,
            CLIENTE_ID = :clienteId
        WHERE LOCAL_SALE_ID = :localSaleId
    """
    )
    suspend fun updateSaleFields(
        localSaleId: String,
        nombreCliente: String,
        fechaVenta: String,
        latitud: Double,
        longitud: Double,
        direccion: String,
        parcialidad: Double,
        enganche: Double?,
        telefono: String,
        frecPago: String,
        avalOResponsable: String?,
        nota: String?,
        diaCobranza: String,
        precioTotal: Double,
        tiempoACortoPlazoMeses: Int,
        montoACortoPlazo: Double,
        montoDeContado: Double,
        enviado: Boolean,
        numero: String?,
        colonia: String?,
        poblacion: String?,
        ciudad: String?,
        tipoVenta: String?,
        zonaClienteId: Int?,
        zonaCliente: String?,
        clienteId: Int?
    )
}
