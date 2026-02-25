package com.example.msp_app.features.guarantees.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.example.msp_app.components.DrawerContainer
import com.example.msp_app.navigation.Screen

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WarrantiesHomeScreen(navController: NavController) {
    DrawerContainer(navController = navController) { openDrawer ->
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Garantías") },
                    navigationIcon = {
                        IconButton(onClick = openDrawer) {
                            Icon(
                                imageVector = Icons.Default.Menu,
                                contentDescription = "Menú"
                            )
                        }
                    }
                )
            }
        ) { innerPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Button(
                    onClick = {
                        navController.navigate(Screen.NewWarranty.route)
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Crear garantía")
                }
            }
        }
    }
}
