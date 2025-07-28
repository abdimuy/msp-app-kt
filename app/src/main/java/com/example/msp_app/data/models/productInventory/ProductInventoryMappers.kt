package com.example.msp_app.data.models.productInventory

import com.example.msp_app.data.local.entities.ProductInventoryEntity

fun ProductInventory.toEntity(): ProductInventoryEntity = ProductInventoryEntity(
    ARTICULO_ID = ARTICULO_ID,
    ARTICULO = ARTICULO,
    EXISTENCIAS = EXISTENCIAS,
    LINEA_ARTICULO_ID = LINEA_ARTICULO_ID,
    LINEA_ARTICULO = LINEA_ARTICULO,
    PRECIOS = PRECIOS
)

fun ProductInventoryEntity.toDomain(): ProductInventory = ProductInventory(
    ARTICULO_ID = ARTICULO_ID,
    ARTICULO = ARTICULO,
    EXISTENCIAS = EXISTENCIAS,
    LINEA_ARTICULO_ID = LINEA_ARTICULO_ID,
    LINEA_ARTICULO = LINEA_ARTICULO,
    PRECIOS = PRECIOS
)