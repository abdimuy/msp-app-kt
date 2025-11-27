package com.example.msp_app.features.zones

import android.app.Application
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.msp_app.core.utils.ResultState
import com.example.msp_app.data.api.ApiProvider
import com.example.msp_app.data.api.services.zones.ZonesApi
import com.example.msp_app.data.api.services.zones.ZonesResponse
import com.example.msp_app.data.cache.ZonesCache
import com.example.msp_app.data.local.datasource.zonesRemoteDataSource.ZonesRemoteDataSource
import com.example.msp_app.data.local.repository.ZonesRepository
import com.example.msp_app.data.models.zone.ClientZone
import com.example.msp_app.data.models.zone.toDomain
import com.example.msp_app.data.models.zone.toEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ZonesViewModel(application: Application) : AndroidViewModel(application) {

    private val zonesApi: ZonesApi = ApiProvider.create(ZonesApi::class.java)
    private val remoteDataSource = ZonesRemoteDataSource(zonesApi)
    private val repository = ZonesRepository(remoteDataSource)
    private val zonesCache = ZonesCache(application.applicationContext)

    private val _clientZones = MutableStateFlow<ResultState<ZonesResponse>>(ResultState.Idle)
    val clientZones: StateFlow<ResultState<ZonesResponse>> = _clientZones

    private val _isOfflineMode = MutableStateFlow(false)
    val isOfflineMode: StateFlow<Boolean> = _isOfflineMode

    private val _lastUpdateTimestamp = MutableStateFlow<Long?>(null)
    val lastUpdateTimestamp: StateFlow<Long?> = _lastUpdateTimestamp

    init {
        loadLastUpdateTimestamp()
        // ⭐ CARGAR ZONAS AL INICIALIZAR EL VIEWMODEL
        loadClientZones()
    }

    /**
     * Carga las zonas de clientes (online u offline)
     */
    fun loadClientZones(forceRefresh: Boolean = false) {
        viewModelScope.launch {
            _clientZones.value = ResultState.Loading

            android.util.Log.d("ZonesViewModel", "🔄 Iniciando carga de zonas...")

            val hasNetwork = isNetworkAvailable()
            android.util.Log.d("ZonesViewModel", "📡 Red disponible: $hasNetwork")

            if (hasNetwork) {
                // MODO ONLINE
                try {
                    android.util.Log.d("ZonesViewModel", "🌐 Intentando cargar desde API...")

                    repository.getClientZones().fold(
                        onSuccess = { response ->
                            android.util.Log.d("ZonesViewModel", "✅ Respuesta exitosa de API")
                            android.util.Log.d(
                                "ZonesViewModel",
                                "📦 Zonas recibidas: ${response.body.size}"
                            )
                            android.util.Log.d(
                                "ZonesViewModel",
                                "📋 Error en respuesta: ${response.error}"
                            )

                            // ⭐ CORRECCIÓN: Verificar que error no sea null Y no esté vacío
                            if (!response.error.isNullOrBlank() && response.body.isEmpty()) {
                                android.util.Log.e(
                                    "ZonesViewModel",
                                    "⚠️ API retornó error: ${response.error}"
                                )
                                loadFromCache()
                            } else {
                                _clientZones.value = ResultState.Success(response)
                                _isOfflineMode.value = false

                                // Guardar en caché
                                saveZonesToCache(response.body)

                                android.util.Log.d(
                                    "ZonesViewModel",
                                    "✅ Zonas cargadas y guardadas en caché"
                                )
                            }
                        },
                        onFailure = { exception ->
                            android.util.Log.e(
                                "ZonesViewModel",
                                "❌ Error al cargar zonas desde API: ${exception.message}",
                                exception
                            )
                            android.util.Log.e(
                                "ZonesViewModel",
                                "🔍 Stack trace: ${exception.stackTraceToString()}"
                            )
                            // Si falla la API, intenta cargar desde caché
                            loadFromCache()
                        }
                    )
                } catch (e: Exception) {
                    android.util.Log.e("ZonesViewModel", "💥 Excepción inesperada: ${e.message}", e)
                    loadFromCache()
                }
            } else {
                // MODO OFFLINE
                android.util.Log.d("ZonesViewModel", "📴 Sin conexión - Cargando desde caché")
                loadFromCache()
            }
        }
    }

    /**
     * Carga las zonas desde el caché local
     */
    private suspend fun loadFromCache() {
        _isOfflineMode.value = true
        android.util.Log.d("ZonesViewModel", "💾 Intentando cargar zonas desde caché...")

        try {
            val cachedZones = withContext(Dispatchers.IO) {
                zonesCache.getZones().map { it.toDomain() }
            }

            android.util.Log.d("ZonesViewModel", "📂 Zonas en caché: ${cachedZones.size}")

            if (cachedZones.isNotEmpty()) {
                val zonesResponse = ZonesResponse(
                    body = cachedZones,
                    error = null
                )
                _clientZones.value = ResultState.Success(zonesResponse)
                android.util.Log.d("ZonesViewModel", "✅ Zonas cargadas desde caché exitosamente")
            } else {
                val errorMsg =
                    "No hay zonas disponibles offline. Conéctate a internet para cargar las zonas."
                android.util.Log.w("ZonesViewModel", "⚠️ $errorMsg")
                _clientZones.value = ResultState.Error(errorMsg)
            }
        } catch (e: Exception) {
            android.util.Log.e(
                "ZonesViewModel",
                "❌ Error cargando zonas desde caché: ${e.message}",
                e
            )
            _clientZones.value = ResultState.Error("Error cargando zonas offline: ${e.message}")
        }
    }

    /**
     * Guarda las zonas en el caché local
     */
    private suspend fun saveZonesToCache(zones: List<ClientZone>) {
        try {
            withContext(Dispatchers.IO) {
                val entities = zones.map { it.toEntity() }
                zonesCache.saveZones(entities)
                loadLastUpdateTimestamp()
                android.util.Log.d("ZonesViewModel", "💾 Guardadas ${entities.size} zonas en caché")
            }
        } catch (e: Exception) {
            android.util.Log.e(
                "ZonesViewModel",
                "❌ Error guardando zonas en caché: ${e.message}",
                e
            )
        }
    }

    /**
     * Limpia el caché de zonas
     */
    fun clearCache() {
        viewModelScope.launch {
            zonesCache.clearCache()
            _lastUpdateTimestamp.value = null
            android.util.Log.d("ZonesViewModel", "🗑️ Caché de zonas limpiado")
        }
    }

    /**
     * Verifica si hay conexión a internet
     */
    private fun isNetworkAvailable(): Boolean {
        return try {
            val connectivityManager = getApplication<Application>()
                .getSystemService(ConnectivityManager::class.java)
            val network = connectivityManager?.activeNetwork ?: return false
            val networkCapabilities =
                connectivityManager.getNetworkCapabilities(network) ?: return false

            when {
                networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> true
                networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> true
                networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> true
                else -> false
            }
        } catch (e: Exception) {
            android.util.Log.e("ZonesViewModel", "Error verificando red: ${e.message}")
            false
        }
    }

    /**
     * Carga el timestamp de la última actualización
     */
    private fun loadLastUpdateTimestamp() {
        viewModelScope.launch {
            val timestamp = zonesCache.getLastUpdateTimestamp()
            _lastUpdateTimestamp.value = timestamp
            android.util.Log.d(
                "ZonesViewModel",
                "⏰ Última actualización: ${timestamp?.let { java.util.Date(it) }}"
            )
        }
    }

    /**
     * Verifica si el caché está disponible
     */
    suspend fun isCacheAvailable(): Boolean {
        return zonesCache.isCacheAvailable()
    }

    /**
     * Obtiene las zonas activas
     */
    fun getActiveZones(): List<ClientZone> {
        return when (val state = _clientZones.value) {
            is ResultState.Success -> state.data.body
            else -> emptyList()
        }
    }
}