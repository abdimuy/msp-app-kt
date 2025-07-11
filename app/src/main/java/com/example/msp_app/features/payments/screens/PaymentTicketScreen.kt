package com.example.msp_app.features.payments.screens

import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
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
import androidx.compose.material3.CardDefaults
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
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.example.msp_app.components.DrawerContainer
import com.example.msp_app.components.selectbluetoothdevice.SelectBluetoothDevice
import com.example.msp_app.core.context.LocalAuthViewModel
import com.example.msp_app.core.utils.Constants
import com.example.msp_app.core.utils.DateUtils
import com.example.msp_app.core.utils.ResultState
import com.example.msp_app.core.utils.ThermalPrinting
import com.example.msp_app.core.utils.toCurrency
import com.example.msp_app.data.models.auth.User
import com.example.msp_app.data.models.payment.Payment
import com.example.msp_app.data.models.product.Product
import com.example.msp_app.data.models.sale.Sale
import com.example.msp_app.features.payments.viewmodels.PaymentsViewModel
import com.example.msp_app.features.products.viewmodels.ProductsViewModel
import com.example.msp_app.features.sales.components.infofield.InfoField
import com.example.msp_app.features.sales.viewmodels.SaleDetailsViewModel
import kotlinx.coroutines.launch
import java.util.Locale

@RequiresApi(Build.VERSION_CODES.S)
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PaymentTicketScreen(
    paymentId: String,
    navController: NavController
) {
    val isDark = isSystemInDarkTheme()
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val paymentsViewModel: PaymentsViewModel = viewModel()
    val saleViewModel: SaleDetailsViewModel = viewModel()
    val productsViewModel: ProductsViewModel = viewModel()

    val paymentResult by paymentsViewModel.paymentState.collectAsState()
    val saleResult by saleViewModel.saleState.collectAsState()
    val productsResult by productsViewModel.productsByFolioState.collectAsState()

    var selectedPayment by remember { mutableStateOf<Payment?>(null) }
    var ticket by remember { mutableStateOf<String?>(null) }
    var saleDisponible by remember { mutableStateOf(false) }

    val authViewModel = LocalAuthViewModel.current
    val userDataState by authViewModel.userData.collectAsState()

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

    DrawerContainer(navController = navController) { openDrawer ->
        Scaffold { innerPading ->
            Box(
                modifier = Modifier
                    .padding(innerPading)
                    .fillMaxSize(), contentAlignment = Alignment.TopCenter
            ) {
                when {
                    selectedPayment == null -> Text("Cargando pago...")
                    saleResult is ResultState.Loading -> Text("Cargando venta...")
                    productsResult is ResultState.Loading -> Text("Cargando productos...")
                    saleResult is ResultState.Error -> Text("Error: ${(saleResult as ResultState.Error).message}")
                    productsResult is ResultState.Error -> Text("Error: ${(productsResult as ResultState.Error).message}")

                    saleResult is ResultState.Success && productsResult is ResultState.Success -> {
                        val sale = (saleResult as ResultState.Success<Sale?>).data
                        val products = (productsResult as ResultState.Success<List<Product>>).data

                        val saldoAnterior = (selectedPayment?.IMPORTE ?: 0.0) +
                                (sale?.SALDO_REST ?: 0.0)

                        val date = DateUtils.formatIsoDate(
                            iso = selectedPayment!!.FECHA_HORA_PAGO,
                            pattern = "dd/MM/yy hh:mm a",
                            locale = Locale("es", "MX")
                        )
                        val lineBlanck = (" ").repeat(32)

                        ticket = buildString {
                            appendLine(ThermalPrinting.centerText("TICKET DE PAGO", 32))
                            appendLine(lineBlanck)
                            appendLine("FOLIO: ${sale?.FOLIO}")
                            appendLine("CLIENTE: ${sale?.CLIENTE}")
                            appendLine("DIRECCION: ${sale?.CALLE} ${sale?.CIUDAD} ${sale?.ESTADO}")
                            appendLine("TELEFONO: ${sale?.TELEFONO}")
                            appendLine("FECHA VENTA: ${sale?.FECHA}")
                            appendLine("PRECIO TOTAL: ${sale?.PRECIO_TOTAL?.toCurrency(noDecimals = true)}")
                            appendLine(
                                "PRECIO A ${sale?.TIEMPO_A_CORTO_PLAZOMESES} MESES: ${
                                    sale?.MONTO_A_CORTO_PLAZO?.toCurrency(
                                        noDecimals = true
                                    )
                                }"
                            )
                            appendLine(
                                "PRECIO DE CONTADO: ${
                                    sale?.PRECIO_DE_CONTADO?.toCurrency(
                                        noDecimals = true
                                    )
                                }"
                            )
                            appendLine("ENGANCHE: ${sale?.ENGANCHE?.toCurrency(noDecimals = true)}")
                            appendLine("PARCIALIDAD: ${sale?.PARCIALIDAD?.toCurrency(noDecimals = true)}")
                            appendLine("VENDEDORES:${sale?.VENDEDOR_1}")
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
                            appendLine("FECHA DE PAGO:${date}")
                            appendLine("SALDO ANTERIOR: ${saldoAnterior?.toCurrency(noDecimals = true)}")
                            appendLine("ABONADO: ${selectedPayment?.IMPORTE?.toCurrency(noDecimals = true)}")
                            appendLine("SALDO ACTUAL: ${sale?.SALDO_REST?.toCurrency(noDecimals = true)}")
                            appendLine(lineBlanck)
                            appendLine("-".repeat(32))
                            appendLine(ThermalPrinting.centerText("HISTORIAL DE PAGOS", 32))
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
                            appendLine("TELÉFONO: ${Constants.TELEFONO}")
                            appendLine("WHATSAPP: ${Constants.WHATSAPP}")
                            appendLine("AGENTE:${userData?.NOMBRE ?: ""}")
                            appendLine("TELÉFONO DEL AGENTE: ${userData?.TELEFONO ?: ""}")
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
                                        contentDescription = "Menú",
                                        tint = Color.White
                                    )
                                }
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .padding(end = 48.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "Ticket de Pago",
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
                                    .padding(bottom = 42.dp)
                            ) {
                                val scrollState = rememberScrollState()
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
                                            value = "${sale.PRECIO_DE_CONTADO.toCurrency(noDecimals = true)}"
                                        )
                                        InfoField(
                                            label = "ENGANCHE:",
                                            value = "${sale.ENGANCHE.toCurrency(noDecimals = true)}"
                                        )
                                        InfoField(
                                            label = "PARCIALIDAD:",
                                            value = "${sale.PARCIALIDAD.toCurrency(noDecimals = true)}"
                                        )
                                        InfoField(label = "VENDEDORES:", value = sale.VENDEDOR_1)
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
                                            label = "FECHA DE PAGO:",
                                            value = date
                                        )
                                        InfoField(
                                            label = "SALDO ANTERIOR:",
                                            value = "${saldoAnterior.toCurrency(noDecimals = true)}"
                                        )
                                        InfoField(
                                            label = "ABONADO:",
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
                                        InfoField(label = "AGENTE:", value = userData?.NOMBRE ?: "")
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
                if (saleDisponible && ticket != null) {
                    SelectBluetoothDevice(
                        textToPrint = ticket!!,
                        modifier = Modifier
                            .fillMaxWidth()
                            .align(alignment = Alignment.BottomCenter),
                        onPrintRequest = { device, text ->
                            coroutineScope.launch {
                                try {
                                    ThermalPrinting.printText(device, text, context)
                                } catch (_: Exception) {
                                }
                            }
                        }
                    )
                }
            }
        }
    }
}