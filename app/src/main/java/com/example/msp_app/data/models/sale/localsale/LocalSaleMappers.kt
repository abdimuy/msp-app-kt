package com.example.msp_app.data.models.sale.localsale

import com.example.msp_app.data.local.entities.LocalSaleEntity
import com.example.msp_app.data.local.entities.LocalSaleImageEntity

class LocalSaleMappers {
    fun LocalSale.toEntity(): LocalSaleEntity {
        return LocalSaleEntity(
            LOCAL_SALE_ID = this.LOCAL_SALE_ID,
            NOMBRE_CLIENTE = this.NOMBRE_CLIENTE,
            FECHA_VENTA = this.FECHA_VENTA,
            LATITUD = this.LATITUD,
            LONGITUD = this.LONGITUD,
            DIRECCION = this.DIRECCION
        )
    }

    fun LocalSaleEntity.toDomain(): LocalSale {
        return LocalSale(
            LOCAL_SALE_ID = this.LOCAL_SALE_ID,
            NOMBRE_CLIENTE = this.NOMBRE_CLIENTE,
            FECHA_VENTA = this.FECHA_VENTA,
            LATITUD = this.LATITUD,
            LONGITUD = this.LONGITUD,
            DIRECCION = this.DIRECCION
        )
    }

    fun LocalSaleImage.toEntity(): LocalSaleImageEntity {
        return LocalSaleImageEntity(
            LOCAL_SALE_IMAGE_ID = this.LOCAL_SALE_IMAGE_ID,
            LOCAL_SALE_ID = this.LOCAL_SALE_ID,
            IMAGE_URI = this.IMAGE_URI,
            FECHA_SUBIDA = this.FECHA_SUBIDA
        )
    }

    fun LocalSaleImageEntity.toDomain(): LocalSaleImage {
        return LocalSaleImage(
            LOCAL_SALE_IMAGE_ID = this.LOCAL_SALE_IMAGE_ID,
            LOCAL_SALE_ID = this.LOCAL_SALE_ID,
            IMAGE_URI = this.IMAGE_URI,
            FECHA_SUBIDA = this.FECHA_SUBIDA
        )
    }
}