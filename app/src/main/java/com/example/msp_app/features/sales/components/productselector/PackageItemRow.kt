package com.example.msp_app.features.sales.components.productselector

import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Package
import com.composables.icons.lucide.PackageOpen
import com.example.msp_app.data.models.sale.localsale.LocalSaleProductPackage
import com.example.msp_app.features.sales.components.editpackageprices.EditPackagePricesDialog
import com.example.msp_app.features.sales.viewmodels.SaleProductsViewModel

@Composable
fun PackageItemRow(
    productPackage: LocalSaleProductPackage,
    saleProductsViewModel: SaleProductsViewModel
) {
    var showEditDialog by remember { mutableStateOf(false) }
    var showUnpackDialog by remember { mutableStateOf(false) }
    val totalQuantity = saleProductsViewModel.getPackageTotalQuantity(productPackage)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            // Header del paquete
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Lucide.Package,
                        contentDescription = "Paquete",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))

                    Column {
                        Text(
                            text = "PAQUETE",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = productPackage.packageName,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                // Botones de acción
                Row {
                    IconButton(
                        onClick = { showEditDialog = true },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            Icons.Default.Edit,
                            contentDescription = "Editar precios",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(25.dp)
                        )
                    }

                    IconButton(
                        onClick = { showUnpackDialog = true },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            Lucide.PackageOpen,
                            contentDescription = "Deshacer paquete",
                            tint = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.size(25.dp)
                        )
                    }

                    IconButton(
                        onClick = {
                            saleProductsViewModel.removePackage(productPackage.packageId)
                        },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            Icons.Default.Clear,
                            contentDescription = "Eliminar paquete",
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(25.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Información del paquete
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Surface(
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = "${productPackage.products.size} productos • $totalQuantity unidades",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }

                IconButton(
                    onClick = {
                        saleProductsViewModel.togglePackageExpanded(productPackage.packageId)
                    },
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        if (productPackage.isExpanded) Icons.Default.KeyboardArrowUp
                        else Icons.Default.KeyboardArrowDown,
                        contentDescription = if (productPackage.isExpanded) "Ocultar" else "Mostrar"
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Precios del paquete (no editables en línea)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        text = "Precios del paquete:",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Lista: $${productPackage.precioLista}",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                Column {
                    Text(
                        text = "Corto Plazo: $${productPackage.precioCortoplazo}",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.secondary
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Contado: $${productPackage.precioContado}",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.tertiary
                    )
                }
            }

            // Lista de productos expandible
            AnimatedVisibility(visible = productPackage.isExpanded) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp)
                ) {
                    Divider(modifier = Modifier.padding(vertical = 8.dp))

                    Text(
                        text = "Productos incluidos:",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )

                    productPackage.products.forEach { saleItem ->
                        PackageProductItem(saleItem = saleItem)
                        if (saleItem != productPackage.products.last()) {
                            Spacer(modifier = Modifier.height(8.dp))
                        }
                    }
                }
            }
        }
    }

    // Diálogo para editar precios del paquete
    if (showEditDialog) {
        EditPackagePricesDialog(
            productPackage = productPackage,
            saleProductsViewModel = saleProductsViewModel,
            onDismiss = { showEditDialog = false }
        )
    }

    // Diálogo de confirmación para deshacer paquete
    if (showUnpackDialog) {
        AlertDialog(
            onDismissRequest = { showUnpackDialog = false },
            title = { Text("¿Deshacer paquete?") },
            text = {
                Text("Los productos del paquete se devolverán a la lista individual y podrás modificarlos por separado.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        saleProductsViewModel.unpackPackage(productPackage.packageId)
                        showUnpackDialog = false
                    }
                ) {
                    Text("Deshacer", color = Color.White)
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { showUnpackDialog = false }) {
                    Text("Cancelar")
                }
            }
        )
    }
}