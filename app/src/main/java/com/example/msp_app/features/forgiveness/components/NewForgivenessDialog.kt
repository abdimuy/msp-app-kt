package com.example.msp_app.features.forgiveness.components

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.msp_app.components.fullscreendialog.FullScreenDialog
import com.example.msp_app.core.utils.Constants
import com.example.msp_app.core.utils.DateUtils
import com.example.msp_app.core.utils.ResultState
import com.example.msp_app.core.utils.toCurrency
import com.example.msp_app.data.models.payment.Payment
import com.example.msp_app.data.models.sale.Sale
import com.example.msp_app.features.auth.viewModels.AuthViewModel
import com.example.msp_app.features.payments.viewmodels.PaymentsViewModel
import com.example.msp_app.services.UpdateLocationService
import com.example.msp_app.ui.theme.ThemeController
import kotlinx.coroutines.launch
import java.util.UUID

@Composable
fun NewForgivenessDialog(
    show: Boolean,
    onDismissRequest: () -> Unit,
    sale: Sale
) {
    if (!show) return

    val context = LocalContext.current

    val isDark = ThemeController.isDarkMode

    val authViewModel: AuthViewModel = viewModel()
    val paymentsViewModel: PaymentsViewModel = viewModel()

    val userData by authViewModel.userData.collectAsState()

    val currentUser = (userData as? ResultState.Success)?.data

    var inputValue by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var showAlertDialog by remember { mutableStateOf(false) }

    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        if (inputValue.isBlank()) {
            inputValue = sale.SALDO_REST.toString()
        }
    }

    fun handleSaveForgiveness() {
        if (inputValue.isBlank() || errorMessage != null) {
            return
        }
        if (currentUser?.COBRADOR_ID == null || currentUser.COBRADOR_ID == 0) {
            errorMessage = "No se pudo obtener el ID del cobrador. Intenta nuevamente."
            return
        }
        val forgivenessAmount = inputValue.toDoubleOrNull()
        if (forgivenessAmount != null && forgivenessAmount > 0) {
            coroutineScope.launch {
                val forgiveness = Payment(
                    CLIENTE_ID = sale.CLIENTE_ID,
                    ID = UUID.randomUUID().toString(),
                    LAT = 0.0,
                    LNG = 0.0,
                    IMPORTE = inputValue.toDouble(),
                    NOMBRE_CLIENTE = sale.CLIENTE,
                    FECHA_HORA_PAGO = DateUtils.getIsoDateTime(),
                    COBRADOR = sale.NOMBRE_COBRADOR,
                    COBRADOR_ID = currentUser.COBRADOR_ID,
                    DOCTO_CC_ID = 0,
                    FORMA_COBRO_ID = Constants.CONDONACION_ID,
                    DOCTO_CC_ACR_ID = sale.DOCTO_CC_ACR_ID,
                    ZONA_CLIENTE_ID = sale.ZONA_CLIENTE_ID,
                    GUARDADO_EN_MICROSIP = false
                )

                paymentsViewModel.savePayment(forgiveness)

                val intent = Intent(context, UpdateLocationService::class.java).apply {
                    putExtra("payment_id", forgiveness.ID)
                }
                ContextCompat.startForegroundService(context, intent)

                inputValue = ""

                showAlertDialog = false
                onDismissRequest()

                paymentsViewModel.getGroupedPaymentsBySaleId(sale.DOCTO_CC_ID)
            }
        } else {
            errorMessage = "Ingrese un monto válido"
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
        when (val state = userData) {
            is ResultState.Idle, is ResultState.Loading -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(
                        color = MaterialTheme.colorScheme.primary,
                        strokeWidth = 2.dp
                    )
                }
            }

            is ResultState.Error -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text("Error: ${state.message}")
                }
            }

            is ResultState.Success -> {
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
                                    color = if (isDark) Color.White else MaterialTheme.colorScheme.primary
                                )
                            ) {
                                append(sale.SALDO_REST.toCurrency(noDecimals = true))
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
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Number,
                            imeAction = ImeAction.Done
                        )
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
                        Text(
                            text = "GUARDAR CONDONACIÓN",
                            color = Color.White
                        )
                    }
                }
            }
        }
    }

    if (showAlertDialog) {
        val forgiveness = inputValue.toDoubleOrNull()?.toCurrency(noDecimals = true)
        AlertDialog(
            onDismissRequest = { showAlertDialog = false },
            confirmButton = {
                TextButton(
                    onClick = { handleSaveForgiveness() }
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
                            text = "$forgiveness",
                            color = if (isDark) Color.White else MaterialTheme.colorScheme.primary,
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