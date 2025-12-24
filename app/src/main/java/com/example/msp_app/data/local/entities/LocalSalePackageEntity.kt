package com.example.msp_app.data.local.entities

import androidx.room.Entity
import androidx.room.Index

@Entity(
    tableName = "local_sale_packages",
    primaryKeys = ["LOCAL_SALE_ID", "PACKAGE_ID"],
    indices = [Index(value = ["LOCAL_SALE_ID"])]
)
data class LocalSalePackageEntity(
    val LOCAL_SALE_ID: String,
    val PACKAGE_ID: String,
    val PACKAGE_NAME: String,
    val PRECIO_LISTA: Double,
    val PRECIO_CORTO_PLAZO: Double,
    val PRECIO_CONTADO: Double,
    val PRODUCT_IDS_JSON: String
)

data class PackageProductRelation(
    val ARTICULO_ID: Int,
    val CANTIDAD: Int
)