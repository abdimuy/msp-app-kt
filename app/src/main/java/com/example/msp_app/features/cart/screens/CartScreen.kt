package com.example.msp_app.features.cart.screens

import androidx.activity.ComponentActivity
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.example.msp_app.R
import com.example.msp_app.components.DrawerContainer
import com.example.msp_app.core.utils.ResultState
import com.example.msp_app.core.utils.parsePriceJsonToMap
import com.example.msp_app.core.utils.toCurrency
import com.example.msp_app.data.models.productInventory.ProductInventory
import com.example.msp_app.features.cart.viewmodels.CartItem
import com.example.msp_app.features.cart.viewmodels.CartViewModel
import com.example.msp_app.features.productsInventoryImages.viewmodels.ProductInventoryImagesViewModel
import com.example.msp_app.features.warehouses.WarehouseViewModel
import com.example.msp_app.navigation.Screen
import com.example.msp_app.ui.theme.ThemeController

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CartScreen(navController: NavController) {

    val activity = LocalActivity.current as? ComponentActivity
        ?: error("Se requiere una ComponentActivity para obtener ViewModel")

    val cartViewModel: CartViewModel = viewModel(viewModelStoreOwner = activity)
    val warehouseViewModel: WarehouseViewModel = viewModel(viewModelStoreOwner = activity)
    val imagesViewModel: ProductInventoryImagesViewModel = viewModel(viewModelStoreOwner = activity)

    val cartProducts = cartViewModel.cartProducts
    val imagesByProduct by imagesViewModel.imagesByProduct.collectAsState()
    val saveCartState by warehouseViewModel.saveCartState.collectAsState()
    val warehouseState by warehouseViewModel.warehouseProducts.collectAsState()
    val hasUnsavedChanges by cartViewModel.hasUnsavedChanges.collectAsState()

    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        warehouseViewModel.getWarehouseProducts()
        imagesViewModel.loadLocalImages()
    }

    LaunchedEffect(warehouseState) {
        val state = warehouseState
        if (state is ResultState.Success) {
            val warehouseProducts = warehouseViewModel.getWarehouseProductsForCart()
            cartViewModel.mergeCartWithWarehouse(warehouseProducts, isInitialLoad = true)
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
                    title = { Text("Almacén (${cartViewModel.getTotalItems()})") },
                    navigationIcon = {
                        IconButton(onClick = openDrawer) {
                            Icon(
                                imageVector = Icons.Default.Menu,
                                contentDescription = "Menú"
                            )
                        }
                    }
                )
            },
            bottomBar = {
                if (cartProducts.isNotEmpty()) {
                    Button(
                        onClick = {
                            val cartItems = cartViewModel.getCartItemsForWarehouse()
                            warehouseViewModel.saveCartToWarehouse(cartItems)
                        },
                        enabled = hasUnsavedChanges && saveCartState !is ResultState.Loading,
                        modifier = Modifier
                            .fillMaxWidth()
                            .navigationBarsPadding()
                            .padding(6.dp)
                    ) {
                        if (saveCartState is ResultState.Loading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                        }
                        Text(
                            text = when {
                                saveCartState is ResultState.Loading -> "Guardando..."
                                hasUnsavedChanges -> "Guardar Cambios"
                                else -> "Sin Cambios"
                            }
                        )
                    }
                }
            }
        ) { paddingValues ->
            when (warehouseState) {
                is ResultState.Loading -> {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(paddingValues),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                }

                is ResultState.Error -> {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(paddingValues),
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
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(paddingValues),
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
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(paddingValues)
                        ) {
                            items(cartProducts) { cartItem ->
                                val imageUrls =
                                    imagesByProduct[cartItem.product.ARTICULO_ID] ?: emptyList()
                                CartItemCard(
                                    cartItem = cartItem,
                                    imageUrls = imageUrls,
                                    onRemove = { cartViewModel.removeProduct(cartItem.product) },
                                    onIncreaseQuantity = { cartViewModel.increaseQuantity(cartItem.product) },
                                    onDecreaseQuantity = { cartViewModel.decreaseQuantity(cartItem.product) },
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
}

@Composable
fun CartItemCard(
    cartItem: CartItem,
    imageUrls: List<String>,
    onRemove: () -> Unit,
    onIncreaseQuantity: () -> Unit,
    onDecreaseQuantity: () -> Unit,
    navController: NavController,
    product: ProductInventory
) {
    val isDark = ThemeController.isDarkMode

    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = if (!isDark) 8.dp else 0.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = if (!isDark) null else BorderStroke(1.dp, Color.DarkGray),
        onClick = { navController.navigate(Screen.ProductDetails.createRoute(product.ARTICULO_ID.toString())) }

    ) {
        Column(modifier = Modifier.padding(12.dp)) {

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (imageUrls.isNotEmpty()) {
                    AsyncImage(
                        model = "file://${imageUrls.first()}",
                        contentDescription = "Imagen del producto",
                        modifier = Modifier
                            .size(90.dp)
                            .clip(RoundedCornerShape(8.dp)),
                        contentScale = ContentScale.Crop,
                        placeholder = painterResource(id = R.drawable.images_photo)
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .size(90.dp)
                            .background(
                                if (isDark) Color.Transparent else Color(0xFFE2E3E5),
                                RoundedCornerShape(8.dp)
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.images_photo),
                            contentDescription = "Sin imagen disponible",
                            modifier = Modifier.size(154.dp),
                            tint = Color(0xFFB8C3D4)
                        )
                    }
                }

                Spacer(modifier = Modifier.width(8.dp))

                Column(
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = cartItem.product.ARTICULO,
                        fontSize = 16.sp,
                        style = MaterialTheme.typography.bodyLarge,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        fontWeight = FontWeight.SemiBold,
                        color = if (isDark) MaterialTheme.colorScheme.onSurface else Color(
                            0xFF003366
                        )
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "Stock: ${cartItem.product.EXISTENCIAS}",
                            fontSize = 14.sp,
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Bold,
                            color = if (isDark) MaterialTheme.colorScheme.onSurface else Color(
                                0xFF0056B3
                            ),

                            )

                        Spacer(modifier = Modifier.width(16.dp))

                        IconButton(
                            onClick = onDecreaseQuantity,
                            modifier = Modifier.size(32.dp)
                        ) {
                            Text(
                                text = "−",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }

                        Text(
                            text = "${cartItem.quantity}",
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.padding(horizontal = 4.dp),
                            color = if (isDark) MaterialTheme.colorScheme.onSurface else Color.Unspecified
                        )

                        IconButton(
                            onClick = onIncreaseQuantity,
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = "Aumentar cantidad",
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }

                        IconButton(
                            onClick = onRemove,
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = "Eliminar producto del carrito",
                                tint = Color.Red,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.size(8.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                val priceMap = parsePriceJsonToMap(cartItem.product.PRECIOS)
                priceMap.toList().asReversed().forEach { (label, value) ->
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = label,
                            style = MaterialTheme.typography.labelSmall,
                            color = if (isDark) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = value.toCurrency(noDecimals = true),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }
        }
    }
}