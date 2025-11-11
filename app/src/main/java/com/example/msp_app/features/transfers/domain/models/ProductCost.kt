package com.example.msp_app.features.transfers.domain.models

/**
 * Domain model for product cost preview
 */
data class ProductCost(
    val articuloId: Int,
    val costoUnitario: Double
) {
    /**
     * Calculate total cost for a given quantity
     */
    fun calculateTotal(unidades: Int): Double {
        return costoUnitario * unidades
    }
}
