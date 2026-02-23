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

    suspend fun updateSale(sale: LocalSaleEntity) {
        // Usamos UPDATE real en lugar de REPLACE para evitar que el CASCADE
        // de la foreign key elimine las imágenes relacionadas
        localSaleDao.updateSaleFields(
            localSaleId = sale.LOCAL_SALE_ID,
            nombreCliente = sale.NOMBRE_CLIENTE,
            fechaVenta = sale.FECHA_VENTA,
            latitud = sale.LATITUD,
            longitud = sale.LONGITUD,
            direccion = sale.DIRECCION,
            parcialidad = sale.PARCIALIDAD,
            enganche = sale.ENGANCHE,
            telefono = sale.TELEFONO,
            frecPago = sale.FREC_PAGO,
            avalOResponsable = sale.AVAL_O_RESPONSABLE,
            nota = sale.NOTA,
            diaCobranza = sale.DIA_COBRANZA,
            precioTotal = sale.PRECIO_TOTAL,
            tiempoACortoPlazoMeses = sale.TIEMPO_A_CORTO_PLAZOMESES,
            montoACortoPlazo = sale.MONTO_A_CORTO_PLAZO,
            montoDeContado = sale.MONTO_DE_CONTADO,
            enviado = sale.ENVIADO,
            numero = sale.NUMERO,
            colonia = sale.COLONIA,
            poblacion = sale.POBLACION,
            ciudad = sale.CIUDAD,
            tipoVenta = sale.TIPO_VENTA,
            zonaClienteId = sale.ZONA_CLIENTE_ID,
            zonaCliente = sale.ZONA_CLIENTE,
            clienteId = sale.CLIENTE_ID
        )
    }

    suspend fun deleteImageById(imageId: String) {
        localSaleDao.deleteImageById(imageId)
    }

    suspend fun deleteImagesByIds(imageIds: List<String>) {
        if (imageIds.isNotEmpty()) {
            localSaleDao.deleteImagesByIds(imageIds)
        }
    }
}