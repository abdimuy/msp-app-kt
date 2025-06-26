package com.example.msp_app.features.payments.viewmodels

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.msp_app.core.utils.ResultState
import com.example.msp_app.data.local.datasource.payment.PaymentsLocalDataSource
import com.example.msp_app.data.models.payment.Payment
import com.example.msp_app.data.models.payment.toDomain
import com.example.msp_app.data.models.payment.toEntity
import com.example.msp_app.data.models.sale.EstadoCobranza
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter

class PaymentsViewModel(application: Application) : AndroidViewModel(application) {
    val paymentStore = PaymentsLocalDataSource(application.applicationContext)

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

    fun groupPaymentsByMonthAndYear(payments: List<Payment>): Map<String, List<Payment>> {
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

    fun savePayment(payment: Payment) {
        viewModelScope.launch {
            _savePaymentState.value = ResultState.Loading
            try {
                withContext(Dispatchers.IO) {
                    paymentStore.insertPaymentAndUpdateSale(
                        payment.toEntity(),
                        payment.DOCTO_CC_ACR_ID,
                        payment.IMPORTE,
                        EstadoCobranza.PAGADO
                    )
                }
                _savePaymentState.value = ResultState.Success(Unit)
            } catch (e: Exception) {
                _savePaymentState.value = ResultState.Error(e.message ?: "Error guardando pago")
            }
        }
    }
}