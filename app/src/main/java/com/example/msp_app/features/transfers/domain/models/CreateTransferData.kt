package com.example.msp_app.features.transfers.domain.models

import java.time.LocalDateTime

/**
 * Domain model for creating a new transfer
 * Represents the input data needed to create a transfer
 */
data class CreateTransferData(
    val almacenOrigenId: Int,
    val almacenDestinoId: Int,
    val fecha: LocalDateTime = LocalDateTime.now(),
    val descripcion: String? = null,
    val usuario: String? = null,
    val productos: List<TransferProductItem>
) {
    init {
        require(almacenOrigenId != almacenDestinoId) {
            "Almacén origen y destino no pueden ser iguales"
        }
        require(productos.isNotEmpty()) {
            "Debe incluir al menos un producto"
        }
        require(productos.all { it.unidades > 0 }) {
            "Todas las cantidades deben ser mayor a 0"
        }
    }

    /**
     * Get total number of products
     */
    fun getTotalProducts(): Int = productos.size

    /**
     * Get total units
     */
    fun getTotalUnits(): Int = productos.sumOf { it.unidades }
}

/**
 * Represents a product item in a transfer
 */
data class TransferProductItem(
    val articuloId: Int,
    val claveArticulo: String? = null,
    val unidades: Int,
    val costoUnitario: Double? = null
) {
    init {
        require(unidades > 0) { "Unidades debe ser mayor a 0" }
    }

    /**
     * Calculate total cost if unit cost is available
     */
    fun calculateTotal(): Double? {
        return costoUnitario?.let { it * unidades }
    }
}
