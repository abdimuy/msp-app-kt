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
import com.example.msp_app.core.utils.DateUtils
import com.example.msp_app.core.utils.ResultState
import com.example.msp_app.data.models.sale.Sale
import com.example.msp_app.features.payments.viewmodels.PaymentsViewModel
import com.example.msp_app.features.sales.components.paymentcard.PaymentCard
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@Composable
fun PaymentsHistory(
    sale: Sale,
    navController: NavController
) {
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
                val dateNow = LocalDate.now().format(DateTimeFormatter.ofPattern("dd/MM/yyyy"))

                groupedPayments.forEach { (month, payments) ->
                    Text(
                        text = month,
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(bottom = 5.dp),
                        fontSize = 18.sp,
                    )
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        payments.forEach { payment ->
                            val datePayment =
                                DateUtils.formatIsoDate(payment.FECHA_HORA_PAGO, "dd/MM/yyyy")
                            val isFirstPayment =
                                payment.ID == firstPayment?.ID && datePayment == dateNow
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