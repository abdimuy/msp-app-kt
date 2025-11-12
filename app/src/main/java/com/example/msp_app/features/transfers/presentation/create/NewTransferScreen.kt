package com.example.msp_app.features.transfers.presentation.create

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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.example.msp_app.core.utils.ResultState
import com.example.msp_app.features.transfers.presentation.components.StepIndicator
import com.example.msp_app.features.transfers.presentation.components.WarehouseSelector
import java.text.NumberFormat
import java.util.Locale

/**
 * Format currency helper
 */
private fun formatCurrency(amount: Double): String {
    val format = NumberFormat.getCurrencyInstance(Locale("es", "MX"))
    return format.format(amount)
}

/**
 * New transfer creation screen with 3-step wizard
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewTransferScreen(
    viewModel: NewTransferViewModel,
    navController: NavController,
    preselectedWarehouseId: Int = 0,
    modifier: Modifier = Modifier
) {
    val currentStep by viewModel.currentStep.collectAsState()
    val sourceWarehouse by viewModel.sourceWarehouse.collectAsState()
    val destinationWarehouse by viewModel.destinationWarehouse.collectAsState()
    val selectedProducts by viewModel.selectedProducts.collectAsState()
    val warehouses by viewModel.warehousesState.collectAsState()
    val availableProducts by viewModel.availableProductsState.collectAsState()
    val isLoadingProducts = availableProducts is ResultState.Loading
    val isLoadingCosts = viewModel.productCostsState.collectAsState().value is ResultState.Loading
    val createState by viewModel.createTransferState.collectAsState()
    val validationError by viewModel.validationError.collectAsState()

    // Preselect source warehouse if provided
    LaunchedEffect(preselectedWarehouseId, warehouses) {
        if (preselectedWarehouseId != 0 && warehouses is ResultState.Success) {
            val warehousesList = (warehouses as ResultState.Success).data
            val warehouse = warehousesList.find { it.ALMACEN_ID == preselectedWarehouseId }
            if (warehouse != null) {
                viewModel.selectSourceWarehouse(warehouse)
            }
        }
    }

    // Handle successful creation
    LaunchedEffect(createState) {
        if (createState is ResultState.Success<*>) {
            navController.popBackStack()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Nuevo Traspaso") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Default.Close, contentDescription = "Cerrar")
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Step indicator
            StepIndicator(
                currentStep = currentStep,
                modifier = Modifier.padding(16.dp)
            )

            // Step content
            when (currentStep) {
                TransferStep.WAREHOUSES -> {
                    WarehouseSelectionStep(
                        warehouses = when (warehouses) {
                            is ResultState.Success<*> -> (warehouses as ResultState.Success<List<com.example.msp_app.data.api.services.warehouses.WarehouseListResponse.Warehouse>>).data
                            else -> emptyList()
                        },
                        sourceWarehouse = sourceWarehouse,
                        destinationWarehouse = destinationWarehouse,
                        onSourceSelected = viewModel::selectSourceWarehouse,
                        onDestinationSelected = viewModel::selectDestinationWarehouse,
                        sourceError = validationError,
                        destinationError = validationError,
                        isSourceWarehouseLocked = preselectedWarehouseId != 0,
                        modifier = Modifier
                            .weight(1f)
                            .padding(16.dp)
                    )
                }

                TransferStep.PRODUCTS -> {
                    ProductSelectionStep(
                        selectedProducts = selectedProducts,
                        availableProducts = when (availableProducts) {
                            is ResultState.Success<*> -> (availableProducts as ResultState.Success<List<com.example.msp_app.data.models.productInventory.ProductInventory>>).data
                            else -> emptyList()
                        },
                        isLoadingProducts = isLoadingProducts,
                        isLoadingCosts = isLoadingCosts,
                        onAddProduct = { product -> viewModel.addProduct(product, product.EXISTENCIAS) },
                        onRemoveProduct = viewModel::removeProduct,
                        onUpdateUnits = viewModel::updateProductUnits,
                        productsError = validationError,
                        modifier = Modifier
                            .weight(1f)
                    )
                }

                TransferStep.CONFIRMATION -> {
                    ConfirmationStep(
                        sourceWarehouse = sourceWarehouse,
                        destinationWarehouse = destinationWarehouse,
                        selectedProducts = selectedProducts,
                        isCreating = createState is ResultState.Loading,
                        modifier = Modifier
                            .weight(1f)
                            .padding(16.dp)
                    )
                }
            }

            // Navigation buttons
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (currentStep != TransferStep.WAREHOUSES) {
                    OutlinedButton(
                        onClick = viewModel::previousStep,
                        modifier = Modifier.weight(1f),
                        enabled = createState !is ResultState.Loading
                    ) {
                        Text("Anterior")
                    }
                }

                Button(
                    onClick = {
                        if (currentStep == TransferStep.CONFIRMATION) {
                            viewModel.createTransfer()
                        } else {
                            viewModel.nextStep()
                        }
                    },
                    modifier = Modifier.weight(1f),
                    enabled = createState !is ResultState.Loading
                ) {
                    if (createState is ResultState.Loading) {
                        CircularProgressIndicator(
                            modifier = Modifier.padding(end = 8.dp),
                            color = Color.White
                        )
                    }
                    Text(
                        text = when (currentStep) {
                            TransferStep.CONFIRMATION -> "Crear Traspaso"
                            else -> "Siguiente"
                        },
                        color = Color.White
                    )
                }
            }

            // Error display
            if (createState is ResultState.Error) {
                Text(
                    text = (createState as ResultState.Error).message,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }
        }
    }
}

/**
 * Step 1: Warehouse selection
 */
@Composable
private fun WarehouseSelectionStep(
    warehouses: List<com.example.msp_app.data.api.services.warehouses.WarehouseListResponse.Warehouse>,
    sourceWarehouse: com.example.msp_app.data.api.services.warehouses.WarehouseListResponse.Warehouse?,
    destinationWarehouse: com.example.msp_app.data.api.services.warehouses.WarehouseListResponse.Warehouse?,
    onSourceSelected: (com.example.msp_app.data.api.services.warehouses.WarehouseListResponse.Warehouse) -> Unit,
    onDestinationSelected: (com.example.msp_app.data.api.services.warehouses.WarehouseListResponse.Warehouse) -> Unit,
    sourceError: String?,
    destinationError: String?,
    isSourceWarehouseLocked: Boolean = false,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        Text(
            text = "Selecciona los almacenes",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )

        if (isSourceWarehouseLocked) {
            Text(
                text = "Almacén de origen preseleccionado",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        WarehouseSelector(
            label = "Almacén de Origen",
            warehouses = warehouses,
            selectedWarehouse = sourceWarehouse,
            onWarehouseSelected = onSourceSelected,
            excludeWarehouseId = destinationWarehouse?.ALMACEN_ID,
            enabled = !isSourceWarehouseLocked,
            error = sourceError
        )

        Icon(
            imageVector = Icons.Default.ArrowForward,
            contentDescription = null,
            modifier = Modifier.align(Alignment.CenterHorizontally),
            tint = MaterialTheme.colorScheme.primary
        )

        WarehouseSelector(
            label = "Almacén de Destino",
            warehouses = warehouses,
            selectedWarehouse = destinationWarehouse,
            onWarehouseSelected = onDestinationSelected,
            excludeWarehouseId = sourceWarehouse?.ALMACEN_ID,
            error = destinationError
        )
    }
}

/**
 * Step 2: Product selection
 */
@Composable
private fun ProductSelectionStep(
    selectedProducts: List<SelectedProduct>,
    availableProducts: List<com.example.msp_app.data.models.productInventory.ProductInventory>,
    isLoadingProducts: Boolean,
    isLoadingCosts: Boolean,
    onAddProduct: (com.example.msp_app.data.models.productInventory.ProductInventory) -> Unit,
    onRemoveProduct: (Int) -> Unit,
    onUpdateUnits: (Int, Int) -> Unit,
    productsError: String?,
    modifier: Modifier = Modifier
) {
    var searchQuery by remember { mutableStateOf("") }

    // Filter products based on search query
    val filteredProducts = remember(availableProducts, searchQuery) {
        if (searchQuery.isBlank()) {
            availableProducts
        } else {
            availableProducts.filter { product ->
                product.ARTICULO.contains(searchQuery, ignoreCase = true) ||
                product.LINEA_ARTICULO.contains(searchQuery, ignoreCase = true)
            }
        }
    }

    Column(
        modifier = modifier.fillMaxSize()
    ) {
        // Header with select all button
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "Productos (${selectedProducts.size} seleccionados)",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                if (searchQuery.isNotBlank()) {
                    Text(
                        text = "${filteredProducts.size} de ${availableProducts.size} productos",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            OutlinedButton(
                onClick = {
                    if (selectedProducts.size == availableProducts.size) {
                        // Deselect all - remove all selected products
                        selectedProducts.forEach { onRemoveProduct(it.product.ARTICULO_ID) }
                    } else {
                        // Select all with full stock - only add products that aren't selected
                        val selectedIds = selectedProducts.map { it.product.ARTICULO_ID }.toSet()
                        availableProducts.forEach { product ->
                            if (product.ARTICULO_ID !in selectedIds) {
                                onAddProduct(product)
                            }
                        }
                    }
                },
                enabled = availableProducts.isNotEmpty() && !isLoadingProducts
            ) {
                Text(
                    if (selectedProducts.size == availableProducts.size) "Deseleccionar todos" else "Seleccionar todos"
                )
            }
        }

        if (isLoadingCosts) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }

        if (productsError != null) {
            Text(
                text = productsError,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
        }

        // Search bar
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
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
        if (isLoadingProducts) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else if (filteredProducts.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = if (searchQuery.isBlank()) "No hay productos disponibles" else "No se encontraron productos",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = if (searchQuery.isBlank()) "El almacén de origen no tiene productos" else "Intenta con otra búsqueda",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            // Create a map for O(1) lookup of selected products
            val selectedProductsMap = remember(selectedProducts) {
                selectedProducts.associateBy { it.product.ARTICULO_ID }
            }

            LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(filteredProducts, key = { it.ARTICULO_ID }) { product ->
                    val selectedProduct = selectedProductsMap[product.ARTICULO_ID]
                    val isSelected = selectedProduct != null

                    ProductCheckboxItem(
                        product = product,
                        isSelected = isSelected,
                        selectedUnits = selectedProduct?.units ?: product.EXISTENCIAS,
                        onCheckedChange = { checked ->
                            if (checked) {
                                onAddProduct(product)
                            } else {
                                onRemoveProduct(product.ARTICULO_ID)
                            }
                        },
                        onUnitsChange = { newUnits ->
                            onUpdateUnits(product.ARTICULO_ID, newUnits)
                        }
                    )
                }
            }
        }
    }
}

/**
 * Product item with checkbox for selection
 */
@Composable
private fun ProductCheckboxItem(
    product: com.example.msp_app.data.models.productInventory.ProductInventory,
    isSelected: Boolean,
    selectedUnits: Int,
    onCheckedChange: (Boolean) -> Unit,
    onUnitsChange: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Checkbox
            androidx.compose.material3.Checkbox(
                checked = isSelected,
                onCheckedChange = onCheckedChange
            )

            // Product info
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = product.ARTICULO,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "Stock: ${product.EXISTENCIAS} unidades",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "Línea: ${product.LINEA_ARTICULO}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Quantity selector (only visible when selected)
            if (isSelected) {
                Column(
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = "Cantidad:",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        IconButton(
                            onClick = { if (selectedUnits > 1) onUnitsChange(selectedUnits - 1) },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Text("-", style = MaterialTheme.typography.titleMedium)
                        }

                        OutlinedTextField(
                            value = selectedUnits.toString(),
                            onValueChange = { newValue ->
                                newValue.toIntOrNull()?.let { units ->
                                    if (units > 0 && units <= product.EXISTENCIAS) {
                                        onUnitsChange(units)
                                    }
                                }
                            },
                            modifier = Modifier.width(80.dp),
                            textStyle = MaterialTheme.typography.bodyMedium.copy(
                                textAlign = TextAlign.Center
                            ),
                            singleLine = true
                        )

                        IconButton(
                            onClick = {
                                if (selectedUnits < product.EXISTENCIAS) {
                                    onUnitsChange(selectedUnits + 1)
                                }
                            },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(Icons.Default.Add, contentDescription = "Incrementar")
                        }
                    }
                }
            }
        }
    }
}

/**
 * Step 3: Confirmation
 */
@Composable
private fun ConfirmationStep(
    sourceWarehouse: com.example.msp_app.data.api.services.warehouses.WarehouseListResponse.Warehouse?,
    destinationWarehouse: com.example.msp_app.data.api.services.warehouses.WarehouseListResponse.Warehouse?,
    selectedProducts: List<SelectedProduct>,
    isCreating: Boolean,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Text(
                text = "Confirmar traspaso",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
        }

        // Warehouse info
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Almacenes",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Origen:",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = sourceWarehouse?.ALMACEN ?: "N/A",
                            style = MaterialTheme.typography.bodyLarge
                        )
                    }
                    Icon(
                        imageVector = Icons.Default.ArrowForward,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = "Destino:",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = destinationWarehouse?.ALMACEN ?: "N/A",
                            style = MaterialTheme.typography.bodyLarge
                        )
                    }
                }
            }
        }

        // Products summary
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Productos (${selectedProducts.size})",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium
                )
            }
        }

        items(selectedProducts, key = { it.product.ARTICULO_ID }) { selectedProduct ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = selectedProduct.getDisplayName(),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = "Cantidad: ${selectedProduct.units}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (selectedProduct.costUnitario != null) {
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = formatCurrency(selectedProduct.costUnitario),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        selectedProduct.calculateTotal()?.let { total ->
                            Text(
                                text = formatCurrency(total),
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }
        }

        // Total cost
        item {
            val totalCost = selectedProducts.mapNotNull { it.calculateTotal() }.sum()
            if (totalCost > 0) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Costo Total:",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = formatCurrency(totalCost),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}
