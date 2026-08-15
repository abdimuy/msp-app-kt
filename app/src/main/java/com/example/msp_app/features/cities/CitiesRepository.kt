package com.example.msp_app.features.cities

import com.example.msp_app.data.api.V2ApiProvider
import com.example.msp_app.data.api.services.cities.CitiesApi
import com.example.msp_app.data.models.city.City
import com.example.msp_app.data.models.city.toCityOrNull
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Repository del catálogo de ciudades.
 *
 * Calcado de [com.example.msp_app.features.zones.ZonesRepository] salvo en el
 * proveedor: zonas pega al v1 Node por `ApiProvider`, ciudades pega al API Go por
 * [V2ApiProvider] (bearer de Firebase), que es donde vive `GET /v2/ciudades`.
 *
 * `open` y con [apiFactory] inyectable siguiendo el mecanismo de dobles de este
 * repo (fakes por subclase, sin MockK). La fábrica se consume tras `by lazy`, así
 * que en tests que pasan un fake el `V2ApiProvider.create` por omisión nunca se
 * evalúa — importa porque construirlo arrastra configuración de red y Firebase.
 */
open class CitiesRepository(
    apiFactory: () -> CitiesApi = { V2ApiProvider.create(CitiesApi::class.java) }
) {

    private val api: CitiesApi by lazy(apiFactory)

    /**
     * Obtiene el catálogo de ciudades desde la API.
     *
     * Una lista vacía es un resultado **exitoso**, no un error: significa que el
     * catálogo no trajo filas y la pantalla debe ofrecer el texto libre, no
     * reintentar. Difiere a propósito de `ZonesResponse.isSuccess()`, que trata
     * el vacío como fallo y deja al selector en estado de error.
     *
     * Las filas incompletas se descartan en [toCityOrNull] en vez de tumbar toda
     * la respuesta: una ciudad rota del catálogo no debe dejar al vendedor sin
     * las otras 69.
     */
    open suspend fun getCities(): Result<List<City>> {
        return withContext(Dispatchers.IO) {
            try {
                val response = api.listCities()
                Result.success(response.cities().mapNotNull { it.toCityOrNull() })
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }
}
