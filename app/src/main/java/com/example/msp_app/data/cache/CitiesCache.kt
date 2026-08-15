package com.example.msp_app.data.cache

import android.content.Context
import com.example.msp_app.core.cache.BaseOfflineCache
import com.example.msp_app.core.cache.CacheConfig
import com.example.msp_app.core.cache.CacheMetadata
import com.example.msp_app.data.models.city.City
import com.example.msp_app.data.models.city.normalizeCiudad
import com.google.gson.reflect.TypeToken
import java.lang.reflect.Type

/**
 * Cache del catálogo de ciudades.
 *
 * TTL largo (7 días), igual que [ZonesCache]: `CIUDADES` es tabla de Microsip que
 * mantiene la oficina y cambia muy poco. Que sobreviva sin red es lo que permite
 * capturar una venta en una colonia sin cobertura sin caer al texto libre.
 */
class CitiesCache(context: Context) : BaseOfflineCache<City>(
    context = context,
    config = CacheConfig(
        fileName = "cities_cache",
        // 7 días
        ttlMillis = CacheMetadata.LONG_TTL,
        version = 1,
        enableLogging = true
    )
) {
    override fun getListType(): Type {
        return object : TypeToken<List<City>>() {}.type
    }

    /**
     * Búsqueda por nombre de ciudad. Compara sobre la forma normalizada para que
     * acentos y mayúsculas no escondan una ciudad que sí está en el catálogo.
     */
    suspend fun searchByName(query: String): List<City> {
        val objetivo = normalizeCiudad(query)
        return search { city -> normalizeCiudad(city.ciudad).contains(objetivo) }
    }

    /**
     * Busca una ciudad por ID.
     */
    suspend fun findById(cityId: Int): City? {
        return search { it.ciudadId == cityId }.firstOrNull()
    }

    companion object {
        @Volatile
        private var instance: CitiesCache? = null

        fun getInstance(context: Context): CitiesCache {
            return instance ?: synchronized(this) {
                instance ?: CitiesCache(context.applicationContext).also {
                    instance = it
                }
            }
        }
    }
}
