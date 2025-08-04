package com.example.msp_app.data.api.services.guarantee

import okhttp3.MultipartBody
import okhttp3.RequestBody
import retrofit2.http.Body
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part
import retrofit2.http.Path

data class GuaranteeEventRequest(
    val id: String,
    val fechaEvento: String,
    val comentario: String,
    val tipoEvento: String
)

interface GuaranteesApi {
    @Multipart
    @POST("garantias/{doctoCcId}/imagenes")
    suspend fun saveGuaranteeWithImages(
        @Path("doctoCcId") doctoCcId: Int,
        @Part("externalId") externalId: RequestBody,
        @Part("descripcionFalla") descripcionFalla: RequestBody,
        @Part("observaciones") observaciones: RequestBody?,
        @Part imagenes: List<MultipartBody.Part>
    )

    @POST("garantias/{garantiaId}/eventos")
    suspend fun saveGuaranteeEvent(
        @Path("garantiaId") garantiaId: String,
        @Body event: GuaranteeEventRequest
    )
}