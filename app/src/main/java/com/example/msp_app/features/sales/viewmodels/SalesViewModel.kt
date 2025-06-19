package com.example.msp_app.features.sales.viewmodels

import androidx.lifecycle.viewModelScope
import com.example.msp_app.core.utils.ResultState
import com.example.msp_app.data.api.ApiProvider
import com.example.msp_app.data.api.services.sales.SalesApi
import com.example.msp_app.data.models.sale.Sale
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.example.msp_app.data.local.datasource.payment.PaymentLocalDataSource
import com.example.msp_app.data.models.sale.toDomain
import com.example.msp_app.data.models.sale.toEntity
import com.example.msp_app.data.local.datasource.sale.SalesLocalDataSource
import com.example.msp_app.data.local.datasource.product.ProductsLocalDataSource
import com.example.msp_app.data.local.entities.PaymentEntity
import com.example.msp_app.data.local.entities.ProductEntity

class SalesViewModel(application: Application) : AndroidViewModel(application) {

    private val api = ApiProvider.create(SalesApi::class.java)
    private val saleStore = SalesLocalDataSource(application.applicationContext)

    private val _salesState = MutableStateFlow<ResultState<List<Sale>>>(ResultState.Idle)
    val salesState: StateFlow<ResultState<List<Sale>>> = _salesState

    fun loadLocalSales() {
        viewModelScope.launch {
            _salesState.value = ResultState.Loading
            try {
                val cached = saleStore.getAll().map { it.toDomain() }
                _salesState.value = ResultState.Success(cached)
            } catch (e: Exception) {
                _salesState.value = ResultState.Error(e.message ?: "Error leyendo ventas locales")
            }
        }
    }


    fun syncSales() {
        viewModelScope.launch {
            _salesState.value = ResultState.Loading

            try {
                val sales = api.getAll().body.ventas

                saleStore.saveAll(sales.map { it.toEntity() })

                _salesState.value = ResultState.Success(sales)
                println("Ventas cargadas desde API: ${sales.size}")

            } catch (e: Exception) {
                if (_salesState.value !is ResultState.Success) {
                    _salesState.value = ResultState.Error(e.message ?: "Error al cargar ventas")
                }
            }
        }
    }
}

class ProductsViewModel(application: Application) : AndroidViewModel(application) {
    private val productStore = ProductsLocalDataSource(application.applicationContext)

    private val _productState = MutableStateFlow<ResultState<String>>(ResultState.Idle)
    val productState: StateFlow<ResultState<String>> = _productState

    fun insertProductsLocal(products: List<ProductEntity>) {
        viewModelScope.launch {
            _productState.value = ResultState.Loading

            try {
                productStore.saveAll(products)

                _productState.value = ResultState.Success("Productos insertados correctamente")

            } catch (e: Exception) {
                _productState.value = ResultState.Error(e.message ?: "Error al guardar productos")
            }
        }
    }
}


class PaymentsViewModel(application: Application) : AndroidViewModel(application) {
    private val paymentStore = PaymentLocalDataSource(application.applicationContext)

    private val _paymentState = MutableStateFlow<ResultState<String>>(ResultState.Idle)
    val paymentState: StateFlow<ResultState<String>> = _paymentState

    fun insertPaymentsLocal(payments: List<PaymentEntity>) {
        viewModelScope.launch {
            _paymentState.value = ResultState.Loading

            try {
                paymentStore.saveAll(payments)
                _paymentState.value = ResultState.Success("Pagos insertados correctamente")
            } catch (e: Exception) {
                _paymentState.value = ResultState.Error(e.message ?: "Error al guardar pagos")
            }
        }
    }
}



