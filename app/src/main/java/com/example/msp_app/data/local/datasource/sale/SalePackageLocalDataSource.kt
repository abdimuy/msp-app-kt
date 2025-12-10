package com.example.msp_app.data.local.datasource.sale

import android.content.Context
import com.example.msp_app.data.local.AppDatabase
import com.example.msp_app.data.local.entities.LocalSalePackageEntity

class SalePackageLocalDataSource(context: Context) {
    private val packageDao = AppDatabase.getInstance(context).localSalePackage()

    suspend fun insertPackage(packageEntity: LocalSalePackageEntity) {
        packageDao.insertPackage(packageEntity)
    }

    suspend fun insertPackages(packages: List<LocalSalePackageEntity>) {
        packageDao.insertAllPackages(packages)
    }

    suspend fun getPackagesForSale(saleId: String): List<LocalSalePackageEntity> {
        return try {
            packageDao.getPackagesForSale(saleId)
        } catch (e: Exception) {
            emptyList()
        }
    }

    suspend fun deletePackagesForSale(saleId: String) {
        packageDao.deletePackagesForSale(saleId)
    }

    suspend fun deletePackage(saleId: String, packageId: String) {
        packageDao.deletePackage(saleId, packageId)
    }
}