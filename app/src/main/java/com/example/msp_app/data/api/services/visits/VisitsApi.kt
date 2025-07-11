package com.example.msp_app.data.api.services.visits

import com.example.msp_app.data.models.visit.Visit
import retrofit2.http.Body
import retrofit2.http.POST

interface VisitsApi {
    @POST("visitas")
    suspend fun saveVisit(@Body visit: Visit)
}