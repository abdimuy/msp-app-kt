package com.example.msp_app.features.sales.components.productselector

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Package
import com.composables.icons.lucide.Trash2
import com.composables.icons.lucide.X
import com.example.msp_app.data.models.productInventory.ProductInventory
import com.example.msp_app.features.sales.components.combo.ComboCard
import com.example.msp_app.features.sales.components.combo.ProductItemSelectable
import com.example.msp_app.features.sales.viewmodels.SaleProductsViewModel
import com.example.msp_app.utils.PriceParser
import java.text.NumberFormat
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProductSelectionBottomSheet(
    products: List<ProductInventory>,
    saleProductsViewModel: SaleProductsViewModel,
    onDismiss: () -> Unit,
    onShowCreateComboDialog: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var searchQuery by remember { mutableStateOf("") }
    val isCreatingCombo by saleProductsViewModel.isCreatingCombo.collectAsState()

    val filteredProducts = remember(searchQuery, products) {
        if (searchQuery.isBlank()) {
            products
        } else {
            products.filter {
                it.ARTICULO.contains(searchQuery, ignoreCase = true)
            }
        }
    }

    val cartItemCount = saleProductsViewModel.saleItems.size
    val combosList = saleProductsViewModel.getCombosList()
    val individualProducts = saleProductsViewModel.getIndividualProducts()
    val selectedProductIds = saleProductsViewModel.selectedForCombo.toList()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.9f)
                .padding(horizontal = 16.dp)
                .padding(bottom = 16.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Productos",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "Cerrar")
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Search field
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Buscar producto...") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(Icons.Default.Close, contentDescription = "Limpiar")
                        }
                    }
                },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "${filteredProducts.size} productos disponibles",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Product catalog
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                items(filteredProducts, key = { it.ARTICULO_ID }) { product ->
                    val quantityInCart = saleProductsViewModel.getQuantityForProduct(product)
                    val availableStock = product.EXISTENCIAS - quantityInCart
                    val isOutOfStock = availableStock <= 0

                    ProductCatalogItem(
                        product = product,
                        quantityInCart = quantityInCart,
                        availableStock = availableStock,
                        isOutOfStock = isOutOfStock,
                        onClick = {
                            if (!isOutOfStock) {
                                saleProductsViewModel.addProductToSale(product, 1)
                            }
                        }
                    )
                }
            }

            // Cart section
            if (cartItemCount > 0) {
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 300.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    // Cart header
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Carrito ($cartItemCount)",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        TextButton(onClick = { saleProductsViewModel.clearAll() }) {
                            Icon(
                                imageVector = Lucide.Trash2,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.error
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Limpiar", color = MaterialTheme.colorScheme.error)
                        }
                    }

                    // Combos
                    if (combosList.isNotEmpty()) {
                        Text(
                            text = "Combos",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                        combosList.forEach { combo ->
                            ComboCard(
                                combo = combo,
                                products = saleProductsViewModel.getProductsInCombo(combo.comboId),
                                onDelete = { saleProductsViewModel.deleteCombo(combo.comboId) },
                                modifier = Modifier.padding(bottom = 8.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                    }

                    // Selection bar + Create Combo
                    AnimatedVisibility(
                        visible = selectedProductIds.isNotEmpty(),
                        enter = slideInVertically() + fadeIn(),
                        exit = slideOutVertically() + fadeOut()
                    ) {
                        Column(modifier = Modifier.padding(bottom = 8.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "${selectedProductIds.size} seleccionados",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.Medium
                                )
                                TextButton(onClick = { saleProductsViewModel.clearSelection() }) {
                                    Icon(
                                        imageVector = Lucide.X,
                                        contentDescription = "Limpiar selección",
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Limpiar")
                                }
                            }
                            if (selectedProductIds.size >= 2) {
                                Button(
                                    onClick = onShowCreateComboDialog,
                                    shape = RoundedCornerShape(20.dp),
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.primary
                                    )
                                ) {
                                    Icon(
                                        imageVector = Lucide.Package,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Crear Combo")
                                }
                            }
                        }
                    }

                    // Individual products
                    if (individualProducts.isNotEmpty()) {
                        Text(
                            text = if (combosList.isNotEmpty()) "Productos individuales" else "Productos",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(bottom = 4.dp)
                        )
                        Text(
                            text = "Selecciona 2 o más productos para crear un combo",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.outline,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                        individualProducts.forEach { item ->
                            ProductItemSelectable(
                                saleItem = item,
                                isSelected = selectedProductIds.contains(item.product.ARTICULO_ID),
                                isInCombo = false,
                                onToggleSelect = {
                                    saleProductsViewModel.toggleProductSelection(
                                        item.product.ARTICULO_ID
                                    )
                                },
                                onQuantityChange = { newQty ->
                                    val maxStock = item.product.EXISTENCIAS
                                    val validQty = newQty.coerceIn(1, maxStock)
                                    saleProductsViewModel.updateQuantity(item.product, validQty)
                                },
                                onRemove = {
                                    saleProductsViewModel.removeProductFromSale(item.product)
                                },
                                modifier = Modifier.padding(bottom = 8.dp),
                                isEnabled = !isCreatingCombo
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Footer
            Button(
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Listo")
            }
        }
    }
}

@Composable
private fun ProductCatalogItem(
    product: ProductInventory,
    quantityInCart: Int,
    availableStock: Int,
    isOutOfStock: Boolean,
    onClick: () -> Unit
) {
    val currencyFormat = remember { NumberFormat.getCurrencyInstance(Locale("es", "MX")) }
    val prices = PriceParser.parsePricesFromString(product.PRECIOS)

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (!isOutOfStock) Modifier.clickable { onClick() } else Modifier),
        shape = RoundedCornerShape(8.dp),
        color = if (isOutOfStock) {
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        } else {
            MaterialTheme.colorScheme.surface
        },
        tonalElevation = 1.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = product.ARTICULO,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    color = if (isOutOfStock) {
                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    }
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = currencyFormat.format(prices.precioLista),
                        style = MaterialTheme.typography.bodySmall,
                        color = if (isOutOfStock) {
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)
                        } else {
                            MaterialTheme.colorScheme.primary
                        },
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = if (isOutOfStock) "Sin stock" else "Disp: $availableStock",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isOutOfStock) {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                }
            }

            if (quantityInCart > 0) {
                Surface(
                    color = MaterialTheme.colorScheme.primary,
                    shape = CircleShape,
                    modifier = Modifier.size(28.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = "$quantityInCart",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                    }
                }
            }
        }
    }
}
