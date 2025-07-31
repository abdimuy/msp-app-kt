package com.example.msp_app.features.productsInventory.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.example.msp_app.core.utils.ResultState
import com.example.msp_app.features.productsInventory.viewmodels.ProductsInventoryViewModel

@Composable
fun BlankScreen(navController: NavController) {
    val viewModel: ProductsInventoryViewModel = viewModel()
    val productState = viewModel.productInventoryState.collectAsState().value

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Button(
            onClick = { viewModel.fetchRemoteInventory() },
            modifier = Modifier.fillMaxWidth(0.92f)
        ) {
            Text("ACTUALIZAR CATALOGO", color = Color.White)
        }

        if (productState is ResultState.Loading) {
            CircularProgressIndicator(
                modifier = Modifier
                    .padding(8.dp)
            )
        }
    }
}
