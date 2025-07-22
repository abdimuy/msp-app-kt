package com.example.msp_app.features.home.components.homefootersection

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.msp_app.core.utils.Constants.APP_VERSION
import com.example.msp_app.core.utils.ResultState
import com.example.msp_app.data.models.payment.Payment
import com.example.msp_app.data.models.visit.Visit

@Composable
fun HomeFooterSection(
    isDark: Boolean,
    visitsPendingState: ResultState<List<Visit>>,
    pendingPaymentsState: ResultState<List<Payment>>,
    syncSalesState: ResultState<List<*>>,
    zonaClienteId: Int,
    dateInitWeek: String,
    onSyncSales: (Int, String) -> Unit,
    onSyncPendingVisits: () -> Unit,
    onSyncPendingPayments: () -> Unit,
    onResendAllPayments: () -> Unit,
    onLogout: () -> Unit,
    onInitWeek: () -> Unit,
    modifier: Modifier = Modifier
) {
    val showDialogInitWeek = remember { mutableStateOf(false) }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier.fillMaxWidth()
    ) {
        Spacer(Modifier.height(22.dp))

        OutlinedCard(
            elevation = CardDefaults.cardElevation(defaultElevation = if (isDark) 0.dp else 6.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.background),
            border = BorderStroke(1.dp, if (isDark) Color.Gray else Color.Transparent),
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .background(Color.White, RoundedCornerShape(16.dp))
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = "VISITAS SIN ENVIAR",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                )
                when (visitsPendingState) {
                    is ResultState.Loading -> Text("Cargando visitas pendientes...")
                    is ResultState.Error -> Text(
                        "Error: ${visitsPendingState.message}",
                        color = Color.Red
                    )

                    is ResultState.Success -> {
                        val pending = visitsPendingState.data
                        Text(
                            if (pending.isEmpty())
                                "No hay visitas pendientes"
                            else
                                "Visitas Pendientes: ${pending.size}"
                        )
                    }

                    else -> {}
                }
            }
        }

        Spacer(Modifier.height(20.dp))

        OutlinedCard(
            elevation = CardDefaults.cardElevation(defaultElevation = if (isDark) 0.dp else 6.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.background),
            border = BorderStroke(1.dp, if (isDark) Color.Gray else Color.Transparent),
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .background(Color.White, RoundedCornerShape(16.dp))
                .height(90.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "PAGOS SIN ENVIAR",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                )
                when (pendingPaymentsState) {
                    is ResultState.Loading -> Text("Cargando pagos pendientes...")
                    is ResultState.Error -> Text(
                        "Error: ${pendingPaymentsState.message}",
                        color = Color.Red
                    )

                    is ResultState.Success -> {
                        val pending = pendingPaymentsState.data
                        Text(
                            if (pending.isEmpty())
                                "No hay pagos pendientes"
                            else
                                "Pagos Pendientes: ${pending.size}"
                        )
                    }

                    else -> {}
                }
            }
        }

        Spacer(Modifier.height(14.dp))

        Button(
            onClick = { onSyncSales(zonaClienteId, dateInitWeek) },
            modifier = Modifier.fillMaxWidth(0.92f)
        ) {
            Text("ACTUALIZAR DATOS", color = Color.White)
        }

        when (syncSalesState) {
            is ResultState.Idle -> Text("Presiona el botón para descargar ventas")
            is ResultState.Loading -> CircularProgressIndicator()
            is ResultState.Success -> Text("Ventas descargadas: ${syncSalesState.data.size}")
            is ResultState.Error -> Text("Error: ${syncSalesState.message}")
        }

        Button(
            onClick = {
                onSyncPendingVisits()
                onSyncPendingPayments()
            },
            modifier = Modifier.fillMaxWidth(0.92f)
        ) {
            Text("ENVIAR PAGOS PENDIENTES", color = Color.White)
        }

        Button(onClick = onResendAllPayments, modifier = Modifier.fillMaxWidth(0.92f)) {
            Text("REENVIAR TODOS LOS PAGOS", color = Color.White)
        }

        Button(
            onClick = { showDialogInitWeek.value = true },
            modifier = Modifier.fillMaxWidth(0.92f)
        ) {
            Text("INICIALIZAR SEMANA", color = Color.White)
        }

        if (showDialogInitWeek.value) {
            AlertDialog(
                onDismissRequest = { showDialogInitWeek.value = false },
                title = { Text("Confirmación") },
                text = { Text("¿Están seguros de inicializar semana?") },
                confirmButton = {
                    TextButton(onClick = {
                        showDialogInitWeek.value = false
                        onInitWeek()
                    }) {
                        Text("Sí")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showDialogInitWeek.value = false }) {
                        Text("No")
                    }
                }
            )
        }

        Text(
            text = "Versión $APP_VERSION",
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
            modifier = Modifier.padding(8.dp),
            fontSize = 16.sp
        )
    }
}