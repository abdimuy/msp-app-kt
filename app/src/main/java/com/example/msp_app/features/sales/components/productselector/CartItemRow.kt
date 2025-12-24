package com.example.msp_app.features.sales.components.productselector


import androidx.compose.foundation.layout.Arrangement
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
import com.example.msp_app.features.sales.viewmodels.SaleItem
import com.example.msp_app.features.sales.viewmodels.SaleProductsViewModel
import com.example.msp_app.utils.PriceParser

@Composable
fun CartItemRow(
    saleItem: SaleItem,
    saleProductsViewModel: SaleProductsViewModel
) {
    val parsedPrices = PriceParser.parsePricesFromString(saleItem.product.PRECIOS)

    var priceList by remember { mutableStateOf(parsedPrices.precioLista.toString()) }
    var shortTermPrice by remember { mutableStateOf(parsedPrices.precioCortoplazo.toString()) }
    var cashPrice by remember { mutableStateOf(parsedPrices.precioContado.toString()) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            // Header del producto
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
                    onClick = {
                        saleProductsViewModel.removeProductFromSale(saleItem.product)
                    },
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

            // Controles de cantidad
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
                            val newQuantity = saleItem.quantity - 1
                            saleProductsViewModel.updateQuantity(saleItem.product, newQuantity)
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
                                val newQuantity = saleItem.quantity + 1
                                saleProductsViewModel.updateQuantity(saleItem.product, newQuantity)
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

            // Precios editables inline
            Text(
                text = "Precios:",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Contado:",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.width(100.dp),
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

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "C.Plazo:",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.width(100.dp),
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

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Lista:",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.width(100.dp),
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