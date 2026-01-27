package com.example.msp_app.features.sales.components.combo

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import com.composables.icons.lucide.Check
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Minus
import com.composables.icons.lucide.Plus
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.msp_app.features.sales.viewmodels.SaleItem
import com.example.msp_app.utils.PriceParser
import java.text.NumberFormat
import java.util.Locale

@Composable
fun ProductItemSelectable(
    saleItem: SaleItem,
    isSelected: Boolean,
    isInCombo: Boolean,
    onToggleSelect: () -> Unit,
    onQuantityChange: (Int) -> Unit,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
    isEnabled: Boolean = true
) {
    val currencyFormat = NumberFormat.getCurrencyInstance(Locale("es", "MX"))
    val prices = PriceParser.parsePricesFromString(saleItem.product.PRECIOS)

    val backgroundColor by animateColorAsState(
        targetValue = when {
            isInCombo -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
            isSelected -> MaterialTheme.colorScheme.secondaryContainer
            else -> MaterialTheme.colorScheme.surface
        },
        label = "backgroundColor"
    )

    val borderColor by animateColorAsState(
        targetValue = when {
            isSelected -> MaterialTheme.colorScheme.primary
            isInCombo -> MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
            else -> MaterialTheme.colorScheme.outlineVariant
        },
        label = "borderColor"
    )

    Card(
        modifier = modifier
            .fillMaxWidth()
            .then(
                if (!isInCombo && isEnabled) {
                    Modifier.clickable { onToggleSelect() }
                } else {
                    Modifier
                }
            ),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = backgroundColor),
        elevation = CardDefaults.cardElevation(defaultElevation = if (isSelected) 4.dp else 1.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .border(
                    width = if (isSelected) 2.dp else 1.dp,
                    color = borderColor,
                    shape = RoundedCornerShape(12.dp)
                )
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Checkbox visual
            if (!isInCombo) {
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .clip(CircleShape)
                        .background(
                            if (isSelected) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.surfaceVariant
                        )
                        .border(
                            width = 2.dp,
                            color = if (isSelected) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.outline,
                            shape = CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    if (isSelected) {
                        Icon(
                            imageVector = Lucide.Check,
                            contentDescription = "Seleccionado",
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.width(12.dp))
            }

            // Producto info
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = saleItem.product.ARTICULO ?: "Producto",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = currencyFormat.format(prices.precioLista),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
                if (isInCombo) {
                    Text(
                        text = "En combo",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.tertiary
                    )
                }
            }

            // Controles de cantidad (solo si no está en combo)
            if (!isInCombo) {
                val fontScale = LocalDensity.current.fontScale
                val useLargeLayout = fontScale > 1.3f
                val buttonSize = if (useLargeLayout) 40.dp else 32.dp
                val iconSize = if (useLargeLayout) 22.dp else 18.dp

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(if (useLargeLayout) 8.dp else 4.dp)
                ) {
                    IconButton(
                        onClick = {
                            if (saleItem.quantity > 1) {
                                onQuantityChange(saleItem.quantity - 1)
                            } else {
                                onRemove()
                            }
                        },
                        enabled = isEnabled,
                        modifier = Modifier.size(buttonSize)
                    ) {
                        Icon(
                            imageVector = Lucide.Minus,
                            contentDescription = "Reducir",
                            modifier = Modifier.size(iconSize)
                        )
                    }

                    Text(
                        text = "${saleItem.quantity}",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.width(if (useLargeLayout) 40.dp else 32.dp),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )

                    IconButton(
                        onClick = { onQuantityChange(saleItem.quantity + 1) },
                        enabled = isEnabled,
                        modifier = Modifier.size(buttonSize)
                    ) {
                        Icon(
                            imageVector = Lucide.Plus,
                            contentDescription = "Aumentar",
                            modifier = Modifier.size(iconSize)
                        )
                    }
                }
            } else {
                Text(
                    text = "x${saleItem.quantity}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}
