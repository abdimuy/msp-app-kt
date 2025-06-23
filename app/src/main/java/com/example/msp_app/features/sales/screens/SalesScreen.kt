package com.example.msp_app.features.sales.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.runtime.key
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
import com.example.msp_app.data.models.sale.Sale
import com.example.msp_app.features.sales.components.sale_item.SaleItem
import com.example.msp_app.features.sales.viewmodels.SalesViewModel


@ExperimentalMaterial3Api
@Composable
fun SalesScreen(
    navController: NavController
) {
    val viewModel: SalesViewModel = viewModel()
    val state by viewModel.salesState.collectAsState()

    var selectedTabIndex by remember { mutableStateOf(0) }
    val totalCount = (state as? ResultState.Success<List<Sale>>)?.data?.size ?: 0
    val tabTitles = listOf(
        "POR VISITAR($totalCount)",
        "VISITADOS($totalCount)",
        "PAGADOS($totalCount)"
    )

    var query by remember { mutableStateOf("") }
    val focusManager = LocalFocusManager.current


    LaunchedEffect(Unit) {
        viewModel.loadLocalSales()
    }

    DrawerContainer(navController = navController) { openDrawer ->
        Scaffold(
            bottomBar = {
                SecondaryTabRow(
                    selectedTabIndex = selectedTabIndex,
                    containerColor = MaterialTheme.colorScheme.background,
                    contentColor = MaterialTheme.colorScheme.onSurface,
                    divider = {}) {
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
                            .padding(bottom = 8.dp),
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
                                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = "Atrás"
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Default.Menu,
                                    contentDescription = "Menú"
                                )
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
                                    IconButton(onClick = {
                                        query = ""
                                    }) {
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
                        is ResultState.Idle -> {
                            Text("No hay datos")
                        }

                        is ResultState.Loading -> {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                CircularProgressIndicator()
                            }
                        }

                        is ResultState.Success -> {
                            val sales = (state as ResultState.Success<List<Sale>>).data

                            val filteredSales = sales.filter {
                                it.CLIENTE.contains(query, ignoreCase = true)
                            }

                            key(selectedTabIndex) {
                                LazyColumn(modifier = Modifier.fillMaxSize()) {
                                    items(filteredSales, key = { it.DOCTO_CC_ID }) { sale ->
                                        SaleItem(
                                            sale = sale,
                                            onClick = {
                                                query = sale.CLIENTE
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