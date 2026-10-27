package com.example.msp_app.data.api.services.visits

import retrofit2.http.Body
import retrofit2.http.PATCH
import retrofit2.http.Path

/**
 * `PATCH /v2/visitas/{id}/ubicacion` (msp-api E-VIS-114): la ubicación que el
 * teléfono obtuvo DESPUÉS de subir la visita en 0,0.
 *
 * Interfaz aparte de [V2VisitsApi] a propósito: Retrofit no admite métodos con
 * cuerpo en una interfaz que tiene fakes por todos lados, y una llamada nueva
 * no debe obligar a tocar el contrato del envío de la visita.
 *
 * El servidor solo cambia una visita que sigue en 0,0, no crea ni reabre nada
 * (404 si no existe) y rechaza 0,0 o fuera de rango (422). Es idempotente.
 */
interface V2UbicacionTardiaApi {
    @PATCH("v2/visitas/{id}/ubicacion")
    suspend fun aplicarUbicacion(@Path("id") id: String, @Body body: UbicacionTardiaBody): VisitaDTO
}

/** El cuerpo del PATCH. Ambos campos siempre presentes; el 0,0 no se manda nunca. */
data class UbicacionTardiaBody(val lat: Double, val lng: Double)
