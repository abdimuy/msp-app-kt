package com.example.msp_app.features.warehouses

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.msp_app.core.utils.Constants.ALMACEN_GENERAL_ID
import com.example.msp_app.core.utils.ResultState
import com.example.msp_app.data.api.ApiProvider
import com.example.msp_app.data.api.services.warehouses.TransferDetail
import com.example.msp_app.data.api.services.warehouses.TransferRequest
import com.example.msp_app.data.api.services.warehouses.WarehouseListResponse
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

    private val _warehouseList =
        MutableStateFlow<List<WarehouseListResponse.Warehouse>>(emptyList())
    val warehouseList: StateFlow<List<WarehouseListResponse.Warehouse>> = _warehouseList

    private val _saveCartState = MutableStateFlow<ResultState<String>>(ResultState.Idle)
    val saveCartState: StateFlow<ResultState<String>> = _saveCartState

    private val _transferState = MutableStateFlow<ResultState<String>>(ResultState.Idle)
    val transferState: StateFlow<ResultState<String>> = _transferState

    var selectedWarehouseId: Int? = null

    fun loadAllWarehouses() {
        viewModelScope.launch {
            repository.getAllWarehouses().fold(
                onSuccess = { list ->
                    _warehouseList.value = list.filter { it.ALMACEN_ID != ALMACEN_GENERAL_ID }
                },
                onFailure = {
                    _warehouseList.value = emptyList()
                }
            )
        }
    }

    fun selectWarehouse(id: Int) {
        selectedWarehouseId = id
        getWarehouseProducts()
    }

    fun getWarehouseProducts() {
        val warehouseId = selectedWarehouseId ?: return
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

    fun resetSaveCartState() {
        _saveCartState.value = ResultState.Idle
    }

    fun createTransfer(
        originWarehouseId: Int,
        destinationWarehouseId: Int,
        products: List<Pair<ProductInventory, Int>>,
        description: String = "Traspaso entre almacenes"
    ) {
        viewModelScope.launch {
            _transferState.value = ResultState.Loading
            try {
                val transferDetails = products.map { (product, quantity) ->
                    TransferDetail(
                        articuloId = product.ARTICULO_ID,
                        unidades = quantity
                    )
                }

                val transferRequest = TransferRequest(
                    almacenOrigenId = originWarehouseId,
                    almacenDestinoId = destinationWarehouseId,
                    descripcion = description,
                    detalles = transferDetails
                )

                repository.createTransfer(transferRequest).fold(
                    onSuccess = { response ->
                        _transferState.value =
                            ResultState.Success("Traspaso realizado exitosamente")
                        if (selectedWarehouseId == originWarehouseId || selectedWarehouseId == destinationWarehouseId) {
                            getWarehouseProducts()
                        }
                    },
                    onFailure = { exception ->
                        _transferState.value =
                            ResultState.Error(exception.message ?: "Error en el traspaso")
                    }
                )
            } catch (e: Exception) {
                _transferState.value = ResultState.Error("Error inesperado: ${e.message}")
            }
        }
    }

    fun resetTransferState() {
        _transferState.value = ResultState.Idle
    }
}