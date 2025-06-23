package com.example.msp_app.features.products.viewmodels

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.msp_app.core.utils.ResultState
import com.example.msp_app.data.local.datasource.product.ProductsLocalDataSource
import com.example.msp_app.data.models.product.Product
import com.example.msp_app.data.models.product.toDomain
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext


class ProductsViewModel(application: Application) : AndroidViewModel(application) {
    val productStore = ProductsLocalDataSource(application.applicationContext)

    private val _productsByFolioState =
        MutableStateFlow<ResultState<List<Product>>>(ResultState.Idle)
    val productsByFolioState: StateFlow<ResultState<List<Product>>> = _productsByFolioState

    fun getProductsByFolio(folio: String) {
        viewModelScope.launch {
            _productsByFolioState.value = ResultState.Loading
            try {
                val products = withContext(Dispatchers.IO) {
                    productStore.getProductsByFolio(folio).map { it.toDomain() }
                }
                _productsByFolioState.value = ResultState.Success(products)
            } catch (e: Exception) {
                _productsByFolioState.value =
                    ResultState.Error(e.message ?: "Error leyendo productos locales")
            }
        }
    }
}