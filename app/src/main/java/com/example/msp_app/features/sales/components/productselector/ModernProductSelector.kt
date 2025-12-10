package com.example.msp_app.features.sales.components.productselector

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
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
import com.composables.icons.lucide.Package
import com.composables.icons.lucide.PackageOpen
import com.example.msp_app.core.context.LocalAuthViewModel
import com.example.msp_app.core.utils.ResultState
import com.example.msp_app.core.utils.parsePriceJsonToMap
import com.example.msp_app.data.models.sale.localsale.LocalSaleProductPackage
import com.example.msp_app.features.sales.components.packagedialog.CreatePackageDialog
import com.example.msp_app.features.sales.viewmodels.SaleItem
import com.example.msp_app.features.sales.viewmodels.SaleProductsViewModel
import com.example.msp_app.features.warehouses.WarehouseViewModel

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ModernProductSelector(
    warehouseViewModel: WarehouseViewModel,
    saleProductsViewModel: SaleProductsViewModel,
    onAddProduct: (Int, Int) -> Unit
) {
    val authViewModel = LocalAuthViewModel.current
    var searchQuery by remember { mutableStateOf("") }
    var showProductList by remember { mutableStateOf(false) }
    var showCreatePackageDialog by remember { mutableStateOf(false) }

    val userData by authViewModel.userData.collectAsState()
    val warehouseState by warehouseViewModel.warehouseProducts.collectAsState()

    val camionetaId = when (val userState = userData) {
        is ResultState.Success -> userState.data?.CAMIONETA_ASIGNADA
        else -> null
    }

    LaunchedEffect(camionetaId) {
        if (camionetaId != null) {
            warehouseViewModel.selectWarehouse(camionetaId)
        }
    }

    val productosCamioneta = when (val s = warehouseState) {
        is ResultState.Success -> s.data.body.ARTICULOS.filter { producto ->
            searchQuery.isBlank() || producto.ARTICULO.contains(searchQuery, ignoreCase = true)
        }

        else -> emptyList()
    }

    val saleItems = saleProductsViewModel.saleItems
    val packages = saleProductsViewModel.packages

    // Diálogo para crear paquetes
    CreatePackageDialog(
        show = showCreatePackageDialog,
        saleProductsViewModel = saleProductsViewModel,
        onDismiss = { showCreatePackageDialog = false },
        onPackageCreated = { /* Paquete creado exitosamente */ }
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize()
    ) {
        // Header con título y contador del carrito
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Productos a vender",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Botón para crear paquete
                    if (saleItems.size >= 2) {
                        OutlinedButton(
                            onClick = { showCreatePackageDialog = true },
                            shape = RoundedCornerShape(12.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
                        ) {
                            Icon(
                                Lucide.Package,
                                contentDescription = "Crear paquete",
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Paquete", fontSize = 12.sp)
                        }
                    }

                    // Contador del carrito
                    if (saleItems.isNotEmpty() || packages.isNotEmpty()) {
                        Surface(
                            color = MaterialTheme.colorScheme.primary,
                            shape = CircleShape,
                            modifier = Modifier.size(32.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center
                                ) {
                                    Icon(
                                        Icons.Default.ShoppingCart,
                                        contentDescription = "Carrito",
                                        tint = Color.White,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(2.dp))
                                    Text(
                                        text = saleProductsViewModel.getTotalItems().toString(),
                                        color = Color.White,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Barra de búsqueda mejorada
        OutlinedTextField(
            value = searchQuery,
            onValueChange = {
                searchQuery = it
                showProductList = it.isNotEmpty()
            },
            label = { Text("Buscar productos...") },
            leadingIcon = {
                Icon(Icons.Default.Search, contentDescription = "Buscar")
            },
            trailingIcon = {
                if (searchQuery.isNotEmpty()) {
                    IconButton(onClick = {
                        searchQuery = ""
                        showProductList = false
                    }) {
                        Icon(Icons.Default.Clear, contentDescription = "Limpiar")
                    }
                }
            },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            singleLine = true
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Lista de productos disponibles (solo si hay búsqueda)
        if (showProductList && productosCamioneta.isNotEmpty()) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(300.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                LazyColumn(
                    modifier = Modifier.padding(8.dp),
                    contentPadding = PaddingValues(bottom = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(productosCamioneta) { product ->
                        ProductCard(
                            product = product,
                            saleProductsViewModel = saleProductsViewModel,
                            onAddProduct = onAddProduct
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
        }

        // Carrito de productos seleccionados
        if (saleItems.isNotEmpty() || packages.isNotEmpty()) {
            Text(
                "Productos seleccionados (${saleItems.size})",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    if (packages.isNotEmpty()) {
                        packages.forEach { productPackage ->
                            ModernPackageItemRow(
                                productPackage = productPackage,
                                saleProductsViewModel = saleProductsViewModel
                            )
                            if (productPackage != packages.last() || saleItems.isNotEmpty()) {
                                Spacer(modifier = Modifier.height(12.dp))
                                Divider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                                Spacer(modifier = Modifier.height(12.dp))
                            }
                        }
                    }

                    // Mostrar productos individuales
                    if (saleItems.isNotEmpty()) {
                        saleItems.forEach { saleItem ->
                            ModernCartItemRow(
                                saleItem = saleItem,
                                saleProductsViewModel = saleProductsViewModel
                            )
                            if (saleItem != saleItems.last()) {
                                Spacer(modifier = Modifier.height(8.dp))
                            }
                        }
                    }
                }
            }
        } else if (!showProductList) {
            // Estado vacío elegante
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(120.dp)
                    .background(
                        MaterialTheme.colorScheme.surfaceContainer,
                        RoundedCornerShape(12.dp)
                    )
                    .border(
                        1.dp,
                        MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                        RoundedCornerShape(12.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Default.ShoppingCart,
                        contentDescription = null,
                        modifier = Modifier.size(32.dp),
                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "Busca productos para agregar a la venta",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ProductCard(
    product: com.example.msp_app.data.models.productInventory.ProductInventory,
    saleProductsViewModel: SaleProductsViewModel,
    onAddProduct: (Int, Int) -> Unit
) {
    val cantidadEnVenta = saleProductsViewModel.getQuantityForProduct(product)
    val disponible = product.EXISTENCIAS - cantidadEnVenta
    var quantity by remember { mutableStateOf(1) }

    val priceMap = try {
        parsePriceJsonToMap(product.PRECIOS ?: "")
    } catch (e: Exception) {
        emptyMap()
    }
    val price = priceMap.values.lastOrNull() ?: "N/A"

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (disponible > 0)
                MaterialTheme.colorScheme.surface
            else
                MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.1f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = product.ARTICULO,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Medium,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "$$price",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        "Stock: $disponible",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (disponible > 0)
                            MaterialTheme.colorScheme.primary
                        else
                            MaterialTheme.colorScheme.error
                    )
                    if (cantidadEnVenta > 0) {
                        Text(
                            "En venta: $cantidadEnVenta",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.secondary
                        )
                    }
                }
            }

            if (disponible > 0) {
                Spacer(modifier = Modifier.height(12.dp))

                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalArrangement = Arrangement.Center
                ) {
                    // Controles de cantidad
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilledTonalIconButton(
                            onClick = { if (quantity > 1) quantity-- },
                            modifier = Modifier.size(32.dp),
                            enabled = quantity > 1
                        ) {
                            Icon(
                                Icons.Default.KeyboardArrowDown,
                                contentDescription = "Reducir",
                                modifier = Modifier.size(16.dp)
                            )
                        }

                        Text(
                            text = quantity.toString(),
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier
                                .background(
                                    MaterialTheme.colorScheme.surfaceContainer,
                                    RoundedCornerShape(8.dp)
                                )
                                .padding(horizontal = 12.dp, vertical = 4.dp),
                            textAlign = TextAlign.Center
                        )

                        FilledTonalIconButton(
                            onClick = { if (quantity < disponible) quantity++ },
                            modifier = Modifier.size(32.dp),
                            enabled = quantity < disponible
                        ) {
                            Icon(
                                Icons.Default.KeyboardArrowUp,
                                contentDescription = "Aumentar",
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }

                    // Botón agregar
                    FilledIconButton(
                        onClick = {
                            onAddProduct(product.ARTICULO_ID, quantity)
                            quantity = 1
                        },
                        modifier = Modifier.height(40.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 8.dp)
                        ) {
                            Icon(
                                Icons.Default.Add,
                                contentDescription = "Agregar",
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                "Agregar",
                                fontSize = 12.sp
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ModernPackageItemRow(
    productPackage: LocalSaleProductPackage,
    saleProductsViewModel: SaleProductsViewModel
) {
    var showEditDialog by remember { mutableStateOf(false) }
    var showUnpackDialog by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            // Header compacto
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Lucide.Package,
                        contentDescription = "Paquete",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
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
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    IconButton(
                        onClick = { showEditDialog = true },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            Icons.Default.Edit,
                            contentDescription = "Editar",
                            modifier = Modifier.size(16.dp)
                        )
                    }

                    IconButton(
                        onClick = { showUnpackDialog = true },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            Lucide.PackageOpen,
                            contentDescription = "Deshacer",
                            modifier = Modifier.size(16.dp)
                        )
                    }

                    IconButton(
                        onClick = { saleProductsViewModel.removePackage(productPackage.packageId) },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            Icons.Default.Clear,
                            contentDescription = "Eliminar",
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Info compacta
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "${productPackage.products.size} productos • ${productPackage.getTotalQuantity()} unidades",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                IconButton(
                    onClick = {
                        saleProductsViewModel.togglePackageExpanded(productPackage.packageId)
                    },
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(
                        if (productPackage.isExpanded) Icons.Default.KeyboardArrowUp
                        else Icons.Default.KeyboardArrowDown,
                        contentDescription = if (productPackage.isExpanded) "Ocultar" else "Mostrar",
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            // Precios en línea compacta
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "L: $${productPackage.precioLista}",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "CP: $${productPackage.precioCortoplazo}",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.secondary
                )
                Text(
                    text = "C: $${productPackage.precioContado}",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.tertiary
                )
            }

            // Productos expandibles
            AnimatedVisibility(visible = productPackage.isExpanded) {
                Column(modifier = Modifier.padding(top = 8.dp)) {
                    Divider(modifier = Modifier.padding(vertical = 8.dp))
                    productPackage.products.forEach { saleItem ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = saleItem.product.ARTICULO,
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.weight(1f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "x${saleItem.quantity}",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
    }

    // Diálogo de edición
    if (showEditDialog) {
        EditPackagePricesDialog(
            productPackage = productPackage,
            onDismiss = { showEditDialog = false },
            onConfirm = { lista, cortoplazo, contado ->
                saleProductsViewModel.updatePackagePrices(
                    packageId = productPackage.packageId,
                    precioLista = lista,
                    precioCortoplazo = cortoplazo,
                    precioContado = contado
                )
                showEditDialog = false
            }
        )
    }

    // Diálogo de confirmación
    if (showUnpackDialog) {
        AlertDialog(
            onDismissRequest = { showUnpackDialog = false },
            title = { Text("¿Deshacer paquete?") },
            text = {
                Text("Los productos se devolverán a la lista individual.")
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

@Composable
private fun EditPackagePricesDialog(
    productPackage: LocalSaleProductPackage,
    onDismiss: () -> Unit,
    onConfirm: (Double, Double, Double) -> Unit
) {
    var precioLista by remember { mutableStateOf(productPackage.precioLista.toString()) }
    var precioCortoplazo by remember { mutableStateOf(productPackage.precioCortoplazo.toString()) }
    var precioContado by remember { mutableStateOf(productPackage.precioContado.toString()) }
    var errorMessage by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Editar precios del paquete") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = precioLista,
                    onValueChange = { precioLista = it; errorMessage = "" },
                    label = { Text("Precio Lista") },
                    prefix = { Text("$") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )

                OutlinedTextField(
                    value = precioCortoplazo,
                    onValueChange = { precioCortoplazo = it; errorMessage = "" },
                    label = { Text("Precio Corto Plazo") },
                    prefix = { Text("$") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )

                OutlinedTextField(
                    value = precioContado,
                    onValueChange = { precioContado = it; errorMessage = "" },
                    label = { Text("Precio Contado") },
                    prefix = { Text("$") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )

                if (errorMessage.isNotEmpty()) {
                    Text(
                        text = errorMessage,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val lista = precioLista.toDoubleOrNull()
                    val cortoplazo = precioCortoplazo.toDoubleOrNull()
                    val contado = precioContado.toDoubleOrNull()

                    if (lista == null || lista <= 0 ||
                        cortoplazo == null || cortoplazo <= 0 ||
                        contado == null || contado <= 0
                    ) {
                        errorMessage = "Todos los precios deben ser mayores a 0"
                        return@Button
                    }

                    onConfirm(lista, cortoplazo, contado)
                }
            ) {
                Text("Guardar", color = Color.White)
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) {
                Text("Cancelar")
            }
        }
    )
}

@Composable
private fun ModernCartItemRow(
    saleItem: SaleItem,
    saleProductsViewModel: SaleProductsViewModel
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = saleItem.product.ARTICULO,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = "Cantidad: ${saleItem.quantity}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
            )
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            FilledTonalIconButton(
                onClick = {
                    val newQuantity = saleItem.quantity - 1
                    saleProductsViewModel.updateQuantity(saleItem.product, newQuantity)
                },
                modifier = Modifier.size(32.dp),
                enabled = saleItem.quantity > 1
            ) {
                Icon(
                    Icons.Default.KeyboardArrowDown,
                    contentDescription = "Reducir",
                    modifier = Modifier.size(16.dp)
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

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
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    Icons.Default.KeyboardArrowUp,
                    contentDescription = "Aumentar",
                    modifier = Modifier.size(16.dp)
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            IconButton(
                onClick = {
                    saleProductsViewModel.removeProductFromSale(saleItem.product)
                },
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    Icons.Default.Clear,
                    contentDescription = "Eliminar",
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}