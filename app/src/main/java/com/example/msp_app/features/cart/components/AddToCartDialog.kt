package com.example.msp_app.features.cart.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
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
import androidx.compose.ui.unit.dp
import com.example.msp_app.data.models.productInventory.ProductInventory
import com.example.msp_app.features.cart.viewmodels.CartViewModel

@Composable
fun AddToCartDialog(
    product: ProductInventory?,
    cartViewModel: CartViewModel,
    onDismiss: () -> Unit
) {
    val currentQuantity = product?.let { cartViewModel.getQuantityForProduct(it) } ?: 0
    var amount by remember { mutableStateOf(if (currentQuantity > 0) currentQuantity else 1) }

    val maxStock = product?.EXISTENCIAS ?: 1
    val maxAddable = maxStock - currentQuantity

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Agregar al carrito") },
        text = {
            Column {
                Text("¿Cuántas unidades deseas agregar de este producto?")
                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    IconButton(
                        onClick = { if (amount > 1) amount-- },
                        enabled = amount > 1,
                        modifier = Modifier.size(36.dp)
                    ) { Text("-", style = MaterialTheme.typography.titleLarge) }

                    Spacer(modifier = Modifier.width(16.dp))

                    Text(
                        text = amount.toString(),
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.width(40.dp),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )

                    Spacer(modifier = Modifier.width(16.dp))

                    IconButton(
                        onClick = { if (amount < maxAddable) amount++ },
                        enabled = amount < maxAddable,
                        modifier = Modifier.size(36.dp)
                    ) { Text("+", style = MaterialTheme.typography.titleLarge) }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    product?.let {
                        cartViewModel.addProductToCart(
                            product = it,
                            quantity = amount
                        )
                    }
                    onDismiss()
                },
                enabled = amount > 0 && amount <= maxStock
            ) { Text("Sí") }
        },
        dismissButton = {
            Button(onClick = onDismiss) { Text("No") }
        }
    )
}

