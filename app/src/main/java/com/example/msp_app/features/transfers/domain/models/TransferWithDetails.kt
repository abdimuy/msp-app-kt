package com.example.msp_app.features.transfers.domain.models

/**
 * Domain model for a transfer with its details
 */
data class TransferWithDetails(
    val transfer: Transfer,
    val details: List<TransferDetail>
) {
    /**
     * Get only outbound movements (from source warehouse)
     */
    fun getOutboundMovements(): List<TransferDetail> {
        return details.filter { it.isOutbound() }
    }

    /**
     * Get only inbound movements (to destination warehouse)
     */
    fun getInboundMovements(): List<TransferDetail> {
        return details.filter { it.isInbound() }
    }

    /**
     * Calculate total units transferred
     */
    fun getTotalUnits(): Int {
        return details.filter { it.isOutbound() }.sumOf { it.unidades }
    }

    /**
     * Get unique product count
     */
    fun getUniqueProductCount(): Int {
        return details.filter { it.isOutbound() }.distinctBy { it.articuloId }.size
    }

    /**
     * Calculate total cost
     */
    fun calculateTotalCost(): Double {
        return details.filter { it.isOutbound() }.sumOf { it.costoTotal }
    }
}
