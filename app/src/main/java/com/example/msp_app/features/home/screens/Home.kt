package com.example.msp_app.features.home.screens

import android.Manifest
import android.location.Location
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.example.msp_app.components.DrawerContainer
import com.example.msp_app.core.context.LocalAuthViewModel
import com.example.msp_app.core.utils.Coord
import com.example.msp_app.core.utils.DateUtils
import com.example.msp_app.core.utils.LocationTracker
import com.example.msp_app.core.utils.ResultState
import com.example.msp_app.core.utils.sortGroupsByClosestCentroid
import com.example.msp_app.core.utils.toCurrency
import com.example.msp_app.data.models.auth.User
import com.example.msp_app.data.models.payment.Payment
import com.example.msp_app.data.models.payment.PaymentLocationsGroup
import com.example.msp_app.data.models.sale.Sale
import com.example.msp_app.features.payments.components.paymentitem.PaymentItem
import com.example.msp_app.features.payments.components.paymentitem.PaymentItemVariant
import com.example.msp_app.features.payments.viewmodels.PaymentsViewModel
import com.example.msp_app.features.sales.components.sale_item.SaleItem
import com.example.msp_app.features.sales.components.sale_item.SaleItemVariant
import com.example.msp_app.features.sales.viewmodels.SalesViewModel
import com.example.msp_app.features.visit.viewmodels.VisitsViewModel
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import com.google.accompanist.systemuicontroller.rememberSystemUiController
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale


@OptIn(ExperimentalMaterial3Api::class, ExperimentalPermissionsApi::class)
@Composable
fun HomeScreen(navController: NavController) {
    val systemUiController = rememberSystemUiController()
    val primary = MaterialTheme.colorScheme.primary
    SideEffect {
        systemUiController.setStatusBarColor(color = primary)
    }

    val authViewModel = LocalAuthViewModel.current
    val userDataState by authViewModel.userData.collectAsState()

    val salesViewModel: SalesViewModel = viewModel()
    val salesState by salesViewModel.salesState.collectAsState()

    val paymentsViewModel: PaymentsViewModel = viewModel()
    val paymentsGroupedByDayWeekly: ResultState<Map<String, List<Payment>>> by paymentsViewModel.paymentsGroupedByDayWeeklyState.collectAsState()

    val visitsViewModel: VisitsViewModel = viewModel()
    val visitsPendingState by visitsViewModel.pendingVisits.collectAsState()

    val centroidsBySaleState by paymentsViewModel.centroidsBySaleState.collectAsState()

    var closestCentroidsSorted by remember {
        mutableStateOf<List<Triple<Int, Coord, Long>>>(emptyList())
    }

    var showPaymentsDialog by remember { mutableStateOf(false) }
    var selectedDateLabel by remember { mutableStateOf("") }
    var selectedPayments by remember { mutableStateOf(listOf<Payment>()) }

    val isDark = isSystemInDarkTheme()

    val context = LocalContext.current
    val permissionState = rememberPermissionState(Manifest.permission.ACCESS_FINE_LOCATION)
    var currentLocation by remember { mutableStateOf<Location?>(null) }

    LaunchedEffect(permissionState.status.isGranted) {
        if (permissionState.status.isGranted) {
            LocationTracker(context)
                .locationUpdates()
                .collect { loc -> currentLocation = loc }
        } else {
            permissionState.launchPermissionRequest()
        }
    }

    LaunchedEffect(currentLocation) {
        currentLocation?.let { it1 ->
            when (centroidsBySaleState) {
                is ResultState.Success -> {
                    val groups =
                        (centroidsBySaleState as ResultState.Success<List<PaymentLocationsGroup>>).data
                    val currentCoord = Coord(
                        currentLocation!!.latitude,
                        currentLocation!!.longitude
                    )
                    closestCentroidsSorted = sortGroupsByClosestCentroid(
                        groups,
                        currentCoord
                    ).take(10)
                }

                else -> Unit
            }
        }
    }

    LaunchedEffect(Unit) {
        paymentsViewModel.getCentroidsBySale()
        visitsViewModel.getPendingVisits()
    }

    val initialDate = (userDataState as? ResultState.Success<User?>)
        ?.data
        ?.FECHA_CARGA_INICIAL

    val startWeekDate = remember(initialDate) {
        DateUtils.parseDateToIso(initialDate?.toDate())
    }

    LaunchedEffect(startWeekDate) {
        paymentsViewModel.getPaymentsGroupedByDayWeekly(startWeekDate)
        salesViewModel.getLocalSales()
    }

    val numberOfSales: Int = when (salesState) {
        is ResultState.Success -> (salesState as ResultState.Success<List<Sale>>).data.size
        else -> 0
    }

    val (totalWeeklyPayments, numberOfPaymentsWeekly) = when (val result =
        paymentsGroupedByDayWeekly) {
        is ResultState.Success ->
            result.data.values
                .flatten()
                .fold(0.0 to 0) { (sum, count), payment ->
                    (sum + payment.IMPORTE) to (count + 1)
                }

        else -> 0.0 to 0
    }

    val totalTodayPayments = paymentsGroupedByDayWeekly
        .takeIf { it is ResultState.Success }
        ?.let { (it as ResultState.Success<Map<String, List<Payment>>>).data }
        ?.get(LocalDate.now().toString())
        ?.sumOf { it.IMPORTE } ?: 0.0

    val numberOfPaymentsToday = paymentsGroupedByDayWeekly
        .takeIf { it is ResultState.Success }
        ?.let { (it as ResultState.Success<Map<String, List<Payment>>>).data }
        ?.get(LocalDate.now().toString())
        ?.size ?: 0

    val userData = when (userDataState) {
        is ResultState.Success -> (userDataState as ResultState.Success<User?>).data
        else -> null
    }

    val startDate = DateUtils.formatIsoDate(
        iso = userData?.FECHA_CARGA_INICIAL?.toDate()?.toInstant()?.atZone(ZoneOffset.UTC)
            .toString(),
        pattern = "EEE. dd/MM/yyyy hh:mm a",
        locale = Locale("es", "MX")
    )

    val accountsPercentage = if (numberOfSales > 0) {
        (numberOfPaymentsWeekly.toDouble() / numberOfSales.toDouble()) * 100
    } else 0.0

    val accountsPercentageRounded =
        String.format(Locale.getDefault(), "%.2f", accountsPercentage) + "%"

    val salesMap = remember(salesState) {
        (salesState as? ResultState.Success<List<Sale>>)
            ?.data
            ?.associateBy { it.DOCTO_CC_ID }
            ?: emptyMap()
    }

    DrawerContainer(navController = navController) { openDrawer ->
        Scaffold(
            content = { innerPadding ->

                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    item {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .background(
                                    primary,
                                    RoundedCornerShape(bottomEnd = 18.dp, bottomStart = 18.dp),
                                )
                                .height(130.dp)
                                .fillMaxWidth()
                        ) {
                            IconButton(
                                onClick = openDrawer,
                                modifier = Modifier.offset(y = (-16).dp)
                            ) {
                                Icon(
                                    Icons.Default.Menu,
                                    contentDescription = "Menú",
                                    tint = Color.White
                                )
                            }

                            Spacer(modifier = Modifier.width(0.dp))

                            Column(modifier = Modifier.offset(y = (-16).dp)) {
                                Text(
                                    text = "Hola,",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = Color.LightGray
                                )
                                Text(
                                    text = userData?.NOMBRE ?: "-",
                                    fontSize = 20.sp,
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    item {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier
                                .fillMaxWidth()
                                .overlap(40.dp)
                        ) {
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
                                    .background(
                                        MaterialTheme.colorScheme.background,
                                        RoundedCornerShape(16.dp)
                                    )
                            ) {
                                Column(
                                    modifier = Modifier
                                        .padding(vertical = 20.dp, horizontal = 16.dp)
                                        .fillMaxWidth(),
                                    verticalArrangement = Arrangement.spacedBy(16.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Column(
                                            modifier = Modifier.weight(1.1f)
                                        ) {
                                            PaymentInfoCollector(
                                                label = "Total Cobrado (Hoy)",
                                                value = totalTodayPayments.toCurrency(noDecimals = true),
                                            )
                                            Spacer(
                                                modifier = Modifier.height(8.dp)
                                            )
                                            PaymentInfoCollector(
                                                label = "Total cobrado (Semanal)",
                                                value = totalWeeklyPayments.toCurrency(noDecimals = true),
                                            )
                                        }
                                        Column(
                                            modifier = Modifier
                                                .weight(0.9f),
                                        ) {
                                            PaymentInfoCollector(
                                                label = "Pagos (Hoy)",
                                                value = "$numberOfPaymentsToday",
                                                horizontalAlignment = Alignment.End
                                            )
                                            Spacer(
                                                modifier = Modifier.height(8.dp)
                                            )
                                            PaymentInfoCollector(
                                                label = "Pagos (Semanal)",
                                                value = "$numberOfPaymentsWeekly/$numberOfSales",
                                                horizontalAlignment = Alignment.End
                                            )
                                        }
                                    }

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        Card(
                                            modifier = Modifier
                                                .weight(1f)
                                                .height(100.dp),
                                            colors = CardDefaults.cardColors(
                                                containerColor = Color(
                                                    0xFFF06846
                                                )
                                            ),
                                            elevation = CardDefaults.cardElevation(8.dp)
                                        ) {
                                            Column(
                                                modifier = Modifier.fillMaxSize(),
                                            ) {
                                                Text(
                                                    text = "Porcentaje (Cuentas)",
                                                    color = Color.White,
                                                    modifier = Modifier
                                                        .padding(top = 8.dp),
                                                    fontSize = 14.sp,
                                                    textAlign = TextAlign.Center
                                                )
                                                Text(
                                                    text = accountsPercentageRounded,
                                                    fontWeight = FontWeight.ExtraBold,
                                                    fontSize = 22.sp,
                                                    color = Color.White,
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .padding(top = 10.dp)
                                                        .align(Alignment.CenterHorizontally),
                                                    textAlign = TextAlign.Center
                                                )
                                            }
                                        }

                                        Card(
                                            modifier = Modifier
                                                .weight(1f)
                                                .height(100.dp),
                                            colors = CardDefaults.cardColors(
                                                containerColor = Color(
                                                    0xFF56DA6A
                                                )
                                            ),
                                            elevation = CardDefaults.cardElevation(8.dp)
                                        ) {
                                            Column(
                                                modifier = Modifier.fillMaxSize(),
                                            ) {
                                                Text(
                                                    text = "Porcentaje (Cuentas)",
                                                    color = Color.White,
                                                    modifier = Modifier
                                                        .padding(top = 8.dp),
                                                    fontSize = 14.sp,
                                                    textAlign = TextAlign.Center
                                                )
                                                Text(
                                                    text = accountsPercentageRounded,
                                                    fontWeight = FontWeight.ExtraBold,
                                                    fontSize = 22.sp,
                                                    color = Color.White,
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .padding(top = 10.dp)
                                                        .align(Alignment.CenterHorizontally),
                                                    textAlign = TextAlign.Center
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        Spacer(Modifier.height(12.dp))
                    }

                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(),
                            contentAlignment = Alignment.Center
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth(.92f)
                                    .height(100.dp)
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                when (paymentsGroupedByDayWeekly) {
                                    is ResultState.Loading -> CircularProgressIndicator()
                                    is ResultState.Error -> Text(
                                        text = "Error al cargar pagos: ${(paymentsGroupedByDayWeekly as ResultState.Error).message}",
                                        color = Color.Red
                                    )

                                    is ResultState.Success -> {
                                        val paymentsMap: Map<String, List<Payment>> =
                                            when (val result = paymentsGroupedByDayWeekly) {
                                                is ResultState.Success -> result.data
                                                else -> emptyMap()
                                            }

                                        Spacer(Modifier.width(1.dp))

                                        paymentsMap.forEach { (date, payments) ->
                                            val total = payments.sumOf { it.IMPORTE }
                                            val count = payments.size
                                            val formattedDate = LocalDate.parse(date).format(
                                                DateTimeFormatter.ofPattern(
                                                    "EEE dd/MM", Locale("es", "MX")
                                                )
                                            ).uppercase()

                                            Card(
                                                colors = CardDefaults.cardColors(
                                                    containerColor = if (isDark) Color(0xFF1E1E1E) else MaterialTheme.colorScheme.background
                                                ),
                                                modifier = Modifier
                                                    .width(100.dp)
                                                    .height(100.dp)
                                                    .clickable {
                                                        selectedDateLabel = formattedDate
                                                        selectedPayments = payments
                                                        showPaymentsDialog = true
                                                    }
                                                    .border(
                                                        width = 1.dp,
                                                        color = if (isDark) Color.Gray else Color(
                                                            0xFFE0E0E0
                                                        ),
                                                        shape = RoundedCornerShape(12.dp)
                                                    ),
                                                elevation = CardDefaults.cardElevation(4.dp),
                                            ) {
                                                Column(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .padding(8.dp),
                                                    horizontalAlignment = Alignment.CenterHorizontally,
                                                ) {
                                                    Text(
                                                        text = formattedDate,
                                                        fontSize = 14.sp,
                                                        fontWeight = FontWeight.Bold
                                                    )
                                                    Text(
                                                        text = total.toCurrency(noDecimals = true),
                                                        fontSize = 18.sp,
                                                        color = MaterialTheme.colorScheme.primary,
                                                        fontWeight = FontWeight.Bold
                                                    )
                                                    Text(
                                                        text = "$count pagos",
                                                        fontSize = 14.sp,
                                                        color = Color.Gray
                                                    )
                                                }
                                            }
                                        }

                                        Spacer(Modifier.width(1.dp))
                                    }

                                    else -> {}
                                }

                            }
                        }
                        Spacer(Modifier.height(16.dp))
                    }

                    item {
                        OutlinedCard(
                            modifier = Modifier
                                .fillMaxWidth(0.92f),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.background
                            ),
                            border = BorderStroke(
                                width = 1.dp,
                                color = if (isDark) Color.Gray else Color.LightGray
                            ),
                            shape = RoundedCornerShape(16.dp),
                        ) {
                            Text(
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                                text = buildAnnotatedString {
                                    append("Inicio de semana: \n")
                                    withStyle(style = SpanStyle(fontWeight = FontWeight.Bold)) {
                                        append(startDate.uppercase())
                                    }
                                },
                                fontSize = 16.sp,
                            )
                        }
                        Spacer(Modifier.height(16.dp))
                    }

                    item {
                        Text(
                            modifier = Modifier.padding(horizontal = 16.dp),
                            text = "VENTAS CERCANAS",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Start
                        )
                        Spacer(Modifier.height(8.dp))
                    }

                    items(
                        items = closestCentroidsSorted,
                        key = { it.first }
                    ) { (saleId, _, distanceToCurrentLocation) ->
                        salesMap[saleId]?.let { sale ->
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                SaleItem(
                                    sale = sale,
                                    onClick = {
                                        navController.navigate("sales/sale_details/$saleId")
                                    },
                                    variant = SaleItemVariant.SECONDARY,
                                    distanceToCurrentLocation = distanceToCurrentLocation.toDouble(),
                                    navController
                                )

                            }
                            Spacer(Modifier.height(8.dp))
                        }
                    }

                    item {
                        Spacer(Modifier.height(22.dp))
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
                            Column(
                                modifier = Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.Center,
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = buildAnnotatedString {
                                        withStyle(style = SpanStyle(fontWeight = FontWeight.Bold)) {
                                            append("VISITAS SIN ENVIAR")
                                        }
                                    },
                                )

                                when (val visitsResult = visitsPendingState) {
                                    is ResultState.Loading -> {
                                        Text(
                                            text = "Cargando visitas pendientes...",
                                        )
                                    }

                                    is ResultState.Error -> {
                                        Text(
                                            text = "Error al cargar visitas: ${visitsResult.message}",
                                            color = Color.Red
                                        )
                                    }

                                    is ResultState.Success -> {
                                        val pendingVisits = visitsResult.data
                                        if (pendingVisits.isEmpty()) {
                                            Text(
                                                text = "No hay visitas pendientes",
                                            )
                                        } else {
                                            Text(
                                                text = "Visitas Pendientes: ${pendingVisits.size}",
                                            )
                                            pendingVisits.forEach { visit ->
                                                Text(
                                                    text = "${visit.CLIENTE_ID} - ${
                                                        DateUtils.formatIsoDate(
                                                            visit.FECHA,
                                                            "EEE dd/MM/yy hh:mm a",
                                                        )
                                                    } - ${visit.TIPO_VISITA}",
                                                    modifier = Modifier.padding(vertical = 2.dp),
                                                )
                                            }
                                        }
                                    }

                                    else -> {}
                                }
                            }
                        }

                        Spacer(Modifier.height(20.dp))

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
                                .height(90.dp)
                        ) {
                            Text(
                                text = buildAnnotatedString {
                                    withStyle(style = SpanStyle(fontWeight = FontWeight.Bold)) {
                                        append("PAGOS SIN ENVIAR")
                                    }
                                    append("\nNO HAY PAGOS SIN ENVIAR")
                                },
                                modifier = Modifier.padding(16.dp),
                            )
                        }
                        Spacer(Modifier.height(14.dp))

                        Button(text = "Actualizar datos", onClick = { salesViewModel.syncSales() })

                        when (salesState) {
                            is ResultState.Idle -> {
                                Text("Presiona el botón para descargar ventas")
                            }

                            is ResultState.Loading -> CircularProgressIndicator()

                            is ResultState.Success -> Text("Ventas descargadas: ${(salesState as ResultState.Success<List<*>>).data.size}")
                            is ResultState.Error -> Text("Error: ${(salesState as ResultState.Error).message}")
                        }

                        Button(text = "Enviar Pagos Pendientes", onClick = { salesViewModel })

                        Button(text = "Reenviar todos los pagos", onClick = { salesViewModel })

                        Button(text = "Cerrar sesión", onClick = { salesViewModel })

                        Button(text = "Inicializar semana de Cobro", onClick = { salesViewModel })
                    }
                }
            },
        )

        if (showPaymentsDialog) {
            AlertDialog(
                onDismissRequest = { showPaymentsDialog = false },
                title = { Text("Pagos de $selectedDateLabel") },
                text = {
                    val listState = rememberLazyListState()
                    LazyColumn(
                        state = listState,
                        modifier = Modifier
                            .heightIn(max = 500.dp)
                            .fillMaxWidth()
                    ) {
                        items(
                            items = selectedPayments,
                            key = { it.ID }
                        ) { payment ->
                            PaymentItem(
                                payment = payment,
                                variant = PaymentItemVariant.COMPACT,
                                navController = navController,
                            )
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showPaymentsDialog = false }) {
                        Text("Cerrar")
                    }
                }
            )
        }
    }
}

@Composable
fun Button(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    size: Dp = 50.dp,
    cornerRadius: Dp = 10.dp
) {
    Spacer(modifier = Modifier.height(16.dp))
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier
            .fillMaxWidth(0.92f)
            .size(size)
            .padding(4.dp),
        shape = RoundedCornerShape(cornerRadius)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge.copy(
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp
            )
        )
    }
}

@Composable
fun PaymentInfoCollector(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    horizontalAlignment: Alignment.Horizontal = Alignment.Start
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .then(modifier),
        horizontalAlignment = horizontalAlignment
    ) {
        Text(
            text = label,
            color = Color.Gray,
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = value,
            fontSize = 26.sp,
            fontWeight = FontWeight.ExtraBold,
        )
    }
}


fun Modifier.overlap(offsetY: Dp) = this.then(
    Modifier.layout { measurable, constraints ->
        val placeable = measurable.measure(constraints)
        val oy = offsetY.roundToPx()
        layout(placeable.width, placeable.height - oy) {
            placeable.placeRelative(0, -oy)
        }
    }
)