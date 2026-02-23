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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import com.example.msp_app.features.auth.viewModels.AuthViewModel
import com.example.msp_app.features.productsInventory.viewmodels.ProductsInventoryViewModel
import com.example.msp_app.features.productsInventoryImages.viewmodels.ProductInventoryImagesViewModel

@Composable
fun SaleHomeScreen(navController: NavController) {
    val productsViewModel: ProductsInventoryViewModel = viewModel()
    val imagesViewModel: ProductInventoryImagesViewModel = viewModel()
    val localSalesViewModel: com.example.msp_app.features.sales.viewmodels.NewLocalSaleViewModel =
        viewModel()
    val authViewModel: AuthViewModel = viewModel()

    val productState = productsViewModel.productInventoryState.collectAsState().value
    val loading = productState is ResultState.Loading
    val newImagesCount by imagesViewModel.newImagesCount.collectAsState()
    val productsLoaded by productsViewModel.productsLoaded.collectAsState()
    val downloadProgress by imagesViewModel.downloadProgress.collectAsState()
    val downloadedCount by imagesViewModel.downloadedCount.collectAsState()
    val totalToDownload by imagesViewModel.totalToDownload.collectAsState()
    val pendingSales by localSalesViewModel.pendingSales.collectAsState()
    val userData by authViewModel.userData.collectAsState()

    var showConfirmDialog by remember { mutableStateOf(false) }
    var showProgressDialog by remember { mutableStateOf(false) }
    var shouldCheckImages by remember { mutableStateOf(false) }

    val context = LocalContext.current

    LaunchedEffect(Unit) {
        localSalesViewModel.loadPendingSales()
    }

    LaunchedEffect(productsLoaded, shouldCheckImages) {
        if (productsLoaded && shouldCheckImages) {
            imagesViewModel.checkForNewImages()
            shouldCheckImages = false
        }
    }

    LaunchedEffect(newImagesCount) {
        if (productsLoaded && newImagesCount > 0) {
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
            }
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
                    Text("Ventas", style = MaterialTheme.typography.titleLarge)
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
                Card(
                    modifier = Modifier
                        .fillMaxWidth(0.92f)
                        .padding(bottom = 16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (pendingSales.isNotEmpty())
                            MaterialTheme.colorScheme.tertiaryContainer
                        else
                            MaterialTheme.colorScheme.surfaceVariant
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Card(
                            modifier = Modifier.size(8.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = if (pendingSales.isNotEmpty())
                                    MaterialTheme.colorScheme.error
                                else
                                    MaterialTheme.colorScheme.primary
                            ),
                            shape = androidx.compose.foundation.shape.CircleShape
                        ) {}

                        Spacer(modifier = Modifier.width(12.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = if (pendingSales.isNotEmpty())
                                    "Ventas por sincronizar"
                                else
                                    "Estado de sincronización",
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = if (pendingSales.isNotEmpty())
                                    "${pendingSales.size} venta${if (pendingSales.size > 1) "s" else ""} pendiente${if (pendingSales.size > 1) "s" else ""}"
                                else
                                    "Todas las ventas sincronizadas",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        if (pendingSales.isNotEmpty()) {
                            Card(
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.error
                                ),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text(
                                    text = pendingSales.size.toString(),
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onError
                                )
                            }
                        }
                    }
                }

                if (pendingSales.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = {
                            val currentUser = (userData as? ResultState.Success)?.data
                            currentUser?.EMAIL?.let { email ->
                                localSalesViewModel.retryPendingSales(email)
                                Toast.makeText(
                                    context,
                                    "Enviando ${pendingSales.size} venta${if (pendingSales.size > 1) "s" else ""} pendiente${if (pendingSales.size > 1) "s" else ""}...",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        },
                        modifier = Modifier.fillMaxWidth(0.92f)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("ENVIAR VENTAS PENDIENTES")
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                Button(
                    onClick = {
                        productsViewModel.fetchRemoteInventory()
                        shouldCheckImages = true
                        localSalesViewModel.loadPendingSales() // Actualizar ventas pendientes
                    },
                    enabled = !loading,
                    modifier = Modifier.fillMaxWidth(0.92f)
                ) {
                    Text("ACTUALIZAR CATALOGO", color = Color.White)
                }
                if (loading) {
                    LinearProgressIndicator(modifier = Modifier.padding(8.dp))
                }

                Spacer(modifier = Modifier.height(6.dp))

                Button(
                    onClick = {
                        navController.navigate("new_sale") {
                            popUpTo("sale_home")
                        }
                    },
                    modifier = Modifier.fillMaxWidth(0.92f)
                ) {
                    Text("CREAR NUEVA VENTA", color = Color.White)
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
                    Text("Descargar", color = Color.White)
                }
            },
            dismissButton = {
                Button(onClick = {
                    showConfirmDialog = false
                }) {
                    Text("Cancelar", color = Color.White)
                }
            }
        )
    }

    if (showProgressDialog) {
        AlertDialog(
            onDismissRequest = { },
            title = { Text("Descargando imágenes") },
            text = {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            if (downloadProgress == 0) "Preparando..."
                            else "$downloadedCount de $totalToDownload",
                            style = MaterialTheme.typography.bodyLarge
                        )
                        Text(
                            text = "$downloadProgress%",
                            style = MaterialTheme.typography.bodyLarge.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Bold),
                            color = MaterialTheme.colorScheme.primary
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Custom progress bar
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp),
                        shape = RoundedCornerShape(4.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    ) {
                        LinearProgressIndicator(
                            progress = if (downloadProgress == 0) 0f else downloadProgress / 100f,
                            modifier = Modifier.fillMaxSize(),
                            color = MaterialTheme.colorScheme.primary,
                            trackColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = if (downloadProgress == 0)
                            "Iniciando descarga..."
                        else if (downloadProgress < 100)
                            "Descargando imágenes..."
                        else
                            "¡Descarga completada!",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
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
