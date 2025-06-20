package com.example.msp_app.features.sales.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
    val tabTitles =
        listOf("POR VISITAR($totalCount)", "VISITADOS($totalCount)", "PAGADOS($totalCount)")

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
                        .padding(innerPadding)
                        .padding(horizontal = 8.dp)
                        .fillMaxSize()
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Start
                    ) {
                        IconButton(onClick = openDrawer) {
                            Icon(Icons.Default.Menu, contentDescription = "Menú")
                        }
                    }

                    when (state) {
                        is ResultState.Idle -> {
                            // No hay nada que mostrar
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
                            key(selectedTabIndex) {
                                LazyColumn {
                                    items(sales, key = { it.DOCTO_CC_ID }) { sale ->
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

