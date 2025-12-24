package com.example.msp_app.data.api.services.localSales

import okhttp3.MultipartBody
import okhttp3.RequestBody
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part

data class LocalSaleRequest(
    val localSaleId: String,
    val userEmail: String,
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
    val montoDeContado: Double,
    val productos: List<LocalSaleProductRequest>,
    val numero: String? = null,
    val colonia: String? = null,
    val poblacion: String? = null,
    val ciudad: String? = null,
    val tipoVenta: String? = "CONTADO",
    val zonaClienteId: Int? = null,
    val zonaCliente: String? = null
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
    @Multipart
    @POST("ventas-locales")
    suspend fun saveLocalSale(
        @Part("datos") datos: RequestBody,
        @Part imagenes: List<MultipartBody.Part>
    ): LocalSaleResponse
}