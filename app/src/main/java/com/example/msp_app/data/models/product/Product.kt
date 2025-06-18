package com.example.msp_app.data.models.product

data class Product(
    val DOCTO_PV_DET_ID: Int,
    val DOCTO_PV_ID: Int,
    val FOLIO: String,
    val ARTICULO_ID: Int,
    val ARTICULO: String,
    val CANTIDAD: Int,
    val PRECIO_UNITARIO_IMPTO: Double,
    val PRECIO_TOTAL_NETO: Double,
    val POSICION: Int
)