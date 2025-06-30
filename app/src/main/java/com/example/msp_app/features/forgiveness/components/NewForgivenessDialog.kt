package com.example.msp_app.features.forgiveness.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.msp_app.components.fullscreendialog.FullScreenDialog
import com.example.msp_app.data.models.sale.Sale

@Composable
fun NewForgivenessDialog(
    show: Boolean,
    onDismissRequest: () -> Unit,
    sale: Sale
) {
    if (!show) return
    var inputValue by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var showAlertDialog by remember { mutableStateOf(false) }

    //Carga el valor de la condonacion al entrar al cuadro
    LaunchedEffect(Unit) {
        if (inputValue.isBlank()) {
            inputValue = sale.SALDO_REST.toString()
        }
    }

    val inputDouble = inputValue.toDoubleOrNull()
    errorMessage = when {
        inputValue.isBlank() -> null
        inputDouble == null -> "Ingrese un número válido"
        inputDouble <= 0 -> "El monto debe ser mayor a cero"
        inputDouble > sale.SALDO_REST -> "El monto no puede ser mayor al saldo restante"
        else -> null
    }

    FullScreenDialog(
        show = true,
        onDismissRequest = onDismissRequest
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = "Agregar Condonación",
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

            Spacer(Modifier.height(20.dp))

            OutlinedTextField(
                value = inputValue,
                onValueChange = { inputValue = it },
                label = { Text("Ingrese monto") },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
                textStyle = TextStyle(fontSize = 20.sp),
                isError = errorMessage != null,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
            )

            if (errorMessage != null) {
                Text(
                    text = errorMessage ?: "",
                    color = Color.Red,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
            }

            Spacer(Modifier.height(20.dp))
            Button(
                onClick = { showAlertDialog = true },
                modifier = Modifier.fillMaxWidth(),
                enabled = inputValue.isNotBlank() && errorMessage == null
            ) {
                Text("Guardar")
            }
        }
    }

    if (showAlertDialog) {
        val forgiveness = sale.SALDO_REST
        AlertDialog(
            onDismissRequest = { showAlertDialog = false },
            confirmButton = {
                TextButton(
                    onClick = { }
                ) {
                    Text("Confirmar")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showAlertDialog = false }
                ) {
                    Text("Cancelar")
                }
            },
            title = {
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Realizar Condonación",
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
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(12.dp)
                    ) {
                        Text(
                            text = "Confirmar la condonación por la cantidad de: ",
                            style = MaterialTheme.typography.labelLarge,
                            textAlign = TextAlign.Center,
                            fontSize = 18.sp
                        )
                        Spacer(Modifier.height(10.dp))
                        Text(
                            text = "$${forgiveness}",
                            color = MaterialTheme.colorScheme.primary,
                            style = TextStyle(
                                fontSize = 30.sp,
                                fontWeight = FontWeight.Bold
                            )
                        )
                    }
                }
            }
        )
    }
}