package com.example.msp_app.data.local.dao.localsale

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.msp_app.data.local.entities.LocalSalePackageEntity

@Dao
interface LocalSalePackageDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPackage(packageEntity: LocalSalePackageEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAllPackages(packages: List<LocalSalePackageEntity>)

    @Query(
        """
        SELECT 
            LOCAL_SALE_ID,
            PACKAGE_ID,
            PACKAGE_NAME,
            PRECIO_LISTA,
            PRECIO_CORTO_PLAZO,
            PRECIO_CONTADO,
            PRODUCT_IDS_JSON
        FROM local_sale_packages 
        WHERE LOCAL_SALE_ID = :saleId
    """
    )
    suspend fun getPackagesForSale(saleId: String): List<LocalSalePackageEntity>

    @Query("DELETE FROM local_sale_packages WHERE LOCAL_SALE_ID = :saleId")
    suspend fun deletePackagesForSale(saleId: String)

    @Query("DELETE FROM local_sale_packages WHERE LOCAL_SALE_ID = :saleId AND PACKAGE_ID = :packageId")
    suspend fun deletePackage(saleId: String, packageId: String)
}