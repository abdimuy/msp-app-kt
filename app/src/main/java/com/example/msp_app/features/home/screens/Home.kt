package com.example.msp_app.features.home.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.example.msp_app.components.DrawerContainer
import com.example.msp_app.core.context.LocalAuthViewModel
import com.example.msp_app.core.utils.ResultState
import com.example.msp_app.features.sales.viewmodels.SalesViewModel


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(navController: NavController) {
    val authViewModel = LocalAuthViewModel.current
    val user = authViewModel.currentUser.collectAsState().value

    val viewModel: SalesViewModel = viewModel()
    val state by viewModel.salesState.collectAsState()

    DrawerContainer(navController = navController) { openDrawer ->

        Scaffold { innerPadding ->

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(8.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = openDrawer) {
                        Icon(Icons.Default.Menu, contentDescription = "Menú")
                    }

                    Spacer(modifier = Modifier.width(20.dp))

                    Column {
                        Text(
                            text = "Hola,",
                            style = MaterialTheme.typography.titleSmall
                        )
                        Text(
                            text = user?.email ?: "Usuario no encontrado",
                            style = MaterialTheme.typography.titleLarge
                        )
                    }
                }

                Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
                    Button(
                        onClick = { viewModel.syncSales() }
                    ) {
                        Text("Descargar ventas")
                    }

                    when (state) {
                        is ResultState.Idle -> {
                            Text("Presiona el botón para descargar ventas")
                        }
                        is ResultState.Loading -> Text("Cargando ventas...")
                        is ResultState.Success -> Text("Ventas descargadas: ${(state as ResultState.Success<List<*>>).data.size}")
                        is ResultState.Error -> Text("Error: ${(state as ResultState.Error).message}")
                    }
                }

            }

        }
    }
}

