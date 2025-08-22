package com.example.msp_app.features.productsInventory.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.carousel.HorizontalUncontainedCarousel
import androidx.compose.material3.carousel.rememberCarouselState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import coil.compose.AsyncImage
import coil.compose.AsyncImagePainter
import coil.request.ImageRequest
import com.example.msp_app.R
import com.example.msp_app.components.DrawerContainer
import com.example.msp_app.core.context.LocalAuthViewModel
import com.example.msp_app.core.utils.ResultState
import com.example.msp_app.core.utils.parsePriceJsonToMap
import com.example.msp_app.core.utils.toCurrency
import com.example.msp_app.features.cart.viewmodels.CartViewModel
import com.example.msp_app.features.productsInventory.viewmodels.ProductDetailsViewModel
import com.example.msp_app.features.productsInventory.viewmodels.ProductsInventoryViewModel
import com.example.msp_app.features.productsInventoryImages.viewmodels.ProductInventoryImagesViewModel
import com.example.msp_app.navigation.Screen
import com.example.msp_app.ui.theme.ThemeController
import kotlinx.coroutines.launch
import java.io.File

@Composable
fun ProductDetailsScreen(
    productId: String,
    navController: NavController,
) {
    val detailsViewModel: ProductDetailsViewModel = viewModel()
    val cartViewModel: CartViewModel = viewModel()
    val imagesViewModel: ProductInventoryImagesViewModel = viewModel()
    val productsInventoryViewModel: ProductsInventoryViewModel = viewModel()
    val warehouseViewModel: com.example.msp_app.features.warehouses.WarehouseViewModel = viewModel()
    val authViewModel = LocalAuthViewModel.current

    val imagesByProduct by imagesViewModel.imagesByProduct.collectAsState()
    val product by detailsViewModel.product.collectAsState()
    val transferState by warehouseViewModel.transferState.collectAsState()
    val userData by authViewModel.userData.collectAsState()
    val warehouseProducts by warehouseViewModel.warehouseProducts.collectAsState()
    val isDark = ThemeController.isDarkMode
    var imageReloadTrigger by remember { mutableIntStateOf(0) }
    var showTransferDialog by remember { mutableStateOf(false) }
    var transferQuantity by remember { mutableIntStateOf(1) }
    var transferErrorMessage by remember { mutableStateOf<String?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    val camionetaAsignada = when (val userState = userData) {
        is ResultState.Success -> userState.data?.CAMIONETA_ASIGNADA
        else -> null
    }

    var nombreAlmacenAsignado by remember { mutableStateOf<String?>(null) }
    var generalWarehouseStock by remember { mutableStateOf<Int?>(null) }
    var truckWarehouseStock by remember { mutableStateOf<Int?>(null) }

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

    LaunchedEffect(warehouseProducts) {
        when (val state = warehouseProducts) {
            is ResultState.Success -> {
                nombreAlmacenAsignado = state.data.body.ALMACEN.ALMACEN
            }

            is ResultState.Error -> {
                nombreAlmacenAsignado = camionetaAsignada?.let { "Almacén ID: $it" }
            }

            else -> {}
        }
    }

    LaunchedEffect(productId) {
        productId.toIntOrNull()?.let { id ->
            detailsViewModel.loadProductById(id)
        }
    }

    LaunchedEffect(product?.ARTICULO_ID, camionetaAsignada) {
        product?.ARTICULO_ID?.let { productId ->
            imagesViewModel.loadLocalImages()
            scope.launch {
                generalWarehouseStock = productsInventoryViewModel.getProductStock(productId)
            }

            camionetaAsignada?.let { truckId ->
                try {
                    warehouseViewModel.selectWarehouse(truckId)
                } catch (e: Exception) {
                }
            }
        }
    }

    LaunchedEffect(warehouseProducts, product?.ARTICULO_ID) {
        when (val state = warehouseProducts) {
            is ResultState.Success -> {
                product?.ARTICULO_ID?.let { productId ->
                    val productInTruck =
                        state.data.body.ARTICULOS.find { it.ARTICULO_ID == productId }
                    truckWarehouseStock = productInTruck?.EXISTENCIAS ?: 0
                }
            }

            else -> {
                truckWarehouseStock = null
            }
        }
    }

    LaunchedEffect(transferState) {
        when (val state = transferState) {
            is ResultState.Success -> {
                showTransferDialog = false
                transferErrorMessage = null
                transferQuantity = 1
                snackbarHostState.showSnackbar("Transferencia realizada exitosamente")
                warehouseViewModel.resetTransferState()
            }

            is ResultState.Error -> {
                if (state.message.contains("422") || state.message.contains(
                        "existencias",
                        ignoreCase = true
                    )
                ) {
                    transferErrorMessage = "No hay existencias suficientes en el almacén origen"
                } else {
                    transferErrorMessage = state.message
                }
                warehouseViewModel.resetTransferState()
            }

            else -> {}
        }
    }

    DrawerContainer(navController = navController) { openDrawer ->
        Scaffold(
            modifier = Modifier.statusBarsPadding(),
            snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
            topBar = {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 8.dp)
                        .height(56.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = openDrawer,
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Menu,
                            contentDescription = "Menú",
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = "Detalles",
                            style = MaterialTheme.typography.titleLarge,
                            maxLines = 1
                        )
                        nombreAlmacenAsignado?.let { nombre ->
                            Text(
                                text = "🚚 $nombre",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        } ?: run {
                            if (camionetaAsignada == null) {
                                Text(
                                    text = "⚠️ Sin camioneta asignada",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.error,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }

                    IconButton(
                        onClick = {
                            imageReloadTrigger++
                            imagesViewModel.loadLocalImages()
                        },
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Recargar imágenes",
                            modifier = Modifier.size(24.dp)
                        )
                    }

                    IconButton(
                        onClick = {
                            navController.navigate(Screen.Cart.route)
                        },
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.ShoppingCart,
                            contentDescription = "Carrito",
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            }
        ) { innerPadding ->
            product?.let { currentProduct ->
                val scrollState = rememberScrollState()

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                        .verticalScroll(scrollState)
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = currentProduct.ARTICULO,
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                    )

                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                        ),
                        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                        border = null
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceEvenly
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = "Stock Almacén General",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = generalWarehouseStock?.toString() ?: "Cargando...",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }

                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = "En tu camioneta",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )

                                Text(
                                    text = truckWarehouseStock?.toString() ?: "Cargando...",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.secondary
                                )
                            }
                        }
                    }

                    Box(
                        modifier = Modifier
                            .fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        val imagePaths = imagesByProduct[currentProduct.ARTICULO_ID] ?: emptyList()

                        val carouselItems = remember(imagePaths, imageReloadTrigger) {
                            imagePaths.mapIndexed { index, path ->
                                CarouselItem(
                                    id = index,
                                    imagePath = path,
                                    description = "Imagen ${index + 1} de ${currentProduct.ARTICULO}"
                                )
                            }
                        }

                        if (carouselItems.isNotEmpty()) {
                            Carousel(
                                carouselItems = carouselItems,
                                reloadTrigger = imageReloadTrigger
                            )
                        } else {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(205.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center
                                ) {
                                    Icon(
                                        painter = painterResource(id = R.drawable.images_photo),
                                        contentDescription = "Sin imagen disponible",
                                        modifier = Modifier.size(154.dp),
                                        tint = Color(0xFFB8C3D4)
                                    )
                                    Spacer(modifier = Modifier.height(12.dp))
                                    Text(
                                        text = "Este producto aún no tiene una imagen asignada",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }

                    Spacer(Modifier.height(12.dp))
                    val priceMap = try {
                        parsePriceJsonToMap(currentProduct.PRECIOS)
                    } catch (e: Exception) {
                        emptyMap()
                    }
                    Card(
                        elevation = CardDefaults.cardElevation(
                            defaultElevation = if (!isDark) 8.dp else 0.dp
                        ),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surface
                        ),
                        border = if (!isDark) null else BorderStroke(
                            width = 1.dp,
                            color = Color.DarkGray
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(6.dp),
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = "Precios",
                                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                                modifier = Modifier.padding(bottom = 8.dp)
                            )

                            priceMap.forEach { (label, value) ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 2.dp),
                                    horizontalArrangement = Arrangement.Start
                                ) {
                                    Text(
                                        text = "$label: ",
                                        color = MaterialTheme.colorScheme.onSurface,
                                        style = MaterialTheme.typography.bodyLarge,
                                        modifier = Modifier.alignByBaseline()
                                    )
                                    Text(
                                        text = value.toCurrency(noDecimals = true),
                                        color = Color(0xFF16A34A),
                                        style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold),
                                        modifier = Modifier.alignByBaseline()
                                    )
                                }
                            }
                        }
                    }

                    if (showTransferDialog) {
                        product?.let { currentProduct ->
                            Dialog(
                                onDismissRequest = {
                                    showTransferDialog = false
                                    transferErrorMessage = null
                                }
                            ) {
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(16.dp),
                                    colors = CardDefaults.cardColors(
                                        containerColor = MaterialTheme.colorScheme.surface
                                    ),
                                    elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
                                ) {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(24.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(64.dp)
                                                .background(
                                                    MaterialTheme.colorScheme.primaryContainer,
                                                    RoundedCornerShape(32.dp)
                                                ),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.ShoppingCart,
                                                contentDescription = "Transferir",
                                                modifier = Modifier.size(32.dp),
                                                tint = MaterialTheme.colorScheme.onPrimaryContainer
                                            )
                                        }

                                        Spacer(modifier = Modifier.height(16.dp))

                                        Text(
                                            text = "Agregar al carrito",
                                            style = MaterialTheme.typography.headlineSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )

                                        Spacer(modifier = Modifier.height(8.dp))

                                        Text(
                                            text = currentProduct.ARTICULO,
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            textAlign = TextAlign.Center,
                                            modifier = Modifier.fillMaxWidth()
                                        )

                                        Spacer(modifier = Modifier.height(16.dp))

                                        Card(
                                            modifier = Modifier.fillMaxWidth(),
                                            colors = CardDefaults.cardColors(
                                                containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(
                                                    alpha = 0.3f
                                                )
                                            ),
                                            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                                        ) {
                                            Column(
                                                modifier = Modifier.padding(16.dp),
                                                horizontalAlignment = Alignment.CenterHorizontally
                                            ) {
                                                Text(
                                                    text = "🏪 Almacén General → 🚚 ${nombreAlmacenAsignado ?: "Tu camioneta"}",
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    fontWeight = FontWeight.Medium,
                                                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                                                    textAlign = TextAlign.Center
                                                )
                                                camionetaAsignada?.let { almacenId ->
                                                    Text(
                                                        text = "ID: $almacenId",
                                                        style = MaterialTheme.typography.labelSmall,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                                    )
                                                }
                                            }
                                        }

                                        Spacer(modifier = Modifier.height(24.dp))

                                        Card(
                                            modifier = Modifier.fillMaxWidth(),
                                            colors = CardDefaults.cardColors(
                                                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(
                                                    alpha = 0.3f
                                                )
                                            ),
                                            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                                        ) {
                                            Column(
                                                modifier = Modifier.padding(16.dp),
                                                horizontalAlignment = Alignment.CenterHorizontally
                                            ) {
                                                Text(
                                                    text = "Cantidad a transferir",
                                                    style = MaterialTheme.typography.labelMedium,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )

                                                Spacer(modifier = Modifier.height(12.dp))

                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                                                ) {
                                                    IconButton(
                                                        onClick = { if (transferQuantity > 1) transferQuantity-- },
                                                        enabled = transferQuantity > 1 && transferState !is ResultState.Loading,
                                                        modifier = Modifier
                                                            .size(48.dp)
                                                            .background(
                                                                if (transferQuantity > 1 && transferState !is ResultState.Loading)
                                                                    MaterialTheme.colorScheme.primaryContainer
                                                                else MaterialTheme.colorScheme.surfaceVariant,
                                                                RoundedCornerShape(24.dp)
                                                            )
                                                    ) {
                                                        Text(
                                                            text = "−",
                                                            style = MaterialTheme.typography.titleLarge,
                                                            fontWeight = FontWeight.Bold,
                                                            color = if (transferQuantity > 1 && transferState !is ResultState.Loading)
                                                                MaterialTheme.colorScheme.onPrimaryContainer
                                                            else MaterialTheme.colorScheme.onSurfaceVariant
                                                        )
                                                    }

                                                    Box(
                                                        modifier = Modifier
                                                            .background(
                                                                MaterialTheme.colorScheme.primaryContainer,
                                                                RoundedCornerShape(12.dp)
                                                            )
                                                            .padding(
                                                                horizontal = 24.dp,
                                                                vertical = 12.dp
                                                            ),
                                                        contentAlignment = Alignment.Center
                                                    ) {
                                                        Text(
                                                            text = transferQuantity.toString(),
                                                            style = MaterialTheme.typography.titleLarge,
                                                            fontWeight = FontWeight.Bold,
                                                            color = MaterialTheme.colorScheme.onPrimaryContainer
                                                        )
                                                    }

                                                    IconButton(
                                                        onClick = { transferQuantity++ },
                                                        enabled = transferState !is ResultState.Loading,
                                                        modifier = Modifier
                                                            .size(48.dp)
                                                            .background(
                                                                if (transferState !is ResultState.Loading)
                                                                    MaterialTheme.colorScheme.primaryContainer
                                                                else MaterialTheme.colorScheme.surfaceVariant,
                                                                RoundedCornerShape(24.dp)
                                                            )
                                                    ) {
                                                        Icon(
                                                            imageVector = Icons.Default.Add,
                                                            contentDescription = "Aumentar",
                                                            tint = if (transferState !is ResultState.Loading)
                                                                MaterialTheme.colorScheme.onPrimaryContainer
                                                            else MaterialTheme.colorScheme.onSurfaceVariant
                                                        )
                                                    }
                                                }
                                            }
                                        }

                                        transferErrorMessage?.let { errorMsg ->
                                            Spacer(modifier = Modifier.height(16.dp))
                                            Card(
                                                colors = CardDefaults.cardColors(
                                                    containerColor = MaterialTheme.colorScheme.errorContainer
                                                ),
                                                modifier = Modifier.fillMaxWidth()
                                            ) {
                                                Row(
                                                    modifier = Modifier.padding(12.dp),
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.Warning,
                                                        contentDescription = "Error",
                                                        tint = MaterialTheme.colorScheme.error,
                                                        modifier = Modifier.size(20.dp)
                                                    )
                                                    Spacer(modifier = Modifier.width(8.dp))
                                                    Text(
                                                        text = errorMsg,
                                                        color = MaterialTheme.colorScheme.onErrorContainer,
                                                        style = MaterialTheme.typography.bodySmall
                                                    )
                                                }
                                            }
                                        }

                                        Spacer(modifier = Modifier.height(24.dp))

                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                                        ) {
                                            OutlinedButton(
                                                onClick = {
                                                    showTransferDialog = false
                                                    transferErrorMessage = null
                                                },
                                                enabled = transferState !is ResultState.Loading,
                                                modifier = Modifier.weight(1f)
                                            ) {
                                                Text("Cancelar")
                                            }

                                            Button(
                                                onClick = {
                                                    camionetaAsignada?.let { destinationId ->
                                                        scope.launch {
                                                            warehouseViewModel.createTransfer(
                                                                originWarehouseId = 19,
                                                                destinationWarehouseId = destinationId,
                                                                products = listOf(currentProduct to transferQuantity),
                                                                description = "Transferencia de ${currentProduct.ARTICULO} a camioneta"
                                                            )
                                                        }
                                                    }
                                                },
                                                enabled = transferState !is ResultState.Loading,
                                                modifier = Modifier.weight(1f)
                                            ) {
                                                if (transferState is ResultState.Loading) {
                                                    CircularProgressIndicator(
                                                        modifier = Modifier.size(16.dp),
                                                        color = MaterialTheme.colorScheme.onPrimary
                                                    )
                                                } else {
                                                    Text("Agregar")
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Button(onClick = { }) {
                        Text("Vender")
                    }
                    Button(
                        onClick = {
                            if (camionetaAsignada != null) {
                                showTransferDialog = true
                            } else {
                                scope.launch {
                                    snackbarHostState.showSnackbar(
                                        "No tienes una camioneta asignada. Contacta al administrador."
                                    )
                                }
                            }
                        }
                    ) {
                        Text("Añadir al Carrito")
                    }
                }
            } ?: run {

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        CircularProgressIndicator()
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "Cargando producto...",
                            style = MaterialTheme.typography.bodyLarge
                        )
                    }
                }
            }
        }
    }
}

data class CarouselItem(
    val id: Int,
    val imagePath: String,
    val description: String = ""
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Carousel(
    carouselItems: List<CarouselItem>,
    reloadTrigger: Int = 0
) {
    val selectedItem = remember { mutableStateOf<CarouselItem?>(null) }
    val context = LocalContext.current

    HorizontalUncontainedCarousel(
        state = rememberCarouselState { carouselItems.size },
        modifier = Modifier
            .fillMaxWidth()
            .wrapContentHeight()
            .padding(top = 16.dp, bottom = 16.dp),
        itemWidth = 186.dp,
        itemSpacing = 8.dp,
        contentPadding = PaddingValues(horizontal = 16.dp)
    ) { i ->
        val item = carouselItems[i]
        val file = File(item.imagePath)

        if (file.exists()) {
            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data(file)
                    .memoryCacheKey("${file.absolutePath}_${reloadTrigger}")
                    .diskCacheKey("${file.absolutePath}_${file.lastModified()}")
                    .build(),
                contentDescription = item.description,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .height(205.dp)
                    .maskClip(MaterialTheme.shapes.extraLarge)
                    .clickable {
                        selectedItem.value = item
                    },
                onState = { state ->
                    when (state) {
                        is AsyncImagePainter.State.Error -> {

                        }

                        else -> {}
                    }
                }
            )
        } else {

            Box(
                modifier = Modifier
                    .height(205.dp)
                    .maskClip(MaterialTheme.shapes.extraLarge)
                    .background(Color.Gray.copy(alpha = 0.3f)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Imagen no encontrada",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }

    selectedItem.value?.let { item ->
        ZoomableImageDialog1(
            item = item,
            onDismiss = { selectedItem.value = null },
            reloadTrigger = reloadTrigger
        )
    }
}

@Composable
fun ZoomableImageDialog1(
    item: CarouselItem,
    onDismiss: () -> Unit,
    reloadTrigger: Int = 0
) {
    var scale by remember { mutableFloatStateOf(1f) }
    var offsetX by remember { mutableFloatStateOf(0f) }
    var offsetY by remember { mutableFloatStateOf(0f) }
    val context = LocalContext.current

    val imageModifier = Modifier
        .fillMaxWidth()
        .height(500.dp)
        .graphicsLayer(
            scaleX = scale,
            scaleY = scale,
            translationX = offsetX,
            translationY = offsetY
        )
        .pointerInput(Unit) {
            detectTransformGestures { _, pan, zoom, _ ->
                scale = (scale * zoom).coerceIn(0.6f, 2.6f)
                offsetX += pan.x
                offsetY += pan.y
            }
        }

    Dialog(onDismissRequest = onDismiss) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color.Transparent, shape = MaterialTheme.shapes.large),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                if (item.imagePath.isNotEmpty()) {
                    val file = File(item.imagePath)
                    if (file.exists()) {
                        AsyncImage(
                            model = ImageRequest.Builder(context)
                                .data(file)
                                .memoryCacheKey("${file.absolutePath}_dialog_${reloadTrigger}")
                                .diskCacheKey("${file.absolutePath}_${file.lastModified()}")
                                .build(),
                            contentDescription = item.description,
                            contentScale = ContentScale.Fit,
                            modifier = imageModifier
                        )
                    } else {
                        Box(
                            modifier = imageModifier.background(Color.Gray.copy(alpha = 0.3f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "Imagen no encontrada",
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = {
                        scale = 1f
                        offsetX = 0f
                        offsetY = 0f
                    }) {
                        Text("Restaurar")
                    }

                    Button(onClick = onDismiss) {
                        Text("Cerrar")
                    }
                }
            }
        }
    }
}