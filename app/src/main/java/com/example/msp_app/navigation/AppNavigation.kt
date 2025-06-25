package com.example.msp_app.navigation

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.msp_app.core.context.LocalAuthViewModel
import com.example.msp_app.features.auth.screens.LoginScreen
import com.example.msp_app.features.auth.viewModels.AuthViewModel
import com.example.msp_app.features.home.screens.HomeScreen
import com.example.msp_app.features.payments.screens.DailyReportScreen
import com.example.msp_app.features.sales.screens.SaleDetailsScreen
import com.example.msp_app.features.sales.screens.SalesScreen


sealed class Screen(val route: String) {
    object Login : Screen("login")
    object Home : Screen("home")
    object Sales : Screen("sales")
    object SaleDetails : Screen("sales/sale_details/{saleId}") {
        fun createRoute(saleId: Int) = "sale/sale_details/$saleId"
    }

    object DailyReport : Screen("daily reports")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppNavigation() {
    val authViewModel = remember { AuthViewModel() }
    val currentUser by authViewModel.currentUser.collectAsState()
    val navController = rememberNavController()

    CompositionLocalProvider(LocalAuthViewModel provides authViewModel) {
        NavHost(
            navController = navController,
            startDestination = if (currentUser != null) Screen.Home.route else Screen.Login.route
        ) {
            composable(Screen.Login.route) {
                LoginScreen(
                    onLoginSuccess = {
                        navController.navigate(Screen.Home.route) {
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
                        saleId = saleId
                    )
                } else {
                    // Manejar el caso en que no se proporciona un ID de venta válido
                }
            }

            composable(Screen.DailyReport.route) {
                DailyReportScreen(navController = navController)
            }
        }
    }
}

