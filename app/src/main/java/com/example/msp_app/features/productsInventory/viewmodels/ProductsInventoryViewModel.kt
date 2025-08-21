package com.example.msp_app.features.productsInventory.viewmodels

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.msp_app.core.utils.ResultState
import com.example.msp_app.data.api.ApiProvider
import com.example.msp_app.data.api.services.productInventory.ProductInventoryApi
import com.example.msp_app.data.local.datasource.productInventory.ProductInventoryLocalDataSource
import com.example.msp_app.data.models.productInventory.ProductInventory
import com.example.msp_app.data.models.productInventory.toDomain
import com.example.msp_app.data.models.productInventory.toEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ProductsInventoryViewModel(application: Application) : AndroidViewModel(application) {
    private val localDataSource = ProductInventoryLocalDataSource(application.applicationContext)
    private val api: ProductInventoryApi get() = ApiProvider.create(ProductInventoryApi::class.java)

    private val _productInventoryState =
        MutableStateFlow<ResultState<List<ProductInventory>>>(ResultState.Idle)
    val productInventoryState: StateFlow<ResultState<List<ProductInventory>>> =
        _productInventoryState

    private val _productsLoaded = MutableStateFlow(false)
    val productsLoaded: StateFlow<Boolean> = _productsLoaded


    private val _product = MutableStateFlow<ProductInventory?>(null)
    val product: StateFlow<ProductInventory?> = _product

    fun fetchRemoteInventory() {
        viewModelScope.launch {
            _productsLoaded.value = false
            _productInventoryState.value = ResultState.Loading
            try {
                val response = withContext(Dispatchers.IO) {
                    api.getProductInventory()
                }

                val productsList = response.body

                _productInventoryState.value = ResultState.Success(productsList)
                saveProductsInventoryLocally(productsList)
            } catch (e: Exception) {
                _productInventoryState.value =
                    ResultState.Error(e.message ?: "Error")
            }
        }
    }

    private suspend fun saveProductsInventoryLocally(products: List<ProductInventory>) {
        withContext(Dispatchers.IO) {
            try {
                localDataSource.insertAll(products.map { it.toEntity() })
                _productsLoaded.value = true
            } catch (e: Exception) {
                Log.e("ProductsInventoryViewModel", "Error saving products locally: ${e.message}")
            }
        }
    }

    fun loadLocalProductsInventory() {
        viewModelScope.launch {
            _productInventoryState.value = ResultState.Loading
            try {
                val localProducts = withContext(Dispatchers.IO) {
                    localDataSource.getAll().map { it.toDomain() }
                }
                _productInventoryState.value = ResultState.Success(localProducts)
            } catch (e: Exception) {
                _productInventoryState.value =
                    ResultState.Error(e.message ?: "Error loading local inventory")
            }
        }
    }
}
