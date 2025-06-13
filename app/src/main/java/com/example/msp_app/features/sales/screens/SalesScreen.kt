package com.example.msp_app.features.sales.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.example.msp_app.components.DrawerContainer
import com.example.msp_app.core.utils.ResultState
import androidx.compose.ui.Alignment
import com.example.msp_app.data.models.sale.Sale
import com.example.msp_app.features.sales.components.sale_item.SaleItem
import com.example.msp_app.features.sales.viewmodels.SalesViewModel
import androidx.lifecycle.viewmodel.compose.viewModel

@Composable
fun SalesScreen(
    navController: NavController
) {
    val viewModel: SalesViewModel = viewModel()
    val state by viewModel.salesState.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.loadLocalSales()
    }

    DrawerContainer(navController = navController) { openDrawer ->

        Scaffold(
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
                            LazyColumn {
                                items(sales, key={ it.DOCTO_CC_ID }) { sale ->
                                    SaleItem(sale = sale,
                                        onClick = {
                                            navController.navigate("sales/sale_details/${sale.DOCTO_CC_ID}")
                                        }
                                    )
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

