package com.example.msp_app.features.sales.components.zoneselector

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.msp_app.core.utils.ResultState
import com.example.msp_app.data.models.zone.ClientZone
import com.example.msp_app.features.zones.ZonesViewModel
import com.example.msp_app.ui.components.OfflineSelector
import com.example.msp_app.ui.components.OfflineSelectorConfig

/**
 * Selector de zonas con soporte offline
 *
 * Wrapper que usa OfflineSelector con configuración específica para zonas
 *
 * @param selectedZone Zona seleccionada actualmente
 * @param onZoneSelected Callback cuando se selecciona una zona
 * @param modifier Modifier
 * @param enabled Habilitado/deshabilitado
 * @param error Mensaje de error opcional (para validación)
 * @param isRequired Si el campo es obligatorio
 * @param viewModel ViewModel de zonas (inyectado automáticamente)
 */
@Composable
fun ZoneSelector(
    selectedZone: ClientZone?,
    onZoneSelected: (ClientZone) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    error: String? = null,
    isRequired: Boolean = true,
    viewModel: ZonesViewModel = viewModel()
) {
    val state by viewModel.state.collectAsState()
    val isOfflineMode by viewModel.isOfflineMode.collectAsState()

    // Cargar zonas al iniciar
    LaunchedEffect(Unit) {
        viewModel.fetch()
    }

    // Obtener items según el estado
    val items = when (val s = state) {
        is ResultState.Success -> s.data
        is ResultState.Offline -> s.data
        else -> emptyList()
    }

    // Detectar si los datos están expirados
    val isExpired = state is ResultState.Offline && (state as ResultState.Offline).isExpired

    OfflineSelector(
        items = items,
        state = state,
        selectedItem = selectedZone,
        onItemSelected = onZoneSelected,
        config = OfflineSelectorConfig(
            itemLabel = { it.ZONA_CLIENTE },
            itemId = { it.ZONA_CLIENTE_ID },
            itemSubtitle = null,
            placeholder = "Toca para seleccionar zona...",
            label = "Zona de Cliente",
            searchEnabled = true,
            searchPlaceholder = "Buscar zona...",
            emptyMessage = "No hay zonas disponibles",
            errorMessage = "Error al cargar zonas",
            isRequired = isRequired
        ),
        isOfflineMode = isOfflineMode,
        isExpired = isExpired,
        onRefresh = { viewModel.refresh() },
        modifier = modifier,
        enabled = enabled,
        error = error
    )
}

/**
 * Versión simplificada que usa IDs en lugar de objetos
 * Útil para integración con formularios que solo guardan IDs
 */
@Composable
fun ZoneSelectorSimple(
    selectedZoneId: Int?,
    selectedZoneName: String?,
    onZoneSelected: (zoneId: Int, zoneName: String) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    error: String? = null,
    isRequired: Boolean = true,
    viewModel: ZonesViewModel = viewModel()
) {
    val state by viewModel.state.collectAsState()
    val isOfflineMode by viewModel.isOfflineMode.collectAsState()

    // Cargar zonas al iniciar
    LaunchedEffect(Unit) {
        viewModel.fetch()
    }

    // Obtener items según el estado
    val items = when (val s = state) {
        is ResultState.Success -> s.data
        is ResultState.Offline -> s.data
        else -> emptyList()
    }

    // Encontrar la zona seleccionada por ID
    val selectedZone = items.find { it.ZONA_CLIENTE_ID == selectedZoneId }

    // Detectar si los datos están expirados
    val isExpired = state is ResultState.Offline && (state as ResultState.Offline).isExpired

    OfflineSelector(
        items = items,
        state = state,
        selectedItem = selectedZone,
        onItemSelected = { zone ->
            onZoneSelected(zone.ZONA_CLIENTE_ID, zone.ZONA_CLIENTE)
        },
        config = OfflineSelectorConfig(
            itemLabel = { it.ZONA_CLIENTE },
            itemId = { it.ZONA_CLIENTE_ID },
            itemSubtitle = null,
            placeholder = "Toca para seleccionar zona...",
            label = "Zona de Cliente",
            searchEnabled = true,
            searchPlaceholder = "Buscar zona...",
            emptyMessage = "No hay zonas disponibles",
            errorMessage = "Error al cargar zonas",
            isRequired = isRequired
        ),
        isOfflineMode = isOfflineMode,
        isExpired = isExpired,
        onRefresh = { viewModel.refresh() },
        modifier = modifier,
        enabled = enabled,
        error = error
    )
}
