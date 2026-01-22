package com.example.msp_app.features.sales.components.productselector

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Plus
import com.example.msp_app.core.context.LocalAuthViewModel
import com.example.msp_app.core.utils.ResultState
import com.example.msp_app.core.utils.parsePriceJsonToMap
import com.example.msp_app.features.sales.viewmodels.SaleItem
import com.example.msp_app.features.sales.viewmodels.SaleProductsViewModel
import com.example.msp_app.features.warehouses.WarehouseViewModel
import com.example.msp_app.utils.PriceParser

@Composable
fun SimpleProductSelector(
    warehouseViewModel: WarehouseViewModel,
    saleProductsViewModel: SaleProductsViewModel,
    onAddProduct: (Int, Int) -> Unit
) {
    val authViewModel = LocalAuthViewModel.current
    var expanded by remember { mutableStateOf(false) }
    var selectedProductName by remember { mutableStateOf("") }
    var selectedProductId by remember { mutableStateOf<Int?>(null) }
    var quantity by remember { mutableIntStateOf(1) }

    val userData by authViewModel.userData.collectAsState()
    val warehouseState by warehouseViewModel.warehouseProducts.collectAsState()
    val isOfflineMode by warehouseViewModel.isOfflineMode.collectAsState()

    val camionetaId = when (val userState = userData) {
        is ResultState.Success -> userState.data?.CAMIONETA_ASIGNADA
        else -> null
    }

    LaunchedEffect(camionetaId) {
        if (camionetaId != null) {
            warehouseViewModel.selectWarehouse(camionetaId)
        }
    }

    val productosCamioneta = when (val s = warehouseState) {
        is ResultState.Success -> s.data.body.ARTICULOS
        else -> emptyList()
    }

    val saleItems = saleProductsViewModel.saleItems

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize()
    ) {
        // Header con titulo y contador del carrito
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "Productos a vender",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            if (saleItems.isNotEmpty()) {
                Surface(
                    color = MaterialTheme.colorScheme.primary,
                    shape = CircleShape,
                    modifier = Modifier.size(32.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                Icons.Default.ShoppingCart,
                                contentDescription = "Carrito",
                                tint = Color.White,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(2.dp))
                            Text(
                                text = saleItems.sumOf { it.quantity }.toString(),
                                color = Color.White,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Selector de producto con informacion clara
        Card(
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainer
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Seleccionar producto",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium
                    )

                    if (isOfflineMode) {
                        Text(
                            text = "OFFLINE",
                            modifier = Modifier
                                .background(
                                    color = Color.Red.copy(alpha = 0.2f),
                                    shape = RoundedCornerShape(12.dp)
                                )
                                .padding(horizontal = 10.dp, vertical = 4.dp),
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.Red,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Box {
                    OutlinedTextField(
                        value = selectedProductName,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Producto") },
                        placeholder = { Text("Toca para seleccionar...") },
                        trailingIcon = {
                            Icon(
                                Icons.Default.ArrowDropDown,
                                contentDescription = null,
                                modifier = Modifier.clickable { expanded = true }
                            )
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { expanded = true },
                        shape = RoundedCornerShape(12.dp)
                    )

                    DropdownMenu(
                        expanded = expanded,
                        onDismissRequest = { expanded = false },
                        modifier = Modifier.fillMaxHeight(0.8f)
                    ) {
                        productosCamioneta.forEach { product ->
                            val cantidadEnVenta =
                                saleProductsViewModel.getQuantityForProduct(product)
                            val disponible = product.EXISTENCIAS - cantidadEnVenta

                            val priceMap = try {
                                parsePriceJsonToMap(product.PRECIOS ?: "")
                            } catch (e: Exception) {
                                emptyMap()
                            }
                            val price = priceMap.values.lastOrNull() ?: "N/A"

                            DropdownMenuItem(
                                text = {
                                    Column {
                                        Text(
                                            text = product.ARTICULO,
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.Medium
                                        )
                                        Text(
                                            text = "$$price",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.primary,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            text = if (disponible > 0) "Disponible: $disponible" else "Sin stock",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = if (disponible > 0)
                                                MaterialTheme.colorScheme.secondary
                                            else
                                                MaterialTheme.colorScheme.error
                                        )
                                    }
                                },
                                onClick = {
                                    selectedProductId = product.ARTICULO_ID
                                    selectedProductName = product.ARTICULO
                                    expanded = false
                                },
                                enabled = disponible > 0
                            )
                        }
                    }
                }

                selectedProductId?.let { id ->
                    val producto = productosCamioneta.find { it.ARTICULO_ID == id }
                    if (producto != null) {
                        val cantidadEnVenta = saleProductsViewModel.getQuantityForProduct(producto)
                        val disponible = producto.EXISTENCIAS - cantidadEnVenta

                        Spacer(modifier = Modifier.height(12.dp))

                        val fontScale = LocalDensity.current.fontScale
                        val useLargeLayout = fontScale > 1.3f

                        if (useLargeLayout) {
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Column {
                                    Text(
                                        "Cantidad a agregar",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Medium
                                    )
                                    Text(
                                        "Stock disponible: $disponible",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.secondary
                                    )
                                }

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center
                                ) {
                                    FilledTonalIconButton(
                                        onClick = { if (quantity > 1) quantity-- },
                                        modifier = Modifier.size(44.dp),
                                        enabled = quantity > 1
                                    ) {
                                        Icon(
                                            Icons.Default.KeyboardArrowDown,
                                            contentDescription = "Reducir",
                                            modifier = Modifier.size(24.dp)
                                        )
                                    }

                                    Spacer(modifier = Modifier.width(16.dp))

                                    Text(
                                        text = quantity.toString(),
                                        style = MaterialTheme.typography.titleLarge,
                                        modifier = Modifier
                                            .background(
                                                MaterialTheme.colorScheme.primary,
                                                RoundedCornerShape(8.dp)
                                            )
                                            .padding(horizontal = 24.dp, vertical = 12.dp),
                                        textAlign = TextAlign.Center,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White
                                    )

                                    Spacer(modifier = Modifier.width(16.dp))

                                    FilledTonalIconButton(
                                        onClick = { if (quantity < disponible) quantity++ },
                                        modifier = Modifier.size(44.dp),
                                        enabled = quantity < disponible
                                    ) {
                                        Icon(
                                            Icons.Default.KeyboardArrowUp,
                                            contentDescription = "Aumentar",
                                            modifier = Modifier.size(24.dp)
                                        )
                                    }
                                }
                            }
                        } else {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(
                                        "Cantidad a agregar",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Medium
                                    )
                                    Text(
                                        "Stock disponible: $disponible",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.secondary
                                    )
                                }

                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    FilledTonalIconButton(
                                        onClick = { if (quantity > 1) quantity-- },
                                        modifier = Modifier.size(36.dp),
                                        enabled = quantity > 1
                                    ) {
                                        Icon(
                                            Icons.Default.KeyboardArrowDown,
                                            contentDescription = "Reducir",
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }

                                    Text(
                                        text = quantity.toString(),
                                        style = MaterialTheme.typography.titleLarge,
                                        modifier = Modifier
                                            .background(
                                                MaterialTheme.colorScheme.primary,
                                                RoundedCornerShape(8.dp)
                                            )
                                            .padding(horizontal = 16.dp, vertical = 8.dp),
                                        textAlign = TextAlign.Center,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 20.sp,
                                        color = Color.White
                                    )

                                    FilledTonalIconButton(
                                        onClick = { if (quantity < disponible) quantity++ },
                                        modifier = Modifier.size(36.dp),
                                        enabled = quantity < disponible
                                    ) {
                                        Icon(
                                            Icons.Default.KeyboardArrowUp,
                                            contentDescription = "Aumentar",
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        Button(
                            onClick = {
                                onAddProduct(id, quantity)
                                quantity = 1
                                selectedProductName = ""
                                selectedProductId = null
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(
                                Lucide.Plus,
                                contentDescription = "Agregar",
                                modifier = Modifier.size(18.dp),
                                tint = Color.White
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Agregar a la venta",
                                color = Color.White
                            )
                        }
                    }
                }
            }
        }

        // Mostrar productos que NO estan en combo (productos individuales con edicion de precios)
        val individualItems = saleItems.filter { it.comboId == null }

        if (individualItems.isNotEmpty()) {
            Spacer(modifier = Modifier.height(16.dp))

            Text(
                "Productos seleccionados (${individualItems.size})",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    individualItems.forEach { saleItem ->
                        CartItemRow(
                            saleItem = saleItem,
                            saleProductsViewModel = saleProductsViewModel
                        )
                        if (saleItem != individualItems.last()) {
                            Spacer(modifier = Modifier.height(8.dp))
                        }
                    }
                }
            }
        } else if (saleItems.isEmpty()) {
            Spacer(modifier = Modifier.height(16.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(80.dp)
                    .background(
                        MaterialTheme.colorScheme.surfaceContainer,
                        RoundedCornerShape(12.dp)
                    )
                    .border(
                        1.dp,
                        MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                        RoundedCornerShape(12.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Default.ShoppingCart,
                        contentDescription = null,
                        modifier = Modifier.size(24.dp),
                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        "No hay productos agregados",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                }
            }
        }
    }
}

@Composable
private fun CartItemRow(
    saleItem: SaleItem,
    saleProductsViewModel: SaleProductsViewModel
) {
    val parsedPrices = PriceParser.parsePricesFromString(saleItem.product.PRECIOS)

    var priceList by remember { mutableStateOf(parsedPrices.precioLista.toString()) }
    var shortTermPrice by remember { mutableStateOf(parsedPrices.precioCortoplazo.toString()) }
    var cashPrice by remember { mutableStateOf(parsedPrices.precioContado.toString()) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            // Header del producto
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = saleItem.product.ARTICULO,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(top = 4.dp)
                    ) {
                        Surface(
                            color = MaterialTheme.colorScheme.primaryContainer,
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(
                                text = "Cantidad: ${saleItem.quantity}",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(8.dp))

                        Text(
                            text = "Stock: ${saleItem.product.EXISTENCIAS}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                        )
                    }
                }

                // Boton eliminar
                IconButton(
                    onClick = {
                        saleProductsViewModel.removeProductFromSale(saleItem.product)
                    },
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(
                        Icons.Default.Clear,
                        contentDescription = "Eliminar producto",
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Controles de cantidad
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Ajustar cantidad:",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilledTonalIconButton(
                        onClick = {
                            val newQuantity = saleItem.quantity - 1
                            saleProductsViewModel.updateQuantity(saleItem.product, newQuantity)
                        },
                        modifier = Modifier.size(36.dp),
                        enabled = saleItem.quantity > 1
                    ) {
                        Icon(
                            Icons.Default.KeyboardArrowDown,
                            contentDescription = "Reducir cantidad",
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    Surface(
                        color = MaterialTheme.colorScheme.primary,
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            text = saleItem.quantity.toString(),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                            textAlign = TextAlign.Center
                        )
                    }

                    FilledTonalIconButton(
                        onClick = {
                            val cantidadEnVenta =
                                saleProductsViewModel.getQuantityForProduct(saleItem.product)
                            val disponible =
                                saleItem.product.EXISTENCIAS - cantidadEnVenta + saleItem.quantity

                            if (saleItem.quantity < disponible) {
                                val newQuantity = saleItem.quantity + 1
                                saleProductsViewModel.updateQuantity(saleItem.product, newQuantity)
                            }
                        },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            Icons.Default.KeyboardArrowUp,
                            contentDescription = "Aumentar cantidad",
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Precios editables inline
            Text(
                text = "Precios:",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Precio Contado
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Contado:",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.width(80.dp),
                        color = MaterialTheme.colorScheme.tertiary,
                        fontWeight = FontWeight.Medium
                    )

                    OutlinedTextField(
                        value = cashPrice,
                        onValueChange = { newValue ->
                            cashPrice = newValue
                            newValue.toDoubleOrNull()?.let { price ->
                                if (price > 0) {
                                    saleProductsViewModel.updateProductPrices(
                                        saleItem.product,
                                        priceList.toDoubleOrNull() ?: parsedPrices.precioLista,
                                        shortTermPrice.toDoubleOrNull()
                                            ?: parsedPrices.precioCortoplazo,
                                        price
                                    )
                                }
                            }
                        },
                        prefix = { Text("$") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(8.dp),
                        textStyle = MaterialTheme.typography.bodyMedium.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp
                        )
                    )
                }

                // Precio Corto Plazo
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "C.Plazo:",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.width(80.dp),
                        color = MaterialTheme.colorScheme.secondary,
                        fontWeight = FontWeight.Medium
                    )

                    OutlinedTextField(
                        value = shortTermPrice,
                        onValueChange = { newValue ->
                            shortTermPrice = newValue
                            newValue.toDoubleOrNull()?.let { price ->
                                if (price > 0) {
                                    saleProductsViewModel.updateProductPrices(
                                        saleItem.product,
                                        priceList.toDoubleOrNull() ?: parsedPrices.precioLista,
                                        price,
                                        cashPrice.toDoubleOrNull() ?: parsedPrices.precioContado
                                    )
                                }
                            }
                        },
                        prefix = { Text("$") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(8.dp),
                        textStyle = MaterialTheme.typography.bodyMedium.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp
                        )
                    )
                }

                // Precio Lista
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Lista:",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.width(80.dp),
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Medium
                    )

                    OutlinedTextField(
                        value = priceList,
                        onValueChange = { newValue ->
                            priceList = newValue
                            newValue.toDoubleOrNull()?.let { price ->
                                if (price > 0) {
                                    saleProductsViewModel.updateProductPrices(
                                        saleItem.product,
                                        price,
                                        shortTermPrice.toDoubleOrNull()
                                            ?: parsedPrices.precioCortoplazo,
                                        cashPrice.toDoubleOrNull() ?: parsedPrices.precioContado
                                    )
                                }
                            }
                        },
                        prefix = { Text("$") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(8.dp),
                        textStyle = MaterialTheme.typography.bodyMedium.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp
                        )
                    )
                }
            }
        }
    }
}
