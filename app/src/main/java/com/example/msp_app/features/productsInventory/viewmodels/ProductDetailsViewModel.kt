package com.example.msp_app.features.productsInventory.viewmodels

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.msp_app.core.utils.ResultState
import com.example.msp_app.data.local.datasource.productInventory.ProductInventoryLocalDataSource
import com.example.msp_app.data.models.productInventory.ProductInventory
import com.example.msp_app.data.models.productInventory.toDomain
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ProductDetailsViewModel(application: Application) : AndroidViewModel(application) {

    private val localDataSource = ProductInventoryLocalDataSource(application.applicationContext)

    private val _product = MutableStateFlow<ProductInventory?>(null)
    val product: StateFlow<ProductInventory?> = _product

    private val _productState = MutableStateFlow<ResultState<ProductInventory>>(ResultState.Idle)
    val productState: StateFlow<ResultState<ProductInventory>> = _productState

    fun loadProductById(id: Int) {
        viewModelScope.launch {
            try {
                val result = withContext(Dispatchers.IO) {
                    localDataSource.getProductInventoryById(id).toDomain()
                }
                _product.value = result
            } catch (e: Exception) {
                _product.value = null
            }
        }
    }
}
