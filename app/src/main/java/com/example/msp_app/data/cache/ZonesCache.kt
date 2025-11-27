package com.example.msp_app.data.cache

import android.content.Context
import com.example.msp_app.data.models.zone.ClientZoneEntity
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class ZonesCache(private val context: Context) {
    private val gson = Gson()
    private val cacheFileName = "zones_cache.json"
    private val cacheDir = File(context.filesDir, "zones_cache")

    init {
        if (!cacheDir.exists()) {
            cacheDir.mkdirs()
        }
    }

    private fun getCacheFile(): File {
        return File(cacheDir, cacheFileName)
    }

    /**
     * Guarda las zonas en caché
     */
    suspend fun saveZones(zones: List<ClientZoneEntity>) {
        withContext(Dispatchers.IO) {
            try {
                val cacheFile = getCacheFile()
                val json = gson.toJson(zones)
                cacheFile.writeText(json)

                // Guardar timestamp
                val timestampFile = File(cacheDir, "timestamp.txt")
                timestampFile.writeText(System.currentTimeMillis().toString())

                android.util.Log.d("ZonesCache", "Guardadas ${zones.size} zonas en caché")
            } catch (e: Exception) {
                android.util.Log.e("ZonesCache", "Error guardando zonas en caché", e)
            }
        }
    }

    /**
     * Obtiene las zonas desde el caché
     */
    suspend fun getZones(): List<ClientZoneEntity> {
        return withContext(Dispatchers.IO) {
            try {
                val cacheFile = getCacheFile()

                if (!cacheFile.exists()) {
                    android.util.Log.d("ZonesCache", "No existe archivo de caché")
                    return@withContext emptyList()
                }

                val json = cacheFile.readText()
                val type = object : TypeToken<List<ClientZoneEntity>>() {}.type
                val zones: List<ClientZoneEntity> = gson.fromJson(json, type)

                android.util.Log.d("ZonesCache", "Cargadas ${zones.size} zonas desde caché")
                zones
            } catch (e: Exception) {
                android.util.Log.e("ZonesCache", "Error leyendo zonas desde caché", e)
                emptyList()
            }
        }
    }

    /**
     * Limpia el caché
     */
    suspend fun clearCache() {
        withContext(Dispatchers.IO) {
            try {
                val cacheFile = getCacheFile()
                if (cacheFile.exists()) {
                    cacheFile.delete()
                }

                val timestampFile = File(cacheDir, "timestamp.txt")
                if (timestampFile.exists()) {
                    timestampFile.delete()
                }

                android.util.Log.d("ZonesCache", "Caché de zonas limpiado")
            } catch (e: Exception) {
                android.util.Log.e("ZonesCache", "Error limpiando caché", e)
            }
        }
    }

    /**
     * Obtiene la fecha de la última actualización del caché
     */
    suspend fun getLastUpdateTimestamp(): Long? {
        return withContext(Dispatchers.IO) {
            try {
                val timestampFile = File(cacheDir, "timestamp.txt")
                if (timestampFile.exists()) {
                    timestampFile.readText().toLongOrNull()
                } else {
                    null
                }
            } catch (e: Exception) {
                null
            }
        }
    }

    /**
     * Verifica si el caché está disponible
     */
    suspend fun isCacheAvailable(): Boolean {
        return withContext(Dispatchers.IO) {
            val cacheFile = getCacheFile()
            cacheFile.exists() && cacheFile.length() > 0
        }
    }
}