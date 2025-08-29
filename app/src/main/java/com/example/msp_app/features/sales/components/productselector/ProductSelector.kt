package com.example.msp_app.features.sales.components.productselector

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.msp_app.core.context.LocalAuthViewModel
import com.example.msp_app.core.utils.ResultState
import com.example.msp_app.core.utils.parsePriceJsonToMap
import com.example.msp_app.features.sales.viewmodels.SaleProductsViewModel
import com.example.msp_app.features.warehouses.WarehouseViewModel

@Composable
fun ProductSelector(
    warehouseViewModel: WarehouseViewModel,
    saleProductsViewModel: SaleProductsViewModel,
    onAddProduct: (Int, Int) -> Unit
) {
    val authViewModel = LocalAuthViewModel.current
    var expanded by remember { mutableStateOf(false) }
    var selectedProductName by remember { mutableStateOf("") }
    var selectedProductId by remember { mutableStateOf<Int?>(null) }
    var cantidad by remember { mutableStateOf("1") }

    val userData by authViewModel.userData.collectAsState()
    val warehouseState by warehouseViewModel.warehouseProducts.collectAsState()

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

    Column {
        Text("Seleccionar producto", style = MaterialTheme.typography.titleMedium)

        Box {
            OutlinedTextField(
                value = selectedProductName,
                onValueChange = {},
                readOnly = true,
                label = { Text("Producto") },
                trailingIcon = {
                    Icon(
                        Icons.Default.ArrowDropDown,
                        contentDescription = null,
                        modifier = Modifier.clickable { expanded = true }
                    )
                },
                modifier = Modifier.fillMaxWidth()
            )

            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                productosCamioneta.forEach { p ->
                    val cantidadEnVenta = saleProductsViewModel.getQuantityForProduct(p)
                    val disponible = p.EXISTENCIAS - cantidadEnVenta

                    DropdownMenuItem(
                        text = {
                            Column {
                                val priceMap = try {
                                    parsePriceJsonToMap(p.PRECIOS ?: "")
                                } catch (e: Exception) {
                                    emptyMap()
                                }

                                val firstPrice = priceMap.values.lastOrNull() ?: "N/A"
                                Text("${p.ARTICULO} - $$firstPrice")
                                Text(
                                    "Disponible: $disponible | En venta: $cantidadEnVenta",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                                )
                            }
                        },
                        onClick = {
                            selectedProductId = p.ARTICULO_ID
                            selectedProductName = p.ARTICULO
                            expanded = false
                        },
                        enabled = disponible > 0
                    )
                }
            }
        }

        Spacer(Modifier.height(8.dp))

        OutlinedTextField(
            value = cantidad,
            onValueChange = { cantidad = it },
            label = { Text("Cantidad") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth()
        )

        selectedProductId?.let { id ->
            val producto = productosCamioneta.find { it.ARTICULO_ID == id }
            if (producto != null) {
                val cantidadEnVenta = saleProductsViewModel.getQuantityForProduct(producto)
                val disponible = producto.EXISTENCIAS - cantidadEnVenta

                Text(
                    "Existencias totales: ${producto.EXISTENCIAS} | Ya en venta: $cantidadEnVenta | Disponible: $disponible",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }

        Spacer(Modifier.height(8.dp))

        Button(
            onClick = {
                if (selectedProductId != null && cantidad.isNotBlank()) {
                    try {
                        val cantidadInt = cantidad.toInt()
                        val producto =
                            productosCamioneta.find { it.ARTICULO_ID == selectedProductId }

                        if (producto != null && cantidadInt > 0) {
                            val cantidadEnVenta =
                                saleProductsViewModel.getQuantityForProduct(producto)
                            val disponible = producto.EXISTENCIAS - cantidadEnVenta

                            if (cantidadInt <= disponible) {
                                onAddProduct(selectedProductId!!, cantidadInt)
                                cantidad = "1"
                                selectedProductName = ""
                                selectedProductId = null
                            }
                        }
                    } catch (e: NumberFormatException) {
                    }
                }
            },
            enabled = selectedProductId != null && cantidad.isNotBlank()
        ) {
            Text("Agregar a la venta")
        }
    }
}