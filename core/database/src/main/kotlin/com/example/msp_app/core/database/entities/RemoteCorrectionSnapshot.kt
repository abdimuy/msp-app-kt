package com.example.msp_app.core.database.entities

/**
 * Proyección barata de `(CLAIM_ID, REVISION, CORRECCION_REMOTA_PENDIENTE)` —
 * plan "Corregir una venta DESPUÉS de que subió" (nivel 2), eje 2: el worker
 * de corrección remota la toma justo DESPUÉS de tomar el candado `'REMOTE'` y
 * ANTES de leer el cuerpo (paso 1 de la corrida), la misma disciplina que
 * [SaleClaimSnapshot] ya aplica del lado de la subida. `REVISION` es la
 * referencia contra la que `cerrarCorreccionRemota` decide si el dueño
 * corrigió otra vez mientras la corrida seguía en vuelo.
 *
 * Vive en `entities/` por la misma razón que [SaleClaimSnapshot]: sus campos
 * mapean columnas de `local_sale` uno a uno — el nombre de columna, no una
 * convención Kotlin, es la fuente de verdad (exclusión de
 * `detekt.ConstructorParameterNaming`).
 */
data class RemoteCorrectionSnapshot(
    val CLAIM_ID: String?,
    val REVISION: Int,
    val CORRECCION_REMOTA_PENDIENTE: Boolean
)
