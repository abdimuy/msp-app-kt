package com.example.msp_app.navigation

import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.msp_app.components.ModernSpinner
import com.example.msp_app.core.context.LocalAuthViewModel
import com.example.msp_app.core.utils.ResultState
import com.example.msp_app.data.models.auth.User
import com.example.msp_app.features.auth.screens.LoginScreen
import com.example.msp_app.features.auth.viewModels.AuthViewModel
import com.example.msp_app.features.cart.screens.CartScreen
import com.example.msp_app.features.common.NoModulesScreen
import com.example.msp_app.features.guarantees.screens.GuaranteeScreen
import com.example.msp_app.features.home.screens.HomeScreen
import com.example.msp_app.features.payments.screens.DailyReportScreen
import com.example.msp_app.features.payments.screens.PaymentTicketScreen
import com.example.msp_app.features.payments.screens.WeeklyReportScreen
import com.example.msp_app.features.productsInventory.screens.ProductDetailsScreen
import com.example.msp_app.features.productsInventory.screens.ProductsCatalogScreen
import com.example.msp_app.features.productsInventory.screens.SaleHomeScreen
import com.example.msp_app.features.productsInventory.screens.ProductDetailsScreen
import com.example.msp_app.features.routes.screens.RouteMapScreen
import com.example.msp_app.features.sales.screens.NewSaleScreen
import com.example.msp_app.features.sales.screens.SaleDescriptionScreen
import com.example.msp_app.features.sales.screens.SaleDetailsListScreen
import com.example.msp_app.features.sales.screens.SaleDetailsScreen
import com.example.msp_app.features.sales.screens.SaleMapScreen
import com.example.msp_app.features.sales.screens.SalesScreen
import com.example.msp_app.features.visit.screens.VisitTicketScreen
import com.example.msp_app.features.transfers.presentation.list.TransfersListScreen
import com.example.msp_app.features.transfers.presentation.list.TransfersListViewModel
import com.example.msp_app.features.transfers.presentation.create.NewTransferScreen
import com.example.msp_app.features.transfers.presentation.create.NewTransferViewModel
import com.example.msp_app.features.transfers.presentation.detail.TransferDetailScreen
import com.example.msp_app.features.transfers.presentation.detail.TransferDetailViewModel


sealed class Screen(val route: String) {
    object Loading : Screen("loading")
    object NoModules : Screen("no_modules")
    object Login : Screen("login")
    object Home : Screen("home")
    object Sales : Screen("sales")
    object SaleDetails : Screen("sales/sale_details/{saleId}") {
        fun createRoute(saleId: Int) = "sales/sale_details/$saleId"
    }

    object DailyReport : Screen("daily_reports")
    object WeeklyReport : Screen("weekly_reports")

    object SaleMap : Screen("sales/sale_details/map/{saleId}") {
        fun createRoute(saleId: Int) = "sales/sale_details/map/$saleId"
    }

    object PaymentTicket : Screen("payment_ticket/{paymentId}") {
        fun createRoute(paymentId: String) = "payment_ticket/$paymentId"
    }

    object VisitTicket : Screen("visit_ticket/{saleId}") {
        fun createRoute(saleId: String) = "visit_ticket/$saleId"
    }

    object Guarantee : Screen("guarantee/{saleId}") {
        fun createRoute(saleId: String) = "guarantee/$saleId"
    }

    object RouteMap : Screen("route_map")
    object ProductsCatalog : Screen("products_catalog")
    object SaleHome : Screen("sale_home")

    object ProductDetails : Screen("productDetails/{productId}") {
        fun createRoute(productId: String) = "productDetails/$productId"
    }

    object NewSale : Screen("new_sale")
    object SaleDetailsList : Screen("sales/details_list")

    object SaleDescripction : Screen("saleDescription/{localSaleId}") {
        fun creatRoute(localSaleId: String) = "saleDescription/$localSaleId"
    }

    object Cart : Screen("cart")

    // Transfers routes
    object TransfersList : Screen("transfers")
    object TransferDetail : Screen("transfers/{transferId}") {
        fun createRoute(transferId: Int) = "transfers/$transferId"
    }
    object NewTransfer : Screen("transfers/new?warehouseId={warehouseId}") {
        fun createRoute(warehouseId: Int = 0) = "transfers/new?warehouseId=$warehouseId"
    }
}

@RequiresApi(Build.VERSION_CODES.S)
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppNavigation() {
    val authViewModel: AuthViewModel = viewModel()
    val currentUser by authViewModel.currentUser.collectAsState()
    val userDataState by authViewModel.userData.collectAsState()
    val navController = rememberNavController()

    CompositionLocalProvider(LocalAuthViewModel provides authViewModel) {
        NavHost(
            navController = navController,
            startDestination = Screen.Loading.route
        ) {
            composable(Screen.Loading.route) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    ModernSpinner(size = 80.dp)
                }

                LaunchedEffect(currentUser, userDataState) {
                    when {
                        currentUser == null -> {
                            navController.navigate(Screen.Login.route) {
                                popUpTo(Screen.Loading.route) { inclusive = true }
                            }
                        }

                        userDataState is ResultState.Success -> {
                            val userData = (userDataState as ResultState.Success<User?>).data
                            val modulos = userData?.MODULOS ?: emptyList()

                            val destination = when {
                                modulos.isEmpty() -> Screen.NoModules.route
                                modulos.contains("COBRO") -> Screen.Home.route          // Priority 1
                                modulos.contains("VENTAS") -> Screen.SaleHome.route     // Priority 2
                                modulos.contains("ALMACEN") -> Screen.TransfersList.route // Priority 3
                                else -> Screen.NoModules.route
                            }

                            navController.navigate(destination) {
                                popUpTo(Screen.Loading.route) { inclusive = true }
                            }
                        }
                    }
                }
            }

            composable(Screen.NoModules.route) {
                NoModulesScreen(navController = navController)
            }

            composable(Screen.Login.route) {
                LoginScreen(
                    onLoginSuccess = {
                        navController.navigate(Screen.Loading.route) {
                            popUpTo(Screen.Login.route) { inclusive = true }
                        }
                    }
                )
            }

            composable(Screen.Home.route) {
                HomeScreen(navController = navController)
            }

            composable(Screen.Sales.route) {
                SalesScreen(navController = navController)
            }

            composable(Screen.SaleDetails.route) { backStackEntry ->
                val saleId = backStackEntry.arguments?.getString("saleId")?.toIntOrNull()
                if (saleId != null) {
                    SaleDetailsScreen(
                        saleId = saleId,
                        navController = navController
                    )
                } else {
                    // Handle the case where saleId is null, maybe show an error or navigate back
                    navController.navigate(Screen.Sales.route) {
                        popUpTo(Screen.Sales.route) { inclusive = true }
                    }
                }
            }

            composable(Screen.DailyReport.route) {
                DailyReportScreen(navController = navController)
            }

            composable(Screen.WeeklyReport.route) {
                WeeklyReportScreen(navController = navController)
            }

            composable(Screen.SaleMap.route) { backStackEntry ->
                val saleId = backStackEntry.arguments?.getString("saleId")?.toIntOrNull()
                if (saleId != null) {
                    SaleMapScreen(navController = navController, saleId = saleId)
                }
            }


            composable(Screen.PaymentTicket.route) { backStackEntry ->
                val paymentId = backStackEntry.arguments?.getString("paymentId")
                if (paymentId != null) {
                    PaymentTicketScreen(
                        paymentId = paymentId,
                        navController = navController
                    )
                }
            }

            composable(Screen.VisitTicket.route) { backStackEntry ->
                val saleIdString = backStackEntry.arguments?.getString("saleId")
                val saleId = saleIdString?.toIntOrNull()
                if (saleId != null) {
                    VisitTicketScreen(saleId = saleId, navController = navController)
                }
            }

            composable(Screen.Guarantee.route) { backStackEntry ->
                val saleIdString = backStackEntry.arguments?.getString("saleId")
                val saleId = saleIdString?.toIntOrNull()
                if (saleId != null) {
                    GuaranteeScreen(saleId = saleId, navController = navController)
                }
            }

            composable(Screen.RouteMap.route) {
                RouteMapScreen(navController = navController)
            }

            composable(Screen.ProductsCatalog.route) {
                ProductsCatalogScreen(navController = navController)
            }

            composable(Screen.SaleHome.route) {
                SaleHomeScreen(navController = navController)
            }

            composable("productDetails/{productId}") { backStackEntry ->
                val productId = backStackEntry.arguments?.getString("productId")
                ProductDetailsScreen(
                    productId = productId.toString(),
                    navController = navController
                )
            }

            composable(Screen.NewSale.route) {
                NewSaleScreen(navController = navController)
            }

            composable(Screen.SaleDetailsList.route) {
                SaleDetailsListScreen(navController = navController)
            }

            composable("saleDescription/{localSaleId}") { backStackEntry ->
                val localSaleId = backStackEntry.arguments?.getString("localSaleId")
                SaleDescriptionScreen(
                    localSaleId = localSaleId.toString(),
                    navController = navController
                )
            }
            composable(Screen.Cart.route) {
                CartScreen(
                    navController = navController,
                )
            }

            // Transfers routes
            composable(Screen.TransfersList.route) {
                val viewModel: TransfersListViewModel = viewModel()
                TransfersListScreen(
                    viewModel = viewModel,
                    navController = navController,
                    onTransferClick = { transferId ->
                        navController.navigate(Screen.TransferDetail.createRoute(transferId))
                    },
                    onCreateTransferClick = { warehouseId ->
                        navController.navigate(Screen.NewTransfer.createRoute(warehouseId))
                    }
                )
            }

            composable(Screen.NewTransfer.route) { backStackEntry ->
                val warehouseId = backStackEntry.arguments?.getString("warehouseId")?.toIntOrNull() ?: 0
                val viewModel: NewTransferViewModel = viewModel()
                NewTransferScreen(
                    viewModel = viewModel,
                    navController = navController,
                    preselectedWarehouseId = warehouseId
                )
            }

            composable(Screen.TransferDetail.route) { backStackEntry ->
                val transferId = backStackEntry.arguments?.getString("transferId")?.toIntOrNull()
                if (transferId != null) {
                    val viewModel: TransferDetailViewModel = viewModel()
                    TransferDetailScreen(
                        transferId = transferId,
                        viewModel = viewModel,
                        navController = navController
                    )
                }
            }
        }
    }
}

