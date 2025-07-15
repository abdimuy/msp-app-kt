package com.example.msp_app.features.sales.viewmodels

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.msp_app.core.utils.ResultState
import com.example.msp_app.data.api.ApiProvider
import com.example.msp_app.data.api.services.sales.SalesApi
import com.example.msp_app.data.local.datasource.payment.PaymentsLocalDataSource
import com.example.msp_app.data.local.datasource.product.ProductsLocalDataSource
import com.example.msp_app.data.local.datasource.sale.SalesLocalDataSource
import com.example.msp_app.data.local.datasource.visit.VisitsLocalDataSource
import com.example.msp_app.data.models.payment.PaymentLocationsGroup
import com.example.msp_app.data.models.payment.toEntity
import com.example.msp_app.data.models.product.toEntity
import com.example.msp_app.data.models.sale.Sale
import com.example.msp_app.data.models.sale.toDomain
import com.example.msp_app.data.models.sale.toEntity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch


class SalesViewModel(application: Application) : AndroidViewModel(application) {
    private val api = ApiProvider.create(SalesApi::class.java)
    private val saleStore = SalesLocalDataSource(application.applicationContext)
    private val productStore = ProductsLocalDataSource(application.applicationContext)
    private val paymentStore = PaymentsLocalDataSource(application.applicationContext)
    private val visitsStore = VisitsLocalDataSource(application.applicationContext)

    private val _salesState = MutableStateFlow<ResultState<List<Sale>>>(ResultState.Idle)
    val salesState: StateFlow<ResultState<List<Sale>>> = _salesState

    private val _syncSalesState =
        MutableStateFlow<ResultState<List<Sale>>>(ResultState.Idle)
    val syncSalesState: StateFlow<ResultState<List<Sale>>> = _syncSalesState

    private val _paymentsLocationsState =
        MutableStateFlow<ResultState<List<PaymentLocationsGroup>>>(ResultState.Idle)
    val paymentsLocationsState: StateFlow<ResultState<List<PaymentLocationsGroup>>> =
        _paymentsLocationsState

    fun getLocalSales() {
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

    fun syncSales(zona: Int, dateInit: String) {
        viewModelScope.launch {
            _syncSalesState.value = ResultState.Loading
            try {
                val pendingPayments = paymentStore.getPendingPayments()
                if (pendingPayments.isNotEmpty()) {
                    _syncSalesState.value =
                        ResultState.Error("Hay ${pendingPayments.size} pagos pendientes")
                    return@launch
                }
                val pendingVisits = visitsStore.getPendingVisits()
                if (pendingVisits.isNotEmpty()) {
                    _syncSalesState.value =
                        ResultState.Error("Hay ${pendingVisits.size} visitas pendientes")
                    return@launch
                }

                val salesData = api.getAll(
                    zona = zona,
                    dateInit = dateInit
                )

                val sales = salesData.body.ventas
                val products = salesData.body.productos
                val payments = salesData.body.pagos

                saleStore.saveAll(sales.map { it.toEntity() })
                productStore.saveAll(products.map { it.toEntity() })
                paymentStore.saveAll(payments.map { it.toEntity() })
                visitsStore.deleteAllVisits()

                _syncSalesState.value = ResultState.Success(sales)

            } catch (e: Exception) {
                if (_syncSalesState.value !is ResultState.Success) {
                    _syncSalesState.value = ResultState.Error(e.message ?: "Error al cargar ventas")
                }
            }
        }
    }
}
