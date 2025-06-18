package com.example.msp_app.data.models.product

import com.example.msp_app.data.local.entities.ProductEntity

fun Product.toEntity(): ProductEntity = ProductEntity(
    DOCTO_PV_DET_ID,
    DOCTO_PV_ID,
    FOLIO,
    ARTICULO_ID,
    ARTICULO,
    CANTIDAD,
    PRECIO_UNITARIO_IMPTO,
    PRECIO_TOTAL_NETO,
    POSICION
)

fun ProductEntity.toDomain(): Product = Product(
    DOCTO_PV_DET_ID,
    DOCTO_PV_ID,
    FOLIO,
    ARTICULO_ID,
    ARTICULO,
    CANTIDAD,
    PRECIO_UNITARIO_IMPTO,
    PRECIO_TOTAL_NETO,
    POSICION
)
