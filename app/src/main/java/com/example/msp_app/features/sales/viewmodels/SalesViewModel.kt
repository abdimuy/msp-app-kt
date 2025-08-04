package com.example.msp_app.features.sales.viewmodels

import android.app.Application
import android.content.Context
import android.util.Log
import androidx.core.content.edit
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.msp_app.core.utils.ResultState
import com.example.msp_app.data.api.ApiProvider
import com.example.msp_app.data.api.services.sales.SalesApi
import com.example.msp_app.data.local.datasource.guarantee.GuaranteesLocalDataSource
import com.example.msp_app.data.local.datasource.payment.PaymentsLocalDataSource
import com.example.msp_app.data.local.datasource.product.ProductsLocalDataSource
import com.example.msp_app.data.local.datasource.sale.SalesLocalDataSource
import com.example.msp_app.data.local.datasource.visit.VisitsLocalDataSource
import com.example.msp_app.data.local.entities.OverduePaymentsEntity
import com.example.msp_app.data.models.guarantee.toEntity
import com.example.msp_app.data.models.payment.PaymentLocationsGroup
import com.example.msp_app.data.models.payment.toEntity
import com.example.msp_app.data.models.product.toEntity
import com.example.msp_app.data.models.sale.FrecuenciaPago
import com.example.msp_app.data.models.sale.Sale
import com.example.msp_app.data.models.sale.SaleWithProducts
import com.example.msp_app.data.models.sale.toDomain
import com.example.msp_app.data.models.sale.toEntity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale


class SalesViewModel(application: Application) : AndroidViewModel(application) {
    private val api: SalesApi get() = ApiProvider.create(SalesApi::class.java)
    private val saleStore = SalesLocalDataSource(application.applicationContext)
    private val productStore = ProductsLocalDataSource(application.applicationContext)
    private val paymentStore = PaymentsLocalDataSource(application.applicationContext)
    private val visitsStore = VisitsLocalDataSource(application.applicationContext)
    private val guaranteeStore = GuaranteesLocalDataSource(application.applicationContext)

    private val _salesState =
        MutableStateFlow<ResultState<List<SaleWithProducts>>>(ResultState.Idle)
    val salesState: StateFlow<ResultState<List<SaleWithProducts>>> = _salesState

    private val _syncSalesState =
        MutableStateFlow<ResultState<List<Sale>>>(ResultState.Idle)
    val syncSalesState: StateFlow<ResultState<List<Sale>>> = _syncSalesState

    private val _paymentsLocationsState =
        MutableStateFlow<ResultState<List<PaymentLocationsGroup>>>(ResultState.Idle)
    val paymentsLocationsState: StateFlow<ResultState<List<PaymentLocationsGroup>>> =
        _paymentsLocationsState

    private val _salesByClientState =
        MutableStateFlow<ResultState<List<SaleWithProducts>>>(ResultState.Idle)
    val salesByClientState: StateFlow<ResultState<List<SaleWithProducts>>> = _salesByClientState

    private val _overduePaymentsState =
        MutableStateFlow<ResultState<List<OverduePaymentsEntity>>>(ResultState.Loading)
    val overduePaymentsState = _overduePaymentsState.asStateFlow()

    private val _overduePaymentBySaleState =
        MutableStateFlow<ResultState<OverduePaymentsEntity?>>(ResultState.Loading)
    val overduePaymentBySaleState = _overduePaymentBySaleState.asStateFlow()

    fun getOverduePayments() {
        viewModelScope.launch {
            _overduePaymentsState.value = ResultState.Loading
            try {
                val result = paymentStore.getOverduePayments()
                _overduePaymentsState.value = ResultState.Success(result)
            } catch (e: Exception) {
                _overduePaymentsState.value =
                    ResultState.Error(e.message ?: "Error al obtener pagos atrasados")
            }
        }
    }

    fun getOverduePaymentBySaleId(saleId: Int) {
        viewModelScope.launch {
            _overduePaymentBySaleState.value = ResultState.Loading
            try {
                val result = paymentStore.getPagosAtrasadosBySaleId(saleId)
                _overduePaymentBySaleState.value = ResultState.Success(result)
            } catch (e: Exception) {
                _overduePaymentBySaleState.value =
                    ResultState.Error(e.message ?: "Error al obtener pago atrasado por venta")
            }
        }
    }


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

    fun getSalesByClientId(clientId: Int) {
        viewModelScope.launch {
            _salesByClientState.value = ResultState.Loading
            try {
                val cached = saleStore.getByClientId(clientId).map { it.toDomain() }
                _salesByClientState.value = ResultState.Success(cached)
            } catch (e: Exception) {
                _salesByClientState.value =
                    ResultState.Error(e.message ?: "Error leyendo ventas locales")
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

                val pendingGuarantees = guaranteeStore.getPendingGuarantees()
                if (pendingGuarantees.isNotEmpty()) {
                    _syncSalesState.value =
                        ResultState.Error("Hay ${pendingGuarantees.size} garantías pendientes")
                    return@launch
                }

                val pendingGuaranteeEvents = guaranteeStore.getPendingGuaranteeEvents()
                if (pendingGuaranteeEvents.isNotEmpty()) {
                    _syncSalesState.value =
                        ResultState.Error("Hay ${pendingGuaranteeEvents.size} eventos de garantías pendientes")
                    return@launch
                }

                val salesData = api.getAll(
                    zona = zona,
                    dateInit = dateInit
                )

                val sales = salesData.body.ventas
                val products = salesData.body.productos
                val payments = salesData.body.pagos
                val guarantees = salesData.body.garantias
                val guaranteesEvent = salesData.body.eventosGarantias

                val currentSales = saleStore.getAll().map { it.toDomain() }

                val salesToSave = sales.map { apiSale ->
                    val previous = currentSales.find { it.DOCTO_CC_ID == apiSale.DOCTO_CC_ID }
                    val safeSale = apiSale.copy(
                        FREC_PAGO = apiSale.FREC_PAGO ?: previous?.FREC_PAGO
                        ?: FrecuenciaPago.SEMANAL
                    )

                    val saleWithState = previous
                        ?.let { safeSale.copy(ESTADO_COBRANZA = it.ESTADO_COBRANZA) }
                        ?: safeSale
                    saleWithState.toEntity()
                }
                saleStore.saveAll(salesToSave)
                productStore.saveAll(products.map { it.toEntity() })
                paymentStore.saveAll(payments.map { it.toEntity() })
                guaranteeStore.saveAllGurantees(guarantees.map { it.toEntity() })
                guaranteeStore.saveAllGuaranteeEvents(guaranteesEvent.map { it.toEntity() })
                visitsStore.deleteAllVisits()

                _syncSalesState.value = ResultState.Success(sales)

                val lastSync = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
                    .format(Date())
                saveLastSyncDate(lastSync)
            } catch (e: Exception) {
                Log.e("SalesViewModel", "Error en syncSales", e)
                if (_syncSalesState.value !is ResultState.Success) {
                    _syncSalesState.value = ResultState.Error(e.message ?: "Error al cargar ventas")
                }
            }
        }
    }

    private fun saveLastSyncDate(date: String) {
        val prefs = getApplication<Application>()
            .getSharedPreferences("sync_prefs", Context.MODE_PRIVATE)
        prefs.edit {
            putString("last_sync_date", date)
        }
    }

    fun getLastSyncDate(): String {
        val prefs = getApplication<Application>()
            .getSharedPreferences("sync_prefs", Context.MODE_PRIVATE)
        return prefs.getString("last_sync_date", "") ?: ""
    }
}
