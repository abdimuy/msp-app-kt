package com.example.msp_app.data.local.entities

import androidx.room.Entity
import androidx.room.Index

@Entity(
    tableName = "local_sale_products",
    primaryKeys = ["LOCAL_SALE_ID", "ARTICULO_ID"],
    indices = [Index(value = ["LOCAL_SALE_ID"])]
)
data class LocalSaleProductEntity(
    val LOCAL_SALE_ID: String,
    val ARTICULO_ID: Int,
    val ARTICULO: String,
    val CANTIDAD: Int,
    val PRECIO_LISTA: Double,
    val PRECIO_CORTO_PLAZO: Double,
    val PRECIO_CONTADO: Double

)