package com.example.msp_app.features.sales.components.combo

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Package
import com.composables.icons.lucide.X
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.msp_app.features.sales.viewmodels.ComboItem
import com.example.msp_app.features.sales.viewmodels.SaleItem

@Composable
fun ProductsWithCombosSection(
    individualProducts: List<SaleItem>,
    combos: List<ComboItem>,
    selectedProductIds: List<Int>,
    getProductsInCombo: (String) -> List<SaleItem>,
    onToggleProductSelection: (Int) -> Unit,
    onQuantityChange: (Int, Int) -> Unit,
    onRemoveProduct: (Int) -> Unit,
    onCreateCombo: () -> Unit,
    onDeleteCombo: (String) -> Unit,
    onClearSelection: () -> Unit,
    modifier: Modifier = Modifier,
    isCreatingCombo: Boolean = false
) {
    var showComboDialog by remember { mutableStateOf(false) }

    Column(modifier = modifier.fillMaxWidth()) {
        // Header con contador de selección y botón de crear combo
        AnimatedVisibility(
            visible = selectedProductIds.isNotEmpty(),
            enter = slideInVertically() + fadeIn(),
            exit = slideOutVertically() + fadeOut()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "${selectedProductIds.size} seleccionados",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Medium
                    )
                    TextButton(onClick = onClearSelection) {
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
                        onClick = onCreateCombo,
                        enabled = !isCreatingCombo,
                        shape = RoundedCornerShape(20.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isCreatingCombo) 
                                MaterialTheme.colorScheme.surfaceVariant 
                            else 
                                MaterialTheme.colorScheme.primary
                        )
                    ) {
                        if (isCreatingCombo) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Creando...")
                        } else {
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
        }

        // Combos existentes
        if (combos.isNotEmpty()) {
            Text(
                text = "Combos",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            combos.forEach { combo ->
                ComboCard(
                    combo = combo,
                    products = getProductsInCombo(combo.comboId),
                    onDelete = { onDeleteCombo(combo.comboId) },
                    modifier = Modifier.padding(bottom = 8.dp)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))
        }

        // Productos individuales
        if (individualProducts.isNotEmpty()) {
            Text(
                text = if (combos.isNotEmpty()) "Productos individuales" else "Productos",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            Text(
                text = "Selecciona 2 o más productos para crear un combo",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline,
                modifier = Modifier.padding(bottom = 12.dp)
            )

individualProducts.forEach { item ->
                ProductItemSelectable(
                    saleItem = item,
                    isSelected = selectedProductIds.contains(item.product.ARTICULO_ID),
                    isInCombo = false,
                    enabled = !isCreatingCombo,
                    onToggleSelect = { onToggleProductSelection(item.product.ARTICULO_ID) },
                    onQuantityChange = { newQty -> onQuantityChange(item.product.ARTICULO_ID, newQty) },
                    onRemove = { onRemoveProduct(item.product.ARTICULO_ID) },
                    modifier = Modifier.padding(bottom = 8.dp)
                )
            }
        }

        // Estado vacío
        if (individualProducts.isEmpty() && combos.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Agrega productos para comenzar",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
