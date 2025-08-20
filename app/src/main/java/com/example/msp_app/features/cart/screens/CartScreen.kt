package com.example.msp_app.features.cart.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.example.msp_app.components.DrawerContainer
import com.example.msp_app.core.utils.ResultState
import com.example.msp_app.features.cart.components.CartItemCard
import com.example.msp_app.features.cart.viewmodels.CartViewModel
import com.example.msp_app.features.productsInventoryImages.viewmodels.ProductInventoryImagesViewModel
import com.example.msp_app.features.warehouses.WarehouseViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CartScreen(navController: NavController) {

    val cartViewModel: CartViewModel = viewModel()
    val warehouseViewModel: WarehouseViewModel = viewModel()
    val imagesViewModel: ProductInventoryImagesViewModel = viewModel()

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
                            items(cartViewModel.cartProducts) { cartItem ->
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

