package com.example.msp_app.features.cart.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.ui.window.Dialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.example.msp_app.R
import com.example.msp_app.core.utils.parsePriceJsonToMap
import com.example.msp_app.core.utils.toCurrency
import com.example.msp_app.data.models.productInventory.ProductInventory
import com.example.msp_app.features.cart.viewmodels.CartItem
import com.example.msp_app.navigation.Screen
import com.example.msp_app.ui.theme.ThemeController

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun CartItemCard(
    cartItem: CartItem,
    imageUrls: List<String>,
    onRemove: () -> Unit,
    onIncreaseQuantity: () -> Unit,
    onDecreaseQuantity: () -> Unit,
    onBatchIncrease: ((Int) -> Unit)? = null,
    onBatchDecrease: ((Int) -> Unit)? = null,
    navController: NavController,
    product: ProductInventory,
    generalWarehouseStock: Int? = null,
    isIncreaseLoading: Boolean = false,
    isDecreaseLoading: Boolean = false,
    isRemoveLoading: Boolean = false
) {
    val isDark = ThemeController.isDarkMode
    var showQuantityDialog by remember { mutableStateOf(false) }
    var isIncreaseMode by remember { mutableStateOf(true) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = if (!isDark) 4.dp else 0.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = if (!isDark) null else BorderStroke(1.dp, Color.DarkGray),
        onClick = { navController.navigate(Screen.ProductDetails.createRoute(product.ARTICULO_ID.toString())) }
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top
            ) {
                if (imageUrls.isNotEmpty()) {
                    AsyncImage(
                        model = "file://${imageUrls.first()}",
                        contentDescription = "Imagen del producto",
                        modifier = Modifier
                            .size(80.dp)
                            .clip(RoundedCornerShape(12.dp)),
                        contentScale = ContentScale.Crop,
                        placeholder = painterResource(id = R.drawable.images_photo)
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .size(80.dp)
                            .background(
                                if (isDark) Color.Transparent else Color(0xFFE2E3E5),
                                RoundedCornerShape(12.dp)
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.images_photo),
                            contentDescription = "Sin imagen disponible",
                            modifier = Modifier.size(40.dp),
                            tint = Color(0xFFB8C3D4)
                        )
                    }
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = cartItem.product.ARTICULO,
                        fontSize = 16.sp,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        text = "Stock almacén general: ${generalWarehouseStock ?: "Cargando..."}",
                        fontSize = 12.sp,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .background(
                                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f),
                                    shape = RoundedCornerShape(8.dp)
                                )
                                .clip(RoundedCornerShape(8.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            IconButton(
                                onClick = onDecreaseQuantity,
                                enabled = !isDecreaseLoading,
                                modifier = Modifier.size(36.dp)
                            ) {
                                if (isDecreaseLoading) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(16.dp),
                                        strokeWidth = 2.dp,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                } else {
                                    Text(
                                        text = "−",
                                        style = MaterialTheme.typography.titleMedium,
                                        color = MaterialTheme.colorScheme.primary,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }

                        Text(
                            text = "${cartItem.quantity}",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier
                                .background(
                                    color = MaterialTheme.colorScheme.primaryContainer,
                                    shape = RoundedCornerShape(8.dp)
                                )
                                .combinedClickable(
                                    onClick = { 
                                        showQuantityDialog = true
                                        isIncreaseMode = true
                                    },
                                    onLongClick = {
                                        showQuantityDialog = true
                                        isIncreaseMode = false
                                    }
                                )
                                .padding(horizontal = 16.dp, vertical = 8.dp)
                        )

                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .background(
                                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f),
                                    shape = RoundedCornerShape(8.dp)
                                )
                                .clip(RoundedCornerShape(8.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            IconButton(
                                onClick = onIncreaseQuantity,
                                enabled = !isIncreaseLoading,
                                modifier = Modifier.size(36.dp)
                            ) {
                                if (isIncreaseLoading) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(16.dp),
                                        strokeWidth = 2.dp,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                } else {
                                    Icon(
                                        imageVector = Icons.Default.Add,
                                        contentDescription = "Aumentar cantidad",
                                        modifier = Modifier.size(18.dp),
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.width(8.dp))

                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .background(
                                    color = Color.Red.copy(alpha = 0.1f),
                                    shape = RoundedCornerShape(8.dp)
                                )
                                .clip(RoundedCornerShape(8.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            IconButton(
                                onClick = onRemove,
                                enabled = !isRemoveLoading,
                                modifier = Modifier.size(36.dp)
                            ) {
                                if (isRemoveLoading) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(16.dp),
                                        strokeWidth = 2.dp,
                                        color = Color.Red
                                    )
                                } else {
                                    Icon(
                                        imageVector = Icons.Default.Delete,
                                        contentDescription = "Eliminar producto de la camioneta",
                                        tint = Color.Red,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                    }

                    Text(
                        text = "Toca el número para cambios rápidos",
                        fontSize = 10.sp,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }

            if (!cartItem.product.PRECIOS.isNullOrEmpty()) {
                Spacer(modifier = Modifier.height(12.dp))
                
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        val priceMap = try {
                            parsePriceJsonToMap(cartItem.product.PRECIOS)
                        } catch (e: Exception) {
                            emptyMap()
                        }
                        priceMap.toList().asReversed().forEach { (label, value) ->
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = label,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontWeight = FontWeight.Medium
                                )
                                Text(
                                    text = value.toCurrency(noDecimals = true),
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }
            }
        }
    }
    
    if (showQuantityDialog) {
        var inputQuantity by remember { mutableStateOf("") }
        val maxAvailable = if (isIncreaseMode) (generalWarehouseStock ?: 0) else cartItem.quantity
        
        Dialog(
            onDismissRequest = { 
                showQuantityDialog = false 
                inputQuantity = ""
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
                    modifier = Modifier.padding(24.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(
                            imageVector = if (isIncreaseMode) Icons.Default.Add else Icons.Default.Delete,
                            contentDescription = null,
                            modifier = Modifier
                                .size(32.dp)
                                .background(
                                    color = if (isIncreaseMode) MaterialTheme.colorScheme.primaryContainer 
                                           else MaterialTheme.colorScheme.errorContainer,
                                    shape = RoundedCornerShape(8.dp)
                                )
                                .padding(6.dp),
                            tint = if (isIncreaseMode) MaterialTheme.colorScheme.onPrimaryContainer
                                  else MaterialTheme.colorScheme.onErrorContainer
                        )
                        
                        Spacer(modifier = Modifier.width(12.dp))
                        
                        Text(
                            text = if (isIncreaseMode) "Agregar productos" else "Quitar productos",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    
                    Spacer(modifier = Modifier.height(20.dp))
                    
                        Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                        ),
                        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp)
                        ) {
                            Text(
                                text = cartItem.product.ARTICULO,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            
                            Spacer(modifier = Modifier.height(8.dp))
                            
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "En camioneta:",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = "${cartItem.quantity}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            
                            if (isIncreaseMode) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = "Stock general:",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        text = "${generalWarehouseStock ?: 0}",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(20.dp))
                    
                    OutlinedTextField(
                        value = inputQuantity,
                        onValueChange = { value ->
                            if (value.all { it.isDigit() }) {
                                inputQuantity = value
                            }
                        },
                        label = { Text("Cantidad a ${if (isIncreaseMode) "agregar" else "quitar"}") },
                        placeholder = { Text("Ingresa la cantidad") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        colors = TextFieldDefaults.colors(
                            focusedIndicatorColor = MaterialTheme.colorScheme.primary,
                            unfocusedIndicatorColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
                        )
                    )
                    
                    if (inputQuantity.isNotEmpty()) {
                        val quantity = inputQuantity.toIntOrNull() ?: 0
                        val isValid = quantity > 0 && quantity <= maxAvailable
                        
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        Text(
                            text = if (isValid) {
                                if (isIncreaseMode) "Se agregarán $quantity productos"
                                else "Se quitarán $quantity productos"
                            } else {
                                "Cantidad inválida (máximo: $maxAvailable)"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = if (isValid) MaterialTheme.colorScheme.primary
                                   else MaterialTheme.colorScheme.error
                        )
                    }
                    
                    Spacer(modifier = Modifier.height(24.dp))
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Button(
                            onClick = { 
                                showQuantityDialog = false
                                inputQuantity = ""
                            },
                            modifier = Modifier.weight(1f),
                            colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                                contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        ) {
                            Text("Cancelar")
                        }
                        
                        Button(
                            onClick = {
                                val quantity = inputQuantity.toIntOrNull() ?: 0
                                if (quantity > 0) {
                                    if (isIncreaseMode) {
                                        onBatchIncrease?.invoke(quantity) ?: repeat(quantity) { onIncreaseQuantity() }
                                    } else {
                                        onBatchDecrease?.invoke(quantity) ?: repeat(quantity) { onDecreaseQuantity() }
                                    }
                                }
                                showQuantityDialog = false
                                inputQuantity = ""
                            },
                            enabled = inputQuantity.toIntOrNull()?.let { it > 0 && it <= maxAvailable } ?: false,
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(if (isIncreaseMode) "Agregar" else "Quitar")
                        }
                    }
                }
            }
        }
    }
}