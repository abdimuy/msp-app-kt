package com.example.msp_app.features.sales.screens

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.example.msp_app.core.utils.DateUtils
import com.example.msp_app.core.utils.ResultState
import com.example.msp_app.features.payments.viewmodels.PaymentsViewModel
import com.example.msp_app.features.sales.components.map.MapPin
import com.example.msp_app.features.sales.components.map.MapView

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SaleMapScreen(
    navController: NavController,
    saleId: Int? = null,
    paymentsViewModel: PaymentsViewModel = viewModel(),
) {
    val paymentsBySaleId = paymentsViewModel.paymentsBySaleIdState.collectAsState()

    LaunchedEffect(Unit) {
        if (saleId == null) {
            navController.popBackStack()
            return@LaunchedEffect
        }
        paymentsViewModel.getPaymentsBySaleId(saleId)
    }

    if (paymentsBySaleId.value is ResultState.Error) {
        return
    }

    val payments = (paymentsBySaleId.value as? ResultState.Success)?.data ?: emptyList()
    val pins = payments.mapNotNull { payment ->
        val lat = payment.LAT ?: 0.0
        val lon = payment.LNG ?: 0.0
        if (lat == 0.0 && lon == 0.0) return@mapNotNull null
        val dateFormatted = DateUtils.formatIsoDate(payment.FECHA_HORA_PAGO, "dd/MM/yyyy hh:mm a")
        MapPin(
            lat = lat,
            lon = lon,
            description = "Pago: $${payment.IMPORTE} en $dateFormatted"
        )
    }

    val context = LocalContext.current
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Mapa") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Atrás")
                    }
                })
        }
    ) {
        Box(modifier = Modifier.padding(it)) {
            Map(context = context, pins)
        }
    }
}

@Composable
fun Map(
    context: Context,
    pins: List<MapPin> = emptyList(),
) {
    MapView(
        modifier = Modifier,
        context = context,
        pins = pins,
    )
}

fun isNetworkAvailable(context: Context): Boolean {
    val connectivityManager =
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    val network = connectivityManager.activeNetwork ?: return false
    val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
    return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
}
