package com.example.msp_app.features.home.components.homesummary

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.msp_app.core.utils.toCurrency
import com.example.msp_app.features.home.screens.PaymentInfoCollector
import com.example.msp_app.features.home.screens.overlap


@Composable
fun HomeSummarySection(
    isDark: Boolean,
    totalTodayPayments: Double,
    totalWeeklyPayments: Double,
    numberOfPaymentsToday: Int,
    numberOfPaymentsWeekly: Int,
    numberOfSales: Int,
    accountsPercentageRounded: String,
    accountsPercentageAjusted: String,
    modifier: Modifier = Modifier
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .fillMaxWidth()
            .overlap(40.dp)
    ) {
        OutlinedCard(
            elevation = CardDefaults.cardElevation(defaultElevation = if (isDark) 0.dp else 6.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.background),
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .background(MaterialTheme.colorScheme.background, RoundedCornerShape(16.dp))
        ) {
            Column(
                modifier = Modifier
                    .padding(vertical = 20.dp, horizontal = 16.dp)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Column(modifier = Modifier.weight(1.1f)) {
                        PaymentInfoCollector(
                            "Total Cobrado (Hoy)",
                            totalTodayPayments.toCurrency(noDecimals = true)
                        )
                        Spacer(Modifier.height(8.dp))
                        PaymentInfoCollector(
                            "Total cobrado (Semanal)",
                            totalWeeklyPayments.toCurrency(noDecimals = true)
                        )
                    }
                    Column(modifier = Modifier.weight(0.9f)) {
                        PaymentInfoCollector(
                            "Pagos (Hoy)",
                            "$numberOfPaymentsToday",
                            horizontalAlignment = Alignment.End
                        )
                        Spacer(Modifier.height(8.dp))
                        PaymentInfoCollector(
                            "Pagos (Semanal)",
                            "$numberOfPaymentsWeekly/$numberOfSales",
                            horizontalAlignment = Alignment.End
                        )
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Card(
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 100.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFF06846)),
                        elevation = CardDefaults.cardElevation(8.dp)
                    ) {
                        Column(
                            Modifier
                                .fillMaxWidth()
                                .padding(vertical = 12.dp, horizontal = 8.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Text(
                                "Porcentaje (Cuentas)",
                                color = Color.White,
                                fontSize = 14.sp,
                                textAlign = TextAlign.Center
                            )
                            Spacer(Modifier.height(8.dp))
                            Text(
                                accountsPercentageRounded,
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 22.sp,
                                color = Color.White,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                    Card(
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 100.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF56DA6A)),
                        elevation = CardDefaults.cardElevation(8.dp)
                    ) {
                        Column(
                            Modifier
                                .fillMaxWidth()
                                .padding(vertical = 12.dp, horizontal = 8.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Text(
                                "Porcentaje (Cobro)",
                                color = Color.White,
                                fontSize = 14.sp,
                                textAlign = TextAlign.Center
                            )
                            Spacer(Modifier.height(8.dp))
                            Text(
                                accountsPercentageAjusted,
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 22.sp,
                                color = Color.White,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(12.dp))
    }
}