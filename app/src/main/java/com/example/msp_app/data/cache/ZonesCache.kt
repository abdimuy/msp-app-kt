package com.example.msp_app.data.cache

import android.content.Context
import com.example.msp_app.core.cache.BaseOfflineCache
import com.example.msp_app.core.cache.CacheConfig
import com.example.msp_app.core.cache.CacheMetadata
import com.example.msp_app.data.models.zone.ClientZone
import com.google.gson.reflect.TypeToken
import java.lang.reflect.Type

/**
 * Cache específico para zonas de clientes
 *
 * Usa TTL largo (7 días) porque las zonas cambian poco
 */
class ZonesCache(context: Context) : BaseOfflineCache<ClientZone>(
    context = context,
    config = CacheConfig(
        fileName = "zones_cache",
        ttlMillis = CacheMetadata.LONG_TTL, // 7 días
        version = 1,
        enableLogging = true
    )
) {
    override fun getListType(): Type {
        return object : TypeToken<List<ClientZone>>() {}.type
    }

    /**
     * Búsqueda por nombre de zona
     */
    suspend fun searchByName(query: String): List<ClientZone> {
        return search { zone ->
            zone.ZONA_CLIENTE.contains(query, ignoreCase = true)
        }
    }

    /**
     * Busca una zona por ID
     */
    suspend fun findById(zoneId: Int): ClientZone? {
        return search { it.ZONA_CLIENTE_ID == zoneId }.firstOrNull()
    }

    companion object {
        @Volatile
        private var instance: ZonesCache? = null

        fun getInstance(context: Context): ZonesCache {
            return instance ?: synchronized(this) {
                instance ?: ZonesCache(context.applicationContext).also {
                    instance = it
                }
            }
        }
    }
}
