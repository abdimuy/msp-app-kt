package com.example.msp_app.features.transfers.data.api.dto

import com.google.gson.annotations.SerializedName

/**
 * Request for getting product costs preview
 */
data class CostPreviewRequest(
    @SerializedName("almacenId")
    val almacenId: Int,

    @SerializedName("articulosIds")
    val articulosIds: List<Int>
)
