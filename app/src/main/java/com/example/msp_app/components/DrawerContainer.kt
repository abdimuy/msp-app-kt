package com.example.msp_app.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.example.msp_app.core.context.LocalAuthViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DrawerContainer(
    navController: NavController,
    content: @Composable (openDrawer: () -> Unit) -> Unit,
) {
    val authViewModel = LocalAuthViewModel.current
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet {
                Column(
                    modifier = Modifier
                        .fillMaxHeight()
                        .padding(vertical = 16.dp),
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text(
                            text = "Menú",
                            style = MaterialTheme.typography.titleLarge,
                            modifier = Modifier.padding(start = 16.dp, bottom = 10.dp)
                        )
                        HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp))
                        NavigationDrawerItem(
                            label = { Text("Inicio") },
                            selected = true,
                            onClick = {
                                navController.navigate("home") {
                                    popUpTo("home") { inclusive = true }
                                }
                            }
                        )

                        NavigationDrawerItem(
                            label = { Text("Clientes") },
                            selected = true,
                            onClick = {
                                navController.navigate("sales") {
                                    popUpTo("sales") { inclusive = true }
                                }
                            }
                        )

                        NavigationDrawerItem(
                            label = { Text("Reportes Diarios") },
                            selected = true,
                            onClick = {
                                navController.navigate("daily reports") {
                                    popUpTo("Home")
                                }
                            }
                        )
                    }

                    NavigationDrawerItem(
                        label = { Text("Cerrar sesión") },
                        selected = false,
                        onClick = { authViewModel.logout() },
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                }
            }
        }
    ) {
        content {
            scope.launch { drawerState.open() }
        }
    }
}
