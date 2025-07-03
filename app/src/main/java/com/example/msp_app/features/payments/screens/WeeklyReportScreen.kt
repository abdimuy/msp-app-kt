package com.example.msp_app.features.payments.screens

import android.content.Intent
import android.os.Build
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.example.msp_app.components.DrawerContainer
import com.example.msp_app.components.selectbluetoothdevice.SelectBluetoothDevice
import com.example.msp_app.core.context.LocalAuthViewModel
import com.example.msp_app.core.utils.DateUtils
import com.example.msp_app.core.utils.PdfGenerator
import com.example.msp_app.core.utils.ResultState
import com.example.msp_app.core.utils.ThermalPrinting
import com.example.msp_app.data.models.payment.Payment
import com.example.msp_app.features.payments.components.paymentitem.PaymentItem
import com.example.msp_app.features.payments.components.paymentitem.PaymentItemVariant
import com.example.msp_app.features.payments.viewmodels.PaymentsViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.temporal.ChronoUnit
import java.util.Locale

@RequiresApi(Build.VERSION_CODES.S)
@Composable
fun WeeklyReportScreen(
    navController: NavController,
    viewModel: PaymentsViewModel = viewModel()
) {
    val scrollState = rememberScrollState()
    val paymentsState by viewModel.paymentsByDateState.collectAsState()
    var visiblePayments by remember { mutableStateOf<List<Payment>>(emptyList()) }

    val authViewModel = LocalAuthViewModel.current
    val userDataState by authViewModel.userData.collectAsState()

    val startIso = remember(userDataState) {
        val startDate = (userDataState as? ResultState.Success)?.data?.FECHA_CARGA_INICIAL
        DateUtils.parseDateToIso(startDate?.toDate())
    } ?: DateUtils.parseDateToIso(null)

    val endIso = DateUtils.addToIsoDate(
        DateUtils.addToIsoDate(startIso, 6, ChronoUnit.DAYS),
        -1, ChronoUnit.SECONDS
    )

    LaunchedEffect(startIso) {
        viewModel.getPaymentsByDate(startIso, endIso)
    }

    fun formatPaymentsTextList(payments: List<Payment>): PaymentTextData {
        return PaymentTextData(
            lines = payments.map {
                val date = DateUtils.formatIsoDate(it.FECHA_HORA_PAGO, "dd/MM/yyyy HH:mm a")
                Triple(date, it.NOMBRE_CLIENTE, it.IMPORTE)
            },
            totalCount = payments.size,
            totalAmount = payments.sumOf { it.IMPORTE }
        )
    }

    fun formatPaymentsTextForTicket(
        payments: List<Payment>,
        dateStr: String,
        collectorName: String,
        title: String
    ): String {
        val builder = StringBuilder()

        builder.appendLine("=".repeat(32))
        builder.appendLine(ThermalPrinting.centerText(title, 32))
        builder.appendLine("Fecha: $dateStr")
        builder.appendLine("Cobrador: $collectorName")
        builder.appendLine("-".repeat(32))
        builder.appendLine(String.format("%-8s %-14s %8s", "Fecha", "Cliente", "Importe"))

        payments.forEach { pago ->
            val date =
                DateUtils.formatIsoDate(pago.FECHA_HORA_PAGO, "dd/MM/yy", Locale("es", "MX"))
            val client = pago.NOMBRE_CLIENTE.take(14)
            val amount = "$%,d".format(pago.IMPORTE.toInt())

            builder.appendLine(String.format("%-8s %-14s %8s", date, client, amount))
        }

        builder.appendLine("-".repeat(32))
        builder.appendLine("Total pagos: ${payments.size}")
        builder.appendLine("Total importe: $${"%,d".format(payments.sumOf { it.IMPORTE }.toInt())}")
        builder.appendLine("=".repeat(32))
        builder.appendLine("!!!GRACIAS POR SU PREFERENCIA!!!")

        return builder.toString()
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
                        Icon(imageVector = Icons.Default.Menu, contentDescription = "Menú")
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Text("Reporte Semanal", style = MaterialTheme.typography.titleLarge)
                }
            }
        ) { innerPadding ->
            Column(
                modifier = Modifier
                    .padding(innerPadding)
                    .padding(horizontal = 16.dp)
                    .fillMaxSize()
            ) {
                when (paymentsState) {
                    is ResultState.Loading -> Text("Cargando pagos...")
                    is ResultState.Success -> {
                        val payments = (paymentsState as ResultState.Success<List<Payment>>).data
                        LaunchedEffect(payments) { visiblePayments = payments }

                        if (payments.isEmpty()) {
                            Text("No hay pagos en esta semana.")
                        } else {
                            val context = LocalContext.current
                            val coroutineScope = rememberCoroutineScope()
                            var isGeneratingPdf by remember { mutableStateOf(false) }

                            val dateStr = "Del ${
                                DateUtils.formatIsoDate(
                                    startIso,
                                    "dd/MM/yy"
                                )
                            } al ${DateUtils.formatIsoDate(endIso, "dd/MM/yy")}"
                            val ticketText = formatPaymentsTextForTicket(
                                payments = visiblePayments,
                                dateStr = dateStr,
                                collectorName = visiblePayments.firstOrNull()?.COBRADOR
                                    ?: "No especificado",
                                title = "REPORTE SEMANAL"
                            )

                            Column(modifier = Modifier.weight(1f)) {
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
                                        Text("Ord. por nombre")
                                    }

                                    Button(
                                        onClick = {
                                            visiblePayments = visiblePayments.sortedBy {
                                                DateUtils.parseIsoToDateTime(it.FECHA_HORA_PAGO)
                                            }
                                        },
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Text("Ord. por fecha")
                                    }
                                }

                                Column(
                                    modifier = Modifier
                                        .weight(1f)
                                        .verticalScroll(scrollState)
                                ) {
                                    Spacer(modifier = Modifier.height(2.dp))
                                    visiblePayments.forEach { pago ->
                                        PaymentItem(
                                            pago,
                                            variant = PaymentItemVariant.DEFAULT,
                                            navController = navController
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(16.dp))
                                }

                                Button(
                                    enabled = !isGeneratingPdf,
                                    onClick = {
                                        coroutineScope.launch {
                                            isGeneratingPdf = true
                                            val paymentTextData =
                                                formatPaymentsTextList(visiblePayments)
                                            val file = withContext(Dispatchers.IO) {
                                                PdfGenerator.generatePdfFromLines(
                                                    context = context,
                                                    data = paymentTextData,
                                                    title = "REPORTE DE PAGOS SEMANAL",
                                                    nameCollector = visiblePayments.firstOrNull()?.COBRADOR
                                                        ?: "No especificado",
                                                    fileName = "reporte_semanal.pdf"
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
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(if (isGeneratingPdf) "Generando PDF..." else "Generar PDF")
                                }

                                if (visiblePayments.isNotEmpty()) {
                                    SelectBluetoothDevice(
                                        textToPrint = ticketText,
                                        modifier = Modifier.fillMaxWidth(),
                                        onPrintRequest = { device, text ->
                                            coroutineScope.launch {
                                                try {
                                                    ThermalPrinting.printText(device, text, context)
                                                } catch (e: Exception) {
                                                }
                                            }
                                        }
                                    )
                                }
                            }
                        }
                    }

                    is ResultState.Error -> {
                        Text("Error: ${(paymentsState as ResultState.Error).message}")
                    }

                    else -> {
                        Text("Cargando datos...")
                    }
                }
            }
        }
    }
}

