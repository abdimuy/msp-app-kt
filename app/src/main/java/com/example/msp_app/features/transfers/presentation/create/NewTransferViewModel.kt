package com.example.msp_app.features.transfers.presentation.create

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.msp_app.core.utils.ResultState
import com.example.msp_app.data.api.ApiProvider
import com.example.msp_app.data.api.services.warehouses.WarehouseListResponse
import com.example.msp_app.data.api.services.warehouses.WarehousesApi
import com.example.msp_app.data.local.datasource.warehouseRemoteDataSource.WarehouseRemoteDataSource
import com.example.msp_app.data.local.repository.WarehouseRepository
import com.example.msp_app.data.models.productInventory.ProductInventory
import com.example.msp_app.features.transfers.data.api.TransfersApiService
import com.example.msp_app.features.transfers.data.repository.TransfersRepository
import com.example.msp_app.features.transfers.domain.models.CreateTransferData
import com.example.msp_app.features.transfers.domain.models.ProductCost
import com.example.msp_app.features.transfers.domain.models.TransferProductItem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.LocalDateTime

/**
 * ViewModel for creating new transfers
 * Manages wizard state and product selection
 */
class NewTransferViewModel(
    application: Application
) : AndroidViewModel(application) {

    private val repository: TransfersRepository by lazy {
        val apiService = ApiProvider.create(TransfersApiService::class.java)
        TransfersRepository(apiService)
    }

    private val warehouseRepository: WarehouseRepository by lazy {
        val warehousesApi = ApiProvider.create(WarehousesApi::class.java)
        val remoteDataSource = WarehouseRemoteDataSource(warehousesApi)
        WarehouseRepository(remoteDataSource)
    }

    // ===== Wizard State =====
    private val _currentStep = MutableStateFlow(TransferStep.WAREHOUSES)
    val currentStep: StateFlow<TransferStep> = _currentStep.asStateFlow()

    // ===== Warehouses State =====
    private val _warehousesState = MutableStateFlow<ResultState<List<WarehouseListResponse.Warehouse>>>(ResultState.Idle)
    val warehousesState: StateFlow<ResultState<List<WarehouseListResponse.Warehouse>>> = _warehousesState.asStateFlow()

    private val _selectedSourceWarehouse = MutableStateFlow<WarehouseListResponse.Warehouse?>(null)
    val selectedSourceWarehouse: StateFlow<WarehouseListResponse.Warehouse?> = _selectedSourceWarehouse.asStateFlow()
    val sourceWarehouse: StateFlow<WarehouseListResponse.Warehouse?> = selectedSourceWarehouse

    private val _selectedDestinationWarehouse = MutableStateFlow<WarehouseListResponse.Warehouse?>(null)
    val selectedDestinationWarehouse: StateFlow<WarehouseListResponse.Warehouse?> = _selectedDestinationWarehouse.asStateFlow()
    val destinationWarehouse: StateFlow<WarehouseListResponse.Warehouse?> = selectedDestinationWarehouse

    private val _description = MutableStateFlow("")
    val description: StateFlow<String> = _description.asStateFlow()

    // ===== Products State =====
    private val _availableProductsState = MutableStateFlow<ResultState<List<ProductInventory>>>(ResultState.Idle)
    val availableProductsState: StateFlow<ResultState<List<ProductInventory>>> = _availableProductsState.asStateFlow()

    private val _selectedProducts = MutableStateFlow<List<SelectedProduct>>(emptyList())
    val selectedProducts: StateFlow<List<SelectedProduct>> = _selectedProducts.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    // ===== Product Costs State =====
    private val _productCostsState = MutableStateFlow<ResultState<List<ProductCost>>>(ResultState.Idle)
    val productCostsState: StateFlow<ResultState<List<ProductCost>>> = _productCostsState.asStateFlow()

    // ===== Create Transfer State =====
    private val _createTransferState = MutableStateFlow<ResultState<Int>>(ResultState.Idle)
    val createTransferState: StateFlow<ResultState<Int>> = _createTransferState.asStateFlow()

    // ===== Validation State =====
    private val _validationError = MutableStateFlow<String?>(null)
    val validationError: StateFlow<String?> = _validationError.asStateFlow()

    init {
        loadWarehouses()
    }

    // ===== Warehouse Operations =====

    fun loadWarehouses() {
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

    fun selectSourceWarehouse(warehouse: WarehouseListResponse.Warehouse) {
        _selectedSourceWarehouse.value = warehouse
        _validationError.value = null

        // Reset destination if it's the same as source
        if (_selectedDestinationWarehouse.value?.ALMACEN_ID == warehouse.ALMACEN_ID) {
            _selectedDestinationWarehouse.value = null
        }

        // Auto-select destination warehouse with lowest ID (excluding source)
        if (_selectedDestinationWarehouse.value == null) {
            val currentState = _warehousesState.value
            if (currentState is ResultState.Success) {
                val lowestIdWarehouse = currentState.data
                    .filter { it.ALMACEN_ID != warehouse.ALMACEN_ID }
                    .minByOrNull { it.ALMACEN_ID }

                lowestIdWarehouse?.let {
                    _selectedDestinationWarehouse.value = it
                }
            }
        }
    }

    fun selectDestinationWarehouse(warehouse: WarehouseListResponse.Warehouse) {
        if (warehouse.ALMACEN_ID == _selectedSourceWarehouse.value?.ALMACEN_ID) {
            _validationError.value = "El almacén destino no puede ser igual al origen"
            return
        }

        _selectedDestinationWarehouse.value = warehouse
        _validationError.value = null
    }

    fun updateDescription(text: String) {
        _description.value = text
    }

    // ===== Products Operations =====

    fun loadProducts() {
        val sourceWarehouseId = _selectedSourceWarehouse.value?.ALMACEN_ID ?: return

        viewModelScope.launch {
            _availableProductsState.value = ResultState.Loading
            try {
                val result = warehouseRepository.getWarehouseProducts(sourceWarehouseId)
                result.fold(
                    onSuccess = { response ->
                        _availableProductsState.value = ResultState.Success(response.body.ARTICULOS)
                    },
                    onFailure = { error ->
                        _availableProductsState.value = ResultState.Error(error.message ?: "Error al cargar productos")
                    }
                )
            } catch (e: Exception) {
                _availableProductsState.value = ResultState.Error(e.message ?: "Error al cargar productos")
            }
        }
    }

    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun getFilteredProducts(): List<ProductInventory> {
        val currentState = _availableProductsState.value
        if (currentState !is ResultState.Success) return emptyList()

        val products = currentState.data
        val query = _searchQuery.value

        if (query.isBlank()) return products

        return products.filter { product ->
            product.ARTICULO.contains(query, ignoreCase = true) ||
                    product.ARTICULO_ID.toString().contains(query)
        }
    }

    fun addProduct(product: ProductInventory, units: Int) {
        if (units <= 0) {
            _validationError.value = "La cantidad debe ser mayor a 0"
            return
        }

        val currentList = _selectedProducts.value.toMutableList()
        val existingIndex = currentList.indexOfFirst { it.product.ARTICULO_ID == product.ARTICULO_ID }

        if (existingIndex != -1) {
            // Update existing product
            currentList[existingIndex] = currentList[existingIndex].copy(units = units)
        } else {
            // Add new product
            currentList.add(
                SelectedProduct(
                    product = product,
                    units = units,
                    costUnitario = null
                )
            )
        }

        _selectedProducts.value = currentList
        _validationError.value = null

        // Load costs for the new product list
        loadProductCosts()
    }

    fun removeProduct(productId: Int) {
        _selectedProducts.value = _selectedProducts.value.filter {
            it.product.ARTICULO_ID != productId
        }

        // Reload costs
        if (_selectedProducts.value.isNotEmpty()) {
            loadProductCosts()
        }
    }

    fun updateProductUnits(productId: Int, units: Int) {
        if (units <= 0) {
            removeProduct(productId)
            return
        }

        _selectedProducts.value = _selectedProducts.value.map { selectedProduct ->
            if (selectedProduct.product.ARTICULO_ID == productId) {
                selectedProduct.copy(units = units)
            } else {
                selectedProduct
            }
        }
    }

    fun loadProductCosts() {
        val sourceWarehouse = _selectedSourceWarehouse.value ?: return
        val products = _selectedProducts.value

        if (products.isEmpty()) return

        viewModelScope.launch {
            _productCostsState.value = ResultState.Loading
            try {
                val productIds = products.map { it.product.ARTICULO_ID }
                val result = repository.getProductCosts(sourceWarehouse.ALMACEN_ID, productIds)

                result.fold(
                    onSuccess = { costs ->
                        _productCostsState.value = ResultState.Success(costs)

                        // Update selected products with costs
                        _selectedProducts.value = _selectedProducts.value.map { selectedProduct ->
                            val cost = costs.find { it.articuloId == selectedProduct.product.ARTICULO_ID }
                            selectedProduct.copy(costUnitario = cost?.costoUnitario)
                        }
                    },
                    onFailure = { error ->
                        _productCostsState.value = ResultState.Error(error.message ?: "Error al cargar costos")
                    }
                )
            } catch (e: Exception) {
                _productCostsState.value = ResultState.Error(e.message ?: "Error al cargar costos")
            }
        }
    }

    // ===== Wizard Navigation =====

    fun nextStep(): Boolean {
        val currentStepValue = _currentStep.value

        when (currentStepValue) {
            TransferStep.WAREHOUSES -> {
                if (!validateWarehousesStep()) return false
                loadProducts()
                _currentStep.value = TransferStep.PRODUCTS
            }
            TransferStep.PRODUCTS -> {
                if (!validateProductsStep()) return false
                loadProductCosts()
                _currentStep.value = TransferStep.CONFIRMATION
            }
            TransferStep.CONFIRMATION -> {
                // Final step, do nothing
                return true
            }
        }

        return true
    }

    fun previousStep() {
        when (_currentStep.value) {
            TransferStep.WAREHOUSES -> {
                // First step, can't go back
            }
            TransferStep.PRODUCTS -> {
                _currentStep.value = TransferStep.WAREHOUSES
            }
            TransferStep.CONFIRMATION -> {
                _currentStep.value = TransferStep.PRODUCTS
            }
        }
    }

    fun goToStep(step: TransferStep) {
        _currentStep.value = step
    }

    // ===== Validation =====

    private fun validateWarehousesStep(): Boolean {
        if (_selectedSourceWarehouse.value == null) {
            _validationError.value = "Selecciona el almacén origen"
            return false
        }

        if (_selectedDestinationWarehouse.value == null) {
            _validationError.value = "Selecciona el almacén destino"
            return false
        }

        if (_selectedSourceWarehouse.value?.ALMACEN_ID == _selectedDestinationWarehouse.value?.ALMACEN_ID) {
            _validationError.value = "Los almacenes no pueden ser iguales"
            return false
        }

        return true
    }

    private fun validateProductsStep(): Boolean {
        if (_selectedProducts.value.isEmpty()) {
            _validationError.value = "Agrega al menos un producto"
            return false
        }

        if (_selectedProducts.value.any { it.units <= 0 }) {
            _validationError.value = "Todas las cantidades deben ser mayores a 0"
            return false
        }

        return true
    }

    // ===== Create Transfer =====

    fun createTransfer() {
        if (!validateWarehousesStep() || !validateProductsStep()) {
            return
        }

        viewModelScope.launch {
            _createTransferState.value = ResultState.Loading
            try {
                val transferData = CreateTransferData(
                    almacenOrigenId = _selectedSourceWarehouse.value!!.ALMACEN_ID,
                    almacenDestinoId = _selectedDestinationWarehouse.value!!.ALMACEN_ID,
                    fecha = LocalDateTime.now(),
                    descripcion = _description.value.ifBlank { null },
                    usuario = null, // Will use default SYSDBA from API
                    productos = _selectedProducts.value.map { selected ->
                        TransferProductItem(
                            articuloId = selected.product.ARTICULO_ID,
                            claveArticulo = null,
                            unidades = selected.units,
                            costoUnitario = selected.costUnitario
                        )
                    }
                )

                val result = repository.createTransfer(transferData)

                result.fold(
                    onSuccess = { doctoInId ->
                        _createTransferState.value = ResultState.Success(doctoInId)
                    },
                    onFailure = { error ->
                        _createTransferState.value = ResultState.Error(error.message ?: "Error al crear traspaso")
                    }
                )
            } catch (e: Exception) {
                _createTransferState.value = ResultState.Error(e.message ?: "Error al crear traspaso")
            }
        }
    }

    // ===== Summary Calculations =====

    fun getTotalUnits(): Int {
        return _selectedProducts.value.sumOf { it.units }
    }

    fun getTotalCost(): Double {
        return _selectedProducts.value.sumOf { it.calculateTotal() ?: 0.0 }
    }

    fun getProductCount(): Int {
        return _selectedProducts.value.size
    }

    // ===== Reset =====

    fun resetForm() {
        _currentStep.value = TransferStep.WAREHOUSES
        _selectedSourceWarehouse.value = null
        _selectedDestinationWarehouse.value = null
        _description.value = ""
        _selectedProducts.value = emptyList()
        _searchQuery.value = ""
        _createTransferState.value = ResultState.Idle
        _validationError.value = null
    }

    fun clearValidationError() {
        _validationError.value = null
    }
}

/**
 * Wizard steps enum
 */
enum class TransferStep {
    WAREHOUSES,
    PRODUCTS,
    CONFIRMATION
}

/**
 * Data class for selected product with units
 */
data class SelectedProduct(
    val product: ProductInventory,
    val units: Int,
    val costUnitario: Double?
) {
    fun calculateTotal(): Double? {
        return costUnitario?.let { it * units }
    }

    fun getDisplayName(): String {
        return product.ARTICULO.ifBlank {
            "Producto #${product.ARTICULO_ID}"
        }
    }
}
