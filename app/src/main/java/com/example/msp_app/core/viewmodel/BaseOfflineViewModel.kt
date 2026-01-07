package com.example.msp_app.core.viewmodel

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.msp_app.core.cache.BaseOfflineCache
import com.example.msp_app.core.cache.CacheMetadata
import com.example.msp_app.core.cache.CacheResult
import com.example.msp_app.core.cache.CacheSource
import com.example.msp_app.core.network.ConnectivityMonitor
import com.example.msp_app.core.utils.ResultState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * ViewModel base para entidades con soporte offline
 *
 * Proporciona:
 * - Estado de datos con metadata de fuente
 * - Estado de modo offline
 * - Lógica de fetch con fallback automático
 * - Refresh manual
 * - Búsqueda con cache
 *
 * @param T Tipo de entidad
 * @param application Application context
 */
abstract class BaseOfflineViewModel<T : Any>(
    application: Application
) : AndroidViewModel(application) {

    companion object {
        private const val TAG = "BaseOfflineViewModel"
    }

    // Estado principal de datos
    protected val _state = MutableStateFlow<ResultState<List<T>>>(ResultState.Idle)
    val state: StateFlow<ResultState<List<T>>> = _state.asStateFlow()

    // Estado de modo offline
    protected val _isOfflineMode = MutableStateFlow(false)
    val isOfflineMode: StateFlow<Boolean> = _isOfflineMode.asStateFlow()

    // Estado de refresh en progreso
    protected val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    // Timestamp de última actualización
    protected val _lastUpdateTimestamp = MutableStateFlow<Long?>(null)
    val lastUpdateTimestamp: StateFlow<Long?> = _lastUpdateTimestamp.asStateFlow()

    // Monitor de conectividad
    protected val connectivityMonitor: ConnectivityMonitor by lazy {
        ConnectivityMonitor.getInstance(application)
    }

    // Flag para habilitar/deshabilitar auto-refresh
    protected open val autoRefreshOnReconnect: Boolean = true

    // Observar cambios de conectividad para auto-refresh
    init {
        viewModelScope.launch {
            connectivityMonitor.isConnected
                .distinctUntilChanged()
                .collectLatest { isConnected ->
                    // Si recuperamos conexión y estábamos offline, refrescar
                    if (isConnected && _isOfflineMode.value && autoRefreshOnReconnect) {
                        log("Conexión recuperada, refrescando datos...")
                        fetchWithNetworkFallback()
                    }
                }
        }
    }

    /**
     * Cache a usar - las subclases deben proporcionar
     */
    protected abstract val cache: BaseOfflineCache<T>

    /**
     * Función para obtener datos de la red
     * @return Result con lista de items o error
     */
    protected abstract suspend fun fetchFromNetwork(): Result<List<T>>

    /**
     * Obtiene datos con estrategia de fallback automático:
     * 1. Intenta red si hay conectividad
     * 2. Guarda en cache si éxito
     * 3. Fallback a cache si falla red
     * 4. Reporta modo offline
     */
    fun fetch() {
        viewModelScope.launch {
            _state.value = ResultState.Loading

            val hasNetwork = connectivityMonitor.isNetworkAvailable()

            if (hasNetwork) {
                fetchWithNetworkFallback()
            } else {
                loadFromCache(forceOffline = true)
            }
        }
    }

    /**
     * Intenta fetch de red con fallback a cache
     */
    private suspend fun fetchWithNetworkFallback() {
        try {
            val result = withContext(Dispatchers.IO) {
                fetchFromNetwork()
            }

            result.fold(
                onSuccess = { items ->
                    // Guardar en cache
                    cache.save(items)

                    val metadata = CacheMetadata(source = CacheSource.NETWORK)
                    _state.value = ResultState.Success(
                        data = items,
                        source = CacheSource.NETWORK,
                        metadata = metadata
                    )
                    _isOfflineMode.value = false
                    _lastUpdateTimestamp.value = System.currentTimeMillis()

                    log("Fetched ${items.size} items from network")
                },
                onFailure = { error ->
                    log("Network fetch failed: ${error.message}")
                    loadFromCache(forceOffline = true)
                }
            )
        } catch (e: Exception) {
            log("Network exception: ${e.message}")
            loadFromCache(forceOffline = true)
        }
    }

    /**
     * Carga datos del cache
     */
    protected suspend fun loadFromCache(forceOffline: Boolean = false) {
        when (val cacheResult = cache.get()) {
            is CacheResult.Success -> {
                _state.value = if (forceOffline) {
                    ResultState.Offline(
                        data = cacheResult.data,
                        metadata = cacheResult.metadata,
                        isExpired = false
                    )
                } else {
                    ResultState.Success(
                        data = cacheResult.data,
                        source = CacheSource.CACHE,
                        metadata = cacheResult.metadata
                    )
                }
                _isOfflineMode.value = forceOffline
                _lastUpdateTimestamp.value = cacheResult.metadata.timestamp
                log("Loaded ${cacheResult.data.size} items from cache")
            }

            is CacheResult.Expired -> {
                _state.value = ResultState.Offline(
                    data = cacheResult.data,
                    metadata = cacheResult.metadata,
                    isExpired = true
                )
                _isOfflineMode.value = true
                _lastUpdateTimestamp.value = cacheResult.metadata.timestamp
                log("Loaded ${cacheResult.data.size} expired items from cache")
            }

            is CacheResult.Empty -> {
                _state.value = ResultState.Error("No hay datos disponibles offline")
                _isOfflineMode.value = true
                log("Cache is empty")
            }

            is CacheResult.Error -> {
                _state.value = ResultState.Error(
                    message = "Error al cargar datos offline",
                    exception = cacheResult.exception
                )
                _isOfflineMode.value = true
            }
        }
    }

    /**
     * Fuerza un refresh de los datos
     */
    fun refresh() {
        viewModelScope.launch {
            _isRefreshing.value = true

            if (connectivityMonitor.isNetworkAvailable()) {
                fetchWithNetworkFallback()
            } else {
                // Si no hay conexión, mantener los datos actuales
                // Solo mostrar error si no había datos previos
                val currentData = _state.value.dataOrNull()
                if (currentData.isNullOrEmpty()) {
                    _state.value = ResultState.Error("No hay conexión a internet")
                }
                // Si ya hay datos, los mantenemos sin cambiar el estado
                log("Refresh sin conexión - manteniendo datos actuales")
            }

            _isRefreshing.value = false
        }
    }

    /**
     * Búsqueda en datos cacheados
     */
    suspend fun search(predicate: (T) -> Boolean): List<T> {
        return cache.search(predicate)
    }

    /**
     * Obtiene los datos actuales o lista vacía
     */
    fun getCurrentData(): List<T> {
        return _state.value.dataOrNull() ?: emptyList()
    }

    /**
     * Limpia el cache
     */
    fun clearCache() {
        viewModelScope.launch {
            cache.clear()
            _lastUpdateTimestamp.value = null
        }
    }

    /**
     * Verifica si hay datos en cache
     */
    suspend fun hasCachedData(): Boolean {
        return cache.hasData()
    }

    private fun log(message: String) {
        Log.d(TAG, "[${this::class.simpleName}] $message")
    }
}
