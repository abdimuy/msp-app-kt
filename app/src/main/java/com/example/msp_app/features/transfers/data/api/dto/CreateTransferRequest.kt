package com.example.msp_app.features.transfers.data.api.dto

import com.google.gson.annotations.SerializedName

/**
 * Request body for creating a new transfer
 */
data class CreateTransferRequest(
    @SerializedName("almacenOrigenId")
    val almacenOrigenId: Int,

    @SerializedName("almacenDestinoId")
    val almacenDestinoId: Int,

    @SerializedName("fecha")
    val fecha: String? = null,

    @SerializedName("descripcion")
    val descripcion: String? = null,

    @SerializedName("usuario")
    val usuario: String? = null,

    @SerializedName("detalles")
    val detalles: List<TransferDetailItemDto>
) {
    init {
        require(almacenOrigenId != almacenDestinoId) {
            "Almacén origen y destino no pueden ser iguales"
        }
        require(detalles.isNotEmpty()) {
            "Debe incluir al menos un artículo"
        }
        require(detalles.all { it.unidades > 0 }) {
            "Todas las unidades deben ser mayor a 0"
        }
    }
}
