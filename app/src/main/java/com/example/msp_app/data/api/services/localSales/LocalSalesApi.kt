package com.example.msp_app.data.api.services.localSales

import okhttp3.MultipartBody
import okhttp3.RequestBody
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Part
import retrofit2.http.Path

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
    val zonaCliente: String? = null,
    val combos: List<LocalSaleComboRequest>? = null,
    val omitirTraspaso: Boolean? = null
)

data class LocalSaleProductRequest(
    val articuloId: Int,
    val articulo: String,
    val cantidad: Int,
    val precioLista: Double,
    val precioCortoPlazo: Double,
    val precioContado: Double,
    val comboId: String? = null
)

data class LocalSaleComboRequest(
    val comboId: String,
    val nombreCombo: String,
    val precioLista: Double,
    val precioCortoPlazo: Double,
    val precioContado: Double
)

data class LocalSaleResponse(
    val success: Boolean,
    val message: String? = null,
    val localSaleId: String? = null,
    val combosRegistrados: Int? = null,
    val traspasoOmitido: Boolean? = null
)

// Request para edición (sin localSaleId, se pasa en URL)
data class LocalSaleUpdateRequest(
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
    val productos: List<LocalSaleProductRequest>,
    val numero: String? = null,
    val colonia: String? = null,
    val poblacion: String? = null,
    val ciudad: String? = null,
    val tipoVenta: String? = "CONTADO",
    val zonaClienteId: Int? = null,
    val almacenOrigenId: Int? = null,
    val almacenDestinoId: Int? = null,
    val imagenesAEliminar: List<String> = emptyList(),
    val combos: List<LocalSaleComboRequest>? = null,
    val omitirTraspaso: Boolean? = null
)

// Response de edición
data class LocalSaleUpdateResponse(
    val success: Boolean,
    val localSaleId: String? = null,
    val mensaje: String? = null,
    val productosActualizados: Int? = null,
    val cambiosProductos: CambiosProductos? = null,
    val imagenesEliminadas: Int? = null,
    val imagenesAgregadas: Int? = null,
    val almacenOrigenId: Int? = null,
    val almacenDestinoId: Int? = null
)

data class CambiosProductos(
    val devueltos: Int = 0,
    val agregados: Int = 0,
    val sinCambios: Boolean = false
)

interface LocalSalesApi {
    @Multipart
    @POST("ventas-locales")
    suspend fun saveLocalSale(
        @Part("datos") datos: RequestBody,
        @Part imagenes: List<MultipartBody.Part>
    ): LocalSaleResponse

    @Multipart
    @PUT("ventas-locales/{localSaleId}")
    suspend fun updateLocalSale(
        @Path("localSaleId") localSaleId: String,
        @Part("datos") datos: RequestBody,
        @Part imagenes: List<MultipartBody.Part>
    ): LocalSaleUpdateResponse
}