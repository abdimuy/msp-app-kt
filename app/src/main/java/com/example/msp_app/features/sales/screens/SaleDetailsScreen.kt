package com.example.msp_app.features.sales.screens

import android.content.Intent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.msp_app.R
import com.example.msp_app.core.utils.ResultState
import com.example.msp_app.data.models.sale.Sale
import com.example.msp_app.features.sales.components.CustomMap
import com.example.msp_app.features.sales.viewmodels.SaleDetailsViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

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
    val isDark = isSystemInDarkTheme()

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .padding(bottom = 20.dp)
            .background(
                MaterialTheme.colorScheme.background
            )
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(850.dp)
        ) {
            CustomMap()

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
                        color = MaterialTheme.colorScheme.primary,
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
                .background(Color.White, RoundedCornerShape(16.dp))
        ) {

            val productosDemo = listOf(
                Products(1, 120.0, "Camiseta de algodón 100%"),
                Products(2, 250.0, "Pantalón mezclilla azul"),
                Products(3, 450.0, "Zapatos de piel color negro")
            )

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
                Spacer(modifier = Modifier.height(16.dp))
                Column {
                    ProductsCard(products = productosDemo)
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
        TextButton(
            onClick = { },
            modifier = Modifier
                .background(
                    MaterialTheme.colorScheme.primary,
                    shape = RoundedCornerShape(8.dp)
                )
                .height(40.dp)
                .fillMaxWidth(0.92f)
        ) {
            Text(
                "INICIAR GARANTIA",
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 20.sp
            )
        }

        Spacer(modifier = Modifier.height(24.dp))
        Column(
            modifier = Modifier.fillMaxWidth(0.92f),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            SaleActionButton(
                text = "AGREGAR PAGO",
                backgroundColor = MaterialTheme.colorScheme.primary,
                iconRes = R.drawable.money,
                onClick = {}
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                SaleActionButton(
                    text = "AGREGAR CONDONACIÓN",
                    backgroundColor = Color(0xFFD32F2F),
                    iconRes = R.drawable.checklist,
                    modifier = Modifier.weight(0.3f),
                    onClick = { }
                )

                SaleActionButton(
                    text = "AGREGAR VISITA",
                    backgroundColor = Color(0xFF388E3C),
                    iconRes = R.drawable.visita,
                    modifier = Modifier.weight(0.3f),
                    onClick = { }
                )
            }
        }

        Spacer(modifier = Modifier.height(15.dp))
        Column(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .background(Color.Transparent, RoundedCornerShape(16.dp))
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(
                    modifier = Modifier.height(12.dp)
                )
                Text(
                    "Historial de pagos",
                    fontWeight = FontWeight.Bold,
                    fontSize = 22.sp,
                    textAlign = TextAlign.Left,
                )

                PaymentsHistory()
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
fun SaleContactActions(sale: Sale) {
    val context = LocalContext.current
    val telephone = sale.TELEFONO
    val validPhone = !telephone.isNullOrBlank()
    IconButton(
        onClick = {
            val intent = Intent(Intent.ACTION_DIAL).apply {
                data = ("tel:" + sale.TELEFONO).toUri()
            }
            context.startActivity(intent)
        },
        enabled = validPhone,
        modifier = Modifier
            .size(56.dp)
            .then(
                if (validPhone)
                    Modifier.background(Color(0xFFADD8E6), shape = RoundedCornerShape(12.dp))
                else
                    Modifier.background(Color.Gray, shape = RoundedCornerShape(12.dp))
            )
    ) {
        Icon(
            imageVector = Icons.Default.Call,
            contentDescription = "Telefono",
            tint = if (validPhone) MaterialTheme.colorScheme.primary else Color.DarkGray,
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
            .background(Color(0xFF90EE90), shape = RoundedCornerShape(12.dp))
    ) {
        Icon(
            imageVector = Icons.Default.DateRange,
            contentDescription = "Calendar",
            tint = Color(0xFF008000),
            modifier = Modifier.size((34.dp))
        )
    }
    IconButton(
        onClick = {
            val number = "521" + sale.TELEFONO.replace("\\s".toRegex(), "")
            val url = "https://wa.me/$number"

            val intent = Intent(Intent.ACTION_VIEW).apply {
                data = url.toUri()
                setPackage("com.whatsapp")
            }

            context.startActivity(intent)
        },
        enabled = validPhone,
        modifier = Modifier
            .size(56.dp)
            .then(
                if (validPhone)
                    Modifier.background(Color(0xFF90EE90), shape = RoundedCornerShape(12.dp))
                else
                    Modifier.background(Color.Gray, shape = RoundedCornerShape(12.dp))
            )
    ) {
        Icon(
            painter = painterResource(id = R.drawable.whatsapp),
            contentDescription = "WhatsApp",
            modifier = Modifier.size(28.dp),
            tint = if (validPhone) Color(0xFF008000) else Color.DarkGray
        )
    }
}

@Composable
fun SaleActionButton(
    text: String,
    backgroundColor: Color,
    iconRes: Int,
    modifier: Modifier = Modifier.fillMaxWidth(),
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        colors = ButtonDefaults.buttonColors(containerColor = backgroundColor),
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
                text = text,
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
                "${
                    fecha.month.getDisplayName(TextStyle.FULL, Locale("es")).uppercase()
                } ${fecha.year}"
            }

        _monthlyPayments.value = grouped
    }
}

@Composable
fun PaymentsHistory(viewModel: HistoryViewModel = viewModel()) {
    val monthlyPayments by viewModel.monthlyPayments.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(10.dp)
    ) {
        monthlyPayments.forEach { (month, payments) ->
            Text(
                text = month,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(bottom = 8.dp),
            )

            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                payments.forEach { payment ->
                    PaymentCard(payment)
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
fun PaymentCard(payment: Payment) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .height(80.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        shape = RoundedCornerShape(12.dp)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Image(
                painter = painterResource(id = R.drawable.bg_gradient),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(
                    modifier = Modifier
                        .padding(16.dp)
                        .weight(0.7f),
                ) {
                    Text(
                        payment.fecha,
                        style = MaterialTheme.typography.bodyLarge,
                        color = Color.White
                    )
                    Text(
                        payment.metodoPago,
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White
                    )
                }
                Column(
                    modifier = Modifier
                        .padding(16.dp)
                        .weight(0.3f),
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text(
                        "$${payment.monto}",
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("Enviado", color = Color.White, style = MaterialTheme.typography.bodyLarge)
                }
            }
        }
    }
}

data class Products(
    val cantidad: Int,
    val precio: Double,
    val descripcion: String
)

@Composable
fun ProductsCard(products: List<Products>) {
    Column {
        products.forEach { product ->
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
                shape = RoundedCornerShape(12.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(
                        modifier = Modifier.weight(1f),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "${product.cantidad}",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                    Column(
                        modifier = Modifier.weight(3f)
                    ) {
                        Text(
                            text = product.descripcion,
                            style = MaterialTheme.typography.titleMedium,
                        )
                    }
                    Column(
                        modifier = Modifier.weight(1.5f),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "$${product.precio}",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}