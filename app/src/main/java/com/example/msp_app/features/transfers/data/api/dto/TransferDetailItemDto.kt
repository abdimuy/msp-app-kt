package com.example.msp_app.features.transfers.data.api.dto

import com.google.gson.annotations.SerializedName

/**
 * DTO for transfer detail item in create request
 */
data class TransferDetailItemDto(
    @SerializedName("articuloId")
    val articuloId: Int,

    @SerializedName("claveArticulo")
    val claveArticulo: String? = null,

    @SerializedName("unidades")
    val unidades: Int
)
