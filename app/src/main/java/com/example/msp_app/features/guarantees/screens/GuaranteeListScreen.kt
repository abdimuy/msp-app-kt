package com.example.msp_app.features.guarantees.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.example.msp_app.components.DrawerContainer
import com.example.msp_app.features.guarantees.screens.components.GuaranteeListItem
import com.example.msp_app.features.guarantees.screens.viewmodels.GuaranteeListViewModel
import com.example.msp_app.navigation.Screen

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GuaranteeListScreen(navController: NavController) {
    val viewModel: GuaranteeListViewModel = viewModel()
    val guarantees by viewModel.guarantees.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.loadGuarantees()
    }

    DrawerContainer(navController = navController) { openDrawer ->
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Garantías") },
                    navigationIcon = {
                        IconButton(onClick = openDrawer) {
                            Icon(Icons.Default.Menu, contentDescription = "Menú")
                        }
                    }
                )
            },
            floatingActionButton = {
                ExtendedFloatingActionButton(
                    onClick = { navController.navigate(Screen.CreateGuarantee.route) },
                    icon = { Icon(Icons.Default.Add, contentDescription = null) },
                    text = { Text("Nueva garantía") }
                )
            }
        ) { innerPadding ->
            if (guarantees.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No hay garantías registradas",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(guarantees) { guarantee ->
                        GuaranteeListItem(
                            guarantee = guarantee,
                            onClick = {
                                navController.navigate(
                                    Screen.GuaranteeDetail.createRoute(guarantee.ID)
                                )
                            }
                        )
                    }
                }
            }
        }
    }
}
