package com.example.msp_app.features.home.screens

import android.Manifest
import android.location.Location
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
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
import com.example.msp_app.data.models.auth.User
import com.example.msp_app.data.models.payment.Payment
import com.example.msp_app.data.models.payment.PaymentLocationsGroup
import com.example.msp_app.data.models.sale.SaleWithProducts
import com.example.msp_app.features.home.components.homefootersection.HomeFooterSection
import com.example.msp_app.features.home.components.homeheader.HomeHeader
import com.example.msp_app.features.home.components.homestartweeksection.HomeStartWeekSection
import com.example.msp_app.features.home.components.homesummary.HomeSummarySection
import com.example.msp_app.features.home.components.homeweeklypaymentssection.HomeWeeklyPaymentsSection
import com.example.msp_app.features.payments.components.paymentitem.PaymentItem
import com.example.msp_app.features.payments.components.paymentitem.PaymentItemVariant
import com.example.msp_app.features.guarantees.screens.viewmodels.GuaranteesViewModel
import com.example.msp_app.features.payments.viewmodels.PaymentsViewModel
import com.example.msp_app.features.sales.components.sale_item.SaleItem
import com.example.msp_app.features.sales.components.sale_item.SaleItemVariant
import com.example.msp_app.features.sales.viewmodels.SalesViewModel
import com.example.msp_app.features.visit.viewmodels.VisitsViewModel
import com.example.msp_app.ui.theme.ThemeController
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import com.google.accompanist.systemuicontroller.rememberSystemUiController
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale


@RequiresApi(Build.VERSION_CODES.O)
@OptIn(ExperimentalMaterial3Api::class, ExperimentalPermissionsApi::class)
@Composable
fun HomeScreen(navController: NavController) {
    val systemUiController = rememberSystemUiController()
    val isDark = ThemeController.isDarkMode
    val listState = rememberLazyListState()
    val primary = MaterialTheme.colorScheme.primary
    val scrollThresholdDp = 100.dp

    val scrollThresholdPx = with(LocalDensity.current) {
        scrollThresholdDp.toPx().toInt()
    }

    LaunchedEffect(listState, isDark) {
        snapshotFlow {
            listState.firstVisibleItemIndex > 0 ||
                    listState.firstVisibleItemScrollOffset > scrollThresholdPx
        }.collect { scrolled ->
            val backgroundColor = if (scrolled) Color.Black else primary
            systemUiController.setStatusBarColor(
                color = backgroundColor,
                darkIcons = false
            )
        }
    }

    SideEffect {
        systemUiController.setStatusBarColor(
            color = primary,
            darkIcons = false
        )
    }

    val authViewModel = LocalAuthViewModel.current
    val userDataState by authViewModel.userData.collectAsState()

    val salesViewModel: SalesViewModel = viewModel()
    val salesState by salesViewModel.salesState.collectAsState()
    val syncSalesState by salesViewModel.syncSalesState.collectAsState()

    val paymentsViewModel: PaymentsViewModel = viewModel()
    val paymentsGroupedByDayWeekly: ResultState<Map<String, List<Payment>>> by paymentsViewModel.paymentsGroupedByDayWeeklyState.collectAsState()
    val adjustedPaymentPercentageState by paymentsViewModel.adjustedPaymentPercentageState.collectAsState()

    val pendingPaymentsState by paymentsViewModel.pendingPaymentsState.collectAsState()

    val visitsViewModel: VisitsViewModel = viewModel()
    val visitsPendingState by visitsViewModel.pendingVisits.collectAsState()

    val guaranteesViewModel: GuaranteesViewModel = viewModel()

    val centroidsBySaleState by paymentsViewModel.centroidsBySaleState.collectAsState()

    var closestCentroidsSorted by remember {
        mutableStateOf<List<Triple<Int, Coord, Long>>>(emptyList())
    }

    val updateStartOfWeekDateState by authViewModel.updateStartOfWeekDateState.collectAsState()

    var showPaymentsDialog by remember { mutableStateOf(false) }
    var selectedDateLabel by remember { mutableStateOf("") }
    var selectedPayments by remember { mutableStateOf(listOf<Payment>()) }

    val context = LocalContext.current
    val permissionState = rememberPermissionState(Manifest.permission.ACCESS_FINE_LOCATION)
    var currentLocation by remember { mutableStateOf<Location?>(null) }

    var showUpdateDialog by remember { mutableStateOf(false) }
    var dialogTitle by remember { mutableStateOf("") }
    var dialogMessage by remember { mutableStateOf("") }

    val initialDate = (userDataState as? ResultState.Success<User?>)
        ?.data
        ?.FECHA_CARGA_INICIAL

    val startWeekDate = remember(initialDate) {
        DateUtils.parseDateToIso(initialDate?.toDate())
    }

    LaunchedEffect(syncSalesState) {
        when (syncSalesState) {
            is ResultState.Loading -> {
            }

            is ResultState.Error -> {
                val errorMessage = (syncSalesState as ResultState.Error).message
                println("Error syncing sales: $errorMessage")
            }

            is ResultState.Success -> {
                paymentsViewModel.getPaymentsGroupedByDayWeekly(startWeekDate)
                salesViewModel.getLocalSales()
                paymentsViewModel.getCentroidsBySale()
                visitsViewModel.getPendingVisits()
                paymentsViewModel.getAdjustedPaymentPercentage(
                    startWeekDate
                )
            }

            else -> Unit
        }
    }

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
        currentLocation?.let {
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
        paymentsViewModel.getPendingPayments()
    }

    LaunchedEffect(startWeekDate) {
        if (startWeekDate == "null") return@LaunchedEffect

        salesViewModel.getLocalSales()
        paymentsViewModel.getAdjustedPaymentPercentage(startWeekDate)

        snapshotFlow { salesState }
            .filter { it is ResultState.Success }
            .first()

        paymentsViewModel.getPaymentsGroupedByDayWeekly(startWeekDate)
    }

    LaunchedEffect(updateStartOfWeekDateState) {
        when (updateStartOfWeekDateState) {
            is ResultState.Error -> {
                dialogTitle = "Error"
                dialogMessage = (updateStartOfWeekDateState as ResultState.Error).message
                showUpdateDialog = true
            }

            is ResultState.Success -> {
                dialogTitle = "Listo"
                dialogMessage = "Inicio de semana actualizado correctamente"
                showUpdateDialog = true
                paymentsViewModel.getPaymentsGroupedByDayWeekly(startWeekDate)
            }

            else -> Unit
        }
    }

    val numberOfSales: Int = when (salesState) {
        is ResultState.Success -> (salesState as ResultState.Success<List<SaleWithProducts>>).data.size
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
        (salesState as? ResultState.Success<List<SaleWithProducts>>)
            ?.data
            ?.associateBy { it.DOCTO_CC_ID }
            ?: emptyMap()
    }

    val dateInitWeek = userData?.FECHA_CARGA_INICIAL?.toDate()?.let { date ->
        val localDate = date.toInstant().atZone(ZoneId.systemDefault()).toLocalDate()
        val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd", Locale("es", "MX"))
        localDate.format(formatter)
    } ?: ""

    val adjustedTotal =
        (adjustedPaymentPercentageState as? ResultState.Success<Double>)?.data ?: 0.0
    val accountsPercentageAjusted =
        if (numberOfSales > 0) (adjustedTotal / numberOfSales) * 100 else 0.0
    val accountsPercentageAjustedRounded =
        String.format(Locale.getDefault(), "%.2f", accountsPercentageAjusted) + "%"

    DrawerContainer(
        navController = navController
    ) { openDrawer ->
        Scaffold(
            content = { innerPadding ->

                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    item {
                        HomeHeader(
                            userName = userData?.NOMBRE,
                            onMenuClick = openDrawer,
                            onToggleTheme = { ThemeController.toggle() },
                            backgroundColor = primary
                        )
                    }

                    item {
                        HomeSummarySection(
                            isDark = isDark,
                            totalTodayPayments = totalTodayPayments,
                            totalWeeklyPayments = totalWeeklyPayments,
                            numberOfPaymentsToday = numberOfPaymentsToday,
                            numberOfPaymentsWeekly = numberOfPaymentsWeekly,
                            numberOfSales = numberOfSales,
                            accountsPercentageRounded = accountsPercentageRounded,
                            accountsPercentageAjusted = accountsPercentageAjustedRounded
                        )
                    }

                    item {
                        HomeWeeklyPaymentsSection(
                            paymentsGroupedByDayWeekly = paymentsGroupedByDayWeekly,
                            isDark = isDark,
                            onPaymentDateClick = { label, list ->
                                selectedDateLabel = label
                                selectedPayments = list
                                showPaymentsDialog = true
                            }
                        )

                    }

                    item {
                        HomeStartWeekSection(
                            startDate = startDate,
                            isDark = isDark
                        )
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
                        HomeFooterSection(
                            isDark = isDark,
                            visitsPendingState = visitsPendingState,
                            pendingPaymentsState = pendingPaymentsState,
                            syncSalesState = syncSalesState,
                            zonaClienteId = userData?.ZONA_CLIENTE_ID ?: 0,
                            dateInitWeek = dateInitWeek,
                            onSyncSales = { zona, date -> salesViewModel.syncSales(zona, date) },
                            onSyncPendingVisits = { visitsViewModel.syncPendingVisits() },
                            onSyncPendingPayments = { 
                                paymentsViewModel.syncPendingPayments()
                                guaranteesViewModel.syncPendingGuarantees()
                                guaranteesViewModel.syncPendingGuaranteeEvents()
                            },
                            onResendAllPayments = { /* TODO */ },
                            onLogout = { /* TODO */ },
                            onInitWeek = { authViewModel.updateStartOfWeekDate() },
                        )
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

    if (showUpdateDialog) {
        AlertDialog(
            onDismissRequest = {
                showUpdateDialog = false
                authViewModel.clearUpdateStartOfWeekDateState()
            },
            title = { Text(dialogTitle) },
            text = { Text(dialogMessage) },
            confirmButton = {
                TextButton(onClick = {
                    showUpdateDialog = false
                    authViewModel.clearUpdateStartOfWeekDateState()
                }) {
                    Text("OK")
                }
            }
        )
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