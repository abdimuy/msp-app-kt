package com.example.msp_app.features.sales.components.zoneselector

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.msp_app.core.utils.ResultState
import com.example.msp_app.features.zones.ZonesViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun SimpleZoneSelector(
    zonesViewModel: ZonesViewModel,
    selectedZoneId: Int?,
    onZoneSelected: (Int, String) -> Unit,
    modifier: Modifier = Modifier,
    label: String = "Zona de Cliente",
    isRequired: Boolean = false,
    isError: Boolean = false,
    errorMessage: String? = null
) {
    val zonesState by zonesViewModel.clientZones.collectAsState()
    val isOfflineMode by zonesViewModel.isOfflineMode.collectAsState()
    val lastUpdate by zonesViewModel.lastUpdateTimestamp.collectAsState()

    var expanded by remember { mutableStateOf(false) }
    var selectedZoneName by remember { mutableStateOf("") }

    // Cargar zonas al inicio
    LaunchedEffect(Unit) {
        zonesViewModel.loadClientZones()
    }

    // Actualizar nombre de zona seleccionada
    LaunchedEffect(selectedZoneId, zonesState) {
        if (selectedZoneId != null) {
            val zones = when (val state = zonesState) {
                is ResultState.Success -> state.data.body
                else -> emptyList()
            }
            selectedZoneName =
                zones.find { it.ZONA_CLIENTE_ID == selectedZoneId }?.ZONA_CLIENTE ?: ""
        }
    }

    val zones = when (val state = zonesState) {
        is ResultState.Success -> state.data.body
        else -> emptyList()
    }

    Column(modifier = modifier) {
        // Header con título e indicadores
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label + if (isRequired) " *" else "",
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium
            )

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Indicador de modo offline
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

                // Botón de recarga
                IconButton(
                    onClick = { zonesViewModel.loadClientZones(forceRefresh = true) },
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        Icons.Default.Refresh,
                        contentDescription = "Recargar zonas",
                        modifier = Modifier.size(20.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Selector de zona
        Card(
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainer
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
        ) {
            Box(modifier = Modifier.padding(16.dp)) {
                when (val state = zonesState) {
                    is ResultState.Loading -> {
                        CircularProgressIndicator(
                            modifier = Modifier
                                .size(24.dp)
                                .align(Alignment.Center)
                        )
                    }

                    is ResultState.Error -> {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "Error al cargar zonas",
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Text(
                                text = state.message,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }

                    is ResultState.Success -> {
                        Column {
                            OutlinedTextField(
                                value = selectedZoneName,
                                onValueChange = {},
                                readOnly = true,
                                label = { Text("Seleccionar zona") },
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
                                shape = RoundedCornerShape(12.dp),
                                isError = isError,
                                supportingText = if (isError && errorMessage != null) {
                                    {
                                        Text(
                                            text = errorMessage,
                                            color = MaterialTheme.colorScheme.error
                                        )
                                    }
                                } else null
                            )

                            DropdownMenu(
                                expanded = expanded,
                                onDismissRequest = { expanded = false },
                                modifier = Modifier.fillMaxWidth(0.9f)
                            ) {
                                if (zones.isEmpty()) {
                                    DropdownMenuItem(
                                        text = {
                                            Text(
                                                "No hay zonas disponibles",
                                                color = MaterialTheme.colorScheme.onSurface.copy(
                                                    alpha = 0.6f
                                                )
                                            )
                                        },
                                        onClick = {},
                                        enabled = false
                                    )
                                } else {
                                    zones.forEach { zone ->
                                        DropdownMenuItem(
                                            text = {
                                                Column {
                                                    Text(
                                                        text = zone.ZONA_CLIENTE,
                                                        style = MaterialTheme.typography.bodyMedium,
                                                        fontWeight = FontWeight.Medium
                                                    )
                                                }
                                            },
                                            onClick = {
                                                onZoneSelected(
                                                    zone.ZONA_CLIENTE_ID,
                                                    zone.ZONA_CLIENTE
                                                )
                                                selectedZoneName = zone.ZONA_CLIENTE
                                                expanded = false
                                            }
                                        )
                                    }
                                }
                            }

                            // Información de última actualización
                            lastUpdate?.let { timestamp ->
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = "Última actualización: ${formatTimestamp(timestamp)}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                                )
                            }
                        }
                    }

                    ResultState.Idle -> {
                        Text(
                            text = "Cargando zonas...",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                    }
                }
            }
        }

        // Contador de zonas disponibles
        if (zones.isNotEmpty()) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "${zones.size} zona${if (zones.size != 1) "s" else ""} disponible${if (zones.size != 1) "s" else ""}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

private fun formatTimestamp(timestamp: Long): String {
    val sdf = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
    return sdf.format(Date(timestamp))
}