package com.example.msp_app.features.productsInventory.screens

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.example.msp_app.components.DrawerContainer
import com.example.msp_app.core.utils.ResultState
import com.example.msp_app.features.productsInventory.viewmodels.ProductsInventoryViewModel
import com.example.msp_app.features.productsInventoryImages.viewmodels.ProductInventoryImagesViewModel
import kotlinx.coroutines.delay

@Composable
fun BlankScreen(navController: NavController) {
    val productsViewModel: ProductsInventoryViewModel = viewModel()
    val imagesViewModel: ProductInventoryImagesViewModel = viewModel()

    val productState = productsViewModel.productInventoryState.collectAsState().value
    val loading = productState is ResultState.Loading
    val newImagesCount by imagesViewModel.newImagesCount.collectAsState()
    val productsLoaded by productsViewModel.productsLoaded.collectAsState()
    val downloadProgress by imagesViewModel.downloadProgress.collectAsState()

    var showConfirmDialog by remember { mutableStateOf(false) }
    var showProgressDialog by remember { mutableStateOf(false) }
    var shouldCheckImages by remember { mutableStateOf(false) }

    val context = LocalContext.current

    LaunchedEffect(productsLoaded) {
        if (productsLoaded) {
            imagesViewModel.checkForNewImages()
        }
    }
    LaunchedEffect(shouldCheckImages) {
        if (shouldCheckImages) {
            delay(500)
            if (newImagesCount > 20) {
                showConfirmDialog = true
            } else if (newImagesCount in 1..20) {
                imagesViewModel.downloadNewImages()
                showProgressDialog = true
                Toast.makeText(
                    context,
                    "Descargando $newImagesCount imágenes automáticamente...",
                    Toast.LENGTH_SHORT
                ).show()
            } else if (newImagesCount == 0) {
                Toast.makeText(
                    context,
                    "No hay imágenes nuevas para descargar.",
                    Toast.LENGTH_SHORT
                ).show()
            }
            shouldCheckImages = false
        }
    }

    LaunchedEffect(downloadProgress) {
        if (downloadProgress >= 100) {
            showProgressDialog = false
            Toast.makeText(context, "Descarga completada.", Toast.LENGTH_SHORT).show()
        }
    }

    DrawerContainer(navController = navController) { openDrawer ->
        Scaffold(
            modifier = Modifier.statusBarsPadding(),
            topBar = {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = openDrawer) {
                        Icon(imageVector = Icons.Default.Menu, contentDescription = "Menú")
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Text("Pantalla", style = MaterialTheme.typography.titleLarge)
                }
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
                        productsViewModel.fetchRemoteInventory()
                        shouldCheckImages = true
                        imagesViewModel.checkForNewImages()
                    },
                    enabled = !loading,
                    modifier = Modifier.fillMaxWidth(0.92f)
                ) {
                    Text("ACTUALIZAR CATALOGO", color = Color.White)
                }
                if (loading) {
                    CircularProgressIndicator(modifier = Modifier.padding(8.dp))
                }
            }
        }
    }

    if (showConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showConfirmDialog = false },
            title = { Text("Sincronizar imágenes") },
            text = {
                Text("Hay $newImagesCount imágenes nuevas. Descargar las imágenes puede consumir tus datos móviles. ¿Deseas continuar?")
            },
            confirmButton = {
                Button(onClick = {
                    showConfirmDialog = false
                    showProgressDialog = true
                    imagesViewModel.downloadNewImages()
                    Toast.makeText(context, "Iniciando descarga...", Toast.LENGTH_SHORT).show()
                }) {
                    Text("Descargar")
                }
            },
            dismissButton = {
                Button(onClick = {
                    showConfirmDialog = false
                }) {
                    Text("Cancelar")
                }
            }
        )
    }

    if (showProgressDialog && downloadProgress in 1..99) {
        AlertDialog(
            onDismissRequest = { },
            title = { Text("Descargando imágenes") },
            text = {
                Column {
                    Text("Descargando imágenes: $downloadProgress%")
                    Spacer(modifier = Modifier.height(8.dp))
                    CircularProgressIndicator(progress = downloadProgress / 100f)
                }
            },
            confirmButton = {},
            dismissButton = {
                Button(
                    onClick = {
                        imagesViewModel.cancelDownload()
                        showProgressDialog = false
                    }
                ) {
                    Text("Cancelar")
                }
            }
        )
    }
}
