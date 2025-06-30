package com.example.msp_app.features.payments.screens

import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDatePickerState
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.core.content.FileProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.example.msp_app.components.DrawerContainer
import com.example.msp_app.core.utils.DateUtils.formatIsoDate
import com.example.msp_app.core.utils.PdfGenerator
import com.example.msp_app.core.utils.ResultState
import com.example.msp_app.data.models.payment.Payment
import com.example.msp_app.features.payments.viewmodels.PaymentsViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DailyReportScreen(
    navController: NavController,
    viewModel: PaymentsViewModel = viewModel()
) {
    var showDatePicker by remember { mutableStateOf(false) }
    var textDate by remember { mutableStateOf(TextFieldValue("")) }
    val datePickerState = rememberDatePickerState()
    val scrollState = rememberScrollState()
    val paymentsState by viewModel.paymentsByDateState.collectAsState()
    var visiblePayments by remember { mutableStateOf<List<Payment>>(emptyList()) }


    LaunchedEffect(datePickerState.selectedDateMillis) {
        datePickerState.selectedDateMillis?.let { millis ->
            val localDate = LocalDate.ofEpochDay(millis / (24 * 60 * 60 * 1000))
            val isoDate = localDate.toString() + "T00:00:00"
            val formattedText = formatIsoDate(isoDate, "dd/MM/yyyy", Locale.getDefault())
            textDate = TextFieldValue(formattedText)
            val startDateTime = "${localDate}T00:00:00"
            val endDateTime = "${localDate}T23:59:59"
            viewModel.getPaymentsByDate(startDateTime, endDateTime)
            showDatePicker = false
        }
    }


    DrawerContainer(navController = navController) { openDrawer ->
        Scaffold(
            modifier = Modifier.statusBarsPadding(),
            topBar = {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = openDrawer) {
                        Icon(
                            imageVector = Icons.Default.Menu,
                            contentDescription = "Menú"
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = "Reporte de Pagos",
                        style = MaterialTheme.typography.titleLarge
                    )
                }
            }
        ) { innerPadding ->
            Column(
                modifier = Modifier
                    .statusBarsPadding()
                    .padding(innerPadding)
                    .padding(horizontal = 16.dp)
                    .fillMaxSize()
            ) {
                Box(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OutlinedTextField(
                        value = textDate,
                        onValueChange = {},
                        label = { Text("Fecha de reporte (dia/mes/año)") },
                        readOnly = true,
                        shape = RoundedCornerShape(25.dp),
                        trailingIcon = {
                            IconButton(onClick = { showDatePicker = !showDatePicker }) {
                                Icon(
                                    imageVector = Icons.Default.DateRange,
                                    contentDescription = "Seleccionar fecha"
                                )
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(64.dp),
                        singleLine = true,
                    )

                    if (showDatePicker) {
                        Popup(
                            onDismissRequest = { showDatePicker = false },
                            alignment = Alignment.TopStart
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .offset(y = 64.dp)
                                    .shadow(elevation = 12.dp)
                                    .background(MaterialTheme.colorScheme.surface)
                                    .padding(16.dp)
                            ) {
                                DatePicker(
                                    state = datePickerState,
                                    showModeToggle = false,
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                ) {
                    when (paymentsState) {
                        is ResultState.Loading -> {
                            Text("Cargando pagos...")
                        }

                        is ResultState.Success -> {
                            val pagos = (paymentsState as ResultState.Success<List<Payment>>).data
                            LaunchedEffect(pagos) {
                                visiblePayments = pagos
                            }

                            if (pagos.isEmpty()) {
                                Text("No hay pagos para esta fecha.")
                            } else {
                                val formatter = DateTimeFormatter.ISO_DATE_TIME

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    Button(
                                        onClick = {
                                            visiblePayments =
                                                visiblePayments.sortedBy { it.NOMBRE_CLIENTE }
                                        },
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Text("Ordenar por nombre")
                                    }

                                    Button(
                                        onClick = {
                                            visiblePayments = visiblePayments.sortedBy {
                                                try {
                                                    LocalDateTime.parse(
                                                        it.FECHA_HORA_PAGO,
                                                        formatter
                                                    ).toLocalTime()
                                                } catch (e: Exception) {
                                                    LocalTime.MAX
                                                }
                                            }
                                        },
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Text("Ordenar por hora")
                                    }
                                }
                                val context = LocalContext.current
                                val coroutineScope = rememberCoroutineScope()
                                var isGeneratingPdf by remember { mutableStateOf(false) }

                                Column(
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Column(
                                        modifier = Modifier
                                            .weight(1f)
                                            .verticalScroll(scrollState)
                                    ) {
                                        Spacer(modifier = Modifier.height(2.dp))

                                        visiblePayments.forEach { pago ->
                                            PaymentItem(pago)
                                        }

                                        Spacer(modifier = Modifier.height(16.dp))
                                    }

                                    Button(
                                        enabled = !isGeneratingPdf,
                                        onClick = {
                                            coroutineScope.launch {
                                                isGeneratingPdf = true

                                                val paymentTextData = formatPaymentsTextList(
                                                    visiblePayments
                                                )

                                                val file = withContext(Dispatchers.IO) {
                                                    PdfGenerator.generatePdfFromLines(
                                                        context = context,
                                                        data = paymentTextData,
                                                        title = "REPORTE DE PAGOS DIARIOS",
                                                        nameCollector = visiblePayments.firstOrNull()?.COBRADOR
                                                            ?: "No especificado",
                                                        fileName = "reporte_diario.pdf"
                                                    )
                                                }

                                                isGeneratingPdf = false

                                                if (file != null && file.exists()) {
                                                    val uri = FileProvider.getUriForFile(
                                                        context,
                                                        context.packageName + ".fileprovider",
                                                        file
                                                    )

                                                    val intent = Intent(Intent.ACTION_VIEW).apply {
                                                        setDataAndType(uri, "application/pdf")
                                                        flags =
                                                            Intent.FLAG_ACTIVITY_NO_HISTORY or Intent.FLAG_GRANT_READ_URI_PERMISSION
                                                    }

                                                    try {
                                                        context.startActivity(intent)
                                                    } catch (e: Exception) {
                                                        Toast.makeText(
                                                            context,
                                                            "No hay aplicación para abrir PDF",
                                                            Toast.LENGTH_SHORT
                                                        ).show()
                                                    }
                                                } else {
                                                    Toast.makeText(
                                                        context,
                                                        "Error al generar PDF",
                                                        Toast.LENGTH_SHORT
                                                    ).show()
                                                }
                                            }
                                        },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 8.dp)
                                    ) {
                                        Text(if (isGeneratingPdf) "Generando PDF..." else "Generar PDF")
                                    }

                                }

                            }
                        }

                        is ResultState.Error -> {
                            Text("Error: ${(paymentsState as ResultState.Error).message}")
                        }

                        else -> {
                            Text("Selecciona una fecha para ver los pagos.")
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun PaymentItem(payment: Payment) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
            .background(
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(8.dp)
            )
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
        ) {
            Text(
                text = formatIsoDate(payment.FECHA_HORA_PAGO, "dd/MM/yyyy hh:mm a"),
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = payment.NOMBRE_CLIENTE,
                maxLines = 1,
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.bodyLarge,
                overflow = TextOverflow.Ellipsis
            )
        }

        Box(
            modifier = Modifier
                .padding(start = 8.dp)
                .align(Alignment.CenterVertically)
        ) {
            Text(
                text = String.format("$%.0f", payment.IMPORTE),
                fontWeight = FontWeight.Bold,
                fontSize = 20.sp,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}

data class PaymentTextData(
    val lines: List<Triple<String, String, Double>>,
    val totalCount: Int,
    val totalAmount: Double
)

fun formatPaymentsTextList(payments: List<Payment>): PaymentTextData {
    val lines = payments.map { pago ->
        val formattedDate = formatIsoDate(
            pago.FECHA_HORA_PAGO,
            "dd/MM/yyyy hh:mm a",
            Locale("es", "MX")
        )
        Triple(formattedDate, pago.NOMBRE_CLIENTE, pago.IMPORTE)
    }

    val totalCount = payments.size
    val totalAmount = payments.sumOf { it.IMPORTE }

    return PaymentTextData(lines, totalCount, totalAmount)
}
