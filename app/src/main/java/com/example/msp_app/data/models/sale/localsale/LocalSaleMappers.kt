package com.example.msp_app.data.models.sale.localsale

import com.example.msp_app.data.api.services.localSales.LocalSaleRequest
import com.example.msp_app.data.api.services.localSales.LocalSaleProductRequest
import com.example.msp_app.data.local.entities.LocalSaleEntity
import com.example.msp_app.data.local.entities.LocalSaleImageEntity
import com.example.msp_app.data.local.entities.LocalSaleProductEntity

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

    fun LocalSaleProduct.toEntity(): LocalSaleProductEntity {
        return LocalSaleProductEntity(
            LOCAL_SALE_ID = this.LOCAL_SALE_ID,
            ARTICULO_ID = this.ARTICULO_ID,
            ARTICULO = this.ARTICULO,
            CANTIDAD = this.CANTIDAD,
            PRECIO_LISTA = this.PRECIO_LISTA,
            PRECIO_CORTO_PLAZO = this.PRECIO_CORTO_PLAZO,
            PRECIO_CONTADO = this.PRECIO_CONTADO
        )
    }

    fun LocalSaleProductEntity.toDomain(): LocalSaleProduct {
        return LocalSaleProduct(
            LOCAL_SALE_ID = this.LOCAL_SALE_ID,
            ARTICULO_ID = this.ARTICULO_ID,
            ARTICULO = this.ARTICULO,
            CANTIDAD = this.CANTIDAD,
            PRECIO_LISTA = this.PRECIO_LISTA,
            PRECIO_CORTO_PLAZO = this.PRECIO_CORTO_PLAZO,
            PRECIO_CONTADO = this.PRECIO_CONTADO
        )
    }
    
    fun LocalSaleEntity.toServerRequest(products: List<LocalSaleProductEntity>): LocalSaleRequest {
        return LocalSaleRequest(
            localSaleId = this.LOCAL_SALE_ID,
            nombreCliente = this.NOMBRE_CLIENTE,
            fechaVenta = this.FECHA_VENTA,
            latitud = this.LATITUD,
            longitud = this.LONGITUD,
            direccion = this.DIRECCION,
            parcialidad = this.PARCIALIDAD,
            enganche = this.ENGANCHE,
            telefono = this.TELEFONO,
            frecPago = this.FREC_PAGO,
            avalOResponsable = this.AVAL_O_RESPONSABLE,
            nota = this.NOTA,
            diaCobranza = this.DIA_COBRANZA,
            precioTotal = this.PRECIO_TOTAL,
            tiempoACortoPlazoMeses = this.TIEMPO_A_CORTO_PLAZOMESES,
            montoACortoPlazo = this.MONTO_A_CORTO_PLAZO,
            productos = products.map { it.toServerRequest() }
        )
    }
    
    fun LocalSaleProductEntity.toServerRequest(): LocalSaleProductRequest {
        return LocalSaleProductRequest(
            articuloId = this.ARTICULO_ID,
            articulo = this.ARTICULO,
            cantidad = this.CANTIDAD,
            precioLista = this.PRECIO_LISTA,
            precioCortoPlazo = this.PRECIO_CORTO_PLAZO,
            precioContado = this.PRECIO_CONTADO
        )
    }
}