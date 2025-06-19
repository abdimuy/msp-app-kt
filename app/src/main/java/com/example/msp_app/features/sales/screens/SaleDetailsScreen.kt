package com.example.msp_app.features.sales.screens

import android.content.Intent
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import java.time.LocalTime
import java.time.format.TextStyle
import java.util.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.msp_app.R
import com.example.msp_app.core.utils.ResultState
import com.example.msp_app.data.models.sale.Sale
import com.example.msp_app.features.sales.components.CustomMap
import com.example.msp_app.features.sales.viewmodels.SaleDetailsViewModel
import java.time.LocalDate

@Composable
fun SaleDetailsScreen(
    saleId: Int
) {
    val viewModel: SaleDetailsViewModel = viewModel()
    val state by viewModel.saleState.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.loadSaleDetails(saleId)
    }

    Scaffold { paddingValues ->
        Column(
            modifier = Modifier
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
        ) {
            when (val result = state) {
                is ResultState.Idle -> {
                    Text("No hay datos")
                }

                is ResultState.Loading -> {
                    Text("Cargando...")
                }

                is ResultState.Error -> {
                    Text("Error: ${result.message}")
                }

                is ResultState.Success -> {
                    val sale = result.data
                    if (sale != null) {
                        SaleDetailsContent(sale = sale)
                    } else {
                        Text("No se encontró la venta")
                    }
                }
            }
        }
    }
}

@Composable
fun SaleDetailsContent(
    sale: Sale,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.padding(bottom = 20.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(850.dp)
        ) {
            CustomMap()

            Card(
                elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.background
                ),
                modifier = Modifier
                    .fillMaxWidth(0.92f)
                    .align(Alignment.BottomCenter)
                    .background(Color.White, RoundedCornerShape(16.dp))
            ) {
                Column(modifier = Modifier.padding(vertical = 30.dp, horizontal = 16.dp)) {
                    Text(
                        text = sale.CLIENTE,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 4.dp)
                    )
                    Text(
                        text = sale.FOLIO,
                        color = Color.Blue,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp)
                    )

                    Row(modifier = Modifier.padding(vertical = 10.dp)) {
                        Column(modifier = Modifier.weight(1f)) {
                            Field(label = "Fecha de venta:", value = sale.FECHA)
                            Field(label = "Teléfono:", value = sale.TELEFONO)
                            Field(label = "Total venta:", value = "$${sale.PRECIO_TOTAL.toInt()}")
                            Field(
                                label = "Precio de contado:",
                                value = "$${sale.PRECIO_DE_CONTADO.toInt()}"
                            )
                            Field(label = "Zona:", value = sale.ZONA_NOMBRE)
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Field(label = "Parcialidad:", value = "$${sale.PARCIALIDAD}")
                            Field(label = "Frecuencia de pago:", value = sale.FREC_PAGO.toString())
                            Field(label = "Enganche:", value = "$${sale.ENGANCHE.toInt()}")
                            Field(
                                label = "Precio a ${sale.TIEMPO_A_CORTO_PLAZOMESES} mes(es):",
                                value = "$${sale.MONTO_A_CORTO_PLAZO.toInt()}"
                            )
                        }
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(10.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        SaleContactActions(sale)
                    }

                    Field(
                        label = "Dirección:",
                        value = "${sale.CALLE.replace("\n", " ")} ${
                            sale.CIUDAD.replace(
                                "\n",
                                " "
                            )
                        } ${sale.ESTADO}"
                    )
                    Field(label = "Notas:", value = sale.NOTAS)
                    Field(label = "Vendedores:", value = sale.VENDEDOR_1)
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
        Card(
            elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.background
            ),
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .background(Color.White, RoundedCornerShape(16.dp))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 24.dp, horizontal = 16.dp)
            ) {
                Text(
                    text = "Productos",
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center
                )
                Column {

                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
        TextButton(
            onClick = { },
            modifier = Modifier
                .background(
                    Color(0xFF1827CC),
                    shape = RoundedCornerShape(8.dp)
                )
                .height(36.dp)
                .fillMaxWidth(0.92f)
        ) {
            Text(
                "INICIAR GARANTIA",
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 20.sp
            )
        }

        Spacer( modifier = Modifier.height(24.dp))
        Column(
            modifier = Modifier.fillMaxWidth(0.92f),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ){
            SaleActions(
                texto = "AGREGAR PAGO",
                colorFondo = Color(0xFF1976D2),
                iconRes = R.drawable.money,
                onClick = {}
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                SaleActions(
                    texto = "AGREGAR CONDONACIÓN",
                    colorFondo = Color(0xFFD32F2F),
                    iconRes = R.drawable.checklist,
                    modifier = Modifier.weight(0.3f),
                    onClick = {  }
                )

                SaleActions(
                    texto = "AGREGAR VISITA",
                    colorFondo = Color(0xFF388E3C),
                    iconRes = R.drawable.visita,
                    modifier = Modifier.weight(0.3f),
                    onClick = {  }
                )
            }
        }

        Spacer( modifier = Modifier.height(15.dp))
        Card(
            elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.background
            ),
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .background(Color.White, RoundedCornerShape(16.dp))
        ){
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(
                    modifier = Modifier.height(8.dp)
                )
                Text(
                    "Historial de pagos",
                    fontWeight = FontWeight.Bold,
                    fontSize = 19.sp,
                    textAlign = TextAlign.Left,
                )

                PaymentHistoryScreen()
            }
        }
    }
}

@Composable
fun Field(
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp)
            .then(modifier)
    ) {
        Text(
            text = label,
            color = Color.Gray,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = value,
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
fun SaleContactActions(sale : Sale) {
    val context = LocalContext.current
    val telephone = sale.TELEFONO
    val validPhone = !telephone.isNullOrBlank()
    IconButton(
        onClick = {
            val intent = Intent(Intent.ACTION_DIAL).apply {
                data = Uri.parse("tel:"+sale.TELEFONO)
            }
            context.startActivity(intent)
        },
        enabled = validPhone,
        modifier = Modifier
            .size(56.dp)
            .then(
                if (validPhone)
                    Modifier.background(Color(0xFF49CCF5), shape = RoundedCornerShape(12.dp))
                else
                    Modifier.background(Color.Gray, shape = RoundedCornerShape(12.dp))
            )
    ){
        Icon(
            imageVector = Icons.Default.Call,
            contentDescription = "Telefono",
            tint =  if (validPhone) Color.Black else Color.DarkGray,
            modifier = Modifier.size(34.dp)
        )
    }
    IconButton(
        onClick = {
            val intent = Intent(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_APP_CALENDAR)
            }
            context.startActivity(intent)
        },
        modifier = Modifier
            .size(56.dp)
            .background(Color.Green, shape = RoundedCornerShape(12.dp))
    ){
        Icon(
            imageVector = Icons.Default.DateRange,
            contentDescription =  "Calendar",
            tint = Color.Black,
            modifier = Modifier.size((34.dp))
        )
    }
    IconButton(
        onClick = {
            val number = "521"+sale.TELEFONO.replace("\\s".toRegex(), "")
            val url = "https://wa.me/$number"

            val intent = Intent(Intent.ACTION_VIEW).apply {
                data = Uri.parse(url)
                setPackage("com.whatsapp")
            }

            context.startActivity(intent)
        },
        enabled = validPhone,
        modifier = Modifier
            .size(56.dp)
            .then(
                if(validPhone)
                    Modifier.background(Color(0xFF25D366), shape = RoundedCornerShape(12.dp))
                else
                    Modifier.background(Color.Gray, shape = RoundedCornerShape(12.dp))
            )
    ) {
        Icon(
            painter = painterResource(id = R.drawable.whatsapp),
            contentDescription = "WhatsApp",
            modifier = Modifier.size(28.dp)
        )
    }
}

@Composable
fun SaleActions(
    texto: String,
    colorFondo: Color,
    iconRes: Int,
    modifier: Modifier = Modifier.fillMaxWidth(),
    onClick: () -> Unit
){
    Button(
        onClick = onClick ,
        colors = ButtonDefaults.buttonColors(containerColor = colorFondo),
        modifier = modifier
            .height(88.dp),
        shape = RoundedCornerShape(8.dp),
        contentPadding = PaddingValues(5.dp)
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = texto,
                color = Color.White,
                fontSize = 16.sp,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(4.dp))
            Image(
                painter = painterResource(id = iconRes),
                contentDescription = null,
                modifier = Modifier.size(34.dp),
                colorFilter = ColorFilter.tint(Color.White)
            )
        }
    }
}

data class Payment(
    val monto: Double,
    val fecha: String,
    val metodoPago: String
)

class HistoryViewModel : ViewModel() {
    private val _monthlyPayments = MutableStateFlow<Map<String, List<Payment>>>(emptyMap())
    val monthlyPayments: StateFlow<Map<String, List<Payment>>> = _monthlyPayments

    init {
        loadPayments()
    }

    private fun loadPayments() {
        val paymentList = listOf(
            Payment(150.0, "2025-06-18", "Tarjeta"),
            Payment(100.0, "2025-06-17", "Efectivo"),
            Payment(200.0, "2025-05-22", "Transferencia"),
            Payment(180.0, "2025-05-10", "Tarjeta"),
            Payment(50.0, "2025-04-18", "Efectivo")
        )

        val grouped = paymentList
            .sortedByDescending { it.fecha }
            .groupBy {
                val fecha = LocalDate.parse(it.fecha)
                "${fecha.month.getDisplayName(TextStyle.FULL, Locale("es")).uppercase()} ${fecha.year}"
            }

        _monthlyPayments.value = grouped
    }
}

@Composable
fun PaymentHistoryScreen(viewModel: HistoryViewModel = viewModel()) {
    val monthlyPayments by viewModel.monthlyPayments.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(10.dp)
    ) {
        monthlyPayments.forEach { (mes, pagos) ->
            Text(
                text = mes,
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(vertical = 8.dp)
            )

            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                pagos.forEach { pago ->
                    PagoCard(pago)
                }
            }
        }
    }
}

@Composable
fun PagoCard(pago: Payment) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row {
            Column(modifier = Modifier
                .padding(16.dp)
                .weight(0.7f)
            ) {
                Text("${pago.fecha}", style = MaterialTheme.typography.bodyLarge)
                Text("${pago.metodoPago}", style = MaterialTheme.typography.bodyLarge)
            }
            Column(modifier = Modifier
                .padding(16.dp)
                .weight(0.3f)
            ) {
                Text("$${pago.monto}", style = MaterialTheme.typography.bodyLarge)
                Text("Enviado")
            }
        }
    }
}