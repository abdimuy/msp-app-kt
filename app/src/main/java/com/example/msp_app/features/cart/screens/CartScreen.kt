package com.example.msp_app.features.cart.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
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
    var expanded by remember { mutableStateOf(false) }
    var selectedWarehouseId by remember { mutableStateOf<Int?>(null) }
    val warehouseList by warehouseViewModel.warehouseList.collectAsState()


    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        warehouseViewModel.loadAllWarehouses()
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
                            val warehouseId = selectedWarehouseId ?: return@Button

                            warehouseViewModel.createTransfer(
                                originWarehouseId = warehouseId,
                                destinationWarehouseId = warehouseId,
                                products = cartItems,
                                description = "Actualización de inventario desde carrito"
                            )
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
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("Seleccione el almacén:", fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(4.dp))

                    ExposedDropdownMenuBox(
                        expanded = expanded,
                        onExpandedChange = { expanded = !expanded }
                    ) {
                        val selectedWarehouseName =
                            warehouseList.find { it.ALMACEN_ID == selectedWarehouseId }?.ALMACEN
                                ?: "Selecciona un almacén"

                        OutlinedTextField(
                            value = selectedWarehouseName,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Almacén") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .menuAnchor(),
                            colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors()
                        )

                        ExposedDropdownMenu(
                            expanded = expanded,
                            onDismissRequest = { expanded = false }
                        ) {
                            warehouseList.forEach { warehouse ->
                                DropdownMenuItem(
                                    text = { Text("${warehouse.ALMACEN} (${warehouse.EXISTENCIAS})") },
                                    onClick = {
                                        if (selectedWarehouseId != null && selectedWarehouseId != warehouse.ALMACEN_ID) {
                                            cartViewModel.clearCartForNewWarehouse()
                                        }

                                        selectedWarehouseId = warehouse.ALMACEN_ID
                                        expanded = false
                                        warehouseViewModel.selectWarehouse(warehouse.ALMACEN_ID)
                                    }
                                )
                            }
                        }
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
                                    val stockLimit = cartItem.product.EXISTENCIAS
                                    val imageUrls =
                                        imagesByProduct[cartItem.product.ARTICULO_ID] ?: emptyList()
                                    CartItemCard(
                                        cartItem = cartItem,
                                        imageUrls = imageUrls,
                                        onRemove = { cartViewModel.removeProduct(cartItem.product) },
                                        onIncreaseQuantity = {
                                            cartViewModel.increaseQuantity(
                                                cartItem.product,
                                                stockLimit
                                            )
                                        },
                                        onDecreaseQuantity = {
                                            cartViewModel.decreaseQuantity(
                                                cartItem.product
                                            )
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
    }
}

