package com.example.msp_app.features.products.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.example.msp_app.core.utils.ResultState
import com.example.msp_app.core.utils.parsePriceJsonToMap
import com.example.msp_app.core.utils.searchSimilarItems
import com.example.msp_app.core.utils.toCurrency
import com.example.msp_app.data.models.productInventory.ProductInventory
import com.example.msp_app.features.productsInventory.viewmodels.ProductsInventoryViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProductsCatalogScreen(navController: NavController) {

    val viewModel: ProductsInventoryViewModel = viewModel()
    val productState by viewModel.productInventoryState.collectAsState()
    var query by remember { mutableStateOf("") }
    val focusManager = LocalFocusManager.current

    val products = when (productState) {
        is ResultState.Success -> (productState as ResultState.Success).data
        else -> emptyList()
    }

    LaunchedEffect(Unit) {
        viewModel.loadLocalProductsInventory()
    }

    val filteredProducts = if (query.isBlank()) {
        products.filter { it.EXISTENCIAS > 0 }
    } else {
        searchSimilarItems(
            query = query,
            items = products.filter { it.EXISTENCIAS > 0 },
            threshold = 60
        ) { it.ARTICULO }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
            .statusBarsPadding()
    ) {
        Text(
            text = "Productos",
            fontSize = 20.sp,
            fontWeight = FontWeight.SemiBold,
            color = Color(0xFF003366),
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp),
            textAlign = TextAlign.Center
        )
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            placeholder = { Text("Buscar Producto...") },
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp),
            leadingIcon = {
                Icon(Icons.Default.Search, contentDescription = "Buscar")
            },
            trailingIcon = {
                if (query.isNotEmpty()) {
                    IconButton(onClick = {
                        query = ""
                        focusManager.clearFocus()
                    }) {
                        Icon(Icons.Default.Close, contentDescription = "Eliminar busqueda")
                    }
                }
            },
            shape = RoundedCornerShape(25.dp)
        )

        LazyColumn(
            modifier = Modifier
                .weight(1f),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(bottom = 24.dp)
        ) {
            items(filteredProducts) { product ->
                ProductCard(product = product)
            }
        }
    }
}

@Composable
fun ProductCard(product: ProductInventory) {
    var pressed by remember { mutableStateOf(false) }

    androidx.compose.foundation.layout.Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(130.dp)
            .background(
                color = Color(0xFFF8FAFC),
                shape = RoundedCornerShape(8.dp)
            )
            .border(
                width = 1.dp,
                color = Color(0xFFE2E8F0),
                shape = RoundedCornerShape(8.dp)
            )
            .clickable { pressed = !pressed }
            .alpha(if (pressed) 1f else 1f)
            .padding(8.dp)
    ) {
        Box(
            modifier = Modifier
                .size(115.dp)
                .background(Color(0xFFCBD5E1), RoundedCornerShape(8.dp))
        )

        Column(
            modifier = Modifier
                .fillMaxHeight()
                .padding(start = 12.dp),
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = product.ARTICULO,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color(0xFF003366),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                lineHeight = 16.sp
            )
            Text(
                text = "Stock: ${product.EXISTENCIAS}",
                fontSize = 14.sp,
                fontWeight = FontWeight.Normal,
                color = Color(0xFF0056B3),
                lineHeight = 16.sp
            )
            val priceMap = parsePriceJsonToMap(product.PRECIOS)
            priceMap.forEach { (label, value) ->
                Text(
                    text = "$label: ${value.toCurrency(noDecimals = false)}",
                    fontSize = 14.sp,
                    color = Color(0xFF334155),
                    lineHeight = 16.sp
                )
            }
        }
    }
}
