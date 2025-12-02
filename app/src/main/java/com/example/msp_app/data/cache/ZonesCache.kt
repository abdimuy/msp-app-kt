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

    suspend fun saveZones(zones: List<ClientZoneEntity>) {
        withContext(Dispatchers.IO) {
            try {
                val cacheFile = getCacheFile()
                val json = gson.toJson(zones)
                cacheFile.writeText(json)

                val timestampFile = File(cacheDir, "timestamp.txt")
                timestampFile.writeText(System.currentTimeMillis().toString())

            } catch (e: Exception) {

            }
        }
    }

    suspend fun getZones(): List<ClientZoneEntity> {
        return withContext(Dispatchers.IO) {
            try {
                val cacheFile = getCacheFile()

                if (!cacheFile.exists()) {
                    return@withContext emptyList()
                }

                val json = cacheFile.readText()
                val type = object : TypeToken<List<ClientZoneEntity>>() {}.type
                val zones: List<ClientZoneEntity> = gson.fromJson(json, type)

                zones
            } catch (e: Exception) {
                emptyList()
            }
        }
    }

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

            } catch (e: Exception) {

            }
        }
    }

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

    suspend fun isCacheAvailable(): Boolean {
        return withContext(Dispatchers.IO) {
            val cacheFile = getCacheFile()
            cacheFile.exists() && cacheFile.length() > 0
        }
    }
}