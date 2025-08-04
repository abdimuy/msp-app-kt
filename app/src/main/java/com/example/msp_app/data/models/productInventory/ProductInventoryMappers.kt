package com.example.msp_app.data.models.productInventory

import com.example.msp_app.core.utils.parsePrice
import com.example.msp_app.data.local.entities.ProductInventoryEntity
import com.google.gson.Gson

fun ProductInventory.toEntity(): ProductInventoryEntity {
    val pricesMap = parsePrice(PRECIOS)
    val pricesJson = Gson().toJson(pricesMap)

    return ProductInventoryEntity(
        ARTICULO_ID = ARTICULO_ID,
        ARTICULO = ARTICULO,
        EXISTENCIAS = EXISTENCIAS,
        LINEA_ARTICULO_ID = LINEA_ARTICULO_ID,
        LINEA_ARTICULO = LINEA_ARTICULO,
        PRECIOS = pricesJson
    )
}

fun ProductInventoryEntity.toDomain(): ProductInventory = ProductInventory(
    ARTICULO_ID = ARTICULO_ID,
    ARTICULO = ARTICULO,
    EXISTENCIAS = EXISTENCIAS,
    LINEA_ARTICULO_ID = LINEA_ARTICULO_ID,
    LINEA_ARTICULO = LINEA_ARTICULO,
    PRECIOS = PRECIOS ?: ""
)