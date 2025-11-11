package com.example.msp_app.features.transfers.presentation.list

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.msp_app.core.utils.ResultState
import com.example.msp_app.data.api.ApiProvider
import com.example.msp_app.data.api.services.warehouses.WarehouseListResponse
import com.example.msp_app.data.api.services.warehouses.WarehousesApi
import com.example.msp_app.data.local.datasource.warehouseRemoteDataSource.WarehouseRemoteDataSource
import com.example.msp_app.data.local.repository.WarehouseRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * ViewModel for warehouse dashboard screen
 * Manages warehouses state
 */
class TransfersListViewModel(
    application: Application
) : AndroidViewModel(application) {

    private val warehouseRepository: WarehouseRepository by lazy {
        val warehousesApi = ApiProvider.create(WarehousesApi::class.java)
        val remoteDataSource = WarehouseRemoteDataSource(warehousesApi)
        WarehouseRepository(remoteDataSource)
    }

    private val _warehousesState = MutableStateFlow<ResultState<List<WarehouseListResponse.Warehouse>>>(ResultState.Idle)
    val warehousesState: StateFlow<ResultState<List<WarehouseListResponse.Warehouse>>> = _warehousesState.asStateFlow()

    init {
        loadWarehouses()
    }

    /**
     * Load warehouses
     */
    private fun loadWarehouses() {
        viewModelScope.launch {
            _warehousesState.value = ResultState.Loading
            try {
                val result = warehouseRepository.getAllWarehouses()
                result.fold(
                    onSuccess = { warehouses ->
                        _warehousesState.value = ResultState.Success(warehouses)
                    },
                    onFailure = { error ->
                        _warehousesState.value = ResultState.Error(error.message ?: "Error al cargar almacenes")
                    }
                )
            } catch (e: Exception) {
                _warehousesState.value = ResultState.Error(e.message ?: "Error al cargar almacenes")
            }
        }
    }

    /**
     * Refresh warehouses
     */
    fun refreshWarehouses() {
        loadWarehouses()
    }
}
