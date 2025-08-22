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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import coil.compose.AsyncImage
import coil.compose.AsyncImagePainter
import coil.request.ImageRequest
import com.example.msp_app.R
import com.example.msp_app.components.DrawerContainer
import com.example.msp_app.core.utils.parsePriceJsonToMap
import com.example.msp_app.core.utils.toCurrency
import com.example.msp_app.features.cart.components.AddToCartDialog
import com.example.msp_app.features.cart.viewmodels.CartViewModel
import com.example.msp_app.features.productsInventory.viewmodels.ProductDetailsViewModel
import com.example.msp_app.features.productsInventoryImages.viewmodels.ProductInventoryImagesViewModel
import com.example.msp_app.ui.theme.ThemeController
import java.io.File

@Composable
fun ProductDetailsScreen(
    productId: String,
    navController: NavController,
) {
    val detailsViewModel: ProductDetailsViewModel = viewModel()
    val cartViewModel: CartViewModel = viewModel()
    val imagesViewModel: ProductInventoryImagesViewModel = viewModel()

    val imagesByProduct by imagesViewModel.imagesByProduct.collectAsState()
    val product by detailsViewModel.product.collectAsState()
    val isDark = ThemeController.isDarkMode
    var showDialogAdd by remember { mutableStateOf(false) }
    var imageReloadTrigger by remember { mutableIntStateOf(0) }

    LaunchedEffect(productId) {
        productId.toIntOrNull()?.let { id ->
            detailsViewModel.loadProductById(id)
        }
    }

    LaunchedEffect(product?.ARTICULO_ID) {
        product?.ARTICULO_ID?.let {
            imagesViewModel.loadLocalImages()
        }
    }

    DrawerContainer(navController = navController) { openDrawer ->
        Scaffold(
            modifier = Modifier.statusBarsPadding(),
            topBar = {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 12.dp),
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
                        text = "Detalles",
                        style = MaterialTheme.typography.titleLarge
                    )
                    Spacer(modifier = Modifier.weight(1f))

                    IconButton(
                        onClick = {
                            imageReloadTrigger++
                            imagesViewModel.loadLocalImages()
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Recargar imágenes"
                        )
                    }
                }
            }
        ) { innerPadding ->
            product?.let { currentProduct ->
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = currentProduct.ARTICULO,
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                    )

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
                    val priceMap = parsePriceJsonToMap(currentProduct.PRECIOS)
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
                    if (showDialogAdd) {
                        product?.let {
                            AddToCartDialog(
                                product = it,
                                cartViewModel = cartViewModel,
                                onDismiss = { showDialogAdd = false },
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Button(onClick = { }) {
                        Text("Vender")
                    }
                    Button(onClick = {
                        showDialogAdd = true
                    }) {
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

                            println("Error loading image: ${item.imagePath}")
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