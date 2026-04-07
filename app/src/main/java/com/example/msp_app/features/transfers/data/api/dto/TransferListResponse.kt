package com.example.msp_app.features.transfers.data.api.dto

import com.google.gson.annotations.SerializedName

data class TransferListResponse(
    @SerializedName("error")
    val error: String?,

    @SerializedName("body")
    val body: List<TransferListItemDto>
)
