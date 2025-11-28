package com.example.msp_app.features.transfers.data.api.dto

import com.google.gson.annotations.SerializedName

/**
 * DTO for movement details (entrada/salida)
 */
data class MovementDto(
    @SerializedName("ARTICULO_ID")
    val articuloId: Int,

    @SerializedName("CLAVE_ARTICULO")
    val claveArticulo: String,

    @SerializedName("UNIDADES")
    val unidades: Int,

    @SerializedName("COSTO_UNITARIO")
    val costoUnitario: Double,

    @SerializedName("COSTO_TOTAL")
    val costoTotal: Double,

    @SerializedName("TIPO_MOVTO")
    val tipoMovto: String, // "S" o "E"

    @SerializedName("MOVTO_ID")
    val movtoId: Int? = null,

    @SerializedName("ARTICULO")
    val articulo: String? = null,

    @SerializedName("DESCRIPCION1")
    val descripcion1: String? = null,

    @SerializedName("DESCRIPCION2")
    val descripcion2: String? = null
)
