package com.example.msp_app.feature.pagos.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.msp_app.core.designsystem.theme.MspTheme
import com.example.msp_app.feature.pagos.domain.model.ClienteEnLista
import com.example.msp_app.feature.pagos.ui.components.BarraDeDetalle
import com.example.msp_app.feature.pagos.ui.components.ChipsDeSegmento
import com.example.msp_app.feature.pagos.ui.components.FilaDeCliente
import com.example.msp_app.feature.pagos.ui.components.VerTodos

/** `testTag` del campo de búsqueda. */
const val BUSCADOR_TAG: String = "pagos_buscador"

/** `testTag` del mensaje que se pinta cuando no queda ningún cliente. */
const val LISTA_VACIA_TAG: String = "pagos_lista_vacia"

/** El destino: conecta el ViewModel con el contenido puro. */
@Composable
fun ListaDeClientesScreen(
    viewModel: ListaDeClientesViewModel,
    onAtras: () -> Unit,
    onAbrirCliente: (Int) -> Unit,
    onAbrirVenta: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    ListaDeClientesContent(
        state = state,
        onAtras = onAtras,
        onBuscar = viewModel::buscar,
        onElegirSegmento = viewModel::elegirSegmento,
        onAbrirCliente = onAbrirCliente,
        onAbrirVenta = onAbrirVenta,
        onReintentar = viewModel::cargar,
        modifier = modifier
    )
}

/**
 * La lista de cobranza **por cliente**: una fila por puerta, con sus ventas
 * dentro.
 *
 * ## Qué reemplaza
 *
 * Las DOS listas que mostraban lo mismo con lógicas distintas: la de
 * `SalesScreen` (tres pestañas sobre el enum legado `ESTADO_COBRANZA`) y la de
 * Home ("VENTAS CERCANAS", ordenada por distancia a los centroides). Ninguna de
 * las dos se borró aquí — los puntos de entrada los recableó la Task 21, que
 * además retiró `SalesScreen`.
 *
 * ## La cercanía queda relegada, y por qué
 *
 * El orden de esta lista es el de cobranza: primero quien no ha abonado nada,
 * después las ventas más viejas. La distancia **no participa**. Ordenar por
 * cercanía optimiza gasolina, no cobranza: pone al principio al vecino que ya
 * pagó y manda al final la puerta donde no ha entrado un peso desde que se
 * entregó el mueble. La cercanía tiene su lugar —"estoy aquí, muéstrame su
 * venta"— y ese lugar es entrar por el mapa o por el bloque de cercanas, no
 * reordenar el día.
 *
 * Composable PURO sobre [ListaDeClientesUiState]: no lee puertos, no deriva
 * estados, no ordena y no emite telemetría. Ordenar aquí sería reordenar la
 * ruta en cada recomposición.
 */
@Composable
fun ListaDeClientesContent(
    state: ListaDeClientesUiState,
    onAtras: () -> Unit,
    onBuscar: (String) -> Unit,
    onElegirSegmento: (SegmentoDeCobranza) -> Unit,
    onAbrirCliente: (Int) -> Unit,
    onAbrirVenta: (Int) -> Unit,
    onReintentar: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MspTheme.colors.background)
    ) {
        Column(modifier = Modifier.padding(horizontal = MspTheme.spacing.md)) {
            BarraDeDetalle(onAtras = onAtras)
            Text(
                text = "clientes",
                style = MspTheme.type.screenTitle,
                color = MspTheme.colors.onSurface
            )
            Spacer(Modifier.height(MspTheme.spacing.sm))
            CampoDeBusqueda(query = state.query, onBuscar = onBuscar)
            Spacer(Modifier.height(MspTheme.spacing.sm))
            ChipsDeSegmento(
                seleccionado = state.segmento,
                conteos = state.conteos,
                onElegir = onElegirSegmento
            )
            Spacer(Modifier.height(MspTheme.spacing.sm))
        }
        when {
            state.cargando -> Cargando()
            state.fallo -> MensajeDeFallo(onReintentar)
            state.clientes.isEmpty() -> ListaVacia()
            else -> Clientes(
                clientes = state.clientes,
                onAbrirCliente = onAbrirCliente,
                onAbrirVenta = onAbrirVenta
            )
        }
    }
}

/**
 * **Una fila por cliente.** `key = { it.clienteId }` es literalmente el arreglo
 * del defecto: la lista vieja usaba `key = { it.DOCTO_CC_ID }` y un cliente con
 * dos ventas salía dos veces, como si fueran dos personas.
 */
@Composable
private fun Clientes(
    clientes: List<ClienteEnLista>,
    onAbrirCliente: (Int) -> Unit,
    onAbrirVenta: (Int) -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = MspTheme.spacing.md),
        verticalArrangement = Arrangement.spacedBy(MspTheme.spacing.md)
    ) {
        items(clientes, key = { it.clienteId }) { cliente ->
            FilaDeCliente(
                cliente = cliente,
                onAbrirCliente = { onAbrirCliente(cliente.clienteId) },
                onAbrirVenta = onAbrirVenta
            )
        }
    }
}

@Composable
private fun CampoDeBusqueda(query: String, onBuscar: (String) -> Unit) {
    OutlinedTextField(
        value = query,
        onValueChange = onBuscar,
        modifier = Modifier
            .fillMaxWidth()
            // El mínimo del plan es 50px; el default de Material ya está encima,
            // pero se fija para que no dependa de un default que puede cambiar.
            .heightIn(min = MspTheme.spacing.touchTarget)
            .testTag(BUSCADOR_TAG),
        placeholder = { Text("buscar cliente", style = MspTheme.type.body) },
        singleLine = true,
        shape = MspTheme.shapes.field,
        textStyle = MspTheme.type.body,
        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
            imeAction = ImeAction.Search
        ),
        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
        trailingIcon = {
            if (query.isNotEmpty()) {
                IconButton(
                    onClick = { onBuscar("") },
                    modifier = Modifier.heightIn(min = MspTheme.spacing.touchTarget)
                ) {
                    Icon(Icons.Default.Close, contentDescription = "borrar búsqueda")
                }
            }
        }
    )
}

@Composable
private fun ListaVacia() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(MspTheme.spacing.lg),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "no hay clientes",
            style = MspTheme.type.cardTitle,
            color = MspTheme.colors.onSurfaceMuted,
            textAlign = TextAlign.Center,
            modifier = Modifier.testTag(LISTA_VACIA_TAG)
        )
    }
}

@Composable
private fun MensajeDeFallo(onReintentar: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(MspTheme.spacing.lg),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "no se pudo cargar",
            style = MspTheme.type.cardTitle,
            color = MspTheme.colors.onSurface
        )
        Spacer(Modifier.height(MspTheme.spacing.sm))
        VerTodos("reintentar", onReintentar)
    }
}
