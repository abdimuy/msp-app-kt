package com.example.msp_app.features.sales.screens

import android.net.Uri
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import coil.compose.rememberAsyncImagePainter
import com.example.msp_app.components.DrawerContainer
import com.example.msp_app.core.utils.DateUtils.formatIsoDate
import com.example.msp_app.features.sales.components.saleimagesviewer.ImageViewerDialog
import com.example.msp_app.features.sales.viewmodels.NewLocalSaleViewModel
import com.example.msp_app.ui.theme.ThemeController

@Composable
fun SaleDetailsListScreen(navController: NavController) {
    val viewModel: NewLocalSaleViewModel = viewModel()
    val salesList by viewModel.sales.collectAsState()
    val saleImages by viewModel.saleImages.collectAsState()
    var expandedSaleId by remember { mutableStateOf<String>("") }
    var selectedImageIndex by remember { mutableIntStateOf(0) }
    var showImageViewer by remember { mutableStateOf(false) }
    var selectedImageUris by remember { mutableStateOf<List<Uri>>(emptyList()) }

    LaunchedEffect(Unit) {
        viewModel.loadAllSales()
    }

    DrawerContainer(
        navController = navController
    ) { openDrawer ->
        val isDark = ThemeController.isDarkMode

        Scaffold(
            modifier = Modifier.statusBarsPadding(),
            topBar = {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = openDrawer) {
                        Icon(
                            imageVector = Icons.Default.Menu,
                            contentDescription = "Menú"
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = "Lista de Ventas",
                        style = MaterialTheme.typography.titleLarge
                    )
                    Spacer(modifier = Modifier.width(24.dp))
                    Text(
                        text = "${salesList.size}",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        ) { innerPadding ->
            Column(
                modifier = Modifier
                    .padding(innerPadding)
                    .fillMaxSize()
                    .padding(horizontal = 8.dp),
            ) {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(8.dp)
                ) {
                    items(salesList) { sale ->
                        val dateSale = formatIsoDate(
                            iso = sale.FECHA_VENTA,
                            pattern = "dd/MM/yyyy HH:mm a"
                        )

                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 4.dp, vertical = 8.dp),
                            elevation = CardDefaults.cardElevation(
                                defaultElevation = 4.dp,
                                pressedElevation = 8.dp
                            ),
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surface
                            ),
                            onClick = {
                                if (expandedSaleId == sale.LOCAL_SALE_ID) {
                                    expandedSaleId = ""
                                } else {
                                    expandedSaleId = sale.LOCAL_SALE_ID
                                    viewModel.loadImagesBySaleId(sale.LOCAL_SALE_ID)
                                }
                            }
                        ) {
                            Column(
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(16.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Canvas(
                                        modifier = Modifier.size(10.dp)
                                    ) {
                                        val center = androidx.compose.ui.geometry.Offset(
                                            size.width / 2,
                                            size.height / 2
                                        )
                                        val radius = size.minDimension / 2

                                        val statusColor = if (sale.ENVIADO)
                                            androidx.compose.ui.graphics.Color(0xFF4CAF50)
                                        else
                                            androidx.compose.ui.graphics.Color(0xFFFF5722)

                                        drawCircle(
                                            color = statusColor,
                                            radius = radius,
                                            center = center
                                        )
                                    }

                                    Spacer(modifier = Modifier.width(12.dp))

                                    Column(
                                        modifier = Modifier.weight(1f),
                                        verticalArrangement = Arrangement.spacedBy(3.dp)
                                    ) {
                                        Text(
                                            text = sale.NOMBRE_CLIENTE,
                                            style = MaterialTheme.typography.titleMedium.copy(
                                                fontWeight = FontWeight.SemiBold,
                                                fontSize = 18.sp
                                            ),
                                            color = MaterialTheme.colorScheme.onSurface,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                        Text(
                                            text = dateSale,
                                            style = MaterialTheme.typography.bodyMedium.copy(
                                                fontSize = 14.sp
                                            ),
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        if (sale.DIRECCION.isNotEmpty()) {
                                            Text(
                                                text = sale.DIRECCION,
                                                style = MaterialTheme.typography.bodySmall.copy(
                                                    fontSize = 12.sp
                                                ),
                                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(
                                                    alpha = 0.7f
                                                ),
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                    }

                                    Column(
                                        horizontalAlignment = Alignment.End,
                                        verticalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Text(
                                            text = "$${String.format("%.0f", sale.PRECIO_TOTAL)}",
                                            style = MaterialTheme.typography.titleMedium.copy(
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 18.sp
                                            ),
                                            color = MaterialTheme.colorScheme.primary
                                        )

                                        Text(
                                            text = if (sale.ENVIADO) "Enviado" else "Pendiente",
                                            modifier = Modifier
                                                .background(
                                                    color = if (sale.ENVIADO)
                                                        MaterialTheme.colorScheme.primaryContainer.copy(
                                                            alpha = 0.3f
                                                        )
                                                    else
                                                        MaterialTheme.colorScheme.errorContainer.copy(
                                                            alpha = 0.3f
                                                        ),
                                                    shape = RoundedCornerShape(16.dp)
                                                )
                                                .padding(horizontal = 10.dp, vertical = 4.dp),
                                            style = MaterialTheme.typography.labelSmall.copy(
                                                fontWeight = FontWeight.Medium,
                                                fontSize = 11.sp
                                            ),
                                            color = if (sale.ENVIADO)
                                                MaterialTheme.colorScheme.primary
                                            else
                                                MaterialTheme.colorScheme.error
                                        )
                                    }
                                }

                                if (expandedSaleId == sale.LOCAL_SALE_ID) {
                                    HorizontalDivider(
                                        modifier = Modifier.padding(horizontal = 20.dp),
                                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
                                    )
                                }
                            }

                            if (expandedSaleId == sale.LOCAL_SALE_ID) {
                                Column(
                                    modifier = Modifier.padding(
                                        horizontal = 20.dp,
                                        vertical = 16.dp
                                    )
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.padding(bottom = 12.dp)
                                    ) {
                                        Text(
                                            text = "Imágenes de la venta",
                                            style = MaterialTheme.typography.titleSmall.copy(
                                                fontWeight = FontWeight.Medium
                                            ),
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        if (sale.TELEFONO.isNotEmpty()) {
                                            Spacer(modifier = Modifier.weight(1f))
                                            Text(
                                                text = sale.TELEFONO,
                                                modifier = Modifier
                                                    .background(
                                                        color = MaterialTheme.colorScheme.surfaceVariant,
                                                        shape = RoundedCornerShape(16.dp)
                                                    )
                                                    .padding(
                                                        horizontal = 12.dp,
                                                        vertical = 6.dp
                                                    ),
                                                style = MaterialTheme.typography.labelMedium,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }

                                    if (saleImages.isNotEmpty()) {
                                        LazyRow(
                                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                                        ) {
                                            items(saleImages) { image ->
                                                val imgIndex = saleImages.indexOf(image)
                                                Card(
                                                    modifier = Modifier
                                                        .size(90.dp)
                                                        .clickable {
                                                            selectedImageIndex = imgIndex
                                                            selectedImageUris = saleImages.map {
                                                                if (it.IMAGE_URI is String) {
                                                                    Uri.parse(it.IMAGE_URI)
                                                                } else {
                                                                    it.IMAGE_URI as Uri
                                                                }
                                                            }
                                                            showImageViewer = true
                                                        },
                                                    shape = RoundedCornerShape(12.dp),
                                                    elevation = CardDefaults.cardElevation(
                                                        defaultElevation = 2.dp
                                                    )
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
                                                    shape = RoundedCornerShape(12.dp)
                                                )
                                                .padding(24.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = "No hay imágenes registradas",
                                                style = MaterialTheme.typography.bodyMedium,
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
            }
        }
    }

    if (showImageViewer) {
        ImageViewerDialog(
            imageUris = selectedImageUris,
            initialIndex = selectedImageIndex,
            onDismiss = { showImageViewer = false }
        )
    }
}
