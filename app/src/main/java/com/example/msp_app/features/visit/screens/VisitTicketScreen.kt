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
import androidx.compose.runtime.mutableIntStateOf
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
import com.example.msp_app.core.common.time.AppTime
import com.example.msp_app.core.context.LocalAuthViewModel
import com.example.msp_app.core.database.entities.OverduePaymentsEntity
import com.example.msp_app.core.utils.ResultState
import com.example.msp_app.core.utils.ThermalPrinting
import com.example.msp_app.core.utils.toCurrency
import com.example.msp_app.data.models.auth.User
import com.example.msp_app.data.models.sale.Sale
import com.example.msp_app.features.sales.viewmodels.SaleDetailsViewModel
import com.example.msp_app.features.sales.viewmodels.SalesViewModel
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.launch

/**
 * El ticket de visita LEGADO (recuperado el 2026-09-29, decisión del dueño):
 * la pantalla nueva de `:feature:visitas` (Task 20) todavía no tiene los TRES
 * papeles que Microsip conocía por separado — "ticket de visita", "cliente
 * moroso" y "no pago" —, así que mientras eso no exista se vuelve a imprimir
 * con este diseño, sin tocarlo. Ver [Screen.VisitTicket][com.example.msp_app
 * .navigation.Screen.VisitTicket] para el espacio de id que espera esta
 * pantalla y `DestinosDeCobranzaGraph.navegarAlTicketLegadoDeLaVisita` para
 * quién resuelve ese id al registrar una visita.
 *
 * El único cambio de fondo contra la versión original (borrada en `d76d8f69`)
 * es el ticket 3: el monto fijo `$200.00` de "SU COMPROMISO FUE DAR ABONOS
 * SEMANALES DE…" ahora es la parcialidad REAL de la venta —
 * [construirTicketDeVisita] la formatea igual que el resto del ticket, con
 * [Int.toCurrency] y sin decimales—. El literal fijo mentía en cuanto la
 * parcialidad del cliente no era $200.
 *
 * `expirationDate` ya no parsea `sale.FECHA` con el patrón legado
 * `dd/MM/yyyy`: desde antes de esta recuperación `Sale.FECHA` viaja en ISO
 * 8601 (ver el comentario del campo en `data/models/sale/Sale.kt`), así que
 * ese `LocalDate.parse` de la versión original reventaría con
 * `DateTimeParseException` en cuanto se abriera esta pantalla. Pasa por
 * [AppTime] en su lugar, igual que el resto del código nuevo.
 */
@RequiresApi(Build.VERSION_CODES.S)
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VisitTicketScreen(saleId: Int, navController: NavController) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val saleViewModel: SaleDetailsViewModel = viewModel()
    val viewModel: SalesViewModel = viewModel()
    val authViewModel = LocalAuthViewModel.current
    val userDataState by authViewModel.userData.collectAsState()
    val saleResult by saleViewModel.saleState.collectAsState()
    val overduePaymentBySaleState by viewModel.overduePaymentBySaleState.collectAsState()

    val user = when (userDataState) {
        is ResultState.Success -> (userDataState as ResultState.Success<User?>).data
        else -> null
    }

    val sale = when (saleResult) {
        is ResultState.Success -> (saleResult as ResultState.Success<Sale?>).data
        else -> null
    }

    val latePayment = when (overduePaymentBySaleState) {
        is ResultState.Success -> (overduePaymentBySaleState as ResultState.Success).data
        else -> null
    }

    var ticketText by remember { mutableStateOf<String?>(null) }
    var saleLoaded by remember { mutableStateOf(false) }
    var ticketType by remember { mutableIntStateOf(1) }

    val currentDate = LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd/MM/yyyy hh:mm a"))

    LaunchedEffect(saleId) {
        saleViewModel.loadSaleDetails(saleId)
        viewModel.getOverduePaymentBySaleId(saleId)
    }

    LaunchedEffect(saleResult, ticketType) {
        if (saleResult is ResultState.Success && sale != null && user != null) {
            ticketText = construirTicketDeVisita(
                ticketType = ticketType,
                sale = sale,
                user = user,
                latePayment = latePayment,
                currentDate = currentDate
            )
            saleLoaded = true
        }
    }

    DrawerContainer(
        navController = navController
    ) { openDrawer ->
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
                                        text = "Ticket de Visita",
                                        fontSize = 24.sp,
                                        fontWeight = FontWeight.Bold
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
                                    onSelected = { ticketType = it + 1 }
                                )

                                Spacer(modifier = Modifier.height(12.dp))

                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .verticalScroll(rememberScrollState())
                                ) {
                                    OutlinedCard(
                                        shape = RoundedCornerShape(12.dp),
                                        border = BorderStroke(1.5.dp, Color.LightGray),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Column(modifier = Modifier.padding(16.dp)) {
                                            Text(
                                                text = ticketText!!,
                                                fontSize = 14.sp,
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

/**
 * Arma el texto del ticket, de forma pura — separado del `@Composable` para
 * poder probarlo sin Robolectric ni Compose (ver `VisitTicketScreenTest`).
 * [currentDate] entra ya formateado porque leer el reloj es responsabilidad
 * de quien llama, no de esta función: así el resultado es determinista sobre
 * los mismos argumentos.
 */
internal fun construirTicketDeVisita(
    ticketType: Int,
    sale: Sale,
    user: User,
    latePayment: OverduePaymentsEntity?,
    currentDate: String
): String {
    val expirationDate = AppTime.parseWireFormatOrNull(sale.FECHA)
        ?.let { AppTime.formatDate(AppTime.toBusinessDate(it).plusYears(1)) }
        ?: ""
    val separator = (" ".repeat(32))
    val lines = "-".repeat(32)
    val currentDateFormatted = ThermalPrinting.centerText(currentDate, 32)

    return buildString {
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
                appendLine("TOTAL DE COMPRA: ${sale.PRECIO_TOTAL.toCurrency(noDecimals = true)}")
                appendLine("SALDO ACTUAL: ${sale.SALDO_REST.toCurrency(noDecimals = true)}")
                if (latePayment != null) {
                    val lost = latePayment.NUM_PAGOS_ATRASADOS.toInt()
                    val regularized = (lost * sale.PARCIALIDAD).toCurrency(noDecimals = true)
                    appendLine("PAGOS VENCIDOS: $lost")
                    appendLine("SUGERIDO PARA")
                    appendLine("REGULARIZARSE: $regularized")
                }
            }

            3 -> {
                appendLine("RECUERDE QUE LA PUNTUALIDAD EN")
                appendLine("SUS PAGOS ES IMPORTANTE PARA")
                appendLine("SU HISTORIAL DE CREDITO.")
                appendLine(separator)
                appendLine("SU COMPROMISO FUE DAR ABONOS")
                appendLine("SEMANALES DE ${sale.PARCIALIDAD.toCurrency(noDecimals = true)}")
                appendLine("SE LE EXHORTA A REGULARIZARSE")
                appendLine("PARA EVITAR PENALIZACIONES.")
                appendLine(separator)
                appendLine("SU FECHA DE VENCIMIENTO DE SU")
                appendLine("CREDITO ES EL DIA: $expirationDate")
                appendLine(separator)
                appendLine("TOTAL DE COMPRA: ${sale.PRECIO_TOTAL.toCurrency(noDecimals = true)}")
                appendLine("SALDO ACTUAL: ${sale.SALDO_REST.toCurrency(noDecimals = true)}")
                if (latePayment != null) {
                    val lost = latePayment.NUM_PAGOS_ATRASADOS.toInt()
                    val regularized = (lost * sale.PARCIALIDAD).toCurrency(noDecimals = true)
                    appendLine("PAGOS VENCIDOS: $lost")
                    appendLine("SUGERIDO PARA")
                    appendLine("REGULARIZARSE: $regularized")
                }
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
}

@Composable
fun DropdownMenuWithOptions(options: List<String>, selectedIndex: Int, onSelected: (Int) -> Unit) {
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
