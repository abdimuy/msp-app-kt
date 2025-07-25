package com.example.msp_app.features.routes.screens

import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.example.msp_app.components.DrawerContainer
import com.example.msp_app.core.utils.DateUtils
import com.example.msp_app.core.utils.ResultState
import com.example.msp_app.data.models.payment.Payment
import com.example.msp_app.features.payments.components.paymentitem.PaymentItem
import com.example.msp_app.features.payments.components.paymentitem.PaymentItemVariant
import com.example.msp_app.features.payments.viewmodels.PaymentsViewModel
import com.example.msp_app.features.sales.components.map.MapPin
import com.example.msp_app.features.sales.components.map.MapView
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.rememberCameraPositionState
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@RequiresApi(Build.VERSION_CODES.S)
@Composable
fun RouteMapScreen(
    navController: NavController,
    viewModel: PaymentsViewModel = viewModel()
) {
    var showDatePicker by remember { mutableStateOf(false) }
    val datePickerState = rememberDatePickerState()
    val paymentsState by viewModel.paymentsByDateState.collectAsState()
    var selectedDate by remember { mutableStateOf(DateUtils.getCurrentDate()) }
    var textDate by remember { mutableStateOf(TextFieldValue("")) }
    var reportDateIso by remember { mutableStateOf("") }
    val cameraPositionState = rememberCameraPositionState()
    val context = LocalContext.current

    fun prepareReportDate(date: LocalDate) {
        val iso = DateUtils.parseLocalDateToIso(date)
        val text = TextFieldValue(
            DateUtils.formatIsoDate(iso, "dd/MM/yyyy", Locale("es", "MX"))
        )
        val start = iso
        val end = DateUtils.addToIsoDate(
            DateUtils.addToIsoDate(iso, 1, ChronoUnit.DAYS),
            -1, ChronoUnit.SECONDS
        )
        reportDateIso = iso
        textDate = text
        selectedDate = date
        viewModel.getPaymentsByDate(start, end)
    }

    fun moveCameraToLocation(lat: Double, lng: Double) {
        cameraPositionState.move(
            update = CameraUpdateFactory.newLatLngZoom(
                LatLng(lat, lng),
                17f
            )
        )
    }

    LaunchedEffect(Unit) {
        prepareReportDate(selectedDate)
    }

    LaunchedEffect(datePickerState.selectedDateMillis) {
        datePickerState.selectedDateMillis?.let { millis ->
            val date = LocalDate.ofEpochDay(millis / (24 * 60 * 60 * 1000))
            prepareReportDate(date)
            showDatePicker = false
        }
    }

    val visiblePayments = when (paymentsState) {
        is ResultState.Success -> {
            (paymentsState as ResultState.Success<List<Payment>>).data.filter { payment ->
                val lat = payment.LAT
                val lng = payment.LNG
                lat != null && lng != null &&
                        lat in -90.0..90.0 && lng in -180.0..180.0 &&
                        (lat != 0.0 || lng != 0.0)
            }
        }

        else -> emptyList()
    }

    val pins = visiblePayments.map { payment ->
        MapPin(
            lat = payment.LAT!!,
            lon = payment.LNG!!,
            description = "Pago: $${payment.IMPORTE} - ${
                DateUtils.formatIsoDate(
                    payment.FECHA_HORA_PAGO,
                    "dd/MM/yyyy hh:mm a"
                )
            }"
        )
    }

    DrawerContainer(
        navController = navController
    ) { openDrawer ->
        Scaffold(
            modifier = Modifier.statusBarsPadding(),
            topBar = {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = openDrawer) {
                        Icon(Icons.Default.Menu, contentDescription = "Menú")
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Text("Mapa de Rutas", style = MaterialTheme.typography.titleLarge)
                }
            }
        ) { innerPadding ->
            Column(
                modifier = Modifier
                    .statusBarsPadding()
                    .padding(innerPadding)
                    .padding(horizontal = 16.dp)
                    .fillMaxSize()
            ) {
                Box(modifier = Modifier.fillMaxWidth()) {
                    Button(
                        onClick = { showDatePicker = !showDatePicker },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(45.dp),
                        shape = RoundedCornerShape(25.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color.Blue)
                    ) {
                        Icon(
                            imageVector = Icons.Default.DateRange,
                            contentDescription = "Seleccionar fecha",
                            tint = Color.White
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(text = textDate.text, color = Color.White)
                    }

                    if (showDatePicker) {
                        androidx.compose.ui.window.Popup(
                            onDismissRequest = { showDatePicker = false },
                            alignment = Alignment.TopStart
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .offset(y = 64.dp)
                                    .background(MaterialTheme.colorScheme.surface)
                                    .padding(16.dp)
                            ) {
                                DatePicker(
                                    state = datePickerState,
                                    showModeToggle = false
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                MapView(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(300.dp),
                    context = context,
                    pins = pins,
                    cameraPositionState = cameraPositionState
                )

                Spacer(modifier = Modifier.height(12.dp))

                when (paymentsState) {
                    is ResultState.Loading -> {
                        Text("Cargando pagos...")
                    }

                    is ResultState.Success -> {
                        if (visiblePayments.isEmpty()) {
                            Text("No hay pagos para esta fecha.")
                        } else {
                            LazyColumn(modifier = Modifier.weight(1f)) {
                                items(visiblePayments, key = { it.hashCode() }) { payment ->
                                    PaymentItem(
                                        payment = payment,
                                        variant = PaymentItemVariant.DEFAULT,
                                        navController = navController,
                                        onClick = {
                                            val lat = payment.LAT
                                            val lng = payment.LNG
                                            if (lat != null && lng != null && lat != 0.0 && lng != 0.0) {
                                                moveCameraToLocation(lat, lng)
                                            }
                                        }
                                    )
                                }
                                item { Spacer(modifier = Modifier.height(12.dp)) }
                            }
                        }
                    }

                    is ResultState.Error -> {
                        Text("Error: ${(paymentsState as ResultState.Error).message}")
                    }

                    else -> {
                        Text("Selecciona una fecha para ver los pagos.")
                    }
                }
            }
        }
    }
}
