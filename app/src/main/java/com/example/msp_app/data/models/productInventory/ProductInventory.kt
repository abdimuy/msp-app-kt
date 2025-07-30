package com.example.msp_app.data.models.productInventory

data class ProductInventory(
    val ARTICULO_ID: Int,
    val ARTICULO: String,
    val EXISTENCIAS: Int,
    val LINEA_ARTICULO_ID: Int,
    val LINEA_ARTICULO: String,
    val PRECIOS: String?
)