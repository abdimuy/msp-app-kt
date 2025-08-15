package com.example.msp_app.features.sales.screens

import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
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
                                .padding(vertical = 4.dp)
                                .align(Alignment.CenterHorizontally)
                                .clickable {
                                    if (expandedSaleId == sale.LOCAL_SALE_ID) {
                                        expandedSaleId = ""
                                    } else {
                                        expandedSaleId = sale.LOCAL_SALE_ID
                                        viewModel.loadImagesBySaleId(sale.LOCAL_SALE_ID)
                                    }
                                },
                            elevation = CardDefaults.cardElevation(4.dp),
                            shape = RoundedCornerShape(8.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surface
                            ),
                            border =
                                if (!isDark) null else BorderStroke(
                                    width = 1.dp,
                                    color = Color.DarkGray
                                )
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text(
                                    text = sale.NOMBRE_CLIENTE,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(text = dateSale)

                                if (expandedSaleId == sale.LOCAL_SALE_ID) {
                                    Spacer(Modifier.height(8.dp))
                                    Text(text = sale.DIRECCION)

                                    Spacer(Modifier.height(8.dp))
                                    if (saleImages.isNotEmpty()) {
                                        saleImages.map { it.IMAGE_URI }
                                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                            items(saleImages) { image ->
                                                val imgIndex = saleImages.indexOf(image)
                                                Image(
                                                    painter = rememberAsyncImagePainter(image.IMAGE_URI),
                                                    contentDescription = null,
                                                    modifier = Modifier
                                                        .size(100.dp)
                                                        .clip(RoundedCornerShape(8.dp))
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
                                                    contentScale = ContentScale.Crop
                                                )
                                            }
                                        }
                                    } else {
                                        Text(text = "No hay imágenes registradas")
                                    }

                                    if (showImageViewer) {
                                        ImageViewerDialog(
                                            imageUris = selectedImageUris,
                                            initialIndex = selectedImageIndex,
                                            onDismiss = { showImageViewer = false }
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
