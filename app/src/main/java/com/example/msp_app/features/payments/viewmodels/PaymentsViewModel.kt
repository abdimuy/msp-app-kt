package com.example.msp_app.features.payments.viewmodels

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.msp_app.core.utils.ResultState
import com.example.msp_app.core.utils.computeCentroids
import com.example.msp_app.data.api.ApiProvider
import com.example.msp_app.data.api.services.payment.PaymentRequest
import com.example.msp_app.data.api.services.payment.PaymentsApi
import com.example.msp_app.data.local.datasource.payment.PaymentsLocalDataSource
import com.example.msp_app.data.local.datasource.product.ProductsLocalDataSource
import com.example.msp_app.data.local.datasource.sale.SalesLocalDataSource
import com.example.msp_app.data.local.entities.ProductEntity
import com.example.msp_app.data.models.payment.Payment
import com.example.msp_app.data.models.payment.PaymentLocation
import com.example.msp_app.data.models.payment.PaymentLocationsGroup
import com.example.msp_app.data.models.payment.toDomain
import com.example.msp_app.data.models.payment.toEntity
import com.example.msp_app.data.models.sale.EstadoCobranza
import com.example.msp_app.workmanager.enqueuePendingPaymentsWorker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter

class PaymentsViewModel(application: Application) : AndroidViewModel(application) {
    private val paymentStore = PaymentsLocalDataSource(application.applicationContext)
    private val productsStore = ProductsLocalDataSource(application.applicationContext)
    private val salesStore = SalesLocalDataSource(application.applicationContext)
    private val api: PaymentsApi get() = ApiProvider.create(PaymentsApi::class.java)

    private val _paymentsBySaleIdState =
        MutableStateFlow<ResultState<List<Payment>>>(ResultState.Idle)
    val paymentsBySaleIdState: StateFlow<ResultState<List<Payment>>> = _paymentsBySaleIdState

    private val _paymentsBySaleIdGroupedState =
        MutableStateFlow<ResultState<Map<String, List<Payment>>>>(ResultState.Idle)
    val paymentsBySaleIdGroupedState: StateFlow<ResultState<Map<String, List<Payment>>>> =
        _paymentsBySaleIdGroupedState

    private val _savePaymentState = MutableStateFlow<ResultState<Unit>>(ResultState.Idle)
    val savePaymentState: StateFlow<ResultState<Unit>> = _savePaymentState

    private val _paymentsByDateState =
        MutableStateFlow<ResultState<List<Payment>>>(ResultState.Idle)
    val paymentsByDateState: StateFlow<ResultState<List<Payment>>> = _paymentsByDateState

    private val _forgivenessByDateState =
        MutableStateFlow<ResultState<List<Payment>>>(ResultState.Idle)
    val forgivenessByDateState: StateFlow<ResultState<List<Payment>>> = _forgivenessByDateState

    private val _paymentsGroupedByDayWeeklyState =
        MutableStateFlow<ResultState<Map<String, List<Payment>>>>(ResultState.Idle)
    val paymentsGroupedByDayWeeklyState: StateFlow<ResultState<Map<String, List<Payment>>>> =
        _paymentsGroupedByDayWeeklyState

    private val _centroidsBySaleState =
        MutableStateFlow<ResultState<List<PaymentLocationsGroup>>>(ResultState.Idle)
    val centroidsBySaleState: StateFlow<ResultState<List<PaymentLocationsGroup>>> =
        _centroidsBySaleState

    private val _paymentsBySuggestedAmountsState =
        MutableStateFlow<ResultState<List<Int>>>(ResultState.Idle)
    val paymentsBySuggestedAmountsState: StateFlow<ResultState<List<Int>>> =
        _paymentsBySuggestedAmountsState

    private val _pendingPaymentsState =
        MutableStateFlow<ResultState<List<Payment>>>(ResultState.Idle)
    val pendingPaymentsState: StateFlow<ResultState<List<Payment>>> = _pendingPaymentsState

    private val _syncPendingPaymentsState =
        MutableStateFlow<ResultState<Unit>>(ResultState.Idle)
    val syncPendingPaymentsState: StateFlow<ResultState<Unit>> = _syncPendingPaymentsState

    private val _adjustedPaymentPercentageState =
        MutableStateFlow<ResultState<Double>>(ResultState.Idle)
    val adjustedPaymentPercentageState: StateFlow<ResultState<Double>> =
        _adjustedPaymentPercentageState

    private val _saleProductsState =
        MutableStateFlow<ResultState<List<ProductEntity>>>(ResultState.Idle)
    val saleProductsState: StateFlow<ResultState<List<ProductEntity>>> = _saleProductsState

    fun getSaleProducts(saleId: Int) {
        viewModelScope.launch {
            _saleProductsState.value = ResultState.Loading
            try {
                val products = withContext(Dispatchers.IO) {
                    val sale = salesStore.getById(saleId)
                    if (sale != null) {
                        productsStore.getProductsByFolio(sale.FOLIO)
                    } else {
                        emptyList()
                    }
                }
                _saleProductsState.value = ResultState.Success(products)
            } catch (e: Exception) {
                _saleProductsState.value = ResultState.Error(e.message ?: "Error al cargar productos")
            }
        }
    }

    fun getPaymentsBySaleId(saleId: Int) {
        viewModelScope.launch {
            _paymentsBySaleIdState.value = ResultState.Loading
            try {
                val payments = withContext(Dispatchers.IO) {
                    paymentStore.getPaymentsBySaleId(saleId).map { it.toDomain() }
                }
                _paymentsBySaleIdState.value = ResultState.Success(payments)
            } catch (e: Exception) {
                _paymentsBySaleIdState.value =
                    ResultState.Error(e.message ?: "Error leyendo pagos locales")
            }
        }
    }

    private fun groupPaymentsByMonthAndYear(payments: List<Payment>): Map<String, List<Payment>> {
        val locale = java.util.Locale("es", "MX")
        val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSX")

        val grouped = payments.groupBy { payment ->
            val dateTime =
                OffsetDateTime.parse(payment.FECHA_HORA_PAGO, formatter).toLocalDateTime()
            val month = dateTime.month.getDisplayName(java.time.format.TextStyle.FULL, locale)
                .uppercase(locale)
            val year = dateTime.year
            "$month $year"
        }

        return grouped
            .toList()
            .sortedByDescending { (label, _) ->
                val parts = label.split(" ")
                val year = parts[1].toInt()
                val monthNumber = mapOf(
                    "ENERO" to 1, "FEBRERO" to 2, "MARZO" to 3, "ABRIL" to 4,
                    "MAYO" to 5, "JUNIO" to 6, "JULIO" to 7, "AGOSTO" to 8,
                    "SEPTIEMBRE" to 9, "OCTUBRE" to 10, "NOVIEMBRE" to 11, "DICIEMBRE" to 12
                )[parts[0]] ?: 0
                year * 100 + monthNumber
            }
            .associate { (label, list) ->
                label to list.sortedByDescending {
                    OffsetDateTime.parse(it.FECHA_HORA_PAGO, formatter)
                }
            }
    }

    fun getGroupedPaymentsBySaleId(saleId: Int) {
        viewModelScope.launch {
            _paymentsBySaleIdGroupedState.value = ResultState.Loading
            try {
                val payments = withContext(Dispatchers.IO) {
                    paymentStore.getPaymentsBySaleId(saleId).map { it.toDomain() }
                }
                _paymentsBySaleIdState.value = ResultState.Success(payments)
                val grouped = groupPaymentsByMonthAndYear(payments)
                _paymentsBySaleIdGroupedState.value = ResultState.Success(grouped)
            } catch (e: Exception) {
                _paymentsBySaleIdGroupedState.value =
                    ResultState.Error(e.message ?: "Error agrupando pagos")
            }
        }
    }

    fun getPaymentsByDate(startDate: String, endDate: String) {
        viewModelScope.launch {
            _paymentsByDateState.value = ResultState.Loading

            val payments = withContext(Dispatchers.IO) {
                paymentStore.getPaymentsByDate(startDate, endDate)
                    .map { it.toDomain() }
            }
            _paymentsByDateState.value = ResultState.Success(payments)
        }
    }

    fun getForgivenessByDate(startDate: String, endDate: String) {
        viewModelScope.launch {
            _forgivenessByDateState.value = ResultState.Loading

            val payments = withContext(Dispatchers.IO) {
                paymentStore.getForgivenessByDate(startDate, endDate)
                    .map { it.toDomain() }
            }
            _forgivenessByDateState.value = ResultState.Success(payments)
        }
    }

    fun getPaymentsGroupedByDayWeekly(startWeek: String) {
        viewModelScope.launch {
            _paymentsGroupedByDayWeeklyState.value = ResultState.Loading
            try {
                val paymentsGrouped = paymentStore.getPaymentsGroupedByDaySince(startWeek)
                val payments = paymentsGrouped.mapValues { (_, paymentList) ->
                    paymentList.map { it.toDomain() }
                }.toSortedMap(compareByDescending { it })
                _paymentsGroupedByDayWeeklyState.value = ResultState.Success(payments)
            } catch (e: Exception) {
                _paymentsGroupedByDayWeeklyState.value =
                    ResultState.Error(e.message ?: "Error al cargar pagos")
            }
        }
    }

    fun getCentroidsBySale() {
        viewModelScope.launch {
            _centroidsBySaleState.value = ResultState.Loading
            try {
                val locations = paymentStore.getLocationsGroupedBySaleId()
                val locationsSale = locations.map { group ->
                    group.saleId to group.locations.map { loc ->
                        loc.LAT to loc.LNG
                    }
                }

                val centroidsBySale = locationsSale.map { (saleId, locations) ->
                    val centroids = computeCentroids(
                        locations,
                        eps = 0.0005,
                        minPts = 3
                    )
                    PaymentLocationsGroup(
                        saleId = saleId,
                        locations = centroids.map { point ->
                            PaymentLocation(
                                LAT = point.lat,
                                LNG = point.lng,
                                DOCTO_CC_ACR_ID = saleId
                            )
                        }
                    )
                }

                _centroidsBySaleState.value = ResultState.Success(centroidsBySale)
            } catch (e: Exception) {
                _centroidsBySaleState.value =
                    ResultState.Error(e.message ?: "Error al cargar ubicaciones de pagos")
            }
        }
    }

    fun getSuggestedAmountsBySaleId(saleId: Int) {
        viewModelScope.launch {
            _paymentsBySuggestedAmountsState.value = ResultState.Loading
            try {
                val amounts = withContext(Dispatchers.IO) {
                    paymentStore.getSuggestedAmountsBySaleId(saleId)
                }
                _paymentsBySuggestedAmountsState.value = ResultState.Success(amounts)
            } catch (e: Exception) {
                _paymentsBySuggestedAmountsState.value =
                    ResultState.Error(e.message ?: "Error al cargar montos sugeridos")
            }
        }
    }

    fun getPendingPayments() {
        viewModelScope.launch {
            _pendingPaymentsState.value = ResultState.Loading
            try {
                val payments = withContext(Dispatchers.IO) {
                    paymentStore.getPendingPayments().map { it.toDomain() }
                }
                _pendingPaymentsState.value = ResultState.Success(payments)
            } catch (e: Exception) {
                _pendingPaymentsState.value =
                    ResultState.Error(e.message ?: "Error obteniendo pagos pendientes")
            }
        }
    }

    fun syncPendingPayments() {
        viewModelScope.launch {
            _syncPendingPaymentsState.value = ResultState.Loading
            try {
                val pending = withContext(Dispatchers.IO) {
                    paymentStore.getPendingPayments()
                }
                if (pending.isEmpty()) {
                    _syncPendingPaymentsState.value = ResultState.Success(Unit)
                } else {
                    pending.forEach { payment ->
                        enqueuePendingPaymentsWorker(
                            getApplication(),
                            payment.ID,
                            replace = true
                        )
                    }
                    _syncPendingPaymentsState.value = ResultState.Success(Unit)
                }
                val newPendingPayments = withContext(Dispatchers.IO) {
                    paymentStore.getPendingPayments().map { it.toDomain() }
                }
                _pendingPaymentsState.value = ResultState.Success(newPendingPayments)
            } catch (e: Exception) {
                _syncPendingPaymentsState.value =
                    ResultState.Error(e.message ?: "Error sincronizando pagos pendientes")
            }
        }
    }

    fun getAdjustedPaymentPercentage(startDate: String) {
        viewModelScope.launch {
            _adjustedPaymentPercentageState.value = ResultState.Loading
            try {
                val percentage = withContext(Dispatchers.IO) {
                    paymentStore.getAdjustedPaymentPercentage(startDate)
                }
                _adjustedPaymentPercentageState.value = ResultState.Success(percentage)
            } catch (e: Exception) {
                _adjustedPaymentPercentageState.value =
                    ResultState.Error(e.message ?: "Error obteniendo porcentaje de pagos ajustados")
            }
        }
    }

    fun savePayment(payment: Payment) {
        viewModelScope.launch {
            _savePaymentState.value = ResultState.Loading
            try {
                withContext(Dispatchers.IO) {
                    paymentStore.saveAndEnqueue(
                        payment.toEntity(),
                        payment.DOCTO_CC_ACR_ID,
                        payment.IMPORTE,
                        EstadoCobranza.PAGADO
                    )
                }
                getGroupedPaymentsBySaleId(payment.DOCTO_CC_ACR_ID)
                _savePaymentState.value = ResultState.Success(Unit)
            } catch (e: Exception) {
                _savePaymentState.value = ResultState.Error(e.message ?: "Error guardando pago")
            }
        }
    }

    fun resetSavePaymentState() {
        _savePaymentState.value = ResultState.Idle
    }

    fun postPaymentRemote(payment: Payment) {
        viewModelScope.launch {
            try {
                api.savePayment(PaymentRequest(pago = payment))
            } catch (e: Exception) {
                Log.e("PaymentsViewModel", "Error posting payment: ${e.message}")
            }
        }
    }

    private val _paymentState = MutableStateFlow<ResultState<Payment>>(ResultState.Idle)
    val paymentState: StateFlow<ResultState<Payment>> = _paymentState


    fun getPaymentById(paymentId: String) {
        viewModelScope.launch {
            _paymentState.value = ResultState.Loading
            try {
                val payment = withContext(Dispatchers.IO) {
                    paymentStore.getPaymentById(paymentId)?.toDomain()
                }
                if (payment != null) {
                    _paymentState.value = ResultState.Success(payment)
                } else {
                    _paymentState.value = ResultState.Error("Pago no encontrado")
                }
            } catch (e: Exception) {
                _paymentState.value = ResultState.Error(e.message ?: "Error cargando pago")
            }
        }
    }

}
