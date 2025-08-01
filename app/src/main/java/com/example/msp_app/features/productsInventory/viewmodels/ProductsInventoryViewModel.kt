package com.example.msp_app.features.productsInventory.viewmodels

import android.app.Application
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

    fun fetchRemoteInventory() {
        viewModelScope.launch {
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

    fun saveProductsInventoryLocally(products: List<ProductInventory>) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                localDataSource.deleteAll()
                localDataSource.insertAll(products.map { it.toEntity() })
            } catch (e: Exception) {
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
