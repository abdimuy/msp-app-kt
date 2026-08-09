package com.example.msp_app.features.sales.components.paymentshistorysection

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.example.msp_app.core.common.time.AppClock
import com.example.msp_app.core.common.time.AppTime
import com.example.msp_app.core.utils.ResultState
import com.example.msp_app.data.models.payment.Payment
import com.example.msp_app.data.models.sale.Sale
import com.example.msp_app.features.payments.viewmodels.PaymentsViewModel
import com.example.msp_app.features.sales.components.paymentcard.PaymentCard

@Composable
fun PaymentsHistory(sale: Sale, navController: NavController) {
    val viewModel: PaymentsViewModel = viewModel()
    val paymentsBySaleIdGroupedState by viewModel.paymentsBySaleIdGroupedState.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.getGroupedPaymentsBySaleId(saleId = sale.DOCTO_CC_ID)
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
    ) {
        when (val result = paymentsBySaleIdGroupedState) {
            is ResultState.Success -> {
                val groupedPayments = result.data
                val firstPayment = groupedPayments.values.flatten().firstOrNull()

                groupedPayments.forEach { (month, payments) ->
                    Text(
                        text = month,
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(bottom = 5.dp),
                        fontSize = 18.sp
                    )
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        payments.forEach { payment ->
                            val isFirstPayment = isFirstPaymentOfToday(payment, firstPayment)
                            PaymentCard(
                                payment = payment,
                                navController = navController,
                                isFirstPayment = isFirstPayment
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                }
            }

            is ResultState.Loading -> {
                Text("Cargando pagos...")
            }

            is ResultState.Error -> {
                Text("Error: ${result.message}")
            }

            else -> {
                Text("No hay pagos")
            }
        }
    }
}

/**
 * Is [payment] the first payment of the group AND was it made "today" (business zone)?
 * Drives the highlighted background in [PaymentCard].
 *
 * Extracted as a pure, non-`@Composable` function so both sides of the comparison share the
 * same zone: previously `datePayment` came from the legacy date util's `formatIsoDate` (device zone,
 * implicitly via `ZoneId.systemDefault()`) while `dateNow` came from a bare `LocalDate.now()`
 * (also device zone) — self-consistent only because both happened to use the device zone.
 * Migrating `datePayment` alone to [AppTime.formatIsoForDisplay] (business zone) without also
 * moving `dateNow` to [AppTime.todayInBusinessZone] would have silently broken this comparison
 * for any device whose zone differs from [com.example.msp_app.core.common.time.BUSINESS_ZONE].
 */
fun isFirstPaymentOfToday(
    payment: Payment,
    firstPayment: Payment?,
    clock: AppClock = AppClock.System
): Boolean {
    val datePayment = AppTime.formatIsoForDisplay(
        payment.FECHA_HORA_PAGO,
        AppTime.Formats.DATE_SHORT
    )
    val dateNow = AppTime.formatDate(AppTime.todayInBusinessZone(clock), AppTime.Formats.DATE_SHORT)
    return payment.ID == firstPayment?.ID && datePayment == dateNow
}
