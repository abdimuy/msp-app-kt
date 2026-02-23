package com.example.msp_app.features.transfers.presentation.components

import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SheetState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.example.msp_app.components.selectbluetoothdevice.SelectBluetoothDevice
import com.example.msp_app.core.utils.PdfGenerator
import com.example.msp_app.core.utils.ResultState
import com.example.msp_app.core.utils.ThermalPrinting
import com.example.msp_app.data.models.productInventory.ProductInventory
import com.example.msp_app.features.payments.components.pdfgenerationdialog.PdfGenerationDialog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@RequiresApi(Build.VERSION_CODES.S)
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WarehouseProductsBottomSheet(
    warehouseName: String,
    totalStock: Int,
    assignedUsers: List<com.example.msp_app.data.models.auth.User>,
    productsState: ResultState<List<ProductInventory>>,
    sheetState: SheetState,
    onDismiss: () -> Unit
) {
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var showPrintDialog by remember { mutableStateOf(false) }
    var isGeneratingPdf by remember { mutableStateOf(false) }
    var pdfUri by remember { mutableStateOf<Uri?>(null) }
    var showPdfDialog by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 16.dp)
        ) {
            // Header
            Text(
                text = warehouseName,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            // Stock total badge
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                ),
                modifier = Modifier.padding(bottom = 16.dp)
            ) {
                Text(
                    text = "Stock Total: $totalStock unidades",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
                )
            }

            // Assigned vendors
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(bottom = 16.dp)
            ) {
                Text(
                    text = "Vendedores",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.Medium
                )

                if (assignedUsers.isEmpty()) {
                    Text(
                        text = "Sin vendedores asignados",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                    )
                } else {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        assignedUsers.forEach { user ->
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .background(
                                            MaterialTheme.colorScheme.primary,
                                            shape = CircleShape
                                        )
                                )
                                Text(
                                    text = user.NOMBRE,
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                        }
                    }
                }
            }

            // Action buttons
            if (productsState is ResultState.Success && productsState.data.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // PDF button
                    Button(
                        onClick = {
                            coroutineScope.launch {
                                isGeneratingPdf = true
                                val file = withContext(Dispatchers.IO) {
                                    PdfGenerator.generateWarehouseInventoryPdf(
                                        context = context,
                                        warehouseName = warehouseName,
                                        totalStock = totalStock,
                                        assignedUsers = assignedUsers,
                                        products = productsState.data,
                                        fileName = "inventario_${
                                            warehouseName.replace(
                                                " ",
                                                "_"
                                            )
                                        }.pdf",
                                        watermarkText = assignedUsers.firstOrNull()?.NOMBRE
                                            ?: "CONFIDENCIAL"
                                    )
                                }
                                isGeneratingPdf = false
                                if (file != null && file.exists()) {
                                    val uri = FileProvider.getUriForFile(
                                        context,
                                        context.packageName + ".fileprovider",
                                        file
                                    )
                                    pdfUri = uri
                                    showPdfDialog = true
                                } else {
                                    Toast.makeText(
                                        context,
                                        "Error al generar PDF",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                            }
                        },
                        enabled = !isGeneratingPdf,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            if (isGeneratingPdf) "GENERANDO..." else "GENERAR PDF",
                            color = Color.White
                        )
                    }

                    // Thermal print button
                    Button(
                        onClick = { showPrintDialog = true },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("IMPRIMIR", color = Color.White)
                    }
                }
            }

            // Search bar
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                placeholder = { Text("Buscar producto...") },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = "Buscar"
                    )
                },
                singleLine = true
            )

            // Products list
            when (productsState) {
                is ResultState.Loading -> {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                }

                is ResultState.Success -> {
                    val products = if (searchQuery.isBlank()) {
                        productsState.data
                    } else {
                        productsState.data.filter { product ->
                            product.ARTICULO.contains(searchQuery, ignoreCase = true) ||
                                    product.LINEA_ARTICULO.contains(searchQuery, ignoreCase = true)
                        }
                    }

                    if (products.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(200.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = if (searchQuery.isBlank()) "No hay productos en este almacén" else "No se encontraron productos",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(bottom = 16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(products, key = { it.ARTICULO_ID }) { product ->
                                ProductItem(product = product)
                            }
                        }
                    }
                }

                is ResultState.Error -> {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "Error al cargar productos",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.error
                            )
                            Text(
                                text = productsState.message,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                ResultState.Idle, is ResultState.Offline -> {
                    // Initial state
                }
            }

            // Print dialog
            if (showPrintDialog && productsState is ResultState.Success) {
                val ticketText = generateWarehouseInventoryTicket(
                    warehouseName = warehouseName,
                    totalStock = totalStock,
                    assignedUsers = assignedUsers,
                    products = productsState.data
                )

                androidx.compose.material3.AlertDialog(
                    onDismissRequest = { showPrintDialog = false },
                    title = { Text("Imprimir Inventario") },
                    text = {
                        SelectBluetoothDevice(
                            textToPrint = ticketText,
                            verticalLayout = true,
                            onPrintRequest = { device, text ->
                                coroutineScope.launch {
                                    try {
                                        ThermalPrinting.printText(device, text, context)
                                        showPrintDialog = false
                                    } catch (e: Exception) {
                                        // Error already handled by ThermalPrinting
                                    }
                                }
                            }
                        )
                    },
                    confirmButton = {},
                    dismissButton = {
                        androidx.compose.material3.TextButton(onClick = {
                            showPrintDialog = false
                        }) {
                            Text("Cancelar")
                        }
                    }
                )
            }

            // PDF dialog
            if (showPdfDialog && pdfUri != null) {
                PdfGenerationDialog(
                    pdfUri = pdfUri!!,
                    onDismiss = {
                        showPdfDialog = false
                        pdfUri = null
                    }
                )
            }
        }
    }
}

/**
 * Generate thermal printer ticket for warehouse inventory
 */
private fun generateWarehouseInventoryTicket(
    warehouseName: String,
    totalStock: Int,
    assignedUsers: List<com.example.msp_app.data.models.auth.User>,
    products: List<ProductInventory>
): String {
    return buildString {
        val lineBlanck = " "

        appendLine(ThermalPrinting.centerText("INVENTARIO DE ALMACEN", 32))
        appendLine(lineBlanck)
        appendLine("-".repeat(32))
        appendLine(lineBlanck)
        appendLine(ThermalPrinting.bold("ALMACEN: $warehouseName"))
        appendLine("STOCK TOTAL: $totalStock productos")
        appendLine(lineBlanck)

        // Vendedores
        if (assignedUsers.isNotEmpty()) {
            appendLine("VENDEDORES ASIGNADOS:")
            assignedUsers.forEach { user ->
                appendLine("  - ${user.NOMBRE}")
            }
        } else {
            appendLine("VENDEDORES: Sin asignar")
        }

        appendLine(lineBlanck)
        appendLine("-".repeat(32))
        appendLine(lineBlanck)
        appendLine(ThermalPrinting.centerText("PRODUCTOS", 32))
        appendLine(lineBlanck)

        // Productos ordenados por nombre
        products.sortedBy { it.ARTICULO }.forEach { product ->
            appendLine(ThermalPrinting.bold(product.ARTICULO))
            appendLine("  Linea: ${product.LINEA_ARTICULO}")
            appendLine("  Stock: ${product.EXISTENCIAS} unidades")
            appendLine(lineBlanck)
        }

        appendLine("-".repeat(32))
        appendLine(lineBlanck)
        appendLine("TOTAL DE PRODUCTOS: ${products.size}")
        appendLine(
            "FECHA: ${
                java.text.SimpleDateFormat(
                    "dd/MM/yyyy HH:mm",
                    java.util.Locale.getDefault()
                ).format(java.util.Date())
            }"
        )
        appendLine(lineBlanck)
        appendLine(lineBlanck)
        appendLine(lineBlanck)
    }
}

@Composable
private fun ProductItem(
    product: ProductInventory,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = product.ARTICULO,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = product.LINEA_ARTICULO,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.width(16.dp))

            // Stock badge
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = when {
                        product.EXISTENCIAS == 0 -> MaterialTheme.colorScheme.errorContainer
                        product.EXISTENCIAS < 10 -> MaterialTheme.colorScheme.tertiaryContainer
                        else -> MaterialTheme.colorScheme.primaryContainer
                    }
                )
            ) {
                Text(
                    text = "${product.EXISTENCIAS}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                )
            }
        }
    }
}
