package com.example.msp_app.features.sales.components.productselector

import androidx.compose.foundation.border
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Plus
import com.composables.icons.lucide.ShoppingCart
import com.example.msp_app.data.models.productInventory.ProductInventory
import com.example.msp_app.features.sales.components.combo.ComboCard
import com.example.msp_app.features.sales.viewmodels.SaleItem
import com.example.msp_app.features.sales.viewmodels.SaleProductsViewModel
import com.example.msp_app.utils.PriceParser

@Composable
fun ProductSaleSummary(
    saleProductsViewModel: SaleProductsViewModel,
    productosCamioneta: List<ProductInventory>,
    onOpenProductSheet: () -> Unit,
    hasError: Boolean = false,
    tipoVenta: String = "CREDITO"
) {
    val saleItems = saleProductsViewModel.saleItems
    val combosList = saleProductsViewModel.getCombosList()
    val individualProducts = saleProductsViewModel.getIndividualProducts()
    val totalItems = saleItems.sumOf { it.quantity }

    Column(modifier = Modifier.fillMaxWidth()) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Productos${if (totalItems > 0) " ($totalItems)" else ""}",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Add/Edit button
        OutlinedButton(
            onClick = onOpenProductSheet,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp)
        ) {
            Icon(
                imageVector = if (saleItems.isEmpty()) Lucide.Plus else Lucide.ShoppingCart,
                contentDescription = null,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                if (saleItems.isEmpty()) {
                    "Agregar productos"
                } else {
                    "Agregar / Editar productos"
                }
            )
        }

        if (hasError && saleItems.isEmpty()) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Agrega al menos un producto",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error
            )
        }

        // Combos
        if (combosList.isNotEmpty()) {
            Spacer(modifier = Modifier.height(12.dp))
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
                    modifier = Modifier.padding(bottom = 8.dp),
                    onPriceChange = { precioLista, precioCortoPlazo, precioContado ->
                        saleProductsViewModel.updateComboPrices(
                            combo.comboId,
                            precioLista,
                            precioCortoPlazo,
                            precioContado
                        )
                    },
                    tipoVenta = tipoVenta
                )
            }
        }

        // Individual products with price editing
        if (individualProducts.isNotEmpty()) {
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = if (combosList.isNotEmpty()) "Productos individuales" else "Productos",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            individualProducts.forEach { item ->
                SummaryProductCard(
                    saleItem = item,
                    saleProductsViewModel = saleProductsViewModel,
                    modifier = Modifier.padding(bottom = 8.dp),
                    tipoVenta = tipoVenta
                )
            }
        }

        // Empty state
        if (saleItems.isEmpty() && !hasError) {
            Spacer(modifier = Modifier.height(8.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(60.dp)
                    .border(
                        1.dp,
                        MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                        RoundedCornerShape(12.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "No hay productos agregados",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
            }
        }
    }
}

@Composable
private fun SummaryProductCard(
    saleItem: SaleItem,
    saleProductsViewModel: SaleProductsViewModel,
    modifier: Modifier = Modifier,
    tipoVenta: String = "CREDITO"
) {
    val parsedPrices = PriceParser.parsePricesFromString(saleItem.product.PRECIOS)

    var priceList by remember(saleItem.product.ARTICULO_ID, saleItem.product.PRECIOS) {
        mutableStateOf(parsedPrices.precioLista.toString())
    }
    var shortTermPrice by remember(saleItem.product.ARTICULO_ID, saleItem.product.PRECIOS) {
        mutableStateOf(parsedPrices.precioCortoplazo.toString())
    }
    var cashPrice by remember(saleItem.product.ARTICULO_ID, saleItem.product.PRECIOS) {
        mutableStateOf(parsedPrices.precioContado.toString())
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Header: product name + remove button
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = saleItem.product.ARTICULO,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(top = 4.dp)
                    ) {
                        Surface(
                            color = MaterialTheme.colorScheme.primaryContainer,
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(
                                text = "Cantidad: ${saleItem.quantity}",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Stock: ${saleItem.product.EXISTENCIAS}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                        )
                    }
                }
                IconButton(
                    onClick = { saleProductsViewModel.removeProductFromSale(saleItem.product) },
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(
                        Icons.Default.Clear,
                        contentDescription = "Eliminar producto",
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Quantity controls
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Ajustar cantidad:",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilledTonalIconButton(
                        onClick = {
                            saleProductsViewModel.updateQuantity(
                                saleItem.product,
                                saleItem.quantity - 1
                            )
                        },
                        modifier = Modifier.size(36.dp),
                        enabled = saleItem.quantity > 1
                    ) {
                        Icon(
                            Icons.Default.KeyboardArrowDown,
                            contentDescription = "Reducir cantidad",
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Surface(
                        color = MaterialTheme.colorScheme.primary,
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            text = saleItem.quantity.toString(),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                            textAlign = TextAlign.Center
                        )
                    }
                    FilledTonalIconButton(
                        onClick = {
                            val cantidadEnVenta =
                                saleProductsViewModel.getQuantityForProduct(saleItem.product)
                            val disponible =
                                saleItem.product.EXISTENCIAS - cantidadEnVenta + saleItem.quantity
                            if (saleItem.quantity < disponible) {
                                saleProductsViewModel.updateQuantity(
                                    saleItem.product,
                                    saleItem.quantity + 1
                                )
                            }
                        },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            Icons.Default.KeyboardArrowUp,
                            contentDescription = "Aumentar cantidad",
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Editable prices
            Text(
                text = "Precios:",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                // Precio Contado
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Contado:",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.width(80.dp),
                        color = MaterialTheme.colorScheme.tertiary,
                        fontWeight = FontWeight.Medium
                    )
                    OutlinedTextField(
                        value = cashPrice,
                        onValueChange = { newValue ->
                            cashPrice = newValue
                            newValue.toDoubleOrNull()?.let { price ->
                                if (price > 0) {
                                    saleProductsViewModel.updateProductPrices(
                                        saleItem.product,
                                        priceList.toDoubleOrNull() ?: parsedPrices.precioLista,
                                        shortTermPrice.toDoubleOrNull()
                                            ?: parsedPrices.precioCortoplazo,
                                        price
                                    )
                                }
                            }
                        },
                        prefix = { Text("$") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(8.dp),
                        textStyle = MaterialTheme.typography.bodyMedium.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp
                        )
                    )
                }

                if (tipoVenta != "CONTADO") {
                    // Precio Corto Plazo
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "C.Plazo:",
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.width(80.dp),
                            color = MaterialTheme.colorScheme.secondary,
                            fontWeight = FontWeight.Medium
                        )
                        OutlinedTextField(
                            value = shortTermPrice,
                            onValueChange = { newValue ->
                                shortTermPrice = newValue
                                newValue.toDoubleOrNull()?.let { price ->
                                    if (price > 0) {
                                        saleProductsViewModel.updateProductPrices(
                                            saleItem.product,
                                            priceList.toDoubleOrNull() ?: parsedPrices.precioLista,
                                            price,
                                            cashPrice.toDoubleOrNull() ?: parsedPrices.precioContado
                                        )
                                    }
                                }
                            },
                            prefix = { Text("$") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(8.dp),
                            textStyle = MaterialTheme.typography.bodyMedium.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 18.sp
                            )
                        )
                    }

                    // Precio Lista
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Lista:",
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.width(80.dp),
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Medium
                        )
                        OutlinedTextField(
                            value = priceList,
                            onValueChange = { newValue ->
                                priceList = newValue
                                newValue.toDoubleOrNull()?.let { price ->
                                    if (price > 0) {
                                        saleProductsViewModel.updateProductPrices(
                                            saleItem.product,
                                            price,
                                            shortTermPrice.toDoubleOrNull()
                                                ?: parsedPrices.precioCortoplazo,
                                            cashPrice.toDoubleOrNull() ?: parsedPrices.precioContado
                                        )
                                    }
                                }
                            },
                            prefix = { Text("$") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(8.dp),
                            textStyle = MaterialTheme.typography.bodyMedium.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 18.sp
                            )
                        )
                    }
                }
            }
        }
    }
}
