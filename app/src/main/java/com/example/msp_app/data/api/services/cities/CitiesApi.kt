package com.example.msp_app.data.api.services.cities

import com.example.msp_app.data.models.city.CityDto
import com.google.gson.annotations.SerializedName
import retrofit2.http.GET

/**
 * Sobre de respuesta de `GET /v2/ciudades`. El backend Go envuelve toda lista v2
 * bajo `items` (misma convención que ventas y cobranza) — **no** bajo `body`,
 * que es la del v1 Node de [com.example.msp_app.data.api.services.zones.ZonesApi].
 *
 * [items] es nullable porque Gson ignora el valor por omisión de Kotlin: si la
 * clave falta en el JSON, el campo aterriza como `null` aunque se declare con
 * `= emptyList()`. [cities] es el único acceso que deben usar los llamadores.
 */
data class CitiesResponse(
    @SerializedName("items")
    val items: List<CityDto>? = null
) {
    fun cities(): List<CityDto> = items.orEmpty()
}

/**
 * Catálogo de ciudades del backend Go (v2).
 *
 * Va por [com.example.msp_app.data.api.V2ApiProvider], **no** por `ApiProvider`:
 * el endpoint vive en el API Go y exige bearer de Firebase, mientras que
 * `ApiProvider` construye el cliente del v1 Node legacy sin autenticación.
 */
interface CitiesApi {
    @GET("v2/ciudades")
    suspend fun listCities(): CitiesResponse
}
