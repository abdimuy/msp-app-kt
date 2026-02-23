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
            ZONA_CLIENTE,
            CLIENTE_ID
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
            ZONA_CLIENTE,
            CLIENTE_ID
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
            ZONA_CLIENTE,
            CLIENTE_ID
            FROM local_sale
            WHERE ENVIADO = :enviado
            ORDER BY FECHA_VENTA DESC
        """
    )
    suspend fun getSalesByStatus(enviado: Boolean): List<LocalSaleEntity>

    @Query("DELETE FROM sale_image WHERE LOCAL_SALE_IMAGE_ID = :imageId")
    suspend fun deleteImageById(imageId: String)

    @Query("DELETE FROM sale_image WHERE LOCAL_SALE_IMAGE_ID IN (:imageIds)")
    suspend fun deleteImagesByIds(imageIds: List<String>)

    @Query("""
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
    """)
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