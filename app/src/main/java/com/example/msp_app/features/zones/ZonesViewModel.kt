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
        loadClientZones()
    }

    fun loadClientZones(forceRefresh: Boolean = false) {
        viewModelScope.launch {
            _clientZones.value = ResultState.Loading

            val hasNetwork = isNetworkAvailable()

            if (hasNetwork) {
                try {
                    repository.getClientZones().fold(
                        onSuccess = { response ->

                            if (!response.error.isNullOrBlank() && response.body.isEmpty()) {
                                loadFromCache()
                            } else {
                                _clientZones.value = ResultState.Success(response)
                                _isOfflineMode.value = false

                                saveZonesToCache(response.body)
                            }
                        },
                        onFailure = { exception ->
                            loadFromCache()
                        }
                    )
                } catch (e: Exception) {
                    loadFromCache()
                }
            } else {
                loadFromCache()
            }
        }
    }

    private suspend fun loadFromCache() {
        _isOfflineMode.value = true

        try {
            val cachedZones = withContext(Dispatchers.IO) {
                zonesCache.getZones().map { it.toDomain() }
            }

            if (cachedZones.isNotEmpty()) {
                val zonesResponse = ZonesResponse(
                    body = cachedZones,
                    error = null
                )
                _clientZones.value = ResultState.Success(zonesResponse)
            } else {
                val errorMsg =
                    "No hay zonas disponibles offline. Conéctate a internet para cargar las zonas."
                android.util.Log.w("ZonesViewModel", "⚠️ $errorMsg")
                _clientZones.value = ResultState.Error(errorMsg)
            }
        } catch (e: Exception) {
            _clientZones.value = ResultState.Error("Error cargando zonas offline: ${e.message}")
        }
    }

    private suspend fun saveZonesToCache(zones: List<ClientZone>) {
        try {
            withContext(Dispatchers.IO) {
                val entities = zones.map { it.toEntity() }
                zonesCache.saveZones(entities)
                loadLastUpdateTimestamp()
            }
        } catch (e: Exception) {

        }
    }

    fun clearCache() {
        viewModelScope.launch {
            zonesCache.clearCache()
            _lastUpdateTimestamp.value = null
        }
    }

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
            false
        }
    }

    private fun loadLastUpdateTimestamp() {
        viewModelScope.launch {
            val timestamp = zonesCache.getLastUpdateTimestamp()
            _lastUpdateTimestamp.value = timestamp
        }
    }

    suspend fun isCacheAvailable(): Boolean {
        return zonesCache.isCacheAvailable()
    }

    fun getActiveZones(): List<ClientZone> {
        return when (val state = _clientZones.value) {
            is ResultState.Success -> state.data.body
            else -> emptyList()
        }
    }
}