package com.example.msp_app.data.models.sale.localsale

data class LocalSaleProduct(
    val LOCAL_SALE_ID: String,
    val ARTICULO_ID: Int,
    val ARTICULO: String,
    val CANTIDAD: Int,
    val PRECIO_LISTA: Double,
    val PRECIO_CORTO_PLAZO: Double,
    val PRECIO_CONTADO: Double

)
