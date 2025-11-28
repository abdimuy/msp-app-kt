package com.example.msp_app.features.transfers.data.api.dto

import com.google.gson.annotations.SerializedName

/**
 * Detailed response for a specific transfer
 */
data class TransferDetailResponse(
    @SerializedName("DOCTO_IN_ID")
    val doctoInId: Int,

    @SerializedName("ALMACEN_ID")
    val almacenId: Int,

    @SerializedName("ALMACEN_DESTINO_ID")
    val almacenDestinoId: Int,

    @SerializedName("FECHA")
    val fecha: String,

    @SerializedName("DESCRIPCION")
    val descripcion: String? = null,

    @SerializedName("FOLIO")
    val folio: String? = null,

    @SerializedName("USUARIO")
    val usuario: String? = null,

    @SerializedName("APLICADO")
    val aplicado: String? = null,

    @SerializedName("ALMACEN")
    val almacen: String? = null,

    @SerializedName("ALMACEN_DESTINO")
    val almacenDestino: String? = null,

    @SerializedName("salidas")
    val salidas: List<MovementDto>,

    @SerializedName("entradas")
    val entradas: List<MovementDto>,

    @SerializedName("detallesCompletos")
    val detallesCompletos: List<MovementDto>
)
