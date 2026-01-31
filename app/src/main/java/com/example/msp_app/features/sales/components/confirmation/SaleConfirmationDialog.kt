package com.example.msp_app.features.sales.components.confirmation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.composables.icons.lucide.Check
import com.composables.icons.lucide.CreditCard
import com.composables.icons.lucide.Image
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.MapPin
import com.composables.icons.lucide.Package
import com.composables.icons.lucide.ShoppingCart
import com.composables.icons.lucide.StickyNote
import com.composables.icons.lucide.User
import com.composables.icons.lucide.X
import com.example.msp_app.features.sales.viewmodels.ComboItem
import com.example.msp_app.features.sales.viewmodels.SaleItem
import java.text.NumberFormat
import java.util.Locale

data class SaleConfirmationData(
    val clientName: String,
    val phone: String,
    val street: String,
    val numero: String,
    val colonia: String,
    val poblacion: String,
    val ciudad: String,
    val tipoVenta: String,
    val zoneName: String?,
    val downpayment: String,
    val installment: String,
    val guarantor: String,
    val paymentFrequency: String,
    val collectionDay: String,
    val note: String,
    val imageCount: Int,
    val individualProducts: List<SaleItem>,
    val combos: List<ComboItem>,
    val getProductsInCombo: (String) -> List<SaleItem>,
    val totalPrecioLista: Double,
    val totalCortoPlazo: Double,
    val totalContado: Double
)

@Composable
fun SaleConfirmationDialog(
    show: Boolean,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
    data: SaleConfirmationData
) {
    if (!show) return

    var showFinalConfirmation by remember { mutableStateOf(false) }
    val currencyFormat = remember { NumberFormat.getCurrencyInstance(Locale("es", "MX")) }
    val scrollState = rememberScrollState()

    // Diálogo de confirmación final
    if (showFinalConfirmation) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showFinalConfirmation = false },
            icon = {
                Icon(
                    imageVector = Lucide.Check,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(32.dp)
                )
            },
            title = {
                Text(
                    text = "¿Crear venta?",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            text = {
                Text(
                    text = "Estás a punto de crear esta venta. Una vez creada, se guardará en el sistema.",
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showFinalConfirmation = false
                        onConfirm()
                    },
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Sí, crear venta", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showFinalConfirmation = false }
                ) {
                    Text("Revisar de nuevo")
                }
            }
        )
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = true,
            dismissOnClickOutside = false
        )
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxSize(0.92f)
                .padding(horizontal = 12.dp),
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
            shadowElevation = 8.dp
        ) {
            Column(
                modifier = Modifier.fillMaxSize()
            ) {
                // Header
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Lucide.Check,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "Confirmar Venta",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Revisa los datos antes de continuar",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(
                            imageVector = Lucide.X,
                            contentDescription = "Cerrar"
                        )
                    }
                }

                HorizontalDivider()

                // Scrollable content
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(scrollState)
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Tipo de venta badge
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Surface(
                            color = if (data.tipoVenta == "CREDITO")
                                MaterialTheme.colorScheme.primaryContainer
                            else
                                MaterialTheme.colorScheme.tertiaryContainer,
                            shape = RoundedCornerShape(20.dp)
                        ) {
                            Text(
                                text = if (data.tipoVenta == "CREDITO") "Venta a Crédito" else "Venta de Contado",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = if (data.tipoVenta == "CREDITO")
                                    MaterialTheme.colorScheme.onPrimaryContainer
                                else
                                    MaterialTheme.colorScheme.onTertiaryContainer,
                                modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp)
                            )
                        }
                    }

                    // Cliente
                    SectionCard(
                        title = "Cliente",
                        icon = Lucide.User
                    ) {
                        InfoRow("Nombre", data.clientName)
                        if (data.phone.isNotBlank()) {
                            InfoRow("Teléfono", data.phone)
                        }
                        if (data.guarantor.isNotBlank()) {
                            InfoRow("Aval/Responsable", data.guarantor)
                        }
                    }

                    // Ubicación
                    SectionCard(
                        title = "Ubicación",
                        icon = Lucide.MapPin
                    ) {
                        InfoRow("Calle", data.street)
                        if (data.numero.isNotBlank()) {
                            InfoRow("Número", data.numero)
                        }
                        if (data.colonia.isNotBlank()) {
                            InfoRow("Colonia", data.colonia)
                        }
                        if (data.poblacion.isNotBlank()) {
                            InfoRow("Población", data.poblacion)
                        }
                        if (data.ciudad.isNotBlank()) {
                            InfoRow("Ciudad", data.ciudad)
                        }
                    }

                    // Zona (solo para CREDITO)
                    if (data.tipoVenta == "CREDITO" && !data.zoneName.isNullOrBlank()) {
                        SectionCard(
                            title = "Zona de Cobranza",
                            icon = Lucide.MapPin
                        ) {
                            InfoRow("Zona", data.zoneName)
                        }
                    }

                    // Información de pago (solo para CREDITO)
                    if (data.tipoVenta == "CREDITO") {
                        SectionCard(
                            title = "Información de Pago",
                            icon = Lucide.CreditCard
                        ) {
                            if (data.downpayment.isNotBlank() && data.downpayment != "0") {
                                InfoRow(
                                    "Enganche",
                                    currencyFormat.format(data.downpayment.toDoubleOrNull() ?: 0.0)
                                )
                            }
                            InfoRow(
                                "Parcialidad",
                                currencyFormat.format(data.installment.toDoubleOrNull() ?: 0.0)
                            )
                            InfoRow("Frecuencia", data.paymentFrequency)
                            InfoRow("Día de cobro", data.collectionDay)
                        }
                    }

                    // Productos
                    SectionCard(
                        title = "Productos",
                        icon = Lucide.ShoppingCart
                    ) {
                        // Combos
                        if (data.combos.isNotEmpty()) {
                            Text(
                                text = "Combos (${data.combos.size})",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(bottom = 8.dp)
                            )

                            data.combos.forEach { combo ->
                                ComboSummaryCard(
                                    combo = combo,
                                    products = data.getProductsInCombo(combo.comboId),
                                    currencyFormat = currencyFormat
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                            }

                            if (data.individualProducts.isNotEmpty()) {
                                Spacer(modifier = Modifier.height(8.dp))
                            }
                        }

                        // Productos individuales
                        if (data.individualProducts.isNotEmpty()) {
                            Text(
                                text = "Productos individuales (${data.individualProducts.size})",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.secondary,
                                modifier = Modifier.padding(bottom = 8.dp)
                            )

                            data.individualProducts.forEach { item ->
                                ProductSummaryRow(item)
                                Spacer(modifier = Modifier.height(4.dp))
                            }
                        }
                    }

                    // Imágenes
                    SectionCard(
                        title = "Imágenes",
                        icon = Lucide.Image
                    ) {
                        InfoRow(
                            "Fotos adjuntas",
                            "${data.imageCount} imagen${if (data.imageCount != 1) "es" else ""}"
                        )
                    }

                    // Notas
                    if (data.note.isNotBlank()) {
                        SectionCard(
                            title = "Notas",
                            icon = Lucide.StickyNote
                        ) {
                            Text(
                                text = data.note,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    // Totales
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                        ),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp)
                        ) {
                            Text(
                                text = "Resumen de Precios",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(bottom = 12.dp)
                            )

                            Column(
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                listOf(
                                    Triple(
                                        "Lista",
                                        currencyFormat.format(data.totalPrecioLista),
                                        true
                                    ),
                                    Triple(
                                        "Corto Plazo",
                                        currencyFormat.format(data.totalCortoPlazo),
                                        false
                                    ),
                                    Triple(
                                        "Contado",
                                        currencyFormat.format(data.totalContado),
                                        false
                                    )
                                ).forEach { (label, price, isHighlighted) ->
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = label,
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            fontWeight = FontWeight.Medium
                                        )
                                        Text(
                                            text = price,
                                            style = if (isHighlighted)
                                                MaterialTheme.typography.titleMedium
                                            else
                                                MaterialTheme.typography.bodyLarge,
                                            fontWeight = if (isHighlighted) FontWeight.Bold else FontWeight.Medium,
                                            color = if (isHighlighted)
                                                MaterialTheme.colorScheme.primary
                                            else
                                                MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                }

                // Footer con botones
                HorizontalDivider()

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    TextButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Cancelar")
                    }

                    Button(
                        onClick = { showFinalConfirmation = true },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary
                        )
                    ) {
                        Icon(
                            imageVector = Lucide.Check,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                            tint = Color.White
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Confirmar",
                            color = Color.White,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionCard(
    title: String,
    icon: ImageVector,
    content: @Composable () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
        ),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(bottom = 12.dp)
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
            }
            content()
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
private fun ComboSummaryCard(
    combo: ComboItem,
    products: List<SaleItem>,
    currencyFormat: NumberFormat
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Lucide.Package,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = combo.nombreCombo,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold
                )
            }
            Text(
                text = currencyFormat.format(combo.precioLista),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        products.forEach { item ->
            Text(
                text = "  • ${item.product.ARTICULO} x${item.quantity}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun ProductSummaryRow(item: SaleItem) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f)
        ) {
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(MaterialTheme.colorScheme.secondaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "${item.quantity}",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
            }
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = item.product.ARTICULO ?: "Producto",
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

