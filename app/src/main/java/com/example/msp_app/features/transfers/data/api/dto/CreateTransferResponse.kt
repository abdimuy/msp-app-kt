package com.example.msp_app.features.transfers.data.api.dto

import com.google.gson.annotations.SerializedName

/**
 * Response after creating a transfer
 */
data class CreateTransferResponse(
    @SerializedName("error")
    val error: String,

    @SerializedName("body")
    val body: CreateTransferBody
)

data class CreateTransferBody(
    @SerializedName("success")
    val success: Boolean,

    @SerializedName("doctoInId")
    val doctoInId: Int,

    @SerializedName("folio")
    val folio: String,

    @SerializedName("mensaje")
    val mensaje: String,

    @SerializedName("message")
    val message: String
)
