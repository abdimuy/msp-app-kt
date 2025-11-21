package com.example.msp_app.data.local.datasource.sale

import android.content.Context
import com.example.msp_app.data.local.AppDatabase
import com.example.msp_app.data.local.entities.LocalSaleEntity
import com.example.msp_app.data.local.entities.LocalSaleImageEntity

class LocalSaleDataSource(context: Context) {
    private val localSaleDao = AppDatabase.getInstance(context).localSaleDao()

    suspend fun insertSale(sale: LocalSaleEntity) {
        localSaleDao.insertSale(sale)
    }

    suspend fun getAllSales(): List<LocalSaleEntity> {
        return localSaleDao.getAllSales()
    }

    suspend fun getSaleById(saleId: String): LocalSaleEntity? {
        return localSaleDao.getSaleById(saleId)
    }

    suspend fun insertSaleImage(saleImage: LocalSaleImageEntity) {
        localSaleDao.insertSaleImage(saleImage)
    }

    suspend fun getImagesForSale(saleId: String): List<LocalSaleImageEntity> {
        return localSaleDao.getImagesForSale(saleId)
    }

    suspend fun deleteImagesForSale(saleId: String) {
        localSaleDao.deleteImagesForSale(saleId)
    }

    suspend fun insertSaleWithImages(
        sale: LocalSaleEntity,
        images: List<LocalSaleImageEntity>
    ) {
        insertSale(sale)
        images.forEach { image ->
            insertSaleImage(image)
        }
    }

    suspend fun changeSaleStatus(saleId: String, enviado: Boolean) {
        localSaleDao.updateSaleStatus(saleId, enviado)
    }

    suspend fun getPendingSales(): List<LocalSaleEntity> {
        return localSaleDao.getSalesByStatus(false)
    }

    suspend fun updateSale(
        saleId: String,
        nombreCliente: String,
        telefono: String,
        direccion: String,
        numero: String?,
        colonia: String?,
        poblacion: String?,
        ciudad: String?,
        tipoVenta: String,
        enganche: Double?,
        parcialidad: Double?,
        frecPago: String?,
        diaCobranza: String?,
        avalOResponsable: String?,
        nota: String?
    ) {
        localSaleDao.updateSale(
            saleId = saleId,
            nombreCliente = nombreCliente,
            telefono = telefono,
            direccion = direccion,
            numero = numero,
            colonia = colonia,
            poblacion = poblacion,
            ciudad = ciudad,
            tipoVenta = tipoVenta,
            enganche = enganche,
            parcialidad = parcialidad,
            frecPago = frecPago,
            diaCobranza = diaCobranza,
            avalOResponsable = avalOResponsable,
            nota = nota
        )
    }
}