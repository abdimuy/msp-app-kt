package com.example.msp_app.features.cart.screens

import android.annotation.SuppressLint
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
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
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.example.msp_app.components.selectbluetoothdevice.SelectBluetoothDevice
import com.example.msp_app.core.context.LocalAuthViewModel
import com.example.msp_app.core.utils.ResultState
import com.example.msp_app.core.utils.ThermalPrinting
import com.example.msp_app.data.models.auth.User
import com.example.msp_app.features.cart.viewmodels.CartViewModel
import com.example.msp_app.features.sales.components.infofield.InfoField
import com.example.msp_app.features.warehouses.WarehouseViewModel
import com.example.msp_app.ui.theme.ThemeController
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@SuppressLint("UnrememberedGetBackStackEntry")
@RequiresApi(Build.VERSION_CODES.S)
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InventoryTicketScreen(navController: NavController) {
    val isDark = ThemeController.isDarkMode
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    val parentEntry = remember(navController) {
        navController.getBackStackEntry("cart")
    }
    val cartViewModel: CartViewModel = viewModel(parentEntry)
    val warehouseViewModel: WarehouseViewModel = viewModel()
    val authViewModel = LocalAuthViewModel.current

    val cartProducts = cartViewModel.cartProducts
    val warehouseState by warehouseViewModel.warehouseProducts.collectAsState()
    val userDataState by authViewModel.userData.collectAsState()

    val userData = when (userDataState) {
        is ResultState.Success -> (userDataState as ResultState.Success<User?>).data
        else -> null
    }

    val camionetaAsignada = when (val userState = userDataState) {
        is ResultState.Success -> userState.data?.CAMIONETA_ASIGNADA
        else -> null
    }

    var nombreAlmacenAsignado by remember { mutableStateOf<String?>(null) }
    var ticket by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(camionetaAsignada) {
        if (camionetaAsignada != null) {
            try {
                warehouseViewModel.selectWarehouse(camionetaAsignada)
            } catch (e: Exception) {
                nombreAlmacenAsignado = "Almacén ID: $camionetaAsignada"
            }
        } else {
            nombreAlmacenAsignado = null
        }
    }

    LaunchedEffect(warehouseState) {
        when (val state = warehouseState) {
            is ResultState.Success -> {
                nombreAlmacenAsignado = state.data.body.ALMACEN?.ALMACEN
                    ?: camionetaAsignada?.let { "Almacén ID: $it" }
            }

            is ResultState.Error -> {
                nombreAlmacenAsignado = camionetaAsignada?.let { "Almacén ID: $it" }
            }

            else -> {}
        }
    }

    LaunchedEffect(cartProducts) {
        if (cartProducts.isNotEmpty()) {
            val dateFormat = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale("es", "MX"))
            val currentDate = dateFormat.format(Date())
            val lineBlanck = " ".repeat(32)

            val totalProductos = cartProducts.sumOf { it.quantity }

            ticket = buildString {
                appendLine(ThermalPrinting.centerText("TICKET DE INVENTARIO", 32))
                appendLine(lineBlanck)
                appendLine("FECHA: $currentDate")
                appendLine("AGENTE: ${userData?.NOMBRE ?: ""}")
                appendLine("ALMACEN: $nombreAlmacenAsignado")
                appendLine(lineBlanck)
                appendLine("-".repeat(32))
                appendLine(ThermalPrinting.centerText("PRODUCTOS", 32))
                appendLine(lineBlanck)

                cartProducts.forEach { cartItem ->
                    val product = cartItem.product
                    appendLine(ThermalPrinting.bold(product.ARTICULO))
                    appendLine("  Stock: ${ThermalPrinting.bold(cartItem.quantity.toString())}")
                    appendLine(lineBlanck)
                }

                appendLine("-".repeat(32))
                appendLine(lineBlanck)
                appendLine(
                    "TOTAL PRODUCTOS: ${ThermalPrinting.bold(totalProductos.toString())}"
                )
                appendLine(lineBlanck)
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text("Ticket de Inventario")
                },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "Volver"
                        )
                    }
                }
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .padding(paddingValues)
                .fillMaxSize(),
            contentAlignment = Alignment.TopCenter
        ) {
            when (warehouseState) {
                is ResultState.Loading -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                }

                is ResultState.Error -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "Error al cargar datos",
                                style = MaterialTheme.typography.bodyLarge
                            )
                        }
                    }
                }

                else -> {
                    if (cartProducts.isEmpty()) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(text = "No hay productos en el inventario")
                        }
                    } else {
                        Column(modifier = Modifier.padding(bottom = 110.dp)) {
                            OutlinedCard(
                                elevation = CardDefaults.cardElevation(
                                    defaultElevation = if (isDark) 0.dp else 6.dp
                                ),
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
                                val dateFormat =
                                    SimpleDateFormat("dd/MM/yyyy HH:mm", Locale("es", "MX"))
                                val currentDate = dateFormat.format(Date())

                                val totalProductos = cartProducts.sumOf { it.quantity }

                                Column(
                                    modifier = Modifier
                                        .verticalScroll(scrollState)
                                        .padding(16.dp)
                                ) {
                                    Text(
                                        text = "TICKET DE INVENTARIO",
                                        style = MaterialTheme.typography.titleLarge,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.align(Alignment.CenterHorizontally)
                                    )

                                    Spacer(Modifier.height(16.dp))

                                    InfoField(label = "FECHA:", value = currentDate)
                                    InfoField(label = "Vendedor:", value = userData?.NOMBRE ?: "")
                                    InfoField(
                                        label = "Almacén:",
                                        value = nombreAlmacenAsignado.toString()
                                    )

                                    Spacer(Modifier.height(16.dp))

                                    Text(
                                        text = "PRODUCTOS",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold
                                    )

                                    Spacer(Modifier.height(8.dp))

                                    cartProducts.forEach { cartItem ->
                                        val product = cartItem.product
                                        Column(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(vertical = 8.dp)
                                        ) {
                                            InfoField(
                                                label = "Artículo:",
                                                value = product.ARTICULO
                                            )
                                            InfoField(
                                                label = "Stock:",
                                                value = cartItem.quantity.toString()
                                            )
                                        }
                                        Spacer(Modifier.height(4.dp))
                                    }

                                    Spacer(Modifier.height(16.dp))

                                    InfoField(
                                        label = "TOTAL PRODUCTOS:",
                                        value = totalProductos.toString()
                                    )
                                }
                            }
                        }

                        // Botón de impresión en la parte inferior
                        Column {
                            Box(modifier = Modifier.fillMaxSize()) {
                                if (ticket != null) {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(8.dp)
                                            .align(alignment = Alignment.BottomCenter),
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        ticket?.let { ticketText ->
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
                                                            // Manejar error silenciosamente
                                                        }
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
    }
}