package com.example.msp_app.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.foundation.layout.*
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
