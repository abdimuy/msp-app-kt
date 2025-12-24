package com.example.msp_app.core.cache

import android.content.Context
import android.util.Log
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.lang.reflect.Type

/**
 * Interface base para todos los caches offline
 */
interface OfflineCache<T> {
    suspend fun save(items: List<T>)
    suspend fun get(): CacheResult<List<T>>
    suspend fun search(predicate: (T) -> Boolean): List<T>
    suspend fun clear()
    suspend fun hasData(): Boolean
    fun getMetadata(): CacheMetadata?
}

/**
 * Cache offline genérico y reutilizable
 *
 * Features:
 * - Thread-safe con Mutex
 * - Serialización con Gson
 * - TTL configurable
 * - Versionado del cache
 * - Logging integrado
 * - Búsqueda genérica
 * - Memory cache + File cache
 *
 * @param T Tipo de entidad a cachear
 * @param context Context de Android
 * @param config Configuración del cache
 */
abstract class BaseOfflineCache<T : Any>(
    protected val context: Context,
    protected val config: CacheConfig
) : OfflineCache<T> {

    companion object {
        private const val TAG = "OfflineCache"
    }

    protected val gson: Gson = GsonBuilder()
        .setDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'")
        .create()

    private val mutex = Mutex()

    private val cacheDir: File by lazy {
        File(context.filesDir, "offline_cache").also {
            if (!it.exists()) it.mkdirs()
        }
    }

    private val cacheFile: File by lazy {
        File(cacheDir, "${config.fileName}.json")
    }

    // Cache en memoria para acceso rápido
    @Volatile
    private var memoryCache: List<T>? = null

    @Volatile
    private var cachedMetadata: CacheMetadata? = null

    /**
     * Tipo de lista para deserialización - las subclases deben proporcionar
     */
    protected abstract fun getListType(): Type

    /**
     * Guarda items en el cache con metadata
     */
    override suspend fun save(items: List<T>) {
        mutex.withLock {
            withContext(Dispatchers.IO) {
                try {
                    val metadata = CacheMetadata(
                        timestamp = System.currentTimeMillis(),
                        version = config.version,
                        ttlMillis = config.ttlMillis,
                        source = CacheSource.NETWORK
                    )

                    val wrapper = CacheWrapper(data = items, metadata = metadata)
                    val json = gson.toJson(wrapper)
                    cacheFile.writeText(json)

                    // Actualizar cache en memoria
                    memoryCache = items
                    cachedMetadata = metadata

                    log("Saved ${items.size} items to cache [${config.fileName}]")
                } catch (e: Exception) {
                    logError("Error saving to cache", e)
                    throw e
                }
            }
        }
    }

    /**
     * Obtiene items del cache con información de expiración
     */
    override suspend fun get(): CacheResult<List<T>> {
        return mutex.withLock {
            withContext(Dispatchers.IO) {
                try {
                    // Intentar obtener de memoria primero
                    memoryCache?.let { cached ->
                        cachedMetadata?.let { meta ->
                            log("Returning ${cached.size} items from memory cache")
                            return@withContext if (meta.isExpired()) {
                                CacheResult.Expired(cached, meta)
                            } else {
                                CacheResult.Success(cached, meta)
                            }
                        }
                    }

                    // Cargar del archivo
                    if (!cacheFile.exists()) {
                        log("Cache file does not exist")
                        return@withContext CacheResult.Empty
                    }

                    val json = cacheFile.readText()
                    if (json.isBlank()) {
                        log("Cache file is empty")
                        return@withContext CacheResult.Empty
                    }

                    // Deserializar wrapper
                    val wrapperType = TypeToken.getParameterized(
                        CacheWrapper::class.java,
                        getListType()
                    ).type

                    val wrapper: CacheWrapper<List<T>> = gson.fromJson(json, wrapperType)

                    // Verificar versión
                    if (wrapper.metadata.version != config.version) {
                        log("Cache version mismatch (cached: ${wrapper.metadata.version}, current: ${config.version}), clearing cache")
                        clear()
                        return@withContext CacheResult.Empty
                    }

                    // Actualizar cache en memoria
                    memoryCache = wrapper.data
                    cachedMetadata = wrapper.metadata

                    log("Loaded ${wrapper.data.size} items from file cache (age: ${wrapper.metadata.ageFormatted()})")

                    return@withContext if (wrapper.metadata.isExpired()) {
                        CacheResult.Expired(wrapper.data, wrapper.metadata)
                    } else {
                        CacheResult.Success(wrapper.data, wrapper.metadata)
                    }
                } catch (e: Exception) {
                    logError("Error reading from cache", e)
                    CacheResult.Error(e)
                }
            }
        }
    }

    /**
     * Búsqueda genérica con predicado
     */
    override suspend fun search(predicate: (T) -> Boolean): List<T> {
        return when (val result = get()) {
            is CacheResult.Success -> result.data.filter(predicate)
            is CacheResult.Expired -> result.data.filter(predicate)
            else -> emptyList()
        }
    }

    /**
     * Limpia el cache completamente
     */
    override suspend fun clear() {
        mutex.withLock {
            withContext(Dispatchers.IO) {
                try {
                    if (cacheFile.exists()) {
                        cacheFile.delete()
                    }
                    memoryCache = null
                    cachedMetadata = null
                    log("Cache cleared [${config.fileName}]")
                } catch (e: Exception) {
                    logError("Error clearing cache", e)
                }
            }
        }
    }

    /**
     * Verifica si hay datos en cache
     */
    override suspend fun hasData(): Boolean {
        return memoryCache?.isNotEmpty() == true ||
                (cacheFile.exists() && cacheFile.length() > 0)
    }

    /**
     * Obtiene la metadata actual del cache
     */
    override fun getMetadata(): CacheMetadata? = cachedMetadata

    /**
     * Obtiene la cantidad de items en cache
     */
    suspend fun count(): Int {
        return when (val result = get()) {
            is CacheResult.Success -> result.data.size
            is CacheResult.Expired -> result.data.size
            else -> 0
        }
    }

    /**
     * Verifica si el cache está expirado
     */
    fun isExpired(): Boolean = cachedMetadata?.isExpired() ?: true

    // Logging helpers
    private fun log(message: String) {
        if (config.enableLogging) {
            Log.d(TAG, "[${config.fileName}] $message")
        }
    }

    private fun logError(message: String, e: Exception) {
        if (config.enableLogging) {
            Log.e(TAG, "[${config.fileName}] $message", e)
        }
    }
}

/**
 * Factory inline para crear caches con reified types
 * Uso: val cache = createOfflineCache<MyEntity>(context, config)
 */
inline fun <reified T : Any> createOfflineCache(
    context: Context,
    config: CacheConfig
): BaseOfflineCache<T> {
    return object : BaseOfflineCache<T>(context, config) {
        override fun getListType(): Type {
            return object : TypeToken<List<T>>() {}.type
        }
    }
}
