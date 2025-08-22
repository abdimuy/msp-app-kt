package com.example.msp_app.features.sales.screens

import android.net.Uri
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.example.msp_app.components.DrawerContainer
import com.example.msp_app.core.utils.toCurrency
import com.example.msp_app.features.productsInventory.components.CarouselItem
import com.example.msp_app.features.productsInventory.components.CarrouselImage
import com.example.msp_app.features.sales.components.map.MapPin
import com.example.msp_app.features.sales.components.map.MapView
import com.example.msp_app.features.sales.viewmodels.NewLocalSaleViewModel
import com.google.maps.android.compose.rememberCameraPositionState

@Composable
fun SaleDescriptionScreen(localSaleId: String, navController: NavController) {
    val viewModel: NewLocalSaleViewModel = viewModel()
    val sale by viewModel.selectedSale.collectAsState()
    val saleImages by viewModel.saleImages.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.getSaleById(localSaleId)
        viewModel.loadImagesBySaleId(localSaleId)
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
                        .padding(horizontal = 8.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = openDrawer) {
                        Icon(
                            imageVector = Icons.Default.Menu,
                            contentDescription = "Menú"
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = "DESCRIPCIÓN DE VENTA",
                        style = MaterialTheme.typography.titleLarge
                    )
                }
            }
        ) { innerPadding ->
            val scrollState = rememberScrollState()
            val context = LocalContext.current
            val cameraPositionState = rememberCameraPositionState()
            Column(
                modifier = Modifier
                    .padding(innerPadding)
                    .fillMaxSize()
                    .padding(horizontal = 8.dp)
                    .verticalScroll(scrollState)
            ) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(8.dp)
                        .align(Alignment.CenterHorizontally),
                    shape = RoundedCornerShape(8.dp),
                    elevation = CardDefaults.cardElevation(4.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.inverseOnSurface
                    ),
                ) {
                    Column(
                        modifier = Modifier
                            .padding(16.dp)
                            .fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            "Información del Cliente",
                            style = MaterialTheme.typography.titleLarge
                        )
                        Spacer(Modifier.height(8.dp))
                        InfoRow("Cliente:", sale?.NOMBRE_CLIENTE ?: "")
                        InfoRow("Teléfono:", sale?.TELEFONO ?: "")
                        InfoRow("Dirección:", sale?.DIRECCION ?: "")
                        InfoRow("Aval o Responsable:", sale?.AVAL_O_RESPONSABLE.toString())

                        Spacer(Modifier.height(12.dp))
                        val pins = listOf(
                            MapPin(
                                lat = sale?.LONGITUD ?: 0.0,
                                lon = sale?.LATITUD ?: 0.0,
                                sale?.DIRECCION ?: ""
                            )
                        )
                        MapView(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(250.dp),
                            context = context,
                            pins = pins,
                            initialZoom = 13f,
                            cameraPositionState = cameraPositionState
                        )
                        Spacer(Modifier.height(12.dp))

                        Text(
                            "Información de Pago",
                            style = MaterialTheme.typography.titleLarge
                        )
                        Spacer(Modifier.height(8.dp))
                        InfoRow("Frecuencia de pago:", sale?.FREC_PAGO ?: "")
                        InfoRow("Día de cobranza:", sale?.DIA_COBRANZA ?: "")

                        Spacer(Modifier.height(12.dp))

                        Text(
                            "Información de Venta",
                            style = MaterialTheme.typography.titleLarge
                        )
                        Spacer(Modifier.height(8.dp))
                        InfoRow("Enganche:", sale?.ENGANCHE?.toCurrency(noDecimals = true) ?: "")
                        InfoRow(
                            "Parcialidad:",
                            sale?.PARCIALIDAD?.toCurrency(noDecimals = true) ?: ""
                        )

                        Spacer(Modifier.height(12.dp))

                        Text(
                            "Notas",
                            style = MaterialTheme.typography.titleLarge
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(sale?.NOTA ?: "")
                    }
                }
                Spacer(Modifier.height(8.dp))

                val carouselItems = remember(saleImages) {
                    saleImages.mapIndexed { index, image ->
                        CarouselItem(
                            id = index,
                            imagePath = if (image.IMAGE_URI is String) {
                                image.IMAGE_URI as String
                            } else {
                                (image.IMAGE_URI as Uri).path ?: ""
                            },
                            description = "Imagen ${index + 1}"
                        )
                    }
                }

                if (carouselItems.isNotEmpty()) {
                    CarrouselImage(carouselItems = carouselItems)
                } else {
                    Text(text = "No hay imágenes registradas")
                }
            }
        }
    }
}

@Composable
fun InfoRow(
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f)
        )
        Modifier.width(5.dp)
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier
                .weight(1.5f)
                .align(Alignment.CenterVertically),
            softWrap = true
        )
    }
}

