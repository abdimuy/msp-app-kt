package com.example.msp_app.features.sales.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import androidx.navigation.NavHostController
import com.example.msp_app.core.utils.ResultState
import com.example.msp_app.data.models.sale.Sale
import com.example.msp_app.features.products.viewmodels.ProductsViewModel
import com.example.msp_app.features.sales.components.CustomMap
import com.example.msp_app.features.sales.components.paymentshistorysection.PaymentsHistory
import com.example.msp_app.features.sales.components.saleactionssection.SaleActionSection
import com.example.msp_app.features.sales.components.saleclientdetailssection.SaleClientDetailsSection
import com.example.msp_app.features.sales.components.saleproductssection.SaleProductsSection
import com.example.msp_app.features.sales.viewmodels.SaleDetailsViewModel
import com.example.msp_app.navigation.Screen


@Composable
fun SaleDetailsScreen(
    saleId: Int,
    navController: NavHostController,
) {
    val viewModel: SaleDetailsViewModel = viewModel()
    val state by viewModel.saleState.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.loadSaleDetails(saleId)
    }

    Scaffold { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            when (val result = state) {
                is ResultState.Idle -> {
                    Text("No hay datos")
                }

                is ResultState.Loading -> {
                    Box(
                        modifier = Modifier
                            .fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator()
                    }
                }

                is ResultState.Error -> {
                    Text("Error: ${result.message}")
                }

                is ResultState.Success -> {
                    val sale = result.data
                    if (sale != null) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .verticalScroll(rememberScrollState())
                        ) {
                            SaleDetailsContent(sale = sale, navController = navController)
                        }

                    } else {
                        Text("No se encontró la venta")
                    }
                }
            }
        }
    }
}

@Composable
fun SaleDetailsContent(
    sale: Sale,
    navController: NavController,
) {
    val isDark = isSystemInDarkTheme()

    val productsViewModel: ProductsViewModel = viewModel()
    val productsState by productsViewModel.productsByFolioState.collectAsState()

    LaunchedEffect(sale.FOLIO) {
        productsViewModel.getProductsByFolio(sale.FOLIO)
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .padding(bottom = 20.dp)
            .background(
                MaterialTheme.colorScheme.background
            )
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(850.dp)
        ) {
            CustomMap(
                onClick = {
                    navController.navigate(Screen.Map.route)
                }
            )

            SaleClientDetailsSection(sale, modifier = Modifier.align(Alignment.BottomCenter))
        }

        Spacer(modifier = Modifier.height(24.dp))
        OutlinedCard(
            elevation = CardDefaults.cardElevation(defaultElevation = if (isDark) 0.dp else 6.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.background
            ),
            border = BorderStroke(
                width = 1.dp,
                color = if (isDark) Color.Gray else Color.Transparent
            ),
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .background(Color.White, RoundedCornerShape(16.dp))
        ) {

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 24.dp, horizontal = 16.dp)
            ) {
                Text(
                    text = "Productos",
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(16.dp))
                when (val result = productsState) {
                    is ResultState.Idle -> {
                        Text("No hay productos")
                    }

                    is ResultState.Loading -> {
                        Text("Cargando productos...")
                    }

                    is ResultState.Error -> {
                        Text("Error: ${result.message}")
                    }

                    is ResultState.Success -> {
                        if (result.data.isEmpty()) {
                            Text("No se encontraron productos para esta venta")
                        } else {
                            SaleProductsSection(products = result.data)
                        }
                    }
                }

            }
        }

        Spacer(modifier = Modifier.height(24.dp))
        TextButton(
            onClick = { },
            modifier = Modifier
                .background(
                    MaterialTheme.colorScheme.primary,
                    shape = RoundedCornerShape(8.dp)
                )
                .height(40.dp)
                .fillMaxWidth(0.92f)
        ) {
            Text(
                "INICIAR GARANTIA",
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 20.sp
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        SaleActionSection()

        Spacer(modifier = Modifier.height(15.dp))
        Column(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .background(Color.Transparent, RoundedCornerShape(16.dp))
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(
                    modifier = Modifier.height(12.dp)
                )
                Text(
                    "Historial de pagos",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    fontSize = 22.sp,
                    textAlign = TextAlign.Left,
                )

                PaymentsHistory(sale)
            }
        }
    }
}