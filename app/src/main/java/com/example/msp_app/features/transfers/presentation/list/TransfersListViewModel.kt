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
import com.example.msp_app.data.models.auth.User
import com.example.msp_app.data.models.productInventory.ProductInventory
import com.example.msp_app.data.repository.UsersRepository
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

    private val usersRepository = UsersRepository()

    private val _warehousesState = MutableStateFlow<ResultState<List<WarehouseListResponse.Warehouse>>>(ResultState.Idle)
    val warehousesState: StateFlow<ResultState<List<WarehouseListResponse.Warehouse>>> = _warehousesState.asStateFlow()

    private val _warehouseProductsState = MutableStateFlow<ResultState<List<ProductInventory>>>(ResultState.Idle)
    val warehouseProductsState: StateFlow<ResultState<List<ProductInventory>>> = _warehouseProductsState.asStateFlow()

    private val _usersByWarehouse = MutableStateFlow<Map<Int, List<User>>>(emptyMap())
    val usersByWarehouse: StateFlow<Map<Int, List<User>>> = _usersByWarehouse.asStateFlow()

    init {
        loadWarehouses()
        loadUsers()
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
     * Load users and group them by warehouse
     */
    private fun loadUsers() {
        viewModelScope.launch {
            try {
                val result = usersRepository.getAllUsers()
                result.fold(
                    onSuccess = { users ->
                        // Group users by CAMIONETA_ASIGNADA, filtering out nulls
                        val groupedUsers = users
                            .mapNotNull { user ->
                                user.CAMIONETA_ASIGNADA?.let { warehouseId ->
                                    warehouseId to user
                                }
                            }
                            .groupBy({ it.first }, { it.second })

                        _usersByWarehouse.value = groupedUsers
                    },
                    onFailure = { error ->
                        android.util.Log.e("TransfersListViewModel", "Error loading users: ${error.message}")
                        _usersByWarehouse.value = emptyMap()
                    }
                )
            } catch (e: Exception) {
                android.util.Log.e("TransfersListViewModel", "Exception loading users", e)
                _usersByWarehouse.value = emptyMap()
            }
        }
    }

    /**
     * Refresh warehouses and users
     */
    fun refreshWarehouses() {
        loadWarehouses()
        loadUsers()
    }

    /**
     * Get users assigned to a specific warehouse
     */
    fun getUsersForWarehouse(warehouseId: Int): List<User> {
        return _usersByWarehouse.value[warehouseId] ?: emptyList()
    }

    /**
     * Load products for a specific warehouse
     */
    fun loadWarehouseProducts(warehouseId: Int) {
        viewModelScope.launch {
            _warehouseProductsState.value = ResultState.Loading
            try {
                val result = warehouseRepository.getWarehouseProducts(warehouseId)
                result.fold(
                    onSuccess = { response ->
                        _warehouseProductsState.value = ResultState.Success(response.body.ARTICULOS)
                    },
                    onFailure = { error ->
                        _warehouseProductsState.value = ResultState.Error(error.message ?: "Error al cargar productos")
                    }
                )
            } catch (e: Exception) {
                _warehouseProductsState.value = ResultState.Error(e.message ?: "Error al cargar productos")
            }
        }
    }

    /**
     * Reset products state
     */
    fun resetProductsState() {
        _warehouseProductsState.value = ResultState.Idle
    }
}
