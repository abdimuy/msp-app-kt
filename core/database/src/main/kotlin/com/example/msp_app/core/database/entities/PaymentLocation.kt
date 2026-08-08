package com.example.msp_app.core.database.entities

/**
 * Proyeccion de fila (no `@Entity`, Room la mapea por nombre de columna) para
 * [com.example.msp_app.core.database.dao.payment.PaymentDao.getAllLocations].
 * Vive en `:core:database` (y no en `com.example.msp_app.data.models.payment`,
 * su hogar original en `:app`) porque es el tipo de retorno de una `@Query`
 * del DAO movido en el hoist de Plan 2 / Task 2. Movida tal cual (mismos 3
 * campos, sin cambios de logica). `PaymentLocationsGroup` (agrupacion de
 * UI, no mapeada por Room) se queda en `:app`.
 */
data class PaymentLocation(
    val DOCTO_CC_ACR_ID: Int,
    val LAT: Double,
    val LNG: Double
)
