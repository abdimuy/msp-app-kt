package com.example.msp_app.features.payments.screens

import android.content.Intent
import android.os.Build
import android.widget.Toast
import androidx.annotation.RequiresApi
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.core.content.FileProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.example.msp_app.components.DrawerContainer
import com.example.msp_app.components.selectbluetoothdevice.SelectBluetoothDevice
import com.example.msp_app.core.models.PaymentMethod
import com.example.msp_app.core.utils.DateUtils
import com.example.msp_app.core.utils.PdfGenerator
import com.example.msp_app.core.utils.ResultState
import com.example.msp_app.core.utils.ThermalPrinting
import com.example.msp_app.data.models.payment.Payment
import com.example.msp_app.data.models.visit.Visit
import com.example.msp_app.features.payments.components.paymentitem.PaymentItem
import com.example.msp_app.features.payments.components.paymentitem.PaymentItemVariant
import com.example.msp_app.features.payments.viewmodels.PaymentsViewModel
import com.example.msp_app.features.visit.viewmodels.VisitsViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

private data class ReportDateData(
    val iso: String,
    val textField: TextFieldValue,
    val startIso: String,
    val endIso: String
)

private fun prepareReportDate(date: LocalDate): ReportDateData {
    val iso = DateUtils.parseLocalDateToIso(date)
    val text = TextFieldValue(
        DateUtils.formatIsoDate(iso, "dd/MM/yyyy", Locale("es", "MX"))
    )
    val start = iso
    val end = DateUtils.addToIsoDate(
        DateUtils.addToIsoDate(iso, 1, ChronoUnit.DAYS),
        -1, ChronoUnit.SECONDS
    )
    return ReportDateData(iso, text, start, end)
}

private fun buildPaymentsTicketText(
    payments: List<Payment>,
    dateStr: String,
    collectorName: String,
    title: String
): String {
    val builder = StringBuilder()
    builder.appendLine(ThermalPrinting.centerText(title, 32))
    builder.appendLine("Fecha: $dateStr")
    builder.appendLine("Cobrador: $collectorName")
    builder.appendLine("-".repeat(32))
    builder.appendLine(String.format("%-6s %-16s %8s", "Hora", "Cliente", "Importe"))
    payments.forEach { pago ->
        val date = DateUtils.formatIsoDate(pago.FECHA_HORA_PAGO, "HH:mm", Locale("es", "MX"))
        val client = pago.NOMBRE_CLIENTE.takeIf { it.length <= 16 } ?: pago.NOMBRE_CLIENTE.take(16)
        builder.appendLine(
            String.format(
                "%-6s %-16s %8s",
                date,
                client,
                "$%,d".format(pago.IMPORTE.toInt())
            )
        )
    }
    builder.appendLine("-".repeat(32))
    val total = payments.sumOf { it.IMPORTE }.toInt()
    val cash =
        payments.filter { PaymentMethod.fromId(it.FORMA_COBRO_ID) == PaymentMethod.PAGO_EN_EFECTIVO }
    val transfers =
        payments.filter { PaymentMethod.fromId(it.FORMA_COBRO_ID) == PaymentMethod.PAGO_CON_TRANSFERENCIA }

    val totalCash = cash.sumOf { it.IMPORTE }.toInt()
    val totalTransfers = transfers.sumOf { it.IMPORTE }.toInt()

    builder.appendLine("Total de pagos: ${payments.size}")
    builder.appendLine("Total importe: $%,d".format(total))
    builder.appendLine("Efectivo (${cash.size} pagos): $%,d".format(totalCash))
    builder.appendLine("Transferencia (${transfers.size} pagos): $%,d".format(totalTransfers))

    return builder.toString()
}

@RequiresApi(Build.VERSION_CODES.S)
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DailyReportScreen(
    navController: NavController,
    viewModel: PaymentsViewModel = viewModel()
) {
    var showDatePicker by remember { mutableStateOf(false) }
    var textDate by remember { mutableStateOf(TextFieldValue("")) }
    val datePickerState = rememberDatePickerState()
    val paymentsState by viewModel.paymentsByDateState.collectAsState()
    var visiblePayments by remember { mutableStateOf<List<Payment>>(emptyList()) }
    var reportDateIso by remember { mutableStateOf("") }
    val visitsViewModel: VisitsViewModel = viewModel()
    val visitsState by visitsViewModel.visitsByDate.collectAsState()

    LaunchedEffect(Unit) {
        prepareReportDate(LocalDate.now()).let { data ->
            reportDateIso = data.iso
            textDate = data.textField
            viewModel.getPaymentsByDate(data.startIso, data.endIso)
            visitsViewModel.getVisitsByDate(data.startIso, data.endIso)
        }
    }

    LaunchedEffect(datePickerState.selectedDateMillis) {
        datePickerState.selectedDateMillis?.let { millis ->
            val selected = LocalDate.ofEpochDay(millis / (24 * 60 * 60 * 1000))
            prepareReportDate(selected).let { data ->
                reportDateIso = data.iso
                textDate = data.textField
                viewModel.getPaymentsByDate(data.startIso, data.endIso)
                visitsViewModel.getVisitsByDate(data.startIso, data.endIso)
                showDatePicker = false
            }
        }
    }

    val ticketText by remember(visiblePayments, textDate.text) {
        derivedStateOf {
            buildPaymentsTicketText(
                payments = visiblePayments,
                dateStr = textDate.text,
                collectorName = visiblePayments.firstOrNull()?.COBRADOR ?: "No especificado",
                title = "Reporte de Pagos Diarios"
            )
        }
    }

    DrawerContainer(navController = navController) { openDrawer ->
        Scaffold(
            modifier = Modifier.statusBarsPadding(),
            topBar = {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 12.dp),
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
                        text = "Reporte diario",
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
                            val payments =
                                (paymentsState as ResultState.Success<List<Payment>>).data
                            LaunchedEffect(payments) {
                                visiblePayments = payments
                            }

                            val context = LocalContext.current
                            val coroutineScope = rememberCoroutineScope()
                            var isGeneratingPdf by remember { mutableStateOf(false) }
                            val reportDate =
                                DateUtils.formatIsoDate(
                                    reportDateIso,
                                    "yyyy-MM-dd",
                                    Locale("es", "MX")
                                )

                            if (payments.isEmpty()) {
                                Text("No hay pagos para esta fecha.")
                            } else {
                                val formatter = DateTimeFormatter.ISO_DATE_TIME

                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    Button(
                                        onClick = {
                                            visiblePayments =
                                                visiblePayments.sortedBy { it.NOMBRE_CLIENTE }
                                        },
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Text(text = "ORD. POR NOMBRE", color = Color.White)
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
                                                    LocalTime.MIN
                                                }
                                            }
                                        },
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Text(text = "ORD. POR HORA", color = Color.White)
                                    }
                                }

                                Spacer(modifier = Modifier.height(6.dp))

                                LazyColumn(modifier = Modifier.weight(1f)) {
                                    item { Spacer(modifier = Modifier.height(2.dp)) }
                                    items(
                                        visiblePayments,
                                        key = { it.hashCode() }
                                    ) { pago ->
                                        PaymentItem(
                                            pago,
                                            variant = PaymentItemVariant.DEFAULT,
                                            navController = navController
                                        )
                                    }
                                    item { Spacer(modifier = Modifier.height(16.dp)) }
                                }

                                if (visiblePayments.isNotEmpty()) {
                                    Button(
                                        enabled = !isGeneratingPdf,
                                        onClick = {
                                            coroutineScope.launch {
                                                isGeneratingPdf = true

                                                val paymentTextData = formatPaymentsTextList(
                                                    visiblePayments
                                                )
                                                val visitTextData = formatVisitsTextList(
                                                    (visitsState as? ResultState.Success)?.data
                                                        ?: emptyList()
                                                )

                                                val file = withContext(Dispatchers.IO) {
                                                    PdfGenerator.generatePdfFromLines(
                                                        context = context,
                                                        data = paymentTextData,
                                                        visits = visitTextData,
                                                        title = "REPORTE DE PAGOS DIARIOS",
                                                        nameCollector = visiblePayments.firstOrNull()?.COBRADOR
                                                            ?: "No especificado",
                                                        fileName = "reporte_diario_$reportDate.pdf"
                                                    )
                                                }

                                                isGeneratingPdf = false

                                                if (file != null && file.exists()) {
                                                    val uri = FileProvider.getUriForFile(
                                                        context,
                                                        context.packageName + ".fileprovider",
                                                        file
                                                    )

                                                    val intent =
                                                        Intent(Intent.ACTION_VIEW).apply {
                                                            setDataAndType(
                                                                uri,
                                                                "application/pdf"
                                                            )
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
                                    ) {
                                        Text(
                                            text = if (isGeneratingPdf) "GENERANDO PDF..." else "GENERAR PDF",
                                            color = Color.White
                                        )
                                    }

                                    SelectBluetoothDevice(
                                        textToPrint = ticketText,
                                        modifier = Modifier.fillMaxWidth(),
                                        onPrintRequest = { device, text ->
                                            coroutineScope.launch {
                                                try {
                                                    ThermalPrinting.printText(
                                                        device,
                                                        text,
                                                        context
                                                    )
                                                } catch (e: Exception) {
                                                }
                                            }
                                        }
                                    )
                                }
                            }
                        }

                        is ResultState.Error -> {
                            Text("Error: ${(paymentsState as ResultState.Error).message}")
                        }

                        else -> {
                            Text("Selecciona una fecha para ver los pago.")
                        }
                    }
                }
            }
        }
    }
}

data class PaymentLineData(
    val date: String,
    val client: String,
    val amount: Double,
    val paymentMethod: PaymentMethod,
)

data class PaymentMethodBreakdown(
    val method: PaymentMethod,
    val count: Int,
    val amount: Double
)

data class PaymentTextData(
    val lines: List<PaymentLineData>,
    val totalCount: Int,
    val totalAmount: Double,
    val breakdownByMethod: List<PaymentMethodBreakdown> = emptyList()
)

data class VisitLineData(
    val date: String,
    val collector: String,
    val type: String,
    val note: String,
)

data class VisitTextData(
    val lines: List<VisitLineData>,
    val totalCount: Int
)

fun formatPaymentsTextList(payments: List<Payment>): PaymentTextData {
    val lines = payments.map { payment ->
        val formattedDate = DateUtils.formatIsoDate(
            payment.FECHA_HORA_PAGO,
            "dd/MM/yy HH:mm",
            Locale("es", "MX")
        )
        PaymentLineData(
            date = formattedDate,
            client = payment.NOMBRE_CLIENTE,
            amount = payment.IMPORTE,
            paymentMethod = PaymentMethod.fromId(payment.FORMA_COBRO_ID),
        )
    }

    val totalCount = payments.size
    val totalAmount = payments.sumOf { it.IMPORTE }
    val breakdownByMethod = payments
        .groupBy { PaymentMethod.fromId(it.FORMA_COBRO_ID) }
        .map { (method, payments) ->
            PaymentMethodBreakdown(
                method = method,
                count = payments.size,
                amount = payments.sumOf { it.IMPORTE }
            )
        }

    return PaymentTextData(lines, totalCount, totalAmount, breakdownByMethod)
}

fun formatVisitsTextList(visits: List<Visit>): VisitTextData {
    val lines = visits.map {
        VisitLineData(
            date = DateUtils.formatIsoDate(it.FECHA, "dd/MM/yy HH:mm", Locale("es", "MX")),
            collector = it.COBRADOR,
            type = it.TIPO_VISITA,
            note = it.NOTA ?: "-"
        )
    }
    return VisitTextData(lines, lines.size)
}