package com.example.msp_app.features.transfers.presentation.list

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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.example.msp_app.components.DrawerContainer
import com.example.msp_app.core.utils.ResultState
import com.example.msp_app.data.api.services.warehouses.WarehouseListResponse

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
    var searchQuery by rememberSaveable { mutableStateOf("") }

    val configuration = LocalConfiguration.current
    val isTablet = configuration.screenWidthDp >= 600

    // Refresh warehouses when screen is displayed
    LaunchedEffect(Unit) {
        viewModel.refreshWarehouses()
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
                            warehouse.ALMACEN.contains(searchQuery, ignoreCase = true)
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
                            placeholder = { Text("Buscar almacén...") },
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
                                        onCreateTransferClick = onCreateTransferClick
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
    }
}

/**
 * Warehouse card showing stock information
 */
@Composable
private fun WarehouseCard(
    warehouse: WarehouseListResponse.Warehouse,
    onCreateTransferClick: (Int) -> Unit,
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
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // Header with warehouse name
            Column(
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = warehouse.ALMACEN,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "ID: ${warehouse.ALMACEN_ID}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Stock total
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Stock Total",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "${warehouse.EXISTENCIAS}",
                        style = MaterialTheme.typography.displaySmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "productos",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // Stock status indicator
                Column(
                    horizontalAlignment = Alignment.End
                ) {
                    val stockLevel = when {
                        warehouse.EXISTENCIAS == 0 -> "Sin stock"
                        warehouse.EXISTENCIAS < 50 -> "Stock bajo"
                        warehouse.EXISTENCIAS < 200 -> "Stock medio"
                        else -> "Stock alto"
                    }

                    val statusColor = when {
                        warehouse.EXISTENCIAS == 0 -> MaterialTheme.colorScheme.error
                        warehouse.EXISTENCIAS < 50 -> MaterialTheme.colorScheme.error
                        warehouse.EXISTENCIAS < 200 -> MaterialTheme.colorScheme.tertiary
                        else -> MaterialTheme.colorScheme.primary
                    }

                    Text(
                        text = stockLevel,
                        style = MaterialTheme.typography.labelLarge,
                        color = statusColor,
                        fontWeight = FontWeight.Medium
                    )

                    Spacer(modifier = Modifier.height(8.dp))
                    LinearProgressIndicator(
                        progress = {
                            (warehouse.EXISTENCIAS.toFloat() / 500f).coerceIn(0f, 1f)
                        },
                        modifier = Modifier.size(width = 100.dp, height = 6.dp),
                        color = statusColor,
                    )
                }
            }

            // Actions
            Button(
                onClick = { onCreateTransferClick(warehouse.ALMACEN_ID) },
                modifier = Modifier.fillMaxWidth()
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
