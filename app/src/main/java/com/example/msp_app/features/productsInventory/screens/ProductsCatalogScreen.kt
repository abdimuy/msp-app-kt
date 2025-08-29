package com.example.msp_app.features.productsInventory.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.example.msp_app.R
import com.example.msp_app.components.DrawerContainer
import com.example.msp_app.core.utils.ResultState
import com.example.msp_app.core.utils.parsePriceJsonToMap
import com.example.msp_app.core.utils.searchSimilarItems
import com.example.msp_app.core.utils.toCurrency
import com.example.msp_app.data.models.productInventory.ProductInventory
import com.example.msp_app.features.productsInventory.viewmodels.ProductsInventoryViewModel
import com.example.msp_app.features.productsInventoryImages.viewmodels.ProductInventoryImagesViewModel
import com.example.msp_app.navigation.Screen
import com.example.msp_app.ui.theme.ThemeController
import kotlinx.coroutines.launch
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProductsCatalogScreen(navController: NavController) {

    val viewModel: ProductsInventoryViewModel = viewModel()
    val productState by viewModel.productInventoryState.collectAsState()
    var query by rememberSaveable { mutableStateOf("") }
    val focusManager = LocalFocusManager.current
    val imagesViewModel: ProductInventoryImagesViewModel = viewModel()
    val imagesByProduct by imagesViewModel.imagesByProduct.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var isRefreshing by remember { mutableStateOf(false) }

    val products = when (productState) {
        is ResultState.Success -> (productState as ResultState.Success).data
        else -> emptyList()
    }

    LaunchedEffect(Unit) {
        // Cargar productos con fallback automático (remoto primero, local si falla)
        viewModel.loadProductsWithFallback()
        imagesViewModel.loadLocalImages()
    }

    val filteredProducts = if (query.isBlank()) {
        products.filter { it.EXISTENCIAS > 0 }
    } else {
        searchSimilarItems(
            query = query,
            items = products.filter { it.EXISTENCIAS > 0 },
            threshold = 90
        ) { product -> "${product.ARTICULO}, ${product.LINEA_ARTICULO}" }
    }

    DrawerContainer(navController = navController) { openDrawer ->
        Scaffold(
            modifier = Modifier.statusBarsPadding(),
            snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
            topBar = {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = openDrawer) {
                            Icon(
                                imageVector = Icons.Default.Menu,
                                contentDescription = "Menú"
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = "Productos",
                            style = MaterialTheme.typography.titleLarge
                        )
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(
                            onClick = {
                                scope.launch {
                                    isRefreshing = true
                                    viewModel.loadProductsWithFallback()
                                    imagesViewModel.loadLocalImages()
                                    isRefreshing = false
                                    snackbarHostState.showSnackbar("Catálogo actualizado")
                                }
                            },
                            enabled = !isRefreshing && productState !is ResultState.Loading
                        ) {
                            if (isRefreshing || productState is ResultState.Loading) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(24.dp),
                                    strokeWidth = 2.dp
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Default.Refresh,
                                    contentDescription = "Actualizar catálogo"
                                )
                            }
                        }

                        IconButton(onClick = {
                            navController.navigate(Screen.Cart.route)
                        }) {
                            Icon(
                                imageVector = Icons.Default.ShoppingCart,
                                contentDescription = "Carrito"
                            )
                        }
                    }
                }
            }
        ) { innerPadding ->
            Column(
                modifier = Modifier
                    .padding(innerPadding)
                    .padding(horizontal = 16.dp)
                    .fillMaxSize()
            ) {
                // Mostrar mensaje de error si hay problemas
                when (productState) {
                    is ResultState.Error -> {
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.errorContainer
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 8.dp)
                        ) {
                            Text(
                                text = "⚠️ Modo sin conexión - Mostrando datos locales",
                                modifier = Modifier.padding(12.dp),
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                    }

                    else -> {}
                }

                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = { Text("Buscar Producto...") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp),
                    leadingIcon = {
                        Icon(Icons.Default.Search, contentDescription = "Buscar")
                    },
                    trailingIcon = {
                        if (query.isNotEmpty()) {
                            IconButton(onClick = {
                                query = ""
                                focusManager.clearFocus()
                            }) {
                                Icon(Icons.Default.Close, contentDescription = "Eliminar búsqueda")
                            }
                        }
                    },
                    shape = RoundedCornerShape(25.dp)
                )

                LazyColumn(
                    modifier = Modifier
                        .weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(bottom = 24.dp)
                ) {
                    items(
                        items = filteredProducts,
                        key = { it.ARTICULO_ID }
                    ) { product ->
                        val imageUrls = imagesByProduct[product.ARTICULO_ID] ?: emptyList()
                        ProductCard(
                            product = product,
                            imageUrls = imageUrls,
                            navController = navController
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ProductCard(
    product: ProductInventory,
    imageUrls: List<String> = emptyList(),
    navController: NavController
) {
    val isDark = ThemeController.isDarkMode

    Card(
        elevation = CardDefaults.cardElevation(
            defaultElevation =
                if (!isDark) 8.dp else 0.dp
        ),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        border =
            if (!isDark) null else BorderStroke(
                width = 1.dp,
                color = Color.DarkGray
            ),
        modifier = Modifier
            .fillMaxWidth(),
        onClick = { navController.navigate(Screen.ProductDetails.createRoute(product.ARTICULO_ID.toString())) }
    ) {

        Row(
            modifier = Modifier.padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        )
        {
            if (imageUrls.isNotEmpty()) {
                val imagePath = imageUrls.first()
                val imageFile = File(imagePath)

                AsyncImage(
                    model = imageFile,
                    contentDescription = "Imagen del producto",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(90.dp)
                        .clip(RoundedCornerShape(8.dp))
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(90.dp)
                        .background(
                            color = if (isDark) {
                                Color.Transparent
                            } else {
                                Color(0xFFE2E3E5)
                            }, RoundedCornerShape(8.dp)
                        )
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.images_photo),
                        contentDescription = "Sin imagen disponible",
                        modifier = Modifier
                            .size(154.dp)
                            .align(Alignment.Center),
                        tint = Color(0xFFB8C3D4)
                    )
                }
            }
            Column(
                modifier = Modifier
                    .fillMaxHeight()
                    .padding(start = 12.dp),
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = product.ARTICULO,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = if (isDark) {
                        MaterialTheme.colorScheme.onSurface
                    } else {
                        Color(0xFF003366)
                    },
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    lineHeight = 16.sp
                )
                Text(
                    text = buildAnnotatedString {
                        append("Stock: ")
                        withStyle(style = SpanStyle(fontWeight = FontWeight.Bold)) {
                            append("${product.EXISTENCIAS}")
                        }
                    },
                    fontSize = 14.sp,
                    color = if (isDark) {
                        MaterialTheme.colorScheme.onSurface
                    } else {
                        Color(0xFF0056B3)
                    },
                    lineHeight = 16.sp
                )
                val priceMap = try {
                    parsePriceJsonToMap(product.PRECIOS)
                } catch (e: Exception) {
                    emptyMap()
                }

                if (priceMap.isNotEmpty()) {
                    priceMap.forEach { (label, value) ->
                        Text(
                            text = buildAnnotatedString {
                                append("$label: ")
                                withStyle(style = SpanStyle(fontWeight = FontWeight.Bold)) {
                                    append(value.toCurrency(noDecimals = true))
                                }
                            },
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurface,
                            lineHeight = 16.sp
                        )
                    }
                }
            }
        }
    }
}
