package com.example.msp_app.core.database.entities

/**
 * Proyección barata de los tres campos que deciden la carrera entre corregir
 * y subir (plan "Corregir una venta antes de que suba", mecanismo paso 3): el
 * subidor toma este snapshot al entrar y lo relee inmediatamente antes del
 * POST — si cambió, se frena sin mandar la venta, en vez de mandar un cuerpo
 * mezclado. Vive en `entities` (no en `dao/localsale`) porque, igual que
 * [LocalSaleEntity], sus campos mapean columnas de `local_sale` uno a uno —
 * el nombre de columna, no una convención Kotlin, es la fuente de verdad.
 */
data class SaleClaimSnapshot(
    val CLAIM_ID: String?,
    val REVISION: Int,
    val ENVIADO: Boolean
)
