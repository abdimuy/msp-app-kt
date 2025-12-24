package com.example.msp_app.features.zones

import android.app.Application
import com.example.msp_app.core.cache.BaseOfflineCache
import com.example.msp_app.core.viewmodel.BaseOfflineViewModel
import com.example.msp_app.data.cache.ZonesCache
import com.example.msp_app.data.models.zone.ClientZone
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * ViewModel para zonas con soporte offline completo
 *
 * Extiende BaseOfflineViewModel para reutilizar toda la lógica de:
 * - Fetch con fallback automático
 * - Estado offline
 * - Cache persistente
 * - Refresh manual
 */
class ZonesViewModel(application: Application) : BaseOfflineViewModel<ClientZone>(application) {

    private val zonesCache = ZonesCache.getInstance(application.applicationContext)
    private val repository = ZonesRepository()

    override val cache: BaseOfflineCache<ClientZone> = zonesCache

    // Zona seleccionada
    private val _selectedZone = MutableStateFlow<ClientZone?>(null)
    val selectedZone: StateFlow<ClientZone?> = _selectedZone.asStateFlow()

    override suspend fun fetchFromNetwork(): Result<List<ClientZone>> {
        return repository.getClientZones()
    }

    /**
     * Selecciona una zona
     */
    fun selectZone(zone: ClientZone) {
        _selectedZone.value = zone
    }

    /**
     * Selecciona una zona por ID
     */
    suspend fun selectZoneById(zoneId: Int) {
        val zone = zonesCache.findById(zoneId)
        _selectedZone.value = zone
    }

    /**
     * Limpia la selección
     */
    fun clearSelection() {
        _selectedZone.value = null
    }

    /**
     * Busca zonas por nombre
     */
    suspend fun searchZones(query: String): List<ClientZone> {
        return zonesCache.searchByName(query)
    }

    /**
     * Obtiene las zonas activas (todos en este caso)
     */
    fun getActiveZones(): List<ClientZone> {
        return getCurrentData()
    }

    companion object {
        @Volatile
        private var instance: ZonesViewModel? = null

        /**
         * Obtiene una instancia del ViewModel
         * Útil para acceso desde AuthViewModel
         */
        fun getInstance(application: Application): ZonesViewModel {
            return instance ?: synchronized(this) {
                instance ?: ZonesViewModel(application).also {
                    instance = it
                }
            }
        }
    }
}
