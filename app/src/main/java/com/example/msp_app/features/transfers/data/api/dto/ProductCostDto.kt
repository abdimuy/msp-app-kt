package com.example.msp_app.features.transfers.data.api.dto

import com.google.gson.annotations.SerializedName

/**
 * DTO for product cost in preview
 */
data class ProductCostDto(
    @SerializedName("articuloId")
    val articuloId: Int,

    @SerializedName("costoUnitario")
    val costoUnitario: Double
)
