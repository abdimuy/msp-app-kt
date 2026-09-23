package com.example.msp_app.features.home.screens

import android.Manifest
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.example.msp_app.components.DrawerContainer
import com.example.msp_app.components.UpdateBanner
import com.example.msp_app.core.common.time.AppTime
import com.example.msp_app.core.context.LocalAuthViewModel
import com.example.msp_app.core.utils.Coord
import com.example.msp_app.core.utils.CurrentLocationReader
import com.example.msp_app.core.utils.ResultState
import com.example.msp_app.data.models.auth.User
import com.example.msp_app.data.models.payment.Payment
import com.example.msp_app.data.models.payment.PaymentLocationsGroup
import com.example.msp_app.data.models.sale.SaleWithProducts
import com.example.msp_app.features.guarantees.screens.viewmodels.GuaranteesViewModel
import com.example.msp_app.features.home.components.homefootersection.HomeFooterSection
import com.example.msp_app.features.home.components.homeheader.HomeHeader
import com.example.msp_app.features.home.components.homenearbyclientssection.HomeNearbyClientsSection
import com.example.msp_app.features.home.components.homenearbyclientssection.nearbyClientsFrom
import com.example.msp_app.features.home.components.homestartweeksection.HomeStartWeekSection
import com.example.msp_app.features.home.components.homesummary.HomeSummarySection
import com.example.msp_app.features.home.components.homeweeklypaymentssection.HomeWeeklyPaymentsSection
import com.example.msp_app.features.payments.components.paymentitem.PaymentItem
import com.example.msp_app.features.payments.components.paymentitem.PaymentItemVariant
import com.example.msp_app.features.payments.viewmodels.PaymentsViewModel
import com.example.msp_app.features.sales.viewmodels.SalesViewModel
import com.example.msp_app.features.visit.viewmodels.VisitsViewModel
import com.example.msp_app.navigation.DestinosDeCobranza
import com.example.msp_app.ui.theme.ThemeController
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import java.time.ZoneOffset
import java.util.Locale
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first

@RequiresApi(Build.VERSION_CODES.O)
@OptIn(ExperimentalMaterial3Api::class, ExperimentalPermissionsApi::class)
@Composable
fun HomeScreen(navController: NavController) {
    val isDark = ThemeController.isDarkMode
    val listState = rememberLazyListState()
    val primary = MaterialTheme.colorScheme.primary
    val context = LocalContext.current

    val authViewModel = LocalAuthViewModel.current
    val userDataState by authViewModel.userData.collectAsState()

    val salesViewModel: SalesViewModel = viewModel()
    val salesState by salesViewModel.salesState.collectAsState()
    val syncSalesState by salesViewModel.syncSalesState.collectAsState()

    val paymentsViewModel: PaymentsViewModel = viewModel()
    val paymentsGroupedByDayWeekly: ResultState<Map<String, List<Payment>>> by paymentsViewModel.paymentsGroupedByDayWeeklyState.collectAsState()
    val adjustedPaymentPercentageState by paymentsViewModel.adjustedPaymentPercentageState.collectAsState()

    val pendingPaymentsState by paymentsViewModel.pendingPaymentsState.collectAsState()
    val syncPendingPaymentsState by paymentsViewModel.syncPendingPaymentsState.collectAsState()

    val visitsViewModel: VisitsViewModel = viewModel()
    val visitsPendingState by visitsViewModel.pendingVisits.collectAsState()

    val guaranteesViewModel: GuaranteesViewModel = viewModel()

    val centroidsBySaleState by paymentsViewModel.centroidsBySaleState.collectAsState()

    val updateStartOfWeekDateState by authViewModel.updateStartOfWeekDateState.collectAsState()

    var showPaymentsDialog by remember { mutableStateOf(false) }
    var selectedDateLabel by remember { mutableStateOf("") }
    var selectedPayments by remember { mutableStateOf(listOf<Payment>()) }

    val permissionState = rememberPermissionState(Manifest.permission.ACCESS_FINE_LOCATION)

    // Dónde está parado el cobrador AHORA. `null` mientras no se sepa —y se
    // queda en `null` para siempre si dijo que no al permiso o si el proveedor
    // no da fix—, que es justo lo que `nearbyClientsFrom` traduce a "no pintes
    // la lista".
    var currentPosition by remember { mutableStateOf<Coord?>(null) }

    var showUpdateDialog by remember { mutableStateOf(false) }
    var dialogTitle by remember { mutableStateOf("") }
    var dialogMessage by remember { mutableStateOf("") }

    val initialDate = (userDataState as? ResultState.Success<User?>)
        ?.data
        ?.FECHA_CARGA_INICIAL

    val startWeekDate = remember(initialDate) {
        resolveStartWeekDate(initialDate?.toDate()?.toInstant())
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
                salesViewModel.getLocalSales()
                paymentsViewModel.getCentroidsBySale()
                visitsViewModel.getPendingVisits()
                startWeekDate?.let {
                    paymentsViewModel.getPaymentsGroupedByDayWeekly(it)
                    paymentsViewModel.getAdjustedPaymentPercentage(it)
                }
            }

            else -> Unit
        }
    }

    // **La petición de permiso se queda pase lo que pase con la lista de
    // cercanos** (Task 21). Home es el ÚNICO lugar de la app que pide
    // `ACCESS_FINE_LOCATION` al arrancar; el resto —`UpdateLocationService`, el
    // adaptador de ubicación de la visita, el pago— solo lo *usa*. Quitarla
    // junto con el bloque de cercanas habría dejado a los cobradores nuevos sin
    // coordenadas en pagos y visitas, en silencio, hasta que abrieran un mapa.
    //
    // **La lista de cercanos vuelve, pero NO vuelve el flujo continuo.** El
    // `LocationTracker.locationUpdates()` que alimentaba esta pantalla pedía un
    // fix de alta precisión cada 2 s —en un teléfono que anda en la calle todo
    // el día— sólo para reordenar diez renglones. Acá se lee la ubicación UNA
    // vez, al abrir y al conceder el permiso, con `CurrentLocationReader`.
    // `LocationTracker` sigue vivo para su consumidor legítimo: el mapa en vivo
    // de `SaleLocationMap`, donde el flujo sí se justifica.
    //
    // Si el cobrador dice que no, o el proveedor no da fix, `current()` devuelve
    // `null` y `currentPosition` se queda como estaba: no hay excepción, no hay
    // estado de carga colgado y el resto de la pantalla no se entera.
    LaunchedEffect(permissionState.status.isGranted) {
        if (!permissionState.status.isGranted) {
            permissionState.launchPermissionRequest()
            return@LaunchedEffect
        }
        currentPosition = CurrentLocationReader(context).current()
    }

    LaunchedEffect(Unit) {
        paymentsViewModel.getCentroidsBySale()
        visitsViewModel.getPendingVisits()
        paymentsViewModel.getPendingPayments()
        guaranteesViewModel.syncPendingGuarantees()
        guaranteesViewModel.syncPendingGuaranteeEvents()
    }

    // `startWeekDate` es null mientras no se sepa dónde abre la semana. Al llegar el dato este
    // efecto se re-dispara solo (la clave cambia) y el tablero se repara sin salir y volver a
    // entrar. Antes había aquí una guarda `== "null"` que ya era código muerto: el fallback a
    // `AppClock.System.now()` (ya retirado) garantizaba una cadena siempre válida — y esa era justo la que
    // encogía la semana al instante actual.
    LaunchedEffect(startWeekDate) {
        val week = startWeekDate ?: return@LaunchedEffect

        salesViewModel.getLocalSales()
        // Ya no hay carrera con `salesState`: el porcentaje es un Flow de Room y se recalcula
        // solo cuando entran las ventas/pagos, en vez de cachear el 0.0 de una tabla aún vacía.
        paymentsViewModel.getAdjustedPaymentPercentage(week)

        snapshotFlow { salesState }
            .filter { it is ResultState.Success }
            .first()

        paymentsViewModel.getPaymentsGroupedByDayWeekly(week)
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
                startWeekDate?.let { paymentsViewModel.getPaymentsGroupedByDayWeekly(it) }
            }

            else -> Unit
        }
    }

    val numberOfSales: Int = when (salesState) {
        is ResultState.Success -> (salesState as ResultState.Success<List<SaleWithProducts>>).data.size
        else -> 0
    }

    val (totalWeeklyPayments, numberOfPaymentsWeekly) = when (
        val result =
            paymentsGroupedByDayWeekly
    ) {
        is ResultState.Success ->
            result.data.values
                .flatten()
                .fold(0.0 to 0) { (sum, count), payment ->
                    (sum + payment.IMPORTE) to (count + 1)
                }

        else -> 0.0 to 0
    }

    val today = AppTime.todayInBusinessZone()

    val totalTodayPayments = paymentsGroupedByDay(paymentsGroupedByDayWeekly, today)
        .sumOf { it.IMPORTE }

    val numberOfPaymentsToday = paymentsGroupedByDay(paymentsGroupedByDayWeekly, today).size

    val userData = when (userDataState) {
        is ResultState.Success -> (userDataState as ResultState.Success<User?>).data
        else -> null
    }

    val startDate = AppTime.formatIsoForDisplay(
        iso = userData?.FECHA_CARGA_INICIAL?.toDate()?.toInstant()?.atZone(ZoneOffset.UTC)
            .toString(),
        pattern = "EEE. dd/MM/yyyy hh:mm a"
    )

    val accountsPercentage = if (numberOfSales > 0) {
        (numberOfPaymentsWeekly.toDouble() / numberOfSales.toDouble()) * 100
    } else {
        0.0
    }

    val accountsPercentageRounded =
        String.format(Locale.getDefault(), "%.2f", accountsPercentage) + "%"

    // Las puertas más cercanas. Se recalcula sólo cuando cambia alguno de los
    // tres insumos, no en cada recomposición: ordenar y colapsar cuesta poco,
    // pero la pantalla principal recompone seguido.
    val nearbyClients = remember(currentPosition, centroidsBySaleState, salesState) {
        nearbyClientsFrom(
            position = currentPosition,
            centroidsBySale = (
                centroidsBySaleState as? ResultState.Success<List<PaymentLocationsGroup>>
                )?.data.orEmpty(),
            sales = (salesState as? ResultState.Success<List<SaleWithProducts>>)?.data.orEmpty()
        )
    }

    val dateInitWeek = userData?.FECHA_CARGA_INICIAL?.toDate()?.toInstant()
        ?.let { AppTime.toBusinessDate(it).toString() }
        ?: ""

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
            modifier = Modifier.statusBarsPadding(),
            content = { innerPadding ->

                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                    horizontalAlignment = Alignment.CenterHorizontally
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
                            accountsPercentageAjusted = accountsPercentageAjustedRounded,
                            startWeekKnown = startWeekDate != null
                        )
                    }

                    item {
                        UpdateBanner()
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
                        // Vuelve al mismo lugar donde estuvo hasta el
                        // `d76d8f69`: después del inicio de semana y antes del
                        // pie. Con la lista vacía no ocupa nada, así que el pie
                        // sube solo y no queda un hueco.
                        HomeNearbyClientsSection(
                            clients = nearbyClients,
                            isDark = isDark,
                            onClientClick = { client ->
                                navController.navigate(
                                    DestinosDeCobranza.clienteCercano(client)
                                )
                            }
                        )
                    }

                    item {
                        HomeFooterSection(
                            isDark = isDark,
                            visitsPendingState = visitsPendingState,
                            pendingPaymentsState = pendingPaymentsState,
                            syncSalesState = syncSalesState,
                            syncPendingPaymentsState = syncPendingPaymentsState,
                            updateStartOfWeekDateState = updateStartOfWeekDateState,
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
                            onInitWeek = { authViewModel.updateStartOfWeekDate() }
                        )
                    }
                }
            }
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
                                // Desde un PAGO se entra a SU VENTA; el "⋯ →
                                // ver cliente" entra a la persona (Task 21).
                                onVerCliente = {
                                    navController.navigate(
                                        DestinosDeCobranza.clienteDeUnPago(payment)
                                    )
                                },
                                onClick = {
                                    navController.navigate(
                                        DestinosDeCobranza.ventaDeUnPago(payment)
                                    )
                                }
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
            fontWeight = FontWeight.ExtraBold
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
