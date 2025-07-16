package com.example.msp_app.features.home.components.homeweeklypaymentssection

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.msp_app.core.utils.ResultState
import com.example.msp_app.core.utils.toCurrency
import com.example.msp_app.data.models.payment.Payment
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun HomeWeeklyPaymentsSection(
    paymentsGroupedByDayWeekly: ResultState<Map<String, List<Payment>>>,
    isDark: Boolean,
    onPaymentDateClick: (dateLabel: String, payments: List<Payment>) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier.fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth(.92f)
                    .height(100.dp)
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                when (paymentsGroupedByDayWeekly) {
                    is ResultState.Idle -> Text(
                        text = "No hay pagos registrados esta semana",
                        color = Color.Gray,
                        modifier = Modifier.padding(16.dp)
                    )

                    is ResultState.Loading -> CircularProgressIndicator()
                    is ResultState.Error -> Text(
                        text = "Error al cargar pagos: ${paymentsGroupedByDayWeekly.message}",
                        color = Color.Red
                    )

                    is ResultState.Success -> {
                        val paymentsMap = paymentsGroupedByDayWeekly.data
                        if (paymentsMap.isEmpty()) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .border(
                                        1.dp,
                                        if (isDark) Color.Gray else Color.LightGray,
                                        RoundedCornerShape(12.dp)
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("No hay pagos registrados esta semana", color = Color.Gray)
                            }
                            return@Row
                        }

                        Spacer(Modifier.width(1.dp))

                        paymentsMap.forEach { (date, payments) ->
                            val total = payments.sumOf { it.IMPORTE }
                            val count = payments.size
                            val formatted = LocalDate.parse(date)
                                .format(
                                    DateTimeFormatter.ofPattern(
                                        "EEE dd/MM",
                                        Locale("es", "MX")
                                    )
                                )
                                .uppercase()

                            Card(
                                colors = CardDefaults.cardColors(
                                    containerColor = if (isDark) Color(0xFF1E1E1E) else MaterialTheme.colorScheme.background
                                ),
                                modifier = Modifier
                                    .width(100.dp)
                                    .height(100.dp)
                                    .clickable { onPaymentDateClick(formatted, payments) }
                                    .border(
                                        1.dp,
                                        if (isDark) Color.Gray else Color(0xFFE0E0E0),
                                        RoundedCornerShape(12.dp)
                                    ),
                                elevation = CardDefaults.cardElevation(4.dp)
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(8.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text(formatted, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                    Text(
                                        text = total.toCurrency(noDecimals = true),
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 18.sp,
                                        color = if (isDark) Color.White else MaterialTheme.colorScheme.primary
                                    )
                                    Text("$count pagos", color = Color.Gray, fontSize = 14.sp)
                                }
                            }
                        }

                        Spacer(Modifier.width(1.dp))
                    }
                }
            }
        }
        Spacer(Modifier.height(16.dp))
    }
}