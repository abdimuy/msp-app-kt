package com.example.msp_app.features.warehouses

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.msp_app.core.utils.ResultState
import com.example.msp_app.data.api.ApiProvider
import com.example.msp_app.data.api.services.warehouses.AddProductRequest
import com.example.msp_app.data.api.services.warehouses.WarehouseResponse
import com.example.msp_app.data.api.services.warehouses.WarehousesApi
import com.example.msp_app.data.local.datasource.warehouseRemoteDataSource.WarehouseRemoteDataSource
import com.example.msp_app.data.local.repository.WarehouseRepository
import com.example.msp_app.data.models.productInventory.ProductInventory
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class WarehouseViewModel(application: Application) : AndroidViewModel(application) {

    private val warehousesApi: WarehousesApi = ApiProvider.create(WarehousesApi::class.java)
    private val remoteDataSource = WarehouseRemoteDataSource(warehousesApi)
    private val repository = WarehouseRepository(remoteDataSource)

    private val _warehouseProducts =
        MutableStateFlow<ResultState<WarehouseResponse>>(ResultState.Idle)
    val warehouseProducts: StateFlow<ResultState<WarehouseResponse>> = _warehouseProducts

    private val _saveCartState = MutableStateFlow<ResultState<String>>(ResultState.Idle)
    val saveCartState: StateFlow<ResultState<String>> = _saveCartState

    var warehouseId = 11374

    fun getWarehouseProducts() {
        viewModelScope.launch {
            _warehouseProducts.value = ResultState.Loading
            repository.getWarehouseProducts(warehouseId).fold(
                onSuccess = { response ->
                    _warehouseProducts.value = ResultState.Success(response)
                },
                onFailure = { exception ->
                    _warehouseProducts.value =
                        ResultState.Error(exception.message ?: "Error desconocido")
                }
            )
        }
    }

    fun sendCartToWarehouseServer(cartItems: List<Pair<ProductInventory, Int>>) {
        viewModelScope.launch {
            _saveCartState.value = ResultState.Loading
            try {
                val productsToSave = cartItems.map { (product, quantity) ->
                    AddProductRequest(
                        ALMACEN_ID = warehouseId,
                        ARTICULO = product.ARTICULO,
                        EXISTENCIAS = quantity
                    )
                }

                repository.postProductsToWarehouse(productsToSave).fold(
                    onSuccess = { responses ->
                        _saveCartState.value = ResultState.Success("Carrito guardado exitosamente")
                    },
                    onFailure = { exception ->
                        _saveCartState.value =
                            ResultState.Error(exception.message ?: "Error al guardar carrito")
                    }
                )
            } catch (e: Exception) {
                _saveCartState.value = ResultState.Error("Error inesperado: ${e.message}")
            }
        }
    }


    fun resetSaveCartState() {
        _saveCartState.value = ResultState.Idle
    }

    fun getWarehouseProductsForCart(): List<Pair<ProductInventory, Int>> {
        return when (val state = _warehouseProducts.value) {
            is ResultState.Success -> {
                state.data.body.ARTICULOS.map { product ->
                    product to product.EXISTENCIAS
                }
            }

            else -> emptyList()
        }
    }
}