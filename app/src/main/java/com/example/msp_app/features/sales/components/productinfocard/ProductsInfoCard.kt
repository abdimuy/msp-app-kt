package com.example.msp_app.features.sales.components.productinfocard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.msp_app.core.utils.toCurrency
import com.example.msp_app.data.local.entities.LocalSaleProductEntity
import com.example.msp_app.features.sales.viewmodels.SaleProductsViewModel

@Composable
fun ProductsInfoCard(
    saleProducts: List<LocalSaleProductEntity>,
    productsViewModel: SaleProductsViewModel,
    total: Double
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp),
        shape = RoundedCornerShape(8.dp),
        elevation = CardDefaults.cardElevation(4.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.inverseOnSurface
        )
    ) {
        Column(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth()
        ) {
            Text(
                "Productos de la Venta",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.align(Alignment.CenterHorizontally)
            )
            Spacer(Modifier.height(12.dp))

            if (saleProducts.isEmpty()) {
                Text(
                    "No hay productos en esta venta",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                )
            } else {

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Producto",
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.weight(1.8f)
                    )
                    Text(
                        "Cant.",
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.weight(0.8f)
                    )
                }

                Spacer(Modifier.height(8.dp))

                saleProducts.forEach { product ->
                    ProductRow(product = product)
                    Spacer(Modifier.height(4.dp))
                }

                Spacer(Modifier.height(12.dp))

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                "Total de artículos:",
                                style = MaterialTheme.typography.titleSmall
                            )
                            Text(
                                "${productsViewModel.getTotalItems()}",
                                style = MaterialTheme.typography.titleSmall
                            )
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                "Total de la venta:",
                                style = MaterialTheme.typography.titleMedium
                            )
                            Text(
                                if (total != null) total.toCurrency(noDecimals = true) else "0.0",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ProductRow(product: LocalSaleProductEntity) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {

            Text(
                text = product.ARTICULO,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1.8f)
            )

            Text(
                text = " x ${product.CANTIDAD}",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(0.8f),
                color = MaterialTheme.colorScheme.primary
            )
        }

        Spacer(Modifier.height(4.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                "Precios",
                style = MaterialTheme.typography.titleSmall,

                )
            Column(horizontalAlignment = Alignment.Start) {
                Text("Lista", style = MaterialTheme.typography.labelSmall)
                Text(product.PRECIO_LISTA.toCurrency(), style = MaterialTheme.typography.bodySmall)
            }

            Column(horizontalAlignment = Alignment.Start) {
                Text("Corto plazo", style = MaterialTheme.typography.labelSmall)
                Text(
                    product.PRECIO_CORTO_PLAZO.toCurrency(),
                    style = MaterialTheme.typography.bodySmall
                )
            }

            Column(horizontalAlignment = Alignment.Start) {
                Text("Contado", style = MaterialTheme.typography.labelSmall)
                Text(
                    product.PRECIO_CONTADO.toCurrency(),
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}