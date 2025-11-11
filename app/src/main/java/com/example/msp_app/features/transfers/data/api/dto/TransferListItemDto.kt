package com.example.msp_app.features.transfers.data.api.dto

import com.google.gson.annotations.SerializedName

/**
 * DTO for transfer list item
 */
data class TransferListItemDto(
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

    @SerializedName("TOTAL_PRODUCTOS")
    val totalProductos: Int? = null,

    @SerializedName("COSTO_TOTAL")
    val costoTotal: Double? = null
)
