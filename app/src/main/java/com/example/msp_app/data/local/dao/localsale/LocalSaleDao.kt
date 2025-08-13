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

    @Query("SELECT LOCAL_SALE_ID, NOMBRE_CLIENTE, FECHA_VENTA, LATITUD, LONGITUD, DIRECCION FROM local_sale ORDER BY FECHA_VENTA DESC")
    suspend fun getAllSales(): List<LocalSaleEntity>

    @Query("SELECT LOCAL_SALE_ID, NOMBRE_CLIENTE, FECHA_VENTA, LATITUD, LONGITUD, DIRECCION FROM local_sale WHERE LOCAL_SALE_ID = :sale_Id")
    suspend fun getSaleById(sale_Id: String): LocalSaleEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSaleImage(saleImage: LocalSaleImageEntity)

    @Query("SELECT LOCAL_SALE_IMAGE_ID, LOCAL_SALE_ID, IMAGE_URI, FECHA_SUBIDA FROM sale_image WHERE LOCAL_SALE_ID = :saleId ORDER BY FECHA_SUBIDA")
    suspend fun getImagesForSale(saleId: String): List<LocalSaleImageEntity>

    @Query("DELETE FROM sale_image WHERE LOCAL_SALE_ID = :saleId")
    suspend fun deleteImagesForSale(saleId: String)
}