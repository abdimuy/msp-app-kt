package com.example.msp_app.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.example.msp_app.core.utils.ResultState

/**
 * Configuración del selector offline
 */
data class OfflineSelectorConfig<T>(
    val itemLabel: (T) -> String,
    val itemId: (T) -> Any,
    val itemSubtitle: ((T) -> String)? = null,
    val placeholder: String = "Seleccionar",
    val label: String = "",
    val searchEnabled: Boolean = true,
    val searchPlaceholder: String = "Buscar...",
    val emptyMessage: String = "No hay elementos disponibles",
    val errorMessage: String = "Error al cargar datos",
    val isRequired: Boolean = false
)

/**
 * Selector dropdown genérico con soporte offline
 *
 * @param T Tipo de item
 * @param items Lista de items a mostrar
 * @param state Estado actual del selector
 * @param selectedItem Item seleccionado actual
 * @param onItemSelected Callback cuando se selecciona un item
 * @param config Configuración del selector
 * @param isOfflineMode Indica si está en modo offline
 * @param isExpired Indica si los datos están expirados
 * @param onRefresh Callback para refresh manual
 * @param modifier Modifier
 * @param enabled Habilitado/deshabilitado
 * @param error Mensaje de error opcional
 */
@Composable
fun <T> OfflineSelector(
    items: List<T>,
    state: ResultState<List<T>>,
    selectedItem: T?,
    onItemSelected: (T) -> Unit,
    config: OfflineSelectorConfig<T>,
    isOfflineMode: Boolean = false,
    isExpired: Boolean = false,
    onRefresh: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    error: String? = null
) {
    var expanded by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }

    // Filtrar items por búsqueda
    val filteredItems = remember(items, searchQuery) {
        if (searchQuery.isBlank()) {
            items
        } else {
            items.filter { item ->
                config.itemLabel(item).contains(searchQuery, ignoreCase = true) ||
                        config.itemSubtitle?.invoke(item)?.contains(searchQuery, ignoreCase = true) == true
            }
        }
    }

    Column(modifier = modifier) {
        // Header con label, badge offline y refresh
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (config.label.isNotBlank()) {
                Text(
                    text = config.label + if (config.isRequired) " *" else "",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Badge offline
                OfflineBadge(
                    isVisible = isOfflineMode,
                    isExpired = isExpired,
                    compact = true
                )

                // Botón refresh
                if (onRefresh != null) {
                    IconButton(
                        onClick = onRefresh,
                        enabled = state !is ResultState.Loading,
                        modifier = Modifier.size(32.dp)
                    ) {
                        if (state is ResultState.Loading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "Actualizar",
                                modifier = Modifier.size(20.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Dropdown principal
        Column {
            OutlinedTextField(
                value = selectedItem?.let { config.itemLabel(it) } ?: "",
                onValueChange = {},
                readOnly = true,
                enabled = enabled && state !is ResultState.Loading,
                placeholder = { Text(config.placeholder) },
                label = { Text("Seleccionar") },
                trailingIcon = {
                    if (state is ResultState.Loading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.ArrowDropDown,
                            contentDescription = null,
                            modifier = Modifier.clickable {
                                if (enabled && state !is ResultState.Loading) {
                                    expanded = true
                                }
                            }
                        )
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        if (enabled && state !is ResultState.Loading) {
                            expanded = true
                        }
                    },
                shape = RoundedCornerShape(12.dp),
                isError = error != null,
                supportingText = if (error != null) {
                    { Text(error, color = MaterialTheme.colorScheme.error) }
                } else null,
                singleLine = true
            )

            DropdownMenu(
                expanded = expanded,
                onDismissRequest = {
                    expanded = false
                    searchQuery = ""
                },
                modifier = Modifier.fillMaxWidth(0.9f)
            ) {
                // Campo de búsqueda (si está habilitado y hay más de 5 items)
                if (config.searchEnabled && items.size > 5) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = { Text(config.searchPlaceholder) },
                        leadingIcon = {
                            Icon(
                                Icons.Default.Search,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = { /* No-op */ })
                    )
                }

                // Estado de error
                when {
                    state is ResultState.Error -> {
                        DropdownMenuItem(
                            text = {
                                Text(
                                    text = config.errorMessage,
                                    color = MaterialTheme.colorScheme.error
                                )
                            },
                            onClick = { onRefresh?.invoke() }
                        )
                    }
                    // Lista vacía
                    filteredItems.isEmpty() -> {
                        DropdownMenuItem(
                            text = {
                                Text(
                                    text = if (searchQuery.isNotBlank())
                                        "No se encontraron resultados"
                                    else
                                        config.emptyMessage,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            },
                            onClick = {},
                            enabled = false
                        )
                    }
                    // Items
                    else -> {
                        filteredItems.forEach { item ->
                            DropdownMenuItem(
                                text = {
                                    Column {
                                        Text(
                                            text = config.itemLabel(item),
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.Medium
                                        )
                                        config.itemSubtitle?.let { getSubtitle ->
                                            val subtitle = getSubtitle(item)
                                            if (subtitle.isNotBlank()) {
                                                Text(
                                                    text = subtitle,
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                        }
                                    }
                                },
                                onClick = {
                                    onItemSelected(item)
                                    expanded = false
                                    searchQuery = ""
                                }
                            )
                        }
                    }
                }
            }
        }

        // Contador de items disponibles
        if (items.isNotEmpty() && state !is ResultState.Loading) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "${items.size} ${if (items.size == 1) "opción disponible" else "opciones disponibles"}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
