package com.example.msp_app.core.database.dao.sale

/**
 * Estado de cobranza de una venta a credito. Vive en `:core:database` (y no
 * en `com.example.msp_app.data.models.sale`, su hogar original en `:app`)
 * porque [SaleDao.updateTotal] lo usa como tipo de parametro enlazado en una
 * `@Query` (`ESTADO_COBRANZA = :estadoCobranza`) — Room necesita resolver el
 * tipo en el modulo donde vive el DAO. Movido tal cual en el hoist de
 * Plan 2 / Task 2 (mismos 5 valores, sin cambios de logica).
 */
enum class EstadoCobranza {
    PAGADO,
    NO_PAGADO,
    PENDIENTE,
    VISITADO,
    VOLVER_VISITAR
}
