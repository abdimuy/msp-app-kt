package com.example.msp_app.features.sales.screens

import android.annotation.SuppressLint
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import coil.compose.rememberAsyncImagePainter
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Truck
import com.example.msp_app.components.DrawerContainer
import com.example.msp_app.core.utils.DateUtils.formatIsoDate
import com.example.msp_app.core.utils.ResultState
import com.example.msp_app.data.local.entities.LocalSaleEntity
import com.example.msp_app.features.auth.viewModels.AuthViewModel
import com.example.msp_app.features.cart.viewmodels.CartViewModel
import com.example.msp_app.features.productsInventory.viewmodels.ProductsInventoryViewModel
import com.example.msp_app.features.productsInventoryImages.viewmodels.ProductInventoryImagesViewModel
import com.example.msp_app.features.sales.components.saleimagesviewer.ImageViewerDialog
import com.example.msp_app.features.sales.viewmodels.NewLocalSaleViewModel
import java.time.LocalDate
import java.time.format.DateTimeFormatter

private enum class TimeFilter(val label: String) {
    TODAY("Hoy"),
    THIS_WEEK("Esta semana"),
    ALL("Todas")
}

private enum class StatusFilter(val label: String) {
    ALL("Todas"),
    PENDING("Pendientes"),
    SENT("Enviadas")
}

@SuppressLint("DefaultLocale")
@Composable
fun UnifiedSalesScreen(navController: NavController) {
    val localSalesViewModel: NewLocalSaleViewModel = viewModel()
    val productsViewModel: ProductsInventoryViewModel = viewModel()
    val imagesViewModel: ProductInventoryImagesViewModel = viewModel()
    val authViewModel: AuthViewModel = viewModel()
    val cartViewModel: CartViewModel = viewModel()
    val context = LocalContext.current

    val salesList by localSalesViewModel.sales.collectAsState()
    val pendingSales by localSalesViewModel.pendingSales.collectAsState()
    val saleImages by localSalesViewModel.saleImages.collectAsState()
    val userData by authViewModel.userData.collectAsState()
    val productState = productsViewModel.productInventoryState.collectAsState().value
    val loading = productState is ResultState.Loading
    val newImagesCount by imagesViewModel.newImagesCount.collectAsState()
    val productsLoaded by productsViewModel.productsLoaded.collectAsState()
    val downloadProgress by imagesViewModel.downloadProgress.collectAsState()
    val downloadedCount by imagesViewModel.downloadedCount.collectAsState()
    val totalToDownload by imagesViewModel.totalToDownload.collectAsState()

    var selectedTimeFilter by remember { mutableStateOf(TimeFilter.TODAY) }
    var selectedStatusFilter by remember { mutableStateOf(StatusFilter.ALL) }
    var expandedSaleId by remember { mutableStateOf("") }
    var selectedImageIndex by remember { mutableIntStateOf(0) }
    var showImageViewer by remember { mutableStateOf(false) }
    var selectedImageUris by remember { mutableStateOf<List<Uri>>(emptyList()) }
    var showConfirmDialog by remember { mutableStateOf(false) }
    var showProgressDialog by remember { mutableStateOf(false) }
    var shouldCheckImages by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        localSalesViewModel.loadAllSales()
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
                    "Descargando $newImagesCount imagenes automaticamente...",
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

    val filteredSales = remember(salesList, selectedTimeFilter, selectedStatusFilter) {
        val today = LocalDate.now()
        val dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")

        salesList.filter { sale ->
            val matchesTime = when (selectedTimeFilter) {
                TimeFilter.TODAY -> sale.FECHA_VENTA.startsWith(today.format(dateFormatter))
                TimeFilter.THIS_WEEK -> {
                    try {
                        val saleDate = LocalDate.parse(
                            sale.FECHA_VENTA.take(10),
                            dateFormatter
                        )
                        !saleDate.isBefore(today.minusDays(7))
                    } catch (_: Exception) {
                        true
                    }
                }
                TimeFilter.ALL -> true
            }

            val matchesStatus = when (selectedStatusFilter) {
                StatusFilter.ALL -> true
                StatusFilter.PENDING -> !sale.ENVIADO
                StatusFilter.SENT -> sale.ENVIADO
            }

            matchesTime && matchesStatus
        }
    }

    val todaySales = remember(salesList) {
        val todayPrefix = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))
        salesList.filter { it.FECHA_VENTA.startsWith(todayPrefix) }
    }
    val todayTotal = todaySales.sumOf { it.PRECIO_TOTAL }

    DrawerContainer(navController = navController) { openDrawer ->
        Scaffold(
            modifier = Modifier.statusBarsPadding(),
            topBar = {
                val cartItemCount = cartViewModel.getTotalItems()
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = openDrawer) {
                        Icon(imageVector = Icons.Default.Menu, contentDescription = "Menu")
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Text("Ventas", style = MaterialTheme.typography.titleLarge)
                    Spacer(modifier = Modifier.weight(1f))
                    IconButton(
                        onClick = {
                            navController.navigate("cart") {
                                popUpTo("sale_home")
                            }
                        }
                    ) {
                        Box {
                            Icon(
                                imageVector = Lucide.Truck,
                                contentDescription = "Mi Camioneta",
                                modifier = Modifier.size(26.dp)
                            )
                            if (cartItemCount > 0) {
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.TopEnd)
                                        .size(18.dp)
                                        .background(
                                            color = MaterialTheme.colorScheme.error,
                                            shape = RoundedCornerShape(9.dp)
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = if (cartItemCount > 99) "99+" else "$cartItemCount",
                                        color = Color.White,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                }
            },
            bottomBar = {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surface)
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Button(
                        onClick = {
                            navController.navigate("new_sale") {
                                popUpTo("sale_home")
                            }
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("+ Nueva Venta", color = Color.White, fontSize = 16.sp)
                    }
                    OutlinedButton(
                        onClick = {
                            navController.navigate("products_catalog") {
                                popUpTo("sale_home")
                            }
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Catalogo", fontSize = 16.sp)
                    }
                }
            }
        ) { innerPadding ->
            LazyColumn(
                modifier = Modifier
                    .padding(innerPadding)
                    .fillMaxSize()
            ) {
                // Header - Resumen del dia
                item {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surface
                        ),
                        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    "Resumen del dia",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    LocalDate.now().format(
                                        DateTimeFormatter.ofPattern("dd MMM yyyy")
                                    ),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                // Ventas hoy
                                Card(
                                    modifier = Modifier.weight(1f),
                                    colors = CardDefaults.cardColors(
                                        containerColor = Color(0xFFECFDF5)
                                    ),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(10.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        Text(
                                            "${todaySales.size}",
                                            style = MaterialTheme.typography.headlineSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = Color(0xFF059669)
                                        )
                                        Text(
                                            "Ventas",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = Color(0xFF059669)
                                        )
                                    }
                                }

                                // Pendientes
                                Card(
                                    modifier = Modifier.weight(1f),
                                    colors = CardDefaults.cardColors(
                                        containerColor = Color(0xFFFFFBEB)
                                    ),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(10.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        Text(
                                            "${pendingSales.size}",
                                            style = MaterialTheme.typography.headlineSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = Color(0xFFD97706)
                                        )
                                        Text(
                                            "Pendientes",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = Color(0xFFD97706)
                                        )
                                    }
                                }

                                // Total
                                Card(
                                    modifier = Modifier.weight(1f),
                                    colors = CardDefaults.cardColors(
                                        containerColor = Color(0xFFEFF6FF)
                                    ),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(10.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        Text(
                                            "$${String.format("%.0f", todayTotal)}",
                                            style = MaterialTheme.typography.titleLarge,
                                            fontWeight = FontWeight.Bold,
                                            color = Color(0xFF2563EB)
                                        )
                                        Text(
                                            "Total",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = Color(0xFF2563EB)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // Action buttons
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                productsViewModel.fetchRemoteInventory()
                                shouldCheckImages = true
                                localSalesViewModel.loadPendingSales()
                            },
                            modifier = Modifier.weight(1f),
                            enabled = !loading,
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            if (loading) {
                                LinearProgressIndicator(
                                    modifier = Modifier
                                        .height(2.dp)
                                        .width(60.dp)
                                )
                            } else {
                                Text(
                                    "Actualizar Catalogo",
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                        }

                        if (pendingSales.isNotEmpty()) {
                            Button(
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
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(8.dp),
                                colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                                    containerColor = Color(0xFFF59E0B)
                                )
                            ) {
                                Text(
                                    "Enviar Pendientes (${pendingSales.size})",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = Color.White
                                )
                            }
                        } else {
                            OutlinedButton(
                                onClick = { },
                                modifier = Modifier.weight(1f),
                                enabled = false,
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text(
                                    "Todo enviado",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = Color(0xFF059669)
                                )
                            }
                        }
                    }
                }

                // Chip filters
                item {
                    Column(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                    ) {
                        // Time filters
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            TimeFilter.entries.forEach { filter ->
                                FilterChip(
                                    selected = selectedTimeFilter == filter,
                                    onClick = { selectedTimeFilter = filter },
                                    label = {
                                        Text(
                                            filter.label,
                                            style = MaterialTheme.typography.bodyMedium
                                        )
                                    },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = MaterialTheme.colorScheme.primary,
                                        selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                                    )
                                )
                            }
                        }
                        // Status filters
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            StatusFilter.entries.forEach { filter ->
                                FilterChip(
                                    selected = selectedStatusFilter == filter,
                                    onClick = { selectedStatusFilter = filter },
                                    label = {
                                        Text(
                                            filter.label,
                                            style = MaterialTheme.typography.bodyMedium
                                        )
                                    },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = MaterialTheme.colorScheme.primary,
                                        selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                                    )
                                )
                            }
                        }
                    }
                }

                // Sales list
                if (filteredSales.isEmpty()) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 48.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                "No hay ventas con estos filtros",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                } else {
                    items(filteredSales, key = { it.LOCAL_SALE_ID }) { sale ->
                        SaleCard(
                            sale = sale,
                            isExpanded = expandedSaleId == sale.LOCAL_SALE_ID,
                            saleImages = saleImages,
                            onCardClick = {
                                navController.navigate("saleDescription/${sale.LOCAL_SALE_ID}")
                            },
                            onExpandToggle = {
                                if (expandedSaleId == sale.LOCAL_SALE_ID) {
                                    expandedSaleId = ""
                                } else {
                                    expandedSaleId = sale.LOCAL_SALE_ID
                                    localSalesViewModel.loadImagesBySaleId(sale.LOCAL_SALE_ID)
                                }
                            },
                            onImageClick = { index, uris ->
                                selectedImageIndex = index
                                selectedImageUris = uris
                                showImageViewer = true
                            }
                        )
                    }
                }

                // Bottom spacing
                item { Spacer(modifier = Modifier.height(8.dp)) }
            }
        }
    }

    // Image viewer dialog
    if (showImageViewer) {
        ImageViewerDialog(
            imageUris = selectedImageUris,
            initialIndex = selectedImageIndex,
            onDismiss = { showImageViewer = false }
        )
    }

    // Image download confirm dialog
    if (showConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showConfirmDialog = false },
            title = { Text("Sincronizar imagenes") },
            text = {
                Text(
                    "Hay $newImagesCount imagenes nuevas. Descargar las imagenes puede consumir tus datos moviles. Deseas continuar?"
                )
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
                Button(onClick = { showConfirmDialog = false }) {
                    Text("Cancelar")
                }
            }
        )
    }

    // Image download progress dialog
    if (showProgressDialog) {
        AlertDialog(
            onDismissRequest = { },
            title = { Text("Descargando imagenes") },
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
                            if (downloadProgress == 0) "Preparando..." else "$downloadedCount de $totalToDownload",
                            style = MaterialTheme.typography.bodyLarge
                        )
                        Text(
                            "$downloadProgress%",
                            style = MaterialTheme.typography.bodyLarge.copy(
                                fontWeight = FontWeight.Bold
                            ),
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    LinearProgressIndicator(
                        progress = { if (downloadProgress == 0) 0f else downloadProgress / 100f },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = when {
                            downloadProgress == 0 -> "Iniciando descarga..."
                            downloadProgress < 100 -> "Descargando imagenes..."
                            else -> "Descarga completada!"
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {},
            dismissButton = {
                Button(onClick = {
                    imagesViewModel.cancelDownload()
                    showProgressDialog = false
                }) {
                    Text("Cancelar")
                }
            }
        )
    }
}

@SuppressLint("DefaultLocale")
@Composable
private fun SaleCard(
    sale: LocalSaleEntity,
    isExpanded: Boolean,
    saleImages: List<com.example.msp_app.data.local.entities.LocalSaleImageEntity>,
    onCardClick: () -> Unit,
    onExpandToggle: () -> Unit,
    onImageClick: (Int, List<Uri>) -> Unit
) {
    val dateSale = formatIsoDate(
        iso = sale.FECHA_VENTA,
        pattern = "dd/MM/yyyy HH:mm a"
    )

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        onClick = onCardClick
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Canvas(modifier = Modifier.size(10.dp)) {
                    drawCircle(
                        color = if (sale.ENVIADO) Color(0xFF4CAF50) else Color(0xFFFF5722),
                        radius = size.minDimension / 2
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text(
                        text = sale.NOMBRE_CLIENTE,
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.SemiBold
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "${sale.TIPO_VENTA ?: "CONTADO"} - $dateSale",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (sale.DIRECCION.isNotEmpty()) {
                        Text(
                            text = sale.DIRECCION,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Column(
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = if (sale.ENVIADO) "Enviada" else "Pendiente",
                        modifier = Modifier
                            .background(
                                color = if (sale.ENVIADO) {
                                    Color(0xFFECFDF5)
                                } else {
                                    Color(0xFFFEF2F2)
                                },
                                shape = RoundedCornerShape(12.dp)
                            )
                            .padding(horizontal = 8.dp, vertical = 3.dp),
                        style = MaterialTheme.typography.labelMedium,
                        color = if (sale.ENVIADO) Color(0xFF059669) else Color(0xFFDC2626)
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "$${String.format("%.0f", sale.PRECIO_TOTAL)}",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold
                            ),
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(Modifier.width(6.dp))
                        Icon(
                            imageVector = if (isExpanded) {
                                Icons.Default.KeyboardArrowUp
                            } else {
                                Icons.Default.KeyboardArrowDown
                            },
                            contentDescription = if (isExpanded) "Contraer" else "Expandir",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier
                                .size(24.dp)
                                .clickable { onExpandToggle() }
                                .background(
                                    color = MaterialTheme.colorScheme.surfaceVariant,
                                    shape = RoundedCornerShape(6.dp)
                                )
                        )
                    }
                }
            }

            if (isExpanded) {
                HorizontalDivider(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
                )

                Column(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(bottom = 8.dp)
                    ) {
                        Text(
                            "Imagenes de la venta",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Medium
                            )
                        )
                        if (sale.TELEFONO.isNotEmpty()) {
                            Spacer(modifier = Modifier.weight(1f))
                            Text(
                                text = sale.TELEFONO,
                                modifier = Modifier
                                    .background(
                                        color = MaterialTheme.colorScheme.surfaceVariant,
                                        shape = RoundedCornerShape(12.dp)
                                    )
                                    .padding(horizontal = 10.dp, vertical = 4.dp),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    if (saleImages.isNotEmpty()) {
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(saleImages) { image ->
                                val imgIndex = saleImages.indexOf(image)
                                Card(
                                    modifier = Modifier
                                        .size(80.dp)
                                        .clickable {
                                            val uris = saleImages.map { Uri.parse(it.IMAGE_URI) }
                                            onImageClick(imgIndex, uris)
                                        },
                                    shape = RoundedCornerShape(8.dp),
                                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                                ) {
                                    Image(
                                        painter = rememberAsyncImagePainter(image.IMAGE_URI),
                                        contentDescription = null,
                                        modifier = Modifier.fillMaxSize(),
                                        contentScale = ContentScale.Crop
                                    )
                                }
                            }
                        }
                    } else {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(
                                    color = MaterialTheme.colorScheme.surfaceVariant.copy(
                                        alpha = 0.3f
                                    ),
                                    shape = RoundedCornerShape(8.dp)
                                )
                                .padding(20.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                "No hay imagenes registradas",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(
                                    alpha = 0.7f
                                )
                            )
                        }
                    }
                }
            }
        }
    }
}
