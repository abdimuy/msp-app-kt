package com.example.msp_app.features.sales.components.cityselector

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.msp_app.core.utils.ResultState
import com.example.msp_app.data.models.city.City
import com.example.msp_app.data.models.city.normalizeCiudad
import com.example.msp_app.features.cities.CitiesViewModel
import com.example.msp_app.ui.components.OfflineSelector
import com.example.msp_app.ui.components.OfflineSelectorConfig

/** Texto del escape a captura libre. Público para que las pruebas no lo dupliquen. */
const val CITY_NOT_LISTED_LABEL = "mi ciudad no está"

/** Texto del regreso al catálogo. */
const val CITY_BACK_TO_CATALOG_LABEL = "elegir del catálogo"

/** Aviso de que una ciudad fuera de catálogo no avanza sola. */
const val CITY_FREE_TEXT_HINT = "la oficina la revisa"

/**
 * Selector de ciudad con soporte offline.
 *
 * Envoltura de [OfflineSelector] al estilo de
 * [com.example.msp_app.features.sales.components.zoneselector.ZoneSelector], con
 * dos reglas propias que definen el diseño:
 *
 * **1. Ciudad y estado viajan juntos.** El catálogo abarca varios estados, así
 * que el estado no es un campo elegible aparte: cada fila lo trae y el único
 * callback de selección, [onCitySelected], entrega el objeto [City] completo. Por
 * eso la etiqueta visible combina ambos (`TEHUACAN · PUEBLA`): así el vendedor ve
 * qué estado se está llevando y puede detectar las filas del catálogo cuyo
 * `ESTADO_ID` contradice al nombre (`QUERETARO` guardado bajo `CIUDAD DE MEXICO`).
 *
 * **2. Una ciudad faltante NO bloquea capturar; bloquea aplicar.** Si la ciudad
 * no está en el catálogo el vendedor toca [CITY_NOT_LISTED_LABEL] y escribe el
 * texto libre: la venta se captura igual y se queda en borrador hasta que la
 * oficina resuelva la fila. La venta NUNCA inserta en `CIUDADES` — es tabla de
 * Microsip compartida con la oficina — y el servidor rechaza al aplicar con
 * `ciudad_no_en_catalogo`.
 *
 * @param ciudad Texto de ciudad capturado actualmente
 * @param estado Estado que vino con la ciudad del catálogo (vacío en texto libre)
 * @param enCatalogo `true` si [ciudad] proviene de una fila del catálogo
 * @param onCitySelected Selección desde el catálogo — entrega ciudad y estado juntos
 * @param onFreeTextChanged Captura libre — el llamador debe limpiar el estado
 * @param modifier Modifier
 * @param enabled Habilitado/deshabilitado
 * @param error Mensaje de error opcional (para validación)
 * @param isRequired Si el campo es obligatorio
 * @param viewModel ViewModel de ciudades (inyectado automáticamente)
 */
@Composable
fun CitySelector(
    ciudad: String,
    estado: String,
    enCatalogo: Boolean,
    onCitySelected: (City) -> Unit,
    onFreeTextChanged: (String) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    error: String? = null,
    isRequired: Boolean = true,
    viewModel: CitiesViewModel = viewModel()
) {
    val state by viewModel.state.collectAsState()
    val isOfflineMode by viewModel.isOfflineMode.collectAsState()

    // Cargar ciudades al iniciar
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

    // Fila del catálogo que corresponde al texto capturado. Se compara con
    // `normalizeCiudad` —el mismo criterio del servidor— para que un borrador
    // restaurado vuelva a quedar ligado a su fila pese a acentos, mayúsculas o
    // los espacios finales que arrastra el catálogo de producción.
    val match = remember(items, ciudad) {
        if (ciudad.isBlank()) {
            null
        } else {
            val objetivo = normalizeCiudad(ciudad)
            items.firstOrNull { normalizeCiudad(it.ciudad) == objetivo }
        }
    }

    // El modo libre es derivado, no un estado paralelo: se activa cuando el
    // vendedor lo pide explícitamente, o cuando ya hay catálogo cargado y el
    // texto capturado no corresponde a ninguna fila (borrador viejo, ciudad que
    // la oficina aún no da de alta). El guard `items.isNotEmpty()` evita saltar a
    // texto libre mientras el catálogo todavía carga.
    var modoLibreForzado by rememberSaveable { mutableStateOf(false) }
    val sinCoincidencia = ciudad.isNotBlank() && items.isNotEmpty() && match == null
    val modoLibre = modoLibreForzado || sinCoincidencia

    // Reconciliación del borrador: si el texto guardado sí corresponde a una fila
    // pero llegó sin estado, se vuelve a propagar la fila completa. Sin esto la
    // ciudad quedaría sin su estado tras restaurar, que es justo la separación
    // que este componente existe para impedir.
    LaunchedEffect(match, enCatalogo, modoLibre) {
        if (!modoLibre && !enCatalogo && match != null) {
            onCitySelected(match)
        }
    }

    Column(modifier = modifier) {
        if (modoLibre) {
            OutlinedTextField(
                value = ciudad,
                onValueChange = onFreeTextChanged,
                label = { Text(etiqueta(isRequired)) },
                enabled = enabled,
                singleLine = true,
                isError = error != null,
                supportingText = {
                    if (error != null) {
                        Text(error, color = MaterialTheme.colorScheme.error)
                    } else {
                        Text(CITY_FREE_TEXT_HINT)
                    }
                },
                shape = RoundedCornerShape(15.dp),
                modifier = Modifier.fillMaxWidth()
            )

            TextButton(
                onClick = { modoLibreForzado = false },
                enabled = enabled
            ) {
                Text(CITY_BACK_TO_CATALOG_LABEL)
            }
        } else {
            OfflineSelector(
                items = items,
                state = state,
                selectedItem = match,
                onItemSelected = onCitySelected,
                config = OfflineSelectorConfig(
                    itemLabel = ::etiquetaCiudad,
                    itemId = { it.ciudadId },
                    itemSubtitle = null,
                    placeholder = "toca para seleccionar ciudad",
                    label = "Ciudad",
                    searchEnabled = true,
                    searchPlaceholder = "buscar ciudad",
                    emptyMessage = "no hay ciudades disponibles",
                    errorMessage = "error al cargar ciudades",
                    isRequired = isRequired
                ),
                isOfflineMode = isOfflineMode,
                isExpired = isExpired,
                onRefresh = { viewModel.refresh() },
                modifier = Modifier.fillMaxWidth(),
                enabled = enabled,
                error = error
            )

            TextButton(
                onClick = { modoLibreForzado = true },
                enabled = enabled,
                modifier = Modifier.semantics {
                    contentDescription = CITY_NOT_LISTED_LABEL
                }
            ) {
                Text(CITY_NOT_LISTED_LABEL)
            }
        }

        // El estado nunca se muestra como campo propio ni editable: se refleja
        // aquí, en la ciudad seleccionada, para que quede claro que es un dato de
        // la fila y no algo que el vendedor elija.
        if (!modoLibre && estado.isNotBlank()) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = estado,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/** `TEHUACAN · PUEBLA`: la ciudad nunca se lee sin su estado. */
private fun etiquetaCiudad(city: City): String =
    if (city.estado.isBlank()) city.ciudad else "${city.ciudad} · ${city.estado}"

private fun etiqueta(isRequired: Boolean): String = if (isRequired) "Ciudad *" else "Ciudad"
