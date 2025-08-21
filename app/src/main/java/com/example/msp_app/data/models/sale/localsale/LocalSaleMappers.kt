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
            DIRECCION = this.DIRECCION,
            PARCIALIDAD = this.PARCIALIDAD,
            ENGANCHE = this.ENGANCHE,
            TELEFONO = this.TELEFONO,
            FREC_PAGO = this.FREC_PAGO,
            AVAL_O_RESPONSABLE = this.FREC_PAGO,
            NOTA = this.NOTA,
            DIA_COBRANZA = this.DIA_COBRANZA,
            PRECIO_TOTAL = this.PRECIO_TOTAL,
            TIEMPO_A_CORTO_PLAZOMESES = this.TIEMPO_A_CORTO_PLAZOMESES,
            MONTO_A_CORTO_PLAZO = this.MONTO_A_CORTO_PLAZO,
            ENVIADO = this.ENVIADO
        )
    }

    fun LocalSaleEntity.toDomain(): LocalSale {
        return LocalSale(
            LOCAL_SALE_ID = this.LOCAL_SALE_ID,
            NOMBRE_CLIENTE = this.NOMBRE_CLIENTE,
            FECHA_VENTA = this.FECHA_VENTA,
            LATITUD = this.LATITUD,
            LONGITUD = this.LONGITUD,
            DIRECCION = this.DIRECCION,
            PARCIALIDAD = this.PARCIALIDAD,
            ENGANCHE = this.ENGANCHE,
            TELEFONO = this.TELEFONO,
            FREC_PAGO = this.FREC_PAGO,
            AVAL_O_RESPONSABLE = this.FREC_PAGO,
            NOTA = this.NOTA,
            DIA_COBRANZA = this.DIA_COBRANZA,
            PRECIO_TOTAL = this.PRECIO_TOTAL,
            TIEMPO_A_CORTO_PLAZOMESES = this.TIEMPO_A_CORTO_PLAZOMESES,
            MONTO_A_CORTO_PLAZO = this.MONTO_A_CORTO_PLAZO,
            ENVIADO = this.ENVIADO
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