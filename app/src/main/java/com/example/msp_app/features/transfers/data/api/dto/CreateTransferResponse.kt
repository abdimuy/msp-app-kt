package com.example.msp_app.features.transfers.data.api.dto

import com.google.gson.annotations.SerializedName

/**
 * Response after creating a transfer
 */
data class CreateTransferResponse(
    @SerializedName("message")
    val message: String,

    @SerializedName("doctoInId")
    val doctoInId: Int,

    @SerializedName("FOLIO")
    val folio: String? = null,

    @SerializedName("FECHA")
    val fecha: String? = null
)
