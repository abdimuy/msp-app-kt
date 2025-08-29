package com.example.msp_app.data.api.services.localSales

import retrofit2.http.Body
import retrofit2.http.POST

data class LocalSaleRequest(
    val localSaleId: String,
    val nombreCliente: String,
    val fechaVenta: String,
    val latitud: Double,
    val longitud: Double,
    val direccion: String,
    val parcialidad: Double,
    val enganche: Double?,
    val telefono: String,
    val frecPago: String,
    val avalOResponsable: String?,
    val nota: String?,
    val diaCobranza: String,
    val precioTotal: Double,
    val tiempoACortoPlazoMeses: Int,
    val montoACortoPlazo: Double,
    val productos: List<LocalSaleProductRequest>
)

data class LocalSaleProductRequest(
    val articuloId: Int,
    val articulo: String,
    val cantidad: Int,
    val precioLista: Double,
    val precioCortoPlazo: Double,
    val precioContado: Double
)

data class LocalSaleResponse(
    val success: Boolean,
    val message: String? = null,
    val localSaleId: String? = null
)

interface LocalSalesApi {
    @POST("ventas-locales")
    suspend fun saveLocalSale(@Body request: LocalSaleRequest): LocalSaleResponse
}