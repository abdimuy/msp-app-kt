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
            ZONA_CLIENTE_ID,
            ZONA_CLIENTE
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
            ZONA_CLIENTE_ID,
            ZONA_CLIENTE
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
            ZONA_CLIENTE_ID,
            ZONA_CLIENTE
            FROM local_sale
            WHERE ENVIADO = :enviado
            ORDER BY FECHA_VENTA DESC
        """
    )
    suspend fun getSalesByStatus(enviado: Boolean): List<LocalSaleEntity>
}