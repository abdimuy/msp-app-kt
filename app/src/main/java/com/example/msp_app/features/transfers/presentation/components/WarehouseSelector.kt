package com.example.msp_app.features.transfers.presentation.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.msp_app.data.api.services.warehouses.WarehouseListResponse

/**
 * Warehouse selector dropdown component
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WarehouseSelector(
    label: String,
    warehouses: List<WarehouseListResponse.Warehouse>,
    selectedWarehouse: WarehouseListResponse.Warehouse?,
    assignedUsers: List<com.example.msp_app.data.models.auth.User> = emptyList(),
    onWarehouseSelected: (WarehouseListResponse.Warehouse) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    excludeWarehouseId: Int? = null,
    error: String? = null
) {
    var expanded by remember { mutableStateOf(false) }

    val availableWarehouses = if (excludeWarehouseId != null) {
        warehouses.filter { it.ALMACEN_ID != excludeWarehouseId }.sortedBy { it.ALMACEN }
    } else {
        warehouses.sortedBy { it.ALMACEN }
    }

    Column(modifier = modifier) {
        ExposedDropdownMenuBox(
            expanded = expanded,
            onExpandedChange = { expanded = !expanded && enabled }
        ) {
            OutlinedTextField(
                value = selectedWarehouse?.let {
                    "${it.ALMACEN} (${it.EXISTENCIAS} unidades)"
                } ?: "",
                onValueChange = {},
                readOnly = true,
                enabled = enabled,
                label = { Text(label) },
                trailingIcon = {
                    ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded)
                },
                leadingIcon = null,
                modifier = Modifier
                    .menuAnchor()
                    .fillMaxWidth(),
                isError = error != null,
                supportingText = if (error != null) {
                    { Text(error) }
                } else null
            )

            ExposedDropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false }
            ) {
                availableWarehouses.forEach { warehouse ->
                    DropdownMenuItem(
                        text = {
                            Column {
                                Text(
                                    text = warehouse.ALMACEN,
                                    style = MaterialTheme.typography.bodyLarge
                                )
                                Text(
                                    text = "${warehouse.EXISTENCIAS} unidades disponibles",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        },
                        onClick = {
                            onWarehouseSelected(warehouse)
                            expanded = false
                        }
                    )
                }
            }
        }

        if (selectedWarehouse != null) {
            Column(
                modifier = Modifier.padding(start = 16.dp, top = 4.dp)
            ) {
                Text(
                    text = "ID: ${selectedWarehouse.ALMACEN_ID} • ${selectedWarehouse.EXISTENCIAS} productos en stock",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                // Show assigned vendors
                if (assignedUsers.isNotEmpty()) {
                    Text(
                        text = "Vendedores: ${assignedUsers.joinToString(", ") { it.NOMBRE }}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                } else {
                    Text(
                        text = "Vendedores: Sin asignar",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
            }
        }
    }
}
