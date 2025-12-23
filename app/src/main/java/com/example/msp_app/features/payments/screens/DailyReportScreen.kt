package com.example.msp_app.features.payments.screens

import android.content.Intent
import android.net.Uri
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
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.material3.DatePickerDialog
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
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
import com.example.msp_app.features.payments.models.ForgivenessTextData
import com.example.msp_app.features.payments.models.PaymentLineData
import com.example.msp_app.features.payments.models.PaymentTextData
import com.example.msp_app.features.payments.models.VisitLineData
import com.example.msp_app.features.payments.models.VisitTextData
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
    title: String,
    forgiveness: List<Payment>
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

    if (forgiveness.isNotEmpty()) {
        builder.appendLine("-".repeat(32))
        builder.appendLine("Condonaciones:")
        builder.appendLine(" ".repeat(32))
        forgiveness.forEach { pago ->
            val date = DateUtils.formatIsoDate(pago.FECHA_HORA_PAGO, "HH:mm", Locale("es", "MX"))
            val client =
                pago.NOMBRE_CLIENTE.takeIf { it.length <= 16 } ?: pago.NOMBRE_CLIENTE.take(16)
            builder.appendLine(
                String.format("%-6s %-16s %8s", date, client, "$%,d".format(pago.IMPORTE.toInt()))
            )
        }
        builder.appendLine(" ".repeat(32))
        val totalForgiveness = forgiveness.sumOf { it.IMPORTE }.toInt()
        builder.appendLine("Total condonaciones: ${forgiveness.size}")
        builder.appendLine("Importe condonado: $%,d".format(totalForgiveness))
    }

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
    val forgivenessState by viewModel.forgivenessByDateState.collectAsState()
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
            viewModel.getForgivenessByDate(data.startIso, data.endIso)
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
                viewModel.getForgivenessByDate(data.startIso, data.endIso)
                showDatePicker = false
            }
        }
    }

    val forgivenessList = (forgivenessState as? ResultState.Success)?.data ?: emptyList()

    val ticketText by remember(visiblePayments, forgivenessList, textDate.text) {
        derivedStateOf {
            buildPaymentsTicketText(
                payments = visiblePayments,
                dateStr = textDate.text,
                forgiveness = forgivenessList,
                collectorName = visiblePayments.firstOrNull()?.COBRADOR ?: "No especificado",
                title = "Reporte de Pagos Diarios"
            )
        }
    }

    val paymentTextData = formatPaymentsTextList(
        visiblePayments
    )
    val visitTextData = formatVisitsTextList(
        (visitsState as? ResultState.Success)?.data
            ?: emptyList()
    )

    val forgivenessTextData = formatForgivenessTextList(
        (forgivenessState as? ResultState.Success)?.data
            ?: emptyList()
    )

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
                        .heightIn(min = 56.dp),
                    singleLine = true,
                )

                if (showDatePicker) {
                    DatePickerDialog(
                        onDismissRequest = { showDatePicker = false },
                        confirmButton = {
                            Button(onClick = { showDatePicker = false }) {
                                Text("Aceptar")
                            }
                        }
                    ) {
                        DatePicker(
                            state = datePickerState,
                            showModeToggle = false,
                        )
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
                            var pdfUri by remember { mutableStateOf<Uri?>(null) }
                            var showDialog by remember { mutableStateOf(false) }
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
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Button(
                                        onClick = {
                                            visiblePayments =
                                                visiblePayments.sortedBy { it.NOMBRE_CLIENTE }
                                        },
                                        modifier = Modifier
                                            .weight(1f)
                                            .heightIn(min = 48.dp)
                                    ) {
                                        Text(
                                            text = "ORD. POR NOMBRE",
                                            color = Color.White,
                                            textAlign = TextAlign.Center
                                        )
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
                                        modifier = Modifier
                                            .weight(1f)
                                            .heightIn(min = 48.dp)
                                    ) {
                                        Text(
                                            text = "ORD. POR HORA",
                                            color = Color.White,
                                            textAlign = TextAlign.Center
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(6.dp))

                                LazyColumn(modifier = Modifier.weight(1f)) {
                                    item { Spacer(modifier = Modifier.height(2.dp)) }
                                    items(
                                        visiblePayments,
                                        key = { it.ID }
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

                                                val file = withContext(Dispatchers.IO) {
                                                    PdfGenerator.generatePdfFromLines(
                                                        context = context,
                                                        data = paymentTextData,
                                                        visits = visitTextData,
                                                        title = "REPORTE DE PAGOS DIARIOS",
                                                        forgiveness = forgivenessTextData,
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

                                                    pdfUri = uri
                                                    showDialog = true
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
                                            .heightIn(min = 48.dp)
                                    ) {
                                        Text(
                                            text = if (isGeneratingPdf) "GENERANDO PDF..." else "GENERAR PDF",
                                            color = Color.White,
                                            maxLines = 1
                                        )
                                    }

                                    if (showDialog && pdfUri != null) {
                                        androidx.compose.material3.AlertDialog(
                                            onDismissRequest = {
                                                showDialog = false
                                                pdfUri = null
                                            },
                                            title = { Text("PDF Generado") },
                                            text = { Text("¿Deseas abrirlo o compartirlo?") },
                                            confirmButton = {
                                                Button(onClick = {
                                                    val openIntent =
                                                        Intent(Intent.ACTION_VIEW).apply {
                                                            setDataAndType(
                                                                pdfUri,
                                                                "application/pdf"
                                                            )
                                                            flags =
                                                                Intent.FLAG_ACTIVITY_NO_HISTORY or Intent.FLAG_GRANT_READ_URI_PERMISSION
                                                        }
                                                    context.startActivity(openIntent)
                                                    showDialog = false
                                                    pdfUri = null
                                                }) {
                                                    Text("Abrir", color = Color.White)
                                                }
                                            },
                                            dismissButton = {
                                                Button(onClick = {
                                                    val shareIntent =
                                                        Intent(Intent.ACTION_SEND).apply {
                                                            type = "application/pdf"
                                                            putExtra(Intent.EXTRA_STREAM, pdfUri)
                                                            flags =
                                                                Intent.FLAG_GRANT_READ_URI_PERMISSION
                                                        }
                                                    context.startActivity(
                                                        Intent.createChooser(
                                                            shareIntent,
                                                            "Compartir PDF"
                                                        )
                                                    )
                                                    showDialog = false
                                                    pdfUri = null
                                                }) {
                                                    Text("Compartir", color = Color.White)
                                                }
                                            }
                                        )
                                    }

                                    Spacer(modifier = Modifier.height(8.dp))

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

data class PaymentMethodBreakdown(
    val method: PaymentMethod,
    val count: Int,
    val amount: Double
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

fun formatForgivenessTextList(forgiveness: List<Payment>): ForgivenessTextData {
    val lines = forgiveness.map { payment ->
        PaymentLineData(
            date = DateUtils.formatIsoDate(
                payment.FECHA_HORA_PAGO,
                "dd/MM/yy HH:mm",
                Locale("es", "MX")
            ),
            client = payment.NOMBRE_CLIENTE,
            amount = payment.IMPORTE,
            paymentMethod = PaymentMethod.fromId(payment.FORMA_COBRO_ID),
        )
    }
    val totalCount = forgiveness.size
    val totalAmount = forgiveness.sumOf { it.IMPORTE }
    return ForgivenessTextData(lines, totalCount, totalAmount)
}