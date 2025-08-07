package com.example.msp_app.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.example.msp_app.R
import com.example.msp_app.core.context.LocalAuthViewModel
import com.example.msp_app.core.utils.ResultState
import com.example.msp_app.data.models.auth.User
import com.example.msp_app.ui.theme.ThemeController
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

    val userDataState by authViewModel.userData.collectAsState()
    val userData = when (userDataState) {
        is ResultState.Success -> (userDataState as ResultState.Success<User?>).data
        else -> null
    }

    var showLogoutDialog by remember { mutableStateOf(false) }

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
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Menú",
                                style = MaterialTheme.typography.titleLarge,
                            )
                            IconButton(onClick = { ThemeController.toggle() }) {
                                Image(
                                    painter = painterResource(
                                        id = if (ThemeController.isDarkMode)
                                            R.drawable.light_mode_24px
                                        else
                                            R.drawable.dark_mode_24px
                                    ),
                                    contentDescription = "Cambiar tema",
                                    modifier = Modifier.size(32.dp),
                                    colorFilter = if (!ThemeController.isDarkMode)
                                        ColorFilter.tint(Color.Gray)
                                    else
                                        null
                                )
                            }
                        }
                        HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp))

                        val modulos = userData?.MODULOS ?: emptyList()
                        val hasCobro = modulos.contains("COBRO")
                        val hasVentas = modulos.contains("VENTAS")


                        if (hasCobro) {

                            NavigationDrawerItem(
                                label = { Text("Inicio") },
                                selected = false,
                                onClick = {
                                    scope.launch {
                                        drawerState.close()
                                        navController.navigate("home") {
                                            popUpTo("home") { inclusive = true }
                                        }
                                    }
                                }
                            )

                            NavigationDrawerItem(
                                label = { Text("Clientes") },
                                selected = false,
                                onClick = {
                                    scope.launch {
                                        drawerState.close()
                                        navController.navigate("sales") {
                                            popUpTo("sales") { inclusive = true }
                                        }
                                    }
                                }
                            )

                            NavigationDrawerItem(
                                label = { Text("Reportes Diarios") },
                                selected = false,
                                onClick = {
                                    scope.launch {
                                        drawerState.close()
                                        navController.navigate("daily_reports") {
                                            popUpTo("home")
                                        }
                                    }
                                }
                            )

                            NavigationDrawerItem(
                                label = { Text("Reportes Semanales") },
                                selected = false,
                                onClick = {
                                    scope.launch {
                                        drawerState.close()
                                        navController.navigate("weekly_reports") {
                                            popUpTo("home")
                                        }
                                    }
                                }
                            )

                            NavigationDrawerItem(
                                label = { Text("Mapa de rutas") },
                                selected = false,
                                onClick = {
                                    scope.launch {
                                        drawerState.close()
                                        navController.navigate("route_map") {
                                            popUpTo("home")
                                        }
                                    }
                                }
                            )
                        }

                        if (hasVentas) {
                            NavigationDrawerItem(
                                label = { Text("Catalogo de Productos") },
                                selected = false,
                                onClick = {
                                    scope.launch {
                                        drawerState.close()
                                        navController.navigate("products_catalog") {
                                            popUpTo("home")
                                        }
                                    }
                                }
                            )
                            
                        }
                        NavigationDrawerItem(
                            label = { Text("Ventas") },
                            selected = false,
                            onClick = {
                                scope.launch {
                                    drawerState.close()
                                    navController.navigate("sale_home") {
                                        popUpTo("home")
                                    }
                                }
                            }
                        )
                    }

                    NavigationDrawerItem(
                        label = { Text("Cerrar sesión") },
                        selected = false,
                        onClick = { showLogoutDialog = true },
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                }
            }
        }
    ) {
        content { scope.launch { drawerState.open() } }
    }

    if (showLogoutDialog) {
        AlertDialog(
            onDismissRequest = { showLogoutDialog = false },
            title = { Text("Cerrar sesión") },
            text = { Text("¿Estás seguro de que deseas cerrar sesión?") },
            confirmButton = {
                TextButton(onClick = {
                    showLogoutDialog = false
                    authViewModel.logout()
                }) {
                    Text("Sí")
                }
            },
            dismissButton = {
                TextButton(onClick = { showLogoutDialog = false }) {
                    Text("No")
                }
            }
        )
    }
}
