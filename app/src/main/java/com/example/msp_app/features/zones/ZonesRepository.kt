package com.example.msp_app.features.zones

import com.example.msp_app.data.api.ApiProvider
import com.example.msp_app.data.api.services.zones.ZonesApi
import com.example.msp_app.data.models.zone.ClientZone
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Repository para zonas
 */
class ZonesRepository {

    private val api: ZonesApi by lazy {
        ApiProvider.create(ZonesApi::class.java)
    }

    /**
     * Obtiene las zonas de cliente desde la API
     */
    suspend fun getClientZones(): Result<List<ClientZone>> {
        return withContext(Dispatchers.IO) {
            try {
                val response = api.getClientZones()

                if (response.isSuccess()) {
                    val sortedZones = response.body.sortedByZoneName()
                    Result.success(sortedZones)
                } else {
                    Result.failure(
                        Exception(
                            response.error ?: "Error desconocido al obtener zonas"
                        )
                    )
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }
}

/**
 * Función de extensión para ordenar zonas
 */
private fun List<ClientZone>.sortedByZoneName(): List<ClientZone> {

    val zonesWithNumber = mutableListOf<Pair<ClientZone, Int>>()
    val zonesWithoutNumber = mutableListOf<ClientZone>()

    this.forEach { zone ->
        val number = extractZoneNumber(zone.ZONA_CLIENTE)
        if (number != null) {
            zonesWithNumber.add(zone to number)
        } else {
            zonesWithoutNumber.add(zone)
        }
    }

    val sortedWithNumber = zonesWithNumber
        .sortedBy { it.second }
        .map { it.first }

    val sortedWithoutNumber = zonesWithoutNumber
        .sortedBy { it.ZONA_CLIENTE }
    
    return sortedWithNumber + sortedWithoutNumber
}

/**
 * Extrae el número de una zona (ej: "R/07" -> 7, "MAYOREO" -> null)
 */
private fun extractZoneNumber(zoneName: String): Int? {
    // Buscar todos los dígitos en el nombre
    val regex = """\d+""".toRegex()
    return regex.find(zoneName)?.value?.toIntOrNull()
}