package com.example.msp_app.data.local.dao.localsale

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.msp_app.data.local.entities.LocalSaleEntity
import com.example.msp_app.data.local.entities.LocalSaleImageEntity

@Dao
interface LocalSaleDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSale(localsale: LocalSaleEntity)

    @Query(
        """
        SELECT
            LOCAL_SALE_ID,
            NOMBRE_CLIENTE,
            FECHA_VENTA,
            LATITUD,
            LONGITUD,
            DIRECCION,
            PARCIALIDAD,
            ENGANCHE,
            TELEFONO,
            FREC_PAGO,
            AVAL_O_RESPONSABLE,
            NOTA,
            DIA_COBRANZA,
            PRECIO_TOTAL,
            TIEMPO_A_CORTO_PLAZOMESES,
            MONTO_A_CORTO_PLAZO,
            MONTO_DE_CONTADO,
            ENVIADO,
            NUMERO,
            COLONIA,
            POBLACION,
            CIUDAD,
            TIPO_VENTA,
            ESTADO
            FROM local_sale
            WHERE FECHA_VENTA >= datetime('now', '-7 days')
            ORDER BY FECHA_VENTA DESC
        """
    )
    suspend fun getAllSales(): List<LocalSaleEntity>

    @Query(
        """
            SELECT
            LOCAL_SALE_ID,
            NOMBRE_CLIENTE,
            FECHA_VENTA,
            LATITUD,
            LONGITUD,
            DIRECCION,
            PARCIALIDAD,
            ENGANCHE,
            TELEFONO,
            FREC_PAGO,
            AVAL_O_RESPONSABLE,
            NOTA,
            DIA_COBRANZA,
            PRECIO_TOTAL,
            TIEMPO_A_CORTO_PLAZOMESES,
            MONTO_A_CORTO_PLAZO,
            MONTO_DE_CONTADO,
            ENVIADO,
            NUMERO,
            COLONIA,
            POBLACION,
            CIUDAD,
            TIPO_VENTA,
            ESTADO
            FROM local_sale
            WHERE LOCAL_SALE_ID = :sale_Id
        """
    )
    suspend fun getSaleById(sale_Id: String): LocalSaleEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSaleImage(saleImage: LocalSaleImageEntity)

    @Query("SELECT LOCAL_SALE_IMAGE_ID, LOCAL_SALE_ID, IMAGE_URI, FECHA_SUBIDA FROM sale_image WHERE LOCAL_SALE_ID = :saleId ORDER BY FECHA_SUBIDA")
    suspend fun getImagesForSale(saleId: String): List<LocalSaleImageEntity>

    @Query("DELETE FROM sale_image WHERE LOCAL_SALE_ID = :saleId")
    suspend fun deleteImagesForSale(saleId: String)

    @Query("UPDATE local_sale SET ENVIADO = :enviado WHERE LOCAL_SALE_ID = :saleId")
    suspend fun updateSaleStatus(saleId: String, enviado: Boolean)

    @Query(
        """
        SELECT
            LOCAL_SALE_ID,
            NOMBRE_CLIENTE,
            FECHA_VENTA,
            LATITUD,
            LONGITUD,
            DIRECCION,
            PARCIALIDAD,
            ENGANCHE,
            TELEFONO,
            FREC_PAGO,
            AVAL_O_RESPONSABLE,
            NOTA,
            DIA_COBRANZA,
            PRECIO_TOTAL,
            TIEMPO_A_CORTO_PLAZOMESES,
            MONTO_A_CORTO_PLAZO,
            MONTO_DE_CONTADO,
            ENVIADO,
            NUMERO,
            COLONIA,
            POBLACION,
            CIUDAD,
            TIPO_VENTA,
            ESTADO
            FROM local_sale
            WHERE ENVIADO = :enviado
            ORDER BY FECHA_VENTA DESC
        """
    )
    suspend fun getSalesByStatus(enviado: Boolean): List<LocalSaleEntity>

    @Query("UPDATE local_sale SET ESTADO = :estado WHERE LOCAL_SALE_ID = :saleId")
    suspend fun updateSaleState(saleId: String, estado: String)

    @Query(
        """
        UPDATE local_sale SET 
        NOMBRE_CLIENTE = :clientName,
        TELEFONO = :phone,
        DIRECCION = :address,
        NUMERO = :numero,
        COLONIA = :colonia,
        POBLACION = :poblacion,
        CIUDAD = :ciudad,
        PARCIALIDAD = :installment,
        ENGANCHE = :downpayment,
        FREC_PAGO = :paymentfrequency,
        AVAL_O_RESPONSABLE = :avaloresponsable,
        NOTA = :note,
        DIA_COBRANZA = :collectionday,
        PRECIO_TOTAL = :totalprice,
        MONTO_A_CORTO_PLAZO = :shorttermamount,
        MONTO_DE_CONTADO = :cashamount
        WHERE LOCAL_SALE_ID = :saleId
    """
    )
    suspend fun updateSale(
        saleId: String,
        clientName: String,
        phone: String,
        address: String,
        numero: String?,
        colonia: String?,
        poblacion: String?,
        ciudad: String?,
        installment: Double,
        downpayment: Double,
        paymentfrequency: String,
        avaloresponsable: String?,
        note: String?,
        collectionday: String,
        totalprice: Double,
        shorttermamount: Double,
        cashamount: Double
    )

    @Query("SELECT * FROM local_sale WHERE ESTADO = :estado ORDER BY FECHA_VENTA DESC")
    suspend fun getSalesByState(estado: String): List<LocalSaleEntity>

    @Query(
        """
        SELECT * FROM local_sale 
        WHERE ENVIADO = 0 AND ESTADO = 'ENVIADA'
        ORDER BY FECHA_VENTA DESC
    """
    )
    suspend fun getCompletedSalesReadyToSend(): List<LocalSaleEntity>
}