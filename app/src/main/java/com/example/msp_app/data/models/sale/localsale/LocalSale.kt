package com.example.msp_app.data.models.sale.localsale

data class LocalSale(
    val LOCAL_SALE_ID: String,
    val NOMBRE_CLIENTE: String,
    val FECHA_VENTA: String,
    val LATITUD: Double,
    val LONGITUD: Double,
    val DIRECCION: String
)