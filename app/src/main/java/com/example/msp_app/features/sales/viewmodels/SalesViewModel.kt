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
import com.example.msp_app.data.local.datasource.product.ProductsLocalDataSource
import com.example.msp_app.data.models.sale.toDomain
import com.example.msp_app.data.models.sale.toEntity
import com.example.msp_app.data.local.datasource.sale.SalesLocalDataSource
import com.example.msp_app.data.models.product.toEntity
import com.example.msp_app.data.models.payment.toEntity
import android.util.Log



class SalesViewModel(application: Application) : AndroidViewModel(application) {

    private val api = ApiProvider.create(SalesApi::class.java)
    private val saleStore = SalesLocalDataSource(application.applicationContext)
    private val productStore = ProductsLocalDataSource(application.applicationContext)
    private val paymentStore = PaymentLocalDataSource(application.applicationContext)


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
            Log.d("SalesViewModel", "syncSales() iniciada")
            _salesState.value = ResultState.Loading
            Log.d("SalesViewModel", "Datos recibidos de API")
            try {
                val salesData  = api.getAll()

                val ventas = salesData.body.ventas
                val productos = salesData.body.productos
                val pagos = salesData.body.pagos

                saleStore.saveAll(ventas.map { it.toEntity()})
                productStore.saveAll(productos.map { it.toEntity()})
                paymentStore.saveAll(pagos.map { it.toEntity()})

                Log.d("SalesViewModel", "Datos guardados localmente en la BD")

                _salesState.value = ResultState.Success(ventas)
                println("Ventas: ${ventas.size}, Productos: ${productos.size}, Pagos: ${pagos.size} sincronizados localmente")

            } catch (e: Exception) {
                Log.e("SalesViewModel", "Error en syncSales: ${e.message}", e)
                if (_salesState.value !is ResultState.Success) {
                    _salesState.value = ResultState.Error(e.message ?: "Error al cargar ventas")
                }
            }
        }
    }
}
