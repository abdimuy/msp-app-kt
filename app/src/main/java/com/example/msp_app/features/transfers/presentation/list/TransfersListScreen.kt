package com.example.msp_app.features.transfers.presentation.list

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.example.msp_app.components.DrawerContainer
import com.example.msp_app.core.utils.ResultState
import com.example.msp_app.data.api.services.warehouses.WarehouseListResponse
import com.example.msp_app.features.transfers.presentation.components.WarehouseProductsBottomSheet
import kotlinx.coroutines.launch

/**
 * Warehouse dashboard screen for transfers
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransfersListScreen(
    viewModel: TransfersListViewModel,
    navController: NavController,
    onTransferClick: (Int) -> Unit,
    onCreateTransferClick: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val warehousesState by viewModel.warehousesState.collectAsState()
    val warehouseProductsState by viewModel.warehouseProductsState.collectAsState()
    val usersByWarehouse by viewModel.usersByWarehouse.collectAsState()
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var showBottomSheet by remember { mutableStateOf(false) }
    var selectedWarehouseForProducts by remember { mutableStateOf<WarehouseListResponse.Warehouse?>(null) }

    val sheetState = rememberModalBottomSheetState()
    val scope = rememberCoroutineScope()

    val configuration = LocalConfiguration.current
    val isTablet = configuration.screenWidthDp >= 600

    // Refresh warehouses when screen is displayed
    LaunchedEffect(Unit) {
        viewModel.refreshWarehouses()
    }

    // Handle view products click
    fun handleViewProducts(warehouse: WarehouseListResponse.Warehouse) {
        selectedWarehouseForProducts = warehouse
        viewModel.loadWarehouseProducts(warehouse.ALMACEN_ID)
        showBottomSheet = true
    }

    // Handle bottom sheet dismiss
    fun handleDismissBottomSheet() {
        scope.launch {
            sheetState.hide()
        }.invokeOnCompletion {
            if (!sheetState.isVisible) {
                showBottomSheet = false
                selectedWarehouseForProducts = null
                viewModel.resetProductsState()
            }
        }
    }

    DrawerContainer(navController = navController) { openDrawer ->
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Dashboard de Almacenes") },
                    navigationIcon = {
                        IconButton(onClick = { openDrawer() }) {
                            Icon(
                                imageVector = Icons.Default.Menu,
                                contentDescription = "Menú"
                            )
                        }
                    },
                    actions = {
                        IconButton(onClick = { viewModel.refreshWarehouses() }) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "Refrescar"
                            )
                        }
                    }
                )
            },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { onCreateTransferClick(0) },
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text("Nuevo Traspaso") }
            )
        }
    ) { paddingValues ->
        Box(
            modifier = modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            when (val state = warehousesState) {
                is ResultState.Loading -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                }

                is ResultState.Success -> {
                    val warehouses = if (searchQuery.isBlank()) {
                        state.data
                    } else {
                        state.data.filter { warehouse ->
                            val assignedUsers = usersByWarehouse[warehouse.ALMACEN_ID] ?: emptyList()
                            val matchesWarehouse = warehouse.ALMACEN.contains(searchQuery, ignoreCase = true)
                            val matchesVendor = assignedUsers.any { user ->
                                user.NOMBRE.contains(searchQuery, ignoreCase = true)
                            }
                            matchesWarehouse || matchesVendor
                        }
                    }

                    Column(
                        modifier = Modifier.fillMaxSize()
                    ) {
                        // Search bar
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            placeholder = { Text("Buscar almacén o vendedor...") },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.Search,
                                    contentDescription = "Buscar"
                                )
                            },
                            singleLine = true
                        )

                            if (warehouses.isEmpty()) {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = if (searchQuery.isBlank()) "No hay almacenes disponibles" else "No se encontraron almacenes",
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        } else {
                            LazyVerticalGrid(
                                columns = GridCells.Fixed(if (isTablet) 2 else 1),
                                contentPadding = PaddingValues(16.dp),
                                horizontalArrangement = Arrangement.spacedBy(16.dp),
                                verticalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                items(warehouses, key = { it.ALMACEN_ID }) { warehouse ->
                                    WarehouseCard(
                                        warehouse = warehouse,
                                        assignedUsers = usersByWarehouse[warehouse.ALMACEN_ID] ?: emptyList(),
                                        onCreateTransferClick = onCreateTransferClick,
                                        onViewProductsClick = { handleViewProducts(warehouse) }
                                    )
                                }
                            }
                        }
                    }
                }

                is ResultState.Error -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "Error al cargar almacenes",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.error
                            )
                            Text(
                                text = state.message,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                ResultState.Idle -> {
                    // Initial state
                }
            }
        }
    }

    // Bottom Sheet for viewing products
    selectedWarehouseForProducts?.let { warehouse ->
        if (showBottomSheet) {
            WarehouseProductsBottomSheet(
                warehouseName = warehouse.ALMACEN,
                totalStock = warehouse.EXISTENCIAS,
                assignedUsers = usersByWarehouse[warehouse.ALMACEN_ID] ?: emptyList(),
                productsState = warehouseProductsState,
                sheetState = sheetState,
                onDismiss = { handleDismissBottomSheet() }
            )
        }
    }
    }
}

/**
 * Warehouse card showing stock information
 */
@Composable
private fun WarehouseCard(
    warehouse: WarehouseListResponse.Warehouse,
    assignedUsers: List<com.example.msp_app.data.models.auth.User>,
    onCreateTransferClick: (Int) -> Unit,
    onViewProductsClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Header: warehouse name and stock in same row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = warehouse.ALMACEN,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "ID: ${warehouse.ALMACEN_ID}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // Stock badge
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "${warehouse.EXISTENCIAS}",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Text(
                            text = "productos",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
            }

            // Assigned vendors (more compact)
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "Vendedores:",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.Medium
                )

                if (assignedUsers.isEmpty()) {
                    Text(
                        text = "Sin asignar",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                    )
                } else {
                    Text(
                        text = assignedUsers.joinToString(", ") { it.NOMBRE },
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            // Actions
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    onClick = onViewProductsClick,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Ver Productos")
                }

                Button(
                    onClick = { onCreateTransferClick(warehouse.ALMACEN_ID) },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.Default.ArrowForward,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = Color.White
                    )
                    Spacer(modifier = Modifier.size(8.dp))
                    Text(
                        text = "Crear Traspaso",
                        color = Color.White
                    )
                }
            }
        }
    }
}
