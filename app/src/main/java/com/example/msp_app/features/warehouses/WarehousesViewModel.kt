package com.example.msp_app.features.warehouses

import android.app.Application
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
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
import com.example.msp_app.data.cache.ProductsCache
import com.example.msp_app.data.local.datasource.warehouseRemoteDataSource.WarehouseRemoteDataSource
import com.example.msp_app.data.local.entities.ProductInventoryEntity
import com.example.msp_app.data.local.repository.WarehouseRepository
import com.example.msp_app.data.models.productInventory.ProductInventory
import com.example.msp_app.data.models.productInventory.toDomain
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class WarehouseViewModel(application: Application) : AndroidViewModel(application) {

    private val warehousesApi: WarehousesApi = ApiProvider.create(WarehousesApi::class.java)
    private val remoteDataSource = WarehouseRemoteDataSource(warehousesApi)
    private val repository = WarehouseRepository(remoteDataSource)
    private val productsCache = ProductsCache(application.applicationContext)

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

    private val _isOfflineMode = MutableStateFlow(false)
    val isOfflineMode: StateFlow<Boolean> = _isOfflineMode

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
            
            val hasNetwork = isNetworkAvailable()
            android.util.Log.d("WarehouseViewModel", "Estado de red: ${if (hasNetwork) "ONLINE" else "OFFLINE"}")

            if (hasNetwork) {
                repository.getWarehouseProducts(warehouseId).fold(
                    onSuccess = { response ->
                        _warehouseProducts.value = ResultState.Success(response)
                        _isOfflineMode.value = false
                        saveProductsToCache(response.body.ARTICULOS)
                    },
                    onFailure = { exception ->
                        loadFromCache()
                    }
                )
            } else {
                loadFromCache()
            }
        }
    }

    private suspend fun loadFromCache() {
        _isOfflineMode.value = true
        android.util.Log.d("WarehouseViewModel", "Intentando cargar productos desde cache...")
        try {
            val cachedProducts = withContext(Dispatchers.IO) {
                productsCache.getProducts().map { it.toDomain() }
            }
            android.util.Log.d("WarehouseViewModel", "Productos en cache: ${cachedProducts.size}")

            if (cachedProducts.isNotEmpty()) {
                val warehouseResponse = WarehouseResponse(
                    body = WarehouseResponse.Body(
                        ALMACEN = WarehouseListResponse.Warehouse(
                            ALMACEN_ID = selectedWarehouseId ?: 0,
                            ALMACEN = "Cache Local",
                            EXISTENCIAS = cachedProducts.sumOf { it.EXISTENCIAS }
                        ),
                        ARTICULOS = cachedProducts
                    ),
                    error = null
                )
                _warehouseProducts.value = ResultState.Success(warehouseResponse)
            } else {
                _warehouseProducts.value = ResultState.Error("No hay productos disponibles offline")
            }
        } catch (e: Exception) {
            _warehouseProducts.value =
                ResultState.Error("Error cargando productos offline: ${e.message}")
        }
    }

    private suspend fun saveProductsToCache(products: List<ProductInventory>) {
        try {
            withContext(Dispatchers.IO) {
                val entities = products.map { product ->
                    ProductInventoryEntity(
                        ARTICULO_ID = product.ARTICULO_ID,
                        ARTICULO = product.ARTICULO ?: "Sin nombre",
                        EXISTENCIAS = product.EXISTENCIAS ?: 0,
                        LINEA_ARTICULO_ID = product.LINEA_ARTICULO_ID ?: 0,
                        LINEA_ARTICULO = product.LINEA_ARTICULO ?: "Sin categoría",
                        PRECIOS = product.PRECIOS
                    )
                }
                productsCache.saveProducts(entities)
                android.util.Log.d("WarehouseViewModel", "Guardados ${entities.size} productos en cache")
            }
        } catch (e: Exception) {
            android.util.Log.e("WarehouseViewModel", "Error guardando productos en cache", e)
        }
    }

    private fun isNetworkAvailable(): Boolean {
        val connectivityManager = getApplication<Application>().getSystemService(ConnectivityManager::class.java)
        val network = connectivityManager?.activeNetwork ?: return false
        val networkCapabilities =
            connectivityManager.getNetworkCapabilities(network) ?: return false

        return when {
            networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> true
            networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> true
            networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> true
            else -> false
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