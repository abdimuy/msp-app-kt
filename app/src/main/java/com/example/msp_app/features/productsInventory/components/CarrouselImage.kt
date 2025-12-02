package com.example.msp_app.features.productsInventory.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.carousel.HorizontalUncontainedCarousel
import androidx.compose.material3.carousel.rememberCarouselState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import coil.compose.AsyncImage
import coil.compose.AsyncImagePainter
import coil.request.ImageRequest
import java.io.File

data class CarouselItem(
    val id: Int,
    val imagePath: String,
    val description: String = ""
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CarrouselImage(
    carouselItems: List<CarouselItem>,
    reloadTrigger: Int = 0
) {
    val selectedItem = remember { mutableStateOf<CarouselItem?>(null) }
    val context = LocalContext.current

    key(carouselItems.size) {
        HorizontalUncontainedCarousel(
            state = rememberCarouselState { carouselItems.size },
            modifier = Modifier
                .fillMaxWidth()
                .wrapContentHeight()
                .padding(top = 16.dp, bottom = 16.dp),
            itemWidth = 186.dp,
            itemSpacing = 12.dp,
            contentPadding = PaddingValues(horizontal = 16.dp)
        ) { index ->
            if (index >= carouselItems.size) return@HorizontalUncontainedCarousel

            val item = carouselItems[index]
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
                        Text(
                            "Restaurar",
                            color = Color.White
                        )
                    }

                    Button(onClick = onDismiss) {
                        Text(
                            "Cerrar",
                            color = Color.White
                        )
                    }
                }
            }
        }
    }
}