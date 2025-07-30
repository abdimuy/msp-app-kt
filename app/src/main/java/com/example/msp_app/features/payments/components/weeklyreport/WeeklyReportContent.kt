package com.example.msp_app.features.payments.components.weeklyreport

import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavController
import com.example.msp_app.core.utils.DateUtils
import com.example.msp_app.core.utils.ResultState
import com.example.msp_app.data.models.payment.Payment
import com.example.msp_app.features.payments.components.paymentslist.PaymentsList
import com.example.msp_app.features.payments.models.VisitTextData
import com.example.msp_app.features.payments.utils.ReportFormatters

@RequiresApi(Build.VERSION_CODES.S)
@Composable
fun WeeklyReportContent(
    paymentsState: ResultState<List<Payment>>,
    forgivenessState: ResultState<List<Payment>>,
    visitTextData: VisitTextData,
    startIso: String,
    endIso: String,
    navController: NavController,
    modifier: Modifier = Modifier
) {
    when (paymentsState) {
        is ResultState.Loading -> Text("Cargando pagos...")
        is ResultState.Success -> {
            val payments = paymentsState.data
            var visiblePayments by remember { mutableStateOf<List<Payment>>(emptyList()) }

            LaunchedEffect(payments) {
                visiblePayments = payments
            }

            if (payments.isEmpty()) {
                Text("No hay pagos en esta semana.")
            } else {
                val forgivenessList =
                    (forgivenessState as? ResultState.Success)?.data ?: emptyList()
                val dateStr = "Del ${
                    DateUtils.formatIsoDate(
                        startIso,
                        "dd/MM/yy"
                    )
                } al ${DateUtils.formatIsoDate(endIso, "dd/MM/yy")}"

                val paymentTextData = ReportFormatters.formatPaymentsTextList(visiblePayments)
                val forgivenessTextData =
                    ReportFormatters.formatForgivenessTextList(forgivenessList)

                val ticketText = ReportFormatters.formatPaymentsTextForTicket(
                    payments = visiblePayments,
                    dateStr = dateStr,
                    collectorName = visiblePayments.firstOrNull()?.COBRADOR ?: "No especificado",
                    title = "REPORTE SEMANAL",
                    forgiveness = forgivenessList
                )

                Column(modifier = modifier) {
                    SortingButtons(
                        onSortByName = {
                            visiblePayments = visiblePayments.sortedBy { it.NOMBRE_CLIENTE }
                        },
                        onSortByDate = {
                            visiblePayments = visiblePayments.sortedBy {
                                DateUtils.parseIsoToDateTime(it.FECHA_HORA_PAGO)
                            }
                        }
                    )

                    PaymentsList(
                        payments = visiblePayments,
                        navController = navController,
                        modifier = Modifier.weight(1f)
                    )

                    ReportActions(
                        paymentTextData = paymentTextData,
                        visitTextData = visitTextData,
                        forgivenessTextData = forgivenessTextData,
                        ticketText = ticketText,
                        collectorName = visiblePayments.firstOrNull()?.COBRADOR
                            ?: "No especificado",
                        startIso = startIso,
                        endIso = endIso,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }

        is ResultState.Error -> {
            Text("Error: ${paymentsState.message}")
        }

        else -> {
            Text("Cargando datos...")
        }
    }
}