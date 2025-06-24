package com.example.msp_app.features.payments.components.newpaymentdialog

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.msp_app.components.fullscreendialog.FullScreenDialog
import com.example.msp_app.core.utils.Constants

@Composable
fun NewPaymentDialog(
    show: Boolean,
    onDismissRequest: () -> Unit,
    suggestions: List<Int> = emptyList(),
    suggestedPayment: Int = 0
) {
    if (!show) return

    var inputValue by remember { mutableStateOf("") }
    var showConfirmDialog by remember { mutableStateOf(false) }
    var selectedPaymentMethod by remember { mutableIntStateOf(Constants.PAGO_EN_EFECTIVO_ID) }

    val paymentMethods = listOf(
        Constants.PAGO_EN_EFECTIVO_ID to "Efectivo",
        Constants.PAGO_CON_TRANSFERENCIA_ID to "Transferencia"
    )

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
                    .padding(bottom = 16.dp),
                textStyle = TextStyle(fontSize = 20.sp)
            )

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
                enabled = inputValue.isNotBlank()
            ) {
                Text("Guardar")
            }
        }
    }

    if (showConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showConfirmDialog = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        showConfirmDialog = false
                        onDismissRequest()
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
                    Text(
                        text = "$$inputValue",
                        style = TextStyle(
                            fontSize = 36.sp,
                            color = MaterialTheme.colorScheme.primary
                        ),
                        textAlign = TextAlign.Center,
                    )
                }
            },
        )
    }
}