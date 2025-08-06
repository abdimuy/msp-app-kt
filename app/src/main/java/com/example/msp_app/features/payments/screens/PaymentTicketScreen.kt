package com.example.msp_app.features.payments.screens

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.example.msp_app.components.DrawerContainer
import com.example.msp_app.components.selectbluetoothdevice.SelectBluetoothDevice
import com.example.msp_app.core.context.LocalAuthViewModel
import com.example.msp_app.core.models.PaymentMethod
import com.example.msp_app.core.utils.Constants
import com.example.msp_app.core.utils.DateUtils
import com.example.msp_app.core.utils.ResultState
import com.example.msp_app.core.utils.ThermalPrinting
import com.example.msp_app.core.utils.toCurrency
import com.example.msp_app.data.models.auth.User
import com.example.msp_app.data.models.payment.Payment
import com.example.msp_app.data.models.product.Product
import com.example.msp_app.data.models.sale.Sale
import com.example.msp_app.features.payments.components.newpaymentpdf.PaymentPdfGenerator
import com.example.msp_app.features.payments.viewmodels.PaymentsViewModel
import com.example.msp_app.features.products.viewmodels.ProductsViewModel
import com.example.msp_app.features.sales.components.infofield.InfoField
import com.example.msp_app.features.sales.viewmodels.SaleDetailsViewModel
import com.example.msp_app.ui.theme.ThemeController
import kotlinx.coroutines.launch
import java.io.File
import java.util.Locale

data class PaymentLine(
    val date: String,
    val amount: Double,
    val method: String
)

@RequiresApi(Build.VERSION_CODES.S)
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PaymentTicketScreen(
    paymentId: String,
    navController: NavController
) {
    val isDark = ThemeController.isDarkMode
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val paymentsViewModel: PaymentsViewModel = viewModel()
    val saleViewModel: SaleDetailsViewModel = viewModel()
    val productsViewModel: ProductsViewModel = viewModel()

    val paymentResult by paymentsViewModel.paymentState.collectAsState()
    val saleResult by saleViewModel.saleState.collectAsState()
    val productsResult by productsViewModel.productsByFolioState.collectAsState()

    var selectedPayment by remember { mutableStateOf<Payment?>(null) }
    val isCondonacion = selectedPayment?.let {
        PaymentMethod.fromId(it.FORMA_COBRO_ID).label.equals(
            PaymentMethod.CONDONACION.label,
            ignoreCase = true
        )
    } == true
    var ticket by remember { mutableStateOf<String?>(null) }
    var saleDisponible by remember { mutableStateOf(false) }

    val authViewModel = LocalAuthViewModel.current
    val userDataState by authViewModel.userData.collectAsState()
    val paymentsState by paymentsViewModel.paymentsBySaleIdState.collectAsState()

    val userData = when (userDataState) {
        is ResultState.Success -> (userDataState as ResultState.Success<User?>).data
        else -> null
    }

    LaunchedEffect(paymentId) {
        paymentsViewModel.getPaymentById(paymentId)
    }

    LaunchedEffect(paymentResult) {
        if (paymentResult is ResultState.Success) {
            val payment = (paymentResult as ResultState.Success<Payment>).data
            selectedPayment = payment
            saleViewModel.loadSaleDetails(payment.DOCTO_CC_ACR_ID)
            paymentsViewModel.getPaymentsBySaleId(payment.DOCTO_CC_ACR_ID)
        }
    }

    LaunchedEffect(saleResult) {
        if (saleResult is ResultState.Success) {
            val sale = (saleResult as ResultState.Success<Sale?>).data
            sale?.let {
                productsViewModel.getProductsByFolio(it.FOLIO)
            }
        }
    }
    fun buildPaymentLines(payments: List<Payment>): List<String> {
        return payments.asReversed().map { pago ->
            val datepayment = DateUtils.formatIsoDate(
                iso = pago.FECHA_HORA_PAGO,
                pattern = "dd/MM/yyyy",
                locale = Locale("es", "MX")
            )
            "ABONO: $datepayment - ${pago.IMPORTE.toCurrency(noDecimals = true)}"
        }
    }

    fun buildPaymentLineData(payments: List<Payment>): List<PaymentLine> {
        return payments.asReversed().map { pago ->
            val date = DateUtils.formatIsoDate(
                iso = pago.FECHA_HORA_PAGO,
                pattern = "EEEE, dd/MM/yyyy",
                locale = Locale("es", "MX")
            ).replaceFirstChar { it.uppercaseChar() }

            val method = PaymentMethod.fromId(pago.FORMA_COBRO_ID).label

            PaymentLine(
                date = date,
                amount = pago.IMPORTE,
                method = method
            )
        }
    }

    DrawerContainer(
        navController = navController
    ) { openDrawer ->
        Scaffold { innerPadding ->
            Box(
                modifier = Modifier
                    .padding(innerPadding)
                    .fillMaxSize(), contentAlignment = Alignment.TopCenter
            ) {
                var finalSale: Sale? = null
                var finalProductos: List<Product> = emptyList()
                var finalSaldo: Double
                var pdfUri by remember { mutableStateOf<Uri?>(null) }
                var showDialog by remember { mutableStateOf(false) }
                var finalFormattedDate by remember { mutableStateOf("") }
                var isGeneratingPdf by remember { mutableStateOf(false) }
                val pagos = (paymentsState as? ResultState.Success)?.data ?: emptyList()

                Column(modifier = Modifier.padding(bottom = 110.dp)) {
                    when {
                        selectedPayment == null -> Text("Cargando pago...")
                        saleResult is ResultState.Loading -> Text("Cargando venta...")
                        productsResult is ResultState.Loading -> Text("Cargando productos...")
                        saleResult is ResultState.Error -> Text("Error: ${(saleResult as ResultState.Error).message}")
                        productsResult is ResultState.Error -> Text("Error: ${(productsResult as ResultState.Error).message}")

                        saleResult is ResultState.Success && productsResult is ResultState.Success -> {
                            val sale = (saleResult as ResultState.Success<Sale?>).data
                            val products =
                                (productsResult as ResultState.Success<List<Product>>).data
                            finalSale = sale
                            finalProductos = products

                            val saldoAnterior = (selectedPayment?.IMPORTE ?: 0.0) +
                                    (sale?.SALDO_REST ?: 0.0)

                            finalSaldo = saldoAnterior

                            val date = DateUtils.formatIsoDate(
                                iso = selectedPayment!!.FECHA_HORA_PAGO,
                                pattern = "dd/MM/yy HH:mm",
                                locale = Locale("es", "MX")
                            )
                            finalFormattedDate = date
                            val lineBlanck = (" ").repeat(32)

                            ticket = buildString {
                                appendLine(
                                    ThermalPrinting.centerText(
                                        if (isCondonacion) "TICKET DE CONDONACION" else "TICKET DE PAGO",
                                        32
                                    )
                                )
                                appendLine(lineBlanck)
                                appendLine("FOLIO: ${sale?.FOLIO}")
                                appendLine("CLIENTE: ${sale?.CLIENTE}")
                                appendLine("DIRECCION: ${sale?.CALLE} ${sale?.CIUDAD} ${sale?.ESTADO}")
                                appendLine("TELEFONO: ${sale?.TELEFONO}")
                                appendLine("FECHA VENTA: ${sale?.FECHA}")
                                appendLine(
                                    "PRECIO TOTAL: ${
                                        sale?.PRECIO_TOTAL?.toCurrency(
                                            noDecimals = true
                                        )
                                    }"
                                )
                                appendLine(
                                    "PRECIO A ${sale?.TIEMPO_A_CORTO_PLAZOMESES} MESES: ${
                                        sale?.MONTO_A_CORTO_PLAZO?.toCurrency(
                                            noDecimals = true
                                        )
                                    }"
                                )
                                sale?.PRECIO_DE_CONTADO
                                    ?.takeIf { it > 0.0 }
                                    ?.let {
                                        appendLine("PRECIO DE CONTADO: ${it.toCurrency(noDecimals = true)}")
                                    }
                                appendLine("ENGANCHE: ${sale?.ENGANCHE?.toCurrency(noDecimals = true)}")
                                appendLine("PARCIALIDAD: ${sale?.PARCIALIDAD?.toCurrency(noDecimals = true)}")
                                appendLine("VENDEDORES:")
                                appendLine("- ${sale?.VENDEDOR_1}")
                                sale?.VENDEDOR_2?.takeIf { it.isNotBlank() }?.let {
                                    appendLine("- $it")
                                }
                                sale?.VENDEDOR_3?.takeIf { it.isNotBlank() }?.let {
                                    appendLine("- $it")
                                }
                                appendLine(lineBlanck)
                                appendLine("-".repeat(32))
                                appendLine(ThermalPrinting.centerText("PRODUCTOS", 32))
                                appendLine(lineBlanck)
                                products.forEach { product ->
                                    appendLine(
                                        "- ${product.ARTICULO}: ${
                                            product.PRECIO_UNITARIO_IMPTO?.toCurrency(
                                                noDecimals = true
                                            )
                                        } x ${product.CANTIDAD}"
                                    )
                                }
                                appendLine(lineBlanck)
                                appendLine("-".repeat(32))
                                appendLine(lineBlanck)
                                appendLine(if (isCondonacion) "FECHA DE CONDONACION: ${date}" else "FECHA DE PAGO: ${date}")
                                appendLine(
                                    "SALDO ANTERIOR: ${
                                        ThermalPrinting.bold(
                                            saldoAnterior?.toCurrency(
                                                noDecimals = true
                                            ) ?: ""
                                        )
                                    }"
                                )
                                appendLine(
                                    "ABONADO: ${
                                        ThermalPrinting.bold(
                                            selectedPayment?.IMPORTE?.toCurrency(
                                                noDecimals = true
                                            ) ?: ""
                                        )
                                    }"
                                )
                                appendLine(
                                    "SALDO ACTUAL: ${
                                        ThermalPrinting.bold(
                                            sale?.SALDO_REST?.toCurrency(
                                                noDecimals = true
                                            ) ?: ""
                                        )
                                    }"
                                )
                                appendLine(lineBlanck)
                                appendLine("-".repeat(32))
                                appendLine(ThermalPrinting.centerText("HISTORIAL DE PAGOS", 32))
                                appendLine(lineBlanck)
                                val pago =
                                    (paymentsState as? ResultState.Success)?.data ?: emptyList()
                                val paymentLines = buildPaymentLines(pago).take(4)
                                paymentLines.forEach { appendLine(it) }
                                appendLine(lineBlanck)
                                appendLine("-".repeat(32))
                                appendLine(lineBlanck)
                                appendLine(
                                    ThermalPrinting.centerText(
                                        "EXIJA SU COMPROBANTE DE PAGO",
                                        32
                                    )
                                )
                                appendLine("!!GRACIAS POR SU PREFERENCIA!!")
                                appendLine(lineBlanck)
                                appendLine("TELEFONO: ${Constants.TELEFONO}")
                                appendLine("WHATSAPP: ${Constants.WHATSAPP}")
                                appendLine("AGENTE:${userData?.NOMBRE ?: ""}")
                                appendLine("TELEFONO DEL AGENTE: ${userData?.TELEFONO ?: ""}")
                            }

                            saleDisponible = true

                            Column {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    IconButton(
                                        onClick = openDrawer,
                                        modifier = Modifier.padding(10.dp)
                                    ) {
                                        Icon(
                                            Icons.Default.Menu,
                                            contentDescription = "Menú"
                                        )
                                    }
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .padding(end = 48.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = if (isCondonacion) "Ticket de Condonación" else "Ticket de Pago",
                                            fontSize = 24.sp,
                                            fontWeight = FontWeight.Bold,
                                        )
                                    }
                                }
                                OutlinedCard(
                                    elevation = CardDefaults.cardElevation(defaultElevation = if (isDark) 0.dp else 6.dp),
                                    colors = CardDefaults.cardColors(
                                        containerColor = MaterialTheme.colorScheme.background
                                    ),
                                    border = BorderStroke(
                                        width = 1.dp,
                                        color = if (isDark) Color.Gray else Color.Transparent
                                    ),
                                    modifier = Modifier
                                        .fillMaxWidth(0.92f)
                                        .align(alignment = Alignment.CenterHorizontally)
                                        .background(Color.White, RoundedCornerShape(16.dp))
                                ) {
                                    val scrollState = rememberScrollState()
                                    val vendedores = listOfNotNull(
                                        sale?.VENDEDOR_1,
                                        sale?.VENDEDOR_2,
                                        sale?.VENDEDOR_3
                                    ).filter { it.isNotBlank() } // Elimina strings vacíos
                                        .joinToString(separator = ", ")

                                    if (sale != null) {
                                        Column(
                                            modifier = Modifier
                                                .verticalScroll(scrollState)
                                                .padding(5.dp)
                                        ) {
                                            InfoField(label = "FOLIO:", value = sale.FOLIO)
                                            InfoField(label = "CLIENTE:", value = sale.CLIENTE)
                                            InfoField(
                                                label = "DIRECCIÓN:",
                                                value = "${sale.CALLE} ${sale.CIUDAD} ${sale.ESTADO}"
                                            )
                                            InfoField(label = "TELÉFONO:", value = sale.TELEFONO)
                                            InfoField(label = "FECHA VENTA:", value = sale.FECHA)
                                            InfoField(
                                                label = "PRECIO TOTAL:",
                                                value = "${sale.PRECIO_TOTAL.toCurrency(noDecimals = true)}"
                                            )
                                            InfoField(
                                                label = "PRECIO A ${sale.TIEMPO_A_CORTO_PLAZOMESES} MESES:",
                                                value = "${
                                                    sale.MONTO_A_CORTO_PLAZO.toCurrency(
                                                        noDecimals = true
                                                    )
                                                }"
                                            )
                                            InfoField(
                                                label = "PRECIO DE CONTADO:",
                                                value = "${
                                                    sale.PRECIO_DE_CONTADO.toCurrency(
                                                        noDecimals = true
                                                    )
                                                }"
                                            )
                                            InfoField(
                                                label = "ENGANCHE:",
                                                value = "${sale.ENGANCHE.toCurrency(noDecimals = true)}"
                                            )
                                            InfoField(
                                                label = "PARCIALIDAD:",
                                                value = "${sale.PARCIALIDAD.toCurrency(noDecimals = true)}"
                                            )
                                            InfoField(
                                                label = "VENDEDORES:",
                                                value = vendedores
                                            )
                                            Spacer(Modifier.height(12.dp))
                                            Text(
                                                "PRODUCTOS",
                                                style = MaterialTheme.typography.titleMedium
                                            )
                                            products.forEach { product ->
                                                InfoField(
                                                    label = "- ${product.ARTICULO}:",
                                                    value = "${
                                                        product.PRECIO_UNITARIO_IMPTO.toCurrency(
                                                            noDecimals = true
                                                        )
                                                    } x ${product.CANTIDAD}"
                                                )
                                            }
                                            Spacer(Modifier.height(12.dp))
                                            InfoField(
                                                label = if (isCondonacion) "FECHA DE CONDONACIÓN:" else "FECHA DE PAGO: ",
                                                value = date
                                            )
                                            InfoField(
                                                label = "SALDO ANTERIOR:",
                                                value = "${saldoAnterior.toCurrency(noDecimals = true)}"
                                            )
                                            InfoField(
                                                label = if (isCondonacion) "CONDONACIÓN:" else "ABONADO:",
                                                value = "${
                                                    selectedPayment?.IMPORTE?.toCurrency(
                                                        noDecimals = true
                                                    ) ?: ""
                                                }"
                                            )
                                            InfoField(
                                                label = "SALDO ACTUAL:",
                                                value = "${sale.SALDO_REST.toCurrency(noDecimals = true)}"
                                            )
                                            Spacer(Modifier.height(12.dp))
                                            Text(" HISTORIAL DE PAGOS ")
                                            Spacer(Modifier.height(8.dp))
                                            when (paymentsState) {
                                                is ResultState.Success -> {
                                                    Column {
                                                        val lines = buildPaymentLines(pagos)
                                                        lines.forEach { line ->
                                                            Text(text = line)
                                                        }
                                                    }
                                                }

                                                is ResultState.Loading -> {
                                                    CircularProgressIndicator()
                                                }

                                                is ResultState.Error -> {
                                                    Text("Error: ${(paymentsState as ResultState.Error).message}")
                                                }

                                                else -> {
                                                    // Estado Idle o vacío
                                                }
                                            }
                                            Spacer(Modifier.height(12.dp))
                                            Text(" EXIJA SU COMPROBANTE DE PAGO ")
                                            Text("!!GRACIAS POR SU PREFERENCIA!!")
                                            Spacer(Modifier.height(12.dp))
                                            InfoField(
                                                label = "TELÉFONO:",
                                                value = "${Constants.TELEFONO}"
                                            )
                                            InfoField(
                                                label = "WHATSAPP:",
                                                value = "${Constants.WHATSAPP}"
                                            )
                                            InfoField(
                                                label = "AGENTE:",
                                                value = userData?.NOMBRE ?: ""
                                            )
                                            InfoField(
                                                label = "TELÉFONO DEL AGENTE:",
                                                value = userData?.TELEFONO ?: ""
                                            )
                                        }
                                    } else {
                                        Text("No se encontró la venta.")
                                    }
                                }
                            }
                        }
                    }
                }

                Column {
                    Box(modifier = Modifier.fillMaxSize()) {
                        if (saleDisponible && ticket != null) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(8.dp)
                                    .align(alignment = Alignment.BottomCenter),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Button(
                                    modifier = Modifier
                                        .padding(2.dp)
                                        .fillMaxWidth(),
                                    onClick = {
                                        coroutineScope.launch {
                                            isGeneratingPdf = true
                                            PaymentPdfGenerator.paymentStructuredPdf(
                                                context = context,
                                                folio = finalSale?.FOLIO ?: "",
                                                fechaVenta = finalSale?.FECHA ?: "",
                                                cliente = finalSale?.CLIENTE ?: "",
                                                direccion = "${finalSale?.CALLE} ${finalSale?.CIUDAD} ${finalSale?.ESTADO}",
                                                telefono = finalSale?.TELEFONO ?: "",
                                                enganche = finalSale?.ENGANCHE?.toCurrency(
                                                    noDecimals = true
                                                )
                                                    .toString(),
                                                parcialidad = finalSale?.PARCIALIDAD?.toCurrency(
                                                    noDecimals = true
                                                )
                                                    .toString(),
                                                precioTotal = finalSale?.PRECIO_TOTAL?.toCurrency(
                                                    noDecimals = true
                                                )
                                                    .toString(),
                                                precioMeses = finalSale?.MONTO_A_CORTO_PLAZO?.toCurrency(
                                                    noDecimals = true
                                                ).toString(),
                                                numeroMeses = finalSale?.TIEMPO_A_CORTO_PLAZOMESES.toString(),
                                                precioContado = finalSale?.PRECIO_DE_CONTADO?.toCurrency(
                                                    noDecimals = true
                                                )
                                                    .orEmpty(),
                                                productos = finalProductos.map {
                                                    "${it.ARTICULO}: ${
                                                        it.PRECIO_UNITARIO_IMPTO?.toCurrency(
                                                            noDecimals = true
                                                        )
                                                    } x ${it.CANTIDAD}"
                                                },
                                                fechaPago = finalFormattedDate,
                                                saldoAnterior = ((selectedPayment?.IMPORTE
                                                    ?: 0.0) + (finalSale!!.SALDO_REST)).toCurrency(
                                                    noDecimals = true
                                                ),
                                                abonado = selectedPayment?.IMPORTE?.toCurrency(
                                                    noDecimals = true
                                                )
                                                    .orEmpty(),
                                                saldoActual = finalSale?.SALDO_REST?.toCurrency(
                                                    noDecimals = true
                                                )
                                                    .orEmpty(),
                                                historialpago = buildPaymentLineData(pagos),
                                                onComplete = { success, path ->
                                                    if (success && path != null) {
                                                        val file = File(path)
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
                                                    isGeneratingPdf = false
                                                }
                                            )
                                        }
                                    }
                                ) {
                                    Text(
                                        text = if (isGeneratingPdf) "GENERANDO PDF..." else "GENERAR PDF",
                                        color = Color.White
                                    )
                                }

                                SelectBluetoothDevice(
                                    textToPrint = ticket!!,
                                    modifier = Modifier
                                        .fillMaxWidth(),
                                    onPrintRequest = { device, text ->
                                        coroutineScope.launch {
                                            try {
                                                ThermalPrinting.printText(device, text, context)
                                            } catch (_: Exception) {
                                            }
                                        }
                                    }
                                )

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
                                                val openIntent = Intent(Intent.ACTION_VIEW).apply {
                                                    setDataAndType(pdfUri, "application/pdf")
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
                                                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                                    type = "application/pdf"
                                                    putExtra(Intent.EXTRA_STREAM, pdfUri)
                                                    flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
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
                            }
                        }
                    }
                }
            }
        }
    }
}
