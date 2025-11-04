package com.example.msp_app.features.sales.screens

import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.example.msp_app.components.DrawerContainer
import com.example.msp_app.core.utils.toCurrency
import com.example.msp_app.data.local.entities.SaleStatus
import com.example.msp_app.data.models.productInventory.ProductInventory
import com.example.msp_app.features.productsInventory.components.CarouselItem
import com.example.msp_app.features.productsInventory.components.CarrouselImage
import com.example.msp_app.features.sales.components.map.MapPin
import com.example.msp_app.features.sales.components.map.MapView
import com.example.msp_app.features.sales.components.productinfocard.ProductsInfoCard
import com.example.msp_app.features.sales.viewmodels.NewLocalSaleViewModel
import com.example.msp_app.features.sales.viewmodels.SaleProductsViewModel
import com.google.maps.android.compose.rememberCameraPositionState

@Composable
fun SaleDescriptionScreen(localSaleId: String, navController: NavController) {
    val viewModel: NewLocalSaleViewModel = viewModel()
    val productsViewModel: SaleProductsViewModel = viewModel()

    val sale by viewModel.selectedSale.collectAsState()
    val saleProducts by viewModel.saleProducts.collectAsState()
    val saleImages by viewModel.saleImages.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.getSaleById(localSaleId)
        viewModel.loadImagesBySaleId(localSaleId)
        viewModel.loadProductsBySaleId(localSaleId)
    }

    LaunchedEffect(saleProducts) {
        if (saleProducts.isNotEmpty()) {
            productsViewModel.clearSale()

            saleProducts.forEach { productEntity ->
                val productInventory = ProductInventory(
                    ARTICULO_ID = productEntity.ARTICULO_ID,
                    ARTICULO = productEntity.ARTICULO,
                    PRECIOS = "${productEntity.PRECIO_LISTA},${productEntity.PRECIO_CORTO_PLAZO},${productEntity.PRECIO_CONTADO}",
                    EXISTENCIAS = 0,
                    LINEA_ARTICULO_ID = 0,
                    LINEA_ARTICULO = "",
                )
                productsViewModel.addProductToSale(productInventory, productEntity.CANTIDAD)
            }
        }
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
            },
            bottomBar = {

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
                        InfoRow("Calle:", sale?.DIRECCION ?: "")
                        sale?.let { currentSale ->
                            if (!currentSale.NUMERO.isNullOrBlank()) {
                                InfoRow("Número:", currentSale.NUMERO ?: "")
                            }
                            if (!currentSale.COLONIA.isNullOrBlank()) {
                                InfoRow("Colonia:", currentSale.COLONIA ?: "")
                            }
                            if (!currentSale.POBLACION.isNullOrBlank()) {
                                InfoRow("Población:", currentSale.POBLACION ?: "")
                            }
                            if (!currentSale.CIUDAD.isNullOrBlank()) {
                                InfoRow("Ciudad:", currentSale.CIUDAD ?: "")
                            }
                        }
                        InfoRow("Aval o Responsable:", sale?.AVAL_O_RESPONSABLE.toString())

                        Spacer(Modifier.height(12.dp))
                        val pins = remember(sale) {
                            if (sale != null &&
                                sale!!.LATITUD != null &&
                                sale!!.LONGITUD != null &&
                                sale!!.LATITUD != 0.0 &&
                                sale!!.LONGITUD != 0.0
                            ) {
                                listOf(
                                    MapPin(
                                        lat = sale!!.LATITUD,
                                        lon = sale!!.LONGITUD,
                                        sale!!.DIRECCION ?: "Ubicación de venta"
                                    )
                                )
                            } else {
                                emptyList()
                            }
                        }

                        if (pins.isNotEmpty()) {
                            MapView(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(250.dp),
                                context = context,
                                pins = pins,
                                initialZoom = 15f,
                                cameraPositionState = cameraPositionState
                            )
                        } else {
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(100.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                                )
                            ) {
                                Column(
                                    modifier = Modifier.fillMaxSize(),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center
                                ) {
                                    Text(
                                        text = "No hay ubicación registrada",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                        Spacer(Modifier.height(12.dp))

                        Text(
                            "Información de Venta",
                            style = MaterialTheme.typography.titleLarge
                        )
                        Spacer(Modifier.height(8.dp))
                        InfoRow("Tipo de venta:", sale?.TIPO_VENTA ?: "CONTADO")

                        sale?.let { currentSale ->
                            if (currentSale.TIPO_VENTA == "CREDITO") {
                                Spacer(Modifier.height(12.dp))
                                Text(
                                    "Información de Pago",
                                    style = MaterialTheme.typography.titleLarge
                                )
                                Spacer(Modifier.height(8.dp))
                                InfoRow(
                                    "Enganche:",
                                    currentSale.ENGANCHE?.toCurrency(noDecimals = true) ?: ""
                                )
                                InfoRow(
                                    "Parcialidad:",
                                    currentSale.PARCIALIDAD?.toCurrency(noDecimals = true) ?: ""
                                )
                                InfoRow("Frecuencia de pago:", currentSale.FREC_PAGO ?: "")
                                InfoRow("Día de cobranza:", currentSale.DIA_COBRANZA ?: "")
                            }
                        }

                        Spacer(Modifier.height(12.dp))

                        Text(
                            "Notas",
                            style = MaterialTheme.typography.titleLarge
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(sale?.NOTA ?: "")
                    }
                }

                val calculatedTotal = saleProducts.sumOf { it.PRECIO_LISTA * it.CANTIDAD }
                ProductsInfoCard(
                    saleProducts = saleProducts,
                    productsViewModel = productsViewModel,
                    total = calculatedTotal
                )
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

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val saleStatus = SaleStatus.fromString(sale?.ESTADO)

                when (saleStatus) {
                    SaleStatus.PENDIENTE -> {
                        Button(
                            onClick = { navController.navigate("sales/edit/$localSaleId") },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Editar")
                        }
                        Button(
                            onClick = { viewModel.completeSale(localSaleId) },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Completar")
                        }
                    }

                    SaleStatus.COMPLETADA -> {
                        Button(
                            onClick = { navController.navigate("sales/edit/$localSaleId") },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Editar")
                        }
                        Button(
                            onClick = {
                                // Obtener userEmail del authViewModel
                                viewModel.sendSale(localSaleId, "user@email.com", context)
                            },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary
                            )
                        ) {
                            Text("Enviar")
                        }
                    }

                    SaleStatus.ENVIADA -> {
                        Text(
                            text = "Venta enviada",
                            modifier = Modifier.fillMaxWidth(),
                            textAlign = TextAlign.Center,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
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