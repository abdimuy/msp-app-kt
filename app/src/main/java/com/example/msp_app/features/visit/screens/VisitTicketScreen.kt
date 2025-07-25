package com.example.msp_app.features.visit.screens

import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.example.msp_app.components.DrawerContainer
import com.example.msp_app.components.selectbluetoothdevice.SelectBluetoothDevice
import com.example.msp_app.core.context.LocalAuthViewModel
import com.example.msp_app.core.utils.ResultState
import com.example.msp_app.core.utils.ThermalPrinting
import com.example.msp_app.core.utils.toCurrency
import com.example.msp_app.data.models.auth.User
import com.example.msp_app.data.models.sale.Sale
import com.example.msp_app.features.sales.viewmodels.SaleDetailsViewModel
import com.example.msp_app.ui.theme.ThemeController
import kotlinx.coroutines.launch
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

@RequiresApi(Build.VERSION_CODES.S)
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VisitTicketScreen(
    saleId: Int,
    navController: NavController
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val saleViewModel: SaleDetailsViewModel = viewModel()
    val authViewModel = LocalAuthViewModel.current
    val userDataState by authViewModel.userData.collectAsState()
    val saleResult by saleViewModel.saleState.collectAsState()

    val user = when (userDataState) {
        is ResultState.Success -> (userDataState as ResultState.Success<User?>).data
        else -> null
    }

    val sale = when (saleResult) {
        is ResultState.Success -> (saleResult as ResultState.Success<Sale?>).data
        else -> null
    }

    var ticketText by remember { mutableStateOf<String?>(null) }
    var saleLoaded by remember { mutableStateOf(false) }
    var ticketType by remember { mutableStateOf(1) }

    val currentDate = LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd/MM/yyyy hh:mm a"))

    LaunchedEffect(saleId) {
        saleViewModel.loadSaleDetails(saleId)
    }

    LaunchedEffect(saleResult, ticketType) {
        if (saleResult is ResultState.Success && sale != null && user != null) {
            val expirationDate =
                LocalDateTime.now().plusYears(1).format(DateTimeFormatter.ofPattern("dd/MM/yyyy"))
            val separator = (" ".repeat(32))
            val lines = "-".repeat(32)
            val currentDateFormatted = ThermalPrinting.centerText(currentDate, 32)

            ticketText = buildString {
                appendLine(ThermalPrinting.centerText("MUEBLES SAN PABLO", 32))
                appendLine(ThermalPrinting.centerText("TICKET DE VISITA DE COBRANZA", 32))
                appendLine(lines)
                appendLine(currentDateFormatted)
                appendLine(lines)
                appendLine("ESTIMADO CLIENTE:")
                appendLine(sale.CLIENTE)
                appendLine(lines)
                appendLine(separator)

                when (ticketType) {
                    1 -> {
                        appendLine("SU AGENTE DE COBRANZA DE")
                        appendLine("MUEBLES SAN PABLO PASO A VISITAR")
                        appendLine("EN SU DOMICILIO PARA SU PAGO")
                        appendLine("CORRESPONDIENTE DE ESTA SEMANA,")
                        appendLine("PERO NO FUE POSIBLE ENCONTRARLO,")
                        appendLine("LE INFORMO QUE PASARE NUEVAMENTE")
                        appendLine("A VISITARLO MAS TARDE.")
                        appendLine("EN CASO DE NO ENCONTRARSE LE")
                        appendLine("PEDIMOS DE FAVOR NOS PUEDA")
                        appendLine("APOYAR DEJANDO SU PAGO")
                        appendLine("CORRESPONDIENTE CON LA PERSONA")
                        appendLine("QUE SE ENCUENTRE EN SU DOMICILIO")
                        appendLine("O LLAMAME PARA COORDINARNOS EN")
                        appendLine("EL HORARIO QUE LO PUEDA VISITAR.")
                    }

                    2 -> {
                        appendLine("EN REITERADAS OCASIONES HEMOS")
                        appendLine("TRATADO DE ACERCARNOS A USTED")
                        appendLine("PARA SOLUCIONAR SU ADEUDO")
                        appendLine("PENDIENTE, SIN EMBARGO, NO HEMOS")
                        appendLine("TENIDO UNA RESPUESTA FAVORABLE.")
                        appendLine(separator)
                        appendLine("CON LA INTENCION DE EVITARLE")
                        appendLine("CONTINUAR CON EL PROCESO DE")
                        appendLine("COBRO POR OTRA VIA, ASI COMO")
                        appendLine("GASTOS INNECESARIOS, LO")
                        appendLine("INVITAMOS A QUE JUNTOS")
                        appendLine("ENCONTREMOS LA ALTERNATIVA QUE")
                        appendLine("MAS SE ACOMODE PARA SOLUCIONAR")
                        appendLine("EN DEFINITIVA ESTA SITUACION.")
                        appendLine(separator)
                        appendLine("SU FECHA DE VENCIMIENTO DE SU")
                        appendLine("CREDITO ES EL DIA: $expirationDate")
                        appendLine(separator)
                        appendLine("TOTAL DE COMPRA: ${sale.PRECIO_TOTAL.toCurrency()}")
                        appendLine("SALDO ACTUAL: ${sale.SALDO_REST.toCurrency()}")
                        appendLine("PAGOS VENCIDOS: $0")
                        appendLine("SUGERIDO PARA")
                        appendLine("REGULARIZARSE: $0")
                    }

                    3 -> {
                        appendLine("RECUERDE QUE LA PUNTUALIDAD EN")
                        appendLine("SUS PAGOS ES IMPORTANTE PARA")
                        appendLine("SU HISTORIAL DE CREDITO.")
                        appendLine(separator)
                        appendLine("SU COMPROMISO FUE DAR ABONOS")
                        appendLine("SEMANALES DE $200.00")
                        appendLine("SE LE EXHORTA A REGULARIZARSE")
                        appendLine("PARA EVITAR PENALIZACIONES.")
                        appendLine(separator)
                        appendLine("SU FECHA DE VENCIMIENTO DE SU")
                        appendLine("CREDITO ES EL DIA: $expirationDate")
                        appendLine(separator)
                        appendLine("TOTAL DE COMPRA: ${sale.PRECIO_TOTAL.toCurrency()}")
                        appendLine("SALDO ACTUAL: ${sale.SALDO_REST.toCurrency()}")
                        appendLine("PAGOS VENCIDOS: $0")
                        appendLine("SUGERIDO PARA")
                        appendLine("REGULARIZARSE: $0")
                    }
                }

                appendLine(separator)
                appendLine(lines)
                appendLine(separator)
                appendLine("ATENTAMENTE")
                appendLine(user.NOMBRE)
                appendLine("GESTOR DE COBRANZA")
                appendLine(separator)
                appendLine("TEL: ${user.TELEFONO}")
                appendLine(separator)
                appendLine(lines)
            }

            saleLoaded = true
        }
    }

    DrawerContainer(
        navController = navController,
        onToggleTheme = { ThemeController.toggle() }) { openDrawer ->
        Scaffold { innerPadding ->
            Box(
                modifier = Modifier
                    .padding(innerPadding)
                    .fillMaxSize(),
                contentAlignment = Alignment.TopCenter
            ) {
                when {
                    saleResult is ResultState.Loading || userDataState is ResultState.Loading -> {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator()
                        }
                    }

                    saleResult is ResultState.Error -> {
                        val msg = (saleResult as ResultState.Error).message
                        Text(
                            "Error al cargar venta: $msg",
                            color = Color.Red,
                            modifier = Modifier.padding(16.dp)
                        )
                    }

                    userDataState is ResultState.Error -> {
                        val msg = (userDataState as ResultState.Error).message
                        Text(
                            "Error al cargar usuario: $msg",
                            color = Color.Red,
                            modifier = Modifier.padding(16.dp)
                        )
                    }

                    saleLoaded && ticketText != null -> {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                IconButton(
                                    onClick = openDrawer,
                                    modifier = Modifier.padding(10.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Menu,
                                        contentDescription = "Menú",
                                    )
                                }
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .padding(end = 48.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "Ticket de Visita",
                                        fontSize = 24.sp,
                                        fontWeight = FontWeight.Bold,
                                    )
                                }
                            }

                            Column(
                                modifier = Modifier
                                    .fillMaxWidth(1f)
                                    .padding(horizontal = 16.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {

                                Text("Seleccione el tipo:", fontWeight = FontWeight.Bold)
                                Spacer(modifier = Modifier.height(2.dp))
                                DropdownMenuWithOptions(
                                    options = listOf(
                                        "Ticket de Visita",
                                        "Ticket de Cliente Moroso",
                                        "Ticket de no Pago"
                                    ),
                                    selectedIndex = ticketType - 1,
                                    onSelected = { ticketType = it + 1 },
                                )

                                Spacer(modifier = Modifier.height(12.dp))

                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .verticalScroll(rememberScrollState())
                                ) {
                                    OutlinedCard(
                                        shape = RoundedCornerShape(12.dp),
                                        border = BorderStroke(1.5.dp, Color.Black),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Column(modifier = Modifier.padding(16.dp)) {
                                            Text(
                                                text = ticketText!!,
                                                fontSize = 14.sp,
                                                color = Color.Black,
                                                textAlign = TextAlign.Start,
                                                lineHeight = 20.sp,
                                                modifier = Modifier.fillMaxWidth()
                                            )
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(16.dp))

                                SelectBluetoothDevice(
                                    textToPrint = ticketText!!,
                                    modifier = Modifier.fillMaxWidth(),
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

                    else -> {
                        Text("Loading...", modifier = Modifier.padding(16.dp))
                    }
                }
            }
        }
    }
}

@Composable
fun DropdownMenuWithOptions(
    options: List<String>,
    selectedIndex: Int,
    onSelected: (Int) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    Box {
        Button(onClick = { expanded = true }) {
            Text(text = options[selectedIndex])
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            options.forEachIndexed { index, option ->
                DropdownMenuItem(
                    text = { Text(option) },
                    onClick = {
                        onSelected(index)
                        expanded = false
                    }
                )
            }
        }
    }
}