package com.example.msp_app.features.sales.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SecondaryTabRow
import androidx.compose.material3.Tab
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
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.example.msp_app.components.DrawerContainer
import com.example.msp_app.core.utils.ResultState
import com.example.msp_app.data.models.sale.EstadoCobranza
import com.example.msp_app.data.models.sale.Sale
import com.example.msp_app.features.sales.components.sale_item.SaleItem
import com.example.msp_app.features.sales.viewmodels.SalesViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SalesScreen(
    navController: NavController
) {
    val viewModel: SalesViewModel = viewModel()
    val state by viewModel.salesState.collectAsState()

    var selectedTabIndex by remember { mutableStateOf(0) }
    var query by remember { mutableStateOf("") }
    val focusManager = LocalFocusManager.current

    LaunchedEffect(Unit) {
        viewModel.getLocalSales()
    }

    DrawerContainer(navController = navController) { openDrawer ->
        Scaffold(
            bottomBar = {
                val sales = (state as? ResultState.Success<List<Sale>>)?.data ?: emptyList()
                val filteredSales = sales.filter { it.CLIENTE.contains(query, ignoreCase = true) }

                val salesToVisit = filteredSales.filter {
                    it.ESTADO_COBRANZA == EstadoCobranza.VOLVER_VISITAR ||
                            it.ESTADO_COBRANZA == EstadoCobranza.PENDIENTE ||
                            it.ESTADO_COBRANZA == EstadoCobranza.VISITADO
                }
                val visitedSales =
                    filteredSales.filter { it.ESTADO_COBRANZA == EstadoCobranza.NO_PAGADO }
                val paidSale = filteredSales.filter { it.ESTADO_COBRANZA == EstadoCobranza.PAGADO }

                val tabTitles = listOf(
                    "POR VISITAR(${salesToVisit.size})",
                    "VISITADOS(${visitedSales.size})",
                    "PAGADOS(${paidSale.size})"
                )

                SecondaryTabRow(
                    modifier = Modifier
                        .navigationBarsPadding(),
                    selectedTabIndex = selectedTabIndex,
                    containerColor = MaterialTheme.colorScheme.background,
                    contentColor = MaterialTheme.colorScheme.onSurface,
                    divider = {}
                ) {
                    tabTitles.forEachIndexed { index, title ->
                        Tab(
                            selected = selectedTabIndex == index,
                            onClick = { selectedTabIndex = index },
                            selectedContentColor = MaterialTheme.colorScheme.primary,
                            unselectedContentColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                        ) {
                            Text(
                                text = title,
                                modifier = Modifier.padding(25.dp),
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                }
            },
            content = { innerPadding ->
                Column(
                    modifier = Modifier
                        .statusBarsPadding()
                        .padding(bottom = innerPadding.calculateBottomPadding())
                        .padding(horizontal = 8.dp)
                        .fillMaxSize()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp, top = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = {
                                if (query.isNotEmpty()) {
                                    query = ""
                                    focusManager.clearFocus()
                                } else {
                                    openDrawer()
                                }
                            },
                            modifier = Modifier.padding(end = 8.dp)
                        ) {
                            if (query.isNotEmpty()) {
                                Icon(
                                    Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = "Atrás"
                                )
                            } else {
                                Icon(Icons.Default.Menu, contentDescription = "Menú")
                            }
                        }

                        OutlinedTextField(
                            value = query,
                            onValueChange = { query = it },
                            modifier = Modifier
                                .weight(1f)
                                .height(50.dp),
                            placeholder = { Text("Buscar venta...") },
                            singleLine = true,
                            shape = RoundedCornerShape(25.dp),
                            leadingIcon = {
                                Icon(Icons.Default.Search, contentDescription = null)
                            },
                            trailingIcon = {
                                if (query.isNotEmpty()) {
                                    IconButton(onClick = { query = "" }) {
                                        Icon(
                                            Icons.Default.Close,
                                            contentDescription = "Borrar texto"
                                        )
                                    }
                                } else {
                                    Icon(Icons.Default.MoreVert, contentDescription = null)
                                }
                            }
                        )
                    }

                    when (state) {
                        is ResultState.Idle -> Text("No hay datos")
                        is ResultState.Loading -> Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) { CircularProgressIndicator() }

                        is ResultState.Success -> {
                            val sales = (state as ResultState.Success<List<Sale>>).data
                            val filteredSales =
                                sales.filter { it.CLIENTE.contains(query, ignoreCase = true) }

                            val salesToVisit = filteredSales.filter {
                                it.ESTADO_COBRANZA == EstadoCobranza.VOLVER_VISITAR ||
                                        it.ESTADO_COBRANZA == EstadoCobranza.PENDIENTE ||
                                        it.ESTADO_COBRANZA == EstadoCobranza.VISITADO
                            }
                            val visitedSales =
                                filteredSales.filter { it.ESTADO_COBRANZA == EstadoCobranza.NO_PAGADO }
                            val paidSale =
                                filteredSales.filter { it.ESTADO_COBRANZA == EstadoCobranza.PAGADO }

                            val currentList = when (selectedTabIndex) {
                                0 -> salesToVisit
                                1 -> visitedSales
                                else -> paidSale
                            }

                            if (currentList.isEmpty()) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(horizontal = 32.dp, vertical = 64.dp)
                                        .background(
                                            color = MaterialTheme.colorScheme.surfaceVariant,
                                            shape = RoundedCornerShape(8.dp)
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "No hay ventas en esta pestaña",
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                }
                            } else {
                                LazyColumn(modifier = Modifier.fillMaxSize()) {
                                    items(currentList, key = { it.DOCTO_CC_ID }) { sale ->
                                        SaleItem(
                                            sale = sale,
                                            onClick = {
                                                navController.navigate("sales/sale_details/${sale.DOCTO_CC_ID}")
                                            }
                                        )
                                    }
                                }
                            }
                        }

                        is ResultState.Error -> {
                            val msg = (state as ResultState.Error).message
                            Text("Error: $msg", color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }
        )
    }
}
