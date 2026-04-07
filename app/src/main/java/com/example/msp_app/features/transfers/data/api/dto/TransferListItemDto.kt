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

    @SerializedName("ALMACEN_ORIGEN")
    val almacen: String? = null,

    @SerializedName("ALMACEN_DESTINO")
    val almacenDestino: String? = null,

    @SerializedName("TOTAL_PRODUCTOS")
    val totalProductos: Int? = null,

    @SerializedName("COSTO_TOTAL")
    val costoTotal: Double? = null,

    @SerializedName("FECHA_HORA_CREACION")
    val fechaHoraCreacion: String? = null,

    @SerializedName("productos")
    val productos: List<TransferProductDto>? = null
)

data class TransferProductDto(
    @SerializedName("ARTICULO_ID")
    val articuloId: Int = 0,

    @SerializedName("CLAVE_ARTICULO")
    val claveArticulo: String,

    @SerializedName("ARTICULO_NOMBRE")
    val articuloNombre: String? = null,

    @SerializedName("UNIDADES")
    val unidades: Int,

    @SerializedName("TIPO_MOVTO")
    val tipoMovto: String
)
