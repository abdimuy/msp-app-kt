package com.example.msp_app.data.api.services.clientes

import com.google.gson.annotations.SerializedName
import retrofit2.http.GET

data class Cliente(
    @SerializedName("CLIENTE_ID") val CLIENTE_ID: Int,
    @SerializedName("NOMBRE") val NOMBRE: String,
    @SerializedName("ESTATUS") val ESTATUS: String,
    @SerializedName("CAUSA_SUSP") val CAUSA_SUSP: String? = null
)

data class ClienteResponse(
    @SerializedName("body")
    val body: List<Cliente> = emptyList()
)

interface ClientesApi {
    @GET("clientes/")
    suspend fun getClientes(): ClienteResponse
}
