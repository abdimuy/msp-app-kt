package com.example.msp_app.features.sales.screens

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.example.msp_app.components.DrawerContainer
import com.example.msp_app.core.utils.toCurrency
import com.example.msp_app.data.models.productInventory.ProductInventory
import com.example.msp_app.features.productsInventory.components.CarouselItem
import com.example.msp_app.features.productsInventory.components.CarrouselImage
import com.example.msp_app.features.sales.components.map.MapPin
import com.example.msp_app.features.sales.components.map.MapView
import com.example.msp_app.features.sales.components.productinfocard.ProductsInfoCard
import com.example.msp_app.features.sales.viewmodels.NewLocalSaleViewModel
import com.example.msp_app.features.sales.viewmodels.SaleProductsViewModel
import com.google.maps.android.compose.rememberCameraPositionState
import java.io.File

@Composable
fun SaleDescriptionScreen(localSaleId: String, navController: NavController) {
    val viewModel: NewLocalSaleViewModel = viewModel()
    val productsViewModel: SaleProductsViewModel = viewModel()

    val sale by viewModel.selectedSale.collectAsState()
    val saleProducts by viewModel.saleProducts.collectAsState()
    val saleImages by viewModel.saleImages.collectAsState()
    var cameraImageUri by remember { mutableStateOf<Uri?>(null) }
    var showImageSourceDialog by remember { mutableStateOf(false) }
    var showImageSizeError by remember { mutableStateOf(false) }
    val context = LocalContext.current

    fun createImageUri(context: Context): Uri {
        val imageFile = File(context.cacheDir, "temp_image_${System.currentTimeMillis()}.jpg")
        return FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            imageFile
        )
    }

    fun validateImageSize(context: Context, uri: Uri): Boolean {
        val maxSizeInBytes = 20 * 1000 * 1000
        return try {
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                val size = inputStream.available().toLong()
                size <= maxSizeInBytes
            } ?: false
        } catch (e: Exception) {
            false
        }
    }

    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            if (validateImageSize(context, it)) {
                viewModel.addImagesToExistingSale(context, listOf(it), localSaleId)
                showImageSizeError = false
            } else {
                showImageSizeError = true
            }
        }
        showImageSourceDialog = false
    }

    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success: Boolean ->
        if (success) {
            cameraImageUri?.let { uri ->
                if (validateImageSize(context, uri)) {
                    viewModel.addImagesToExistingSale(context, listOf(uri), localSaleId)
                    showImageSizeError = false
                } else {
                    showImageSizeError = true
                }
            }
        }
        showImageSourceDialog = false
    }

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

    if (showImageSourceDialog) {
        AlertDialog(
            onDismissRequest = { showImageSourceDialog = false },
            title = { Text("Agregar imagen") },
            text = { Text("¿Cómo deseas agregar la imagen?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        cameraImageUri = createImageUri(context)
                        cameraImageUri?.let { uri ->
                            cameraLauncher.launch(uri)
                        }
                    }
                ) {
                    Text("Cámara")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        imagePickerLauncher.launch("image/*")
                    }
                ) {
                    Text("Galería")
                }
            }
        )
    }

    if (showImageSizeError) {
        AlertDialog(
            onDismissRequest = { showImageSizeError = false },
            title = { Text("Imagen muy grande") },
            text = { Text("La imagen es muy grande (máximo 20MB). Por favor, selecciona una imagen más pequeña.") },
            confirmButton = {
                TextButton(onClick = { showImageSizeError = false }) {
                    Text("Entendido")
                }
            }
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
                                    verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center
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

                Spacer(Modifier.height(16.dp))

                Button(
                    onClick = { showImageSourceDialog = true },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "Agregar fotos",
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("Agregar más fotos", color = Color.White)
                }

                Spacer(Modifier.height(16.dp))
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