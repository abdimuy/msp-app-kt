package com.example.msp_app.data.models.productInventoryImage

import com.example.msp_app.data.local.entities.ProductInventoryImageEntity

fun ProductInventoryImage.toEntity(): ProductInventoryImageEntity = ProductInventoryImageEntity(
    IMAGEN_ID = IMAGEN_ID,
    ARTICULO_ID = ARTICULO_ID,
    RUTA_LOCAL = RUTA_LOCAL
)

fun ProductInventoryImageEntity.toDomain(): ProductInventoryImage = ProductInventoryImage(
    IMAGEN_ID = IMAGEN_ID,
    ARTICULO_ID = ARTICULO_ID,
    RUTA_LOCAL = RUTA_LOCAL
)
