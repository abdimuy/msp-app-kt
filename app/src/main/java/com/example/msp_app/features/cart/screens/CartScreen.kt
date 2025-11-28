package com.example.msp_app.features.cart.screens

import android.net.Uri
import android.widget.Toast
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import com.composables.icons.lucide.Lucide
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
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
import androidx.core.content.FileProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.composables.icons.lucide.FileText
import com.composables.icons.lucide.Printer
import com.composables.icons.lucide.Truck
import com.example.msp_app.components.DrawerContainer
import com.example.msp_app.core.context.LocalAuthViewModel
import com.example.msp_app.core.utils.PdfGenerator
import com.example.msp_app.core.utils.ResultState
import com.example.msp_app.data.models.productInventory.ProductInventory
import com.example.msp_app.features.cart.components.CartItemCard
import com.example.msp_app.features.cart.viewmodels.CartViewModel
import com.example.msp_app.features.payments.components.pdfgenerationdialog.PdfGenerationDialog
import com.example.msp_app.features.productsInventory.viewmodels.ProductsInventoryViewModel
import com.example.msp_app.features.productsInventoryImages.viewmodels.ProductInventoryImagesViewModel
import com.example.msp_app.features.warehouses.WarehouseViewModel
import com.example.msp_app.ui.theme.ThemeController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CartScreen(navController: NavController) {
    val cartViewModel: CartViewModel = viewModel()
    val warehouseViewModel: WarehouseViewModel = viewModel()
    val imagesViewModel: ProductInventoryImagesViewModel = viewModel()
    val productsInventoryViewModel: ProductsInventoryViewModel = viewModel()
    val authViewModel = LocalAuthViewModel.current
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current

    var isGeneratingPdf by remember { mutableStateOf(false) }
    var pdfUri by remember { mutableStateOf<Uri?>(null) }
    var showPdfDialog by remember { mutableStateOf(false) }

    val cartProducts = cartViewModel.cartProducts
    val imagesByProduct by imagesViewModel.imagesByProduct.collectAsState()
    val saveCartState by warehouseViewModel.saveCartState.collectAsState()
    val warehouseState by warehouseViewModel.warehouseProducts.collectAsState()
    val hasUnsavedChanges by cartViewModel.hasUnsavedChanges.collectAsState()
    val userData by authViewModel.userData.collectAsState()
    val loadingOperations by cartViewModel.loadingOperations.collectAsState()
    val transferState by warehouseViewModel.transferState.collectAsState()

    val camionetaAsignada = when (val userState = userData) {
        is ResultState.Success -> userState.data?.CAMIONETA_ASIGNADA
        else -> null
    }

    var nombreAlmacenAsignado by remember { mutableStateOf<String?>(null) }

    var showDeleteDialog by remember { mutableStateOf(false) }
    var productToDelete by remember { mutableStateOf<ProductInventory?>(null) }

    var generalWarehouseStock by remember { mutableStateOf<Map<Int, Int>>(emptyMap()) }

    val snackbarHostState = remember { SnackbarHostState() }

    fun handleTransfer(
        product: ProductInventory,
        quantity: Int,
        isIncrease: Boolean,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        camionetaAsignada?.let { camionetaId ->
            if (isIncrease) {
                warehouseViewModel.createTransfer(
                    originWarehouseId = 19,
                    destinationWarehouseId = camionetaId,
                    products = listOf(product to quantity),
                    description = "➕ ${product.ARTICULO}"
                )
            } else {
                warehouseViewModel.createTransfer(
                    originWarehouseId = camionetaId,
                    destinationWarehouseId = 19,
                    products = listOf(product to quantity),
                    description = "➖ ${product.ARTICULO}"
                )
            }
            onSuccess()
        } ?: onError("No hay camioneta asignada")
    }

    fun handleRemoveProduct(product: ProductInventory, quantity: Int) {
        camionetaAsignada?.let { camionetaId ->
            warehouseViewModel.createTransfer(
                originWarehouseId = camionetaId,
                destinationWarehouseId = 19,
                products = listOf(product to quantity),
                description = "🗑️ Eliminado: ${product.ARTICULO}"
            )
        }
    }

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
                nombreAlmacenAsignado = state.data.body.ALMACEN?.ALMACEN ?: camionetaAsignada?.let { "Almacén ID: $it" }
                val warehouseProducts = warehouseViewModel.getWarehouseProductsForCart()
                cartViewModel.mergeCartWithWarehouse(warehouseProducts, isInitialLoad = true)
            }

            is ResultState.Error -> {
                nombreAlmacenAsignado = camionetaAsignada?.let { "Almacén ID: $it" }
            }

            else -> {}
        }
    }

    LaunchedEffect(Unit) {
        imagesViewModel.loadLocalImages()
    }

    LaunchedEffect(cartProducts.size) {
        if (cartProducts.isNotEmpty()) {
            val stockMap = mutableMapOf<Int, Int>()
            cartProducts.forEach { cartItem ->
                val stock = productsInventoryViewModel.getProductStock(cartItem.product.ARTICULO_ID)
                if (stock != null) {
                    stockMap[cartItem.product.ARTICULO_ID] = stock
                }
            }
            generalWarehouseStock = stockMap
        } else {
            generalWarehouseStock = emptyMap()
        }
    }

    LaunchedEffect(transferState) {
        when (val state = transferState) {
            is ResultState.Success -> {
                snackbarHostState.showSnackbar("✅ Transferencia completada")
                warehouseViewModel.resetTransferState()
            }

            is ResultState.Error -> {
                snackbarHostState.showSnackbar("❌ Error: ${state.message}")
                warehouseViewModel.resetTransferState()
            }

            else -> {}
        }
    }

    LaunchedEffect(saveCartState) {
        when (saveCartState) {
            is ResultState.Success -> {
                snackbarHostState.showSnackbar("Carrito guardado en el almacén exitosamente")
                cartViewModel.markAsSaved()
                warehouseViewModel.resetSaveCartState()
            }

            is ResultState.Error -> {
                warehouseViewModel.resetSaveCartState()
            }

            else -> {}
        }
    }

    DrawerContainer(navController = navController) { openDrawer ->
        Scaffold(
            snackbarHost = { SnackbarHost(snackbarHostState) },
            topBar = {
                TopAppBar(
                    title = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Lucide.Truck,
                                contentDescription = null,
                                modifier = Modifier.size(32.dp),
                                tint = if (ThemeController.isDarkMode) Color.White else MaterialTheme.colorScheme.onSurface
                            )
                            Column {
                                Text("Camioneta (${cartViewModel.getTotalItems()})")
                                nombreAlmacenAsignado?.let { nombre ->
                                    Text(
                                        text = nombre,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                } ?: run {
                                    if (camionetaAsignada == null) {
                                        Text(
                                            text = "Sin camioneta asignada",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.error
                                        )
                                    }
                                }
                            }
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = openDrawer) {
                            Icon(
                                imageVector = Icons.Default.Menu,
                                contentDescription = "Menú"
                            )
                        }
                    },
                    actions = {
                        // PDF Button
                        if (cartProducts.isNotEmpty() && nombreAlmacenAsignado != null) {
                            IconButton(
                                onClick = {
                                    coroutineScope.launch {
                                        isGeneratingPdf = true
                                        val file = withContext(Dispatchers.IO) {
                                            PdfGenerator.generateWarehouseInventoryPdf(
                                                context = context,
                                                warehouseName = nombreAlmacenAsignado ?: "Mi Camioneta",
                                                totalStock = cartProducts.sumOf { it.quantity },
                                                assignedUsers = emptyList(),
                                                products = cartProducts.map { it.product },
                                                fileName = "inventario_camioneta.pdf"
                                            )
                                        }
                                        isGeneratingPdf = false
                                        if (file != null && file.exists()) {
                                            val uri = FileProvider.getUriForFile(
                                                context,
                                                context.packageName + ".fileprovider",
                                                file
                                            )
                                            pdfUri = uri
                                            showPdfDialog = true
                                        } else {
                                            Toast.makeText(
                                                context,
                                                "Error al generar PDF",
                                                Toast.LENGTH_SHORT
                                            ).show()
                                        }
                                    }
                                },
                                enabled = !isGeneratingPdf
                            ) {
                                if (isGeneratingPdf) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(24.dp),
                                        strokeWidth = 2.dp
                                    )
                                } else {
                                    Icon(
                                        imageVector = Lucide.Printer,
                                        contentDescription = "Generar PDF"
                                    )
                                }
                            }
                        }
                    }
                )
            },
        ) { paddingValues ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            ) {
                if (camionetaAsignada == null) {
                    // Mostrar mensaje cuando no hay camioneta asignada
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "⚠️ No tienes una camioneta asignada",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.error,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Contacta al administrador para que te asigne una camioneta antes de poder transferir productos.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
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
                                    text = "Error al cargar el almacén",
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
                                Text(text = "El almacén está vacío")
                            }
                        } else {
                            LazyColumn(
                                contentPadding = PaddingValues(
                                    top = 8.dp,
                                    start = 16.dp,
                                    end = 16.dp,
                                    bottom = 16.dp
                                ),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.fillMaxSize()
                            ) {
                                items(cartViewModel.cartProducts) { cartItem ->
                                    val stockLimit =
                                        generalWarehouseStock[cartItem.product.ARTICULO_ID]
                                            ?: cartItem.product.EXISTENCIAS
                                    val imageUrls =
                                        imagesByProduct[cartItem.product.ARTICULO_ID] ?: emptyList()
                                    CartItemCard(
                                        cartItem = cartItem,
                                        imageUrls = imageUrls,
                                        onRemove = {
                                            productToDelete = cartItem.product
                                            showDeleteDialog = true
                                        },
                                        generalWarehouseStock = generalWarehouseStock[cartItem.product.ARTICULO_ID],
                                        isIncreaseLoading = loadingOperations.contains("inc_${cartItem.product.ARTICULO_ID}"),
                                        isDecreaseLoading = loadingOperations.contains("dec_${cartItem.product.ARTICULO_ID}"),
                                        isRemoveLoading = loadingOperations.contains("remove_${cartItem.product.ARTICULO_ID}"),
                                        onIncreaseQuantity = {
                                            cartViewModel.increaseQuantity(
                                                cartItem.product,
                                                stockLimit
                                            ) { product, quantityChange, isIncrease, onSuccess, onError ->
                                                handleTransfer(
                                                    product,
                                                    1,
                                                    isIncrease,
                                                    onSuccess,
                                                    onError
                                                )
                                            }
                                        },
                                        onDecreaseQuantity = {
                                            cartViewModel.decreaseQuantity(
                                                cartItem.product
                                            ) { product, quantityChange, isIncrease, onSuccess, onError ->
                                                handleTransfer(
                                                    product,
                                                    1,
                                                    isIncrease,
                                                    onSuccess,
                                                    onError
                                                )
                                            }
                                        },
                                        onBatchIncrease = { quantity ->
                                            cartViewModel.increaseQuantity(
                                                cartItem.product,
                                                stockLimit
                                            ) { product, quantityChange, isIncrease, onSuccess, onError ->
                                                handleTransfer(
                                                    product,
                                                    quantity,
                                                    isIncrease,
                                                    onSuccess,
                                                    onError
                                                )
                                            }
                                        },
                                        onBatchDecrease = { quantity ->
                                            repeat(quantity) {
                                                cartViewModel.decreaseQuantity(
                                                    cartItem.product
                                                ) { product, quantityChange, isIncrease, onSuccess, onError ->
                                                    handleTransfer(
                                                        product,
                                                        1,
                                                        isIncrease,
                                                        onSuccess,
                                                        onError
                                                    )
                                                }
                                            }
                                        },
                                        navController = navController,
                                        product = cartItem.product
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Diálogo de confirmación para eliminar producto
        if (showDeleteDialog && productToDelete != null) {
            val product = productToDelete!!
            val cartItem =
                cartViewModel.cartProducts.find { it.product.ARTICULO_ID == product.ARTICULO_ID }

            AlertDialog(
                onDismissRequest = {
                    showDeleteDialog = false
                    productToDelete = null
                },
                title = { Text("Eliminar producto") },
                text = {
                    Column {
                        Text("¿Estás seguro de que quieres eliminar este producto del carrito?")
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Producto: ${product.ARTICULO}",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold
                        )
                        cartItem?.let {
                            Text(
                                text = "Cantidad: ${it.quantity}",
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Los productos se devolverán al almacén general.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            cartItem?.let { item ->
                                cartViewModel.removeProduct(product) { prod, quantity, onSuccess, onError ->
                                    handleRemoveProduct(prod, quantity)
                                    onSuccess()
                                }
                            }
                            showDeleteDialog = false
                            productToDelete = null
                        },
                        enabled = !loadingOperations.contains("remove_${product.ARTICULO_ID}")
                    ) {
                        if (loadingOperations.contains("remove_${product.ARTICULO_ID}")) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                        } else {
                            Text("Eliminar")
                        }
                    }
                },
                dismissButton = {
                    Button(
                        onClick = {
                            showDeleteDialog = false
                            productToDelete = null
                        },
                        enabled = !loadingOperations.contains("remove_${product.ARTICULO_ID}")
                    ) {
                        Text("Cancelar")
                    }
                }
            )
        }

        // PDF Dialog
        if (showPdfDialog && pdfUri != null) {
            PdfGenerationDialog(
                pdfUri = pdfUri!!,
                onDismiss = {
                    showPdfDialog = false
                    pdfUri = null
                }
            )
        }
    }
}

