package com.example.msp_app.features.payments.components.newpaymentdialog

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.msp_app.components.fullscreendialog.FullScreenDialog
import com.example.msp_app.core.utils.Constants
import com.example.msp_app.core.utils.DateUtils
import com.example.msp_app.data.models.payment.Payment
import com.example.msp_app.data.models.sale.Sale
import com.example.msp_app.features.payments.viewmodels.PaymentsViewModel
import kotlinx.coroutines.launch
import java.util.UUID

@Composable
fun NewPaymentDialog(
    show: Boolean,
    onDismissRequest: () -> Unit,
    suggestions: List<Int> = emptyList(),
    suggestedPayment: Int = 0,
    sale: Sale
) {
    if (!show) return

    val paymentsViewModel: PaymentsViewModel = viewModel()
    val savePaymentState by paymentsViewModel.savePaymentState.collectAsState()

    var inputValue by remember { mutableStateOf("") }
    var showConfirmDialog by remember { mutableStateOf(false) }
    var selectedPaymentMethod by remember { mutableIntStateOf(Constants.PAGO_EN_EFECTIVO_ID) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    val paymentMethods = listOf(
        Constants.PAGO_EN_EFECTIVO_ID to "Efectivo",
        Constants.PAGO_CON_TRANSFERENCIA_ID to "Transferencia"
    )

    val coroutineScope = rememberCoroutineScope()
    
    val inputDouble = inputValue.toDoubleOrNull()
    errorMessage = when {
        inputValue.isBlank() -> null
        inputDouble == null -> "Ingrese un número válido"
        inputDouble <= 0 -> "El monto debe ser mayor a cero"
        inputDouble > sale.SALDO_REST -> "El monto no puede ser mayor al saldo restante"
        else -> null
    }

    fun handleSavePayment() {
        if (inputValue.isBlank() || errorMessage != null) return

        val payment = Payment(
            CLIENTE_ID = sale.CLIENTE_ID,
            ID = UUID.randomUUID().toString(),
            LAT = 0.0,
            LNG = 0.0,
            IMPORTE = inputValue.toDouble(),
            NOMBRE_CLIENTE = sale.CLIENTE,
            FECHA_HORA_PAGO = DateUtils.getIsoDateTime(),
            COBRADOR = sale.NOMBRE_COBRADOR,
            COBRADOR_ID = sale.COBRADOR_ID,
            DOCTO_CC_ID = 0,
            FORMA_COBRO_ID = selectedPaymentMethod,
            DOCTO_CC_ACR_ID = sale.DOCTO_CC_ACR_ID,
            ZONA_CLIENTE_ID = sale.ZONA_CLIENTE_ID,
            GUARDADO_EN_MICROSIP = false
        )

        coroutineScope.launch {
            try {
                paymentsViewModel.savePayment(payment)

                inputValue = ""
                selectedPaymentMethod = Constants.PAGO_EN_EFECTIVO_ID

                showConfirmDialog = false
                onDismissRequest()

                paymentsViewModel.getGroupedPaymentsBySaleId(sale.DOCTO_CC_ID)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    FullScreenDialog(
        show = true,
        onDismissRequest = onDismissRequest,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = "Agregar Pago",
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.padding(bottom = 24.dp)
            )
            Text(
                text = sale.CLIENTE,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 4.dp)
            )

            Text(
                buildAnnotatedString {
                    withStyle(
                        style = SpanStyle(
                            fontSize = 18.sp,
                            color = MaterialTheme.colorScheme.secondary
                        )
                    ) {
                        append("Saldo actual: ")
                    }
                    withStyle(
                        style = SpanStyle(
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    ) {
                        append("$${sale.SALDO_REST.toInt()}")
                    }
                }
            )

            Spacer(modifier = Modifier.height(16.dp))

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp)
            ) {
                Text(
                    text = "Forma de pago",
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                paymentMethods.forEach { (id, name) ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(bottom = 4.dp)
                    ) {
                        RadioButton(
                            selected = selectedPaymentMethod == id,
                            onClick = { selectedPaymentMethod = id }
                        )
                        Text(
                            text = name,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(start = 4.dp)
                        )
                    }
                }
            }

            OutlinedTextField(
                value = inputValue,
                onValueChange = { inputValue = it },
                label = { Text("Ingrese monto") },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
                textStyle = TextStyle(fontSize = 20.sp),
                isError = errorMessage != null
            )

            if (errorMessage != null) {
                Text(
                    text = errorMessage ?: "",
                    color = Color.Red,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
            }

            val inputInt = inputValue.toIntOrNull()
            if (suggestedPayment > 0 && inputInt != null && inputInt < suggestedPayment) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp)
                        .align(Alignment.CenterHorizontally)
                ) {
                    Text(
                        text = "El pago es menor a la parcialidad acordada de $$suggestedPayment",
                        color = Color.Red,
                        modifier = Modifier
                            .fillMaxWidth()
                            .align(Alignment.Center)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(0x1AFF0000))
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        textAlign = TextAlign.Center
                    )
                }
            }
            if (suggestions.isNotEmpty()) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp)
                ) {
                    suggestions.chunked(3).forEach { row ->
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            row.forEach { amount ->
                                val isSelected = inputValue == amount.toString()
                                Button(
                                    onClick = { inputValue = amount.toString() },
                                    modifier = Modifier.weight(1f),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = if (isSelected) Color(0xFF4CAF50) else Color(
                                            0xFFBBDEFB
                                        ),
                                        contentColor = if (isSelected) Color.White else Color.Black
                                    )
                                ) {
                                    Text(
                                        "$amount",
                                        fontSize = 18.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                    )
                                }
                            }

                            repeat(3 - row.size) {
                                Spacer(modifier = Modifier.weight(1f))
                            }
                        }
                    }
                }
            }
            Button(
                onClick = { showConfirmDialog = true },
                modifier = Modifier.fillMaxWidth(),
                enabled = inputValue.isNotBlank() && errorMessage == null
            ) {
                Text("Guardar")
            }
        }
    }

    if (showConfirmDialog) {
        val amount = inputValue.toDoubleOrNull() ?: 0.0
        val saldoRestante = (sale.SALDO_REST - amount).coerceAtLeast(0.0)
        AlertDialog(
            onDismissRequest = { showConfirmDialog = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        handleSavePayment()
                    }
                ) {
                    Text("Confirmar")
                }
            },
            dismissButton = {
                TextButton(onClick = { showConfirmDialog = false }) {
                    Text("Cancelar")
                }
            },
            title = {
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Confirmar Pago",
                        style = MaterialTheme.typography.titleLarge,
                        textAlign = TextAlign.Center
                    )
                }
            },
            text = {
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "Pago a realizar",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.secondary,
                                textAlign = TextAlign.Center
                            )
                            Spacer(
                                modifier = Modifier.height(8.dp)
                            )
                            Text(
                                text = "$${amount.toInt()}",
                                style = TextStyle(
                                    fontSize = 36.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary // Cambia este color según tu preferencia
                                ),
                                textAlign = TextAlign.Center
                            )

                        }

                        Icon(
                            imageVector = Icons.Default.KeyboardArrowDown,
                            contentDescription = "Flecha hacia abajo",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier
                                .size(42.dp)
                                .padding(vertical = 4.dp)
                        )
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "Saldo restante",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.secondary,
                                textAlign = TextAlign.Center
                            )
                            Spacer(
                                modifier = Modifier.height(8.dp)
                            )
                            Text(
                                text = "$${saldoRestante.toInt()}",
                                style = TextStyle(
                                    fontSize = 36.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF4CAF50) // Cambia este color según tu preferencia
                                ),
                                textAlign = TextAlign.Center
                            )

                        }
                    }
                }
            },
        )
    }
}