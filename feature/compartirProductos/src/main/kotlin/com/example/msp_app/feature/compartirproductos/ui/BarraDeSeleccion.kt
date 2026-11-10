package com.example.msp_app.feature.compartirproductos.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.msp_app.feature.compartirproductos.domain.SeleccionDeProductos

/** La barra del catálogo mientras hay productos marcados: «Compartir (N)» y «Cancelar». */
@Composable
fun BarraDeSeleccion(
    cantidad: Int,
    alCompartir: () -> Unit,
    alCancelar: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        tonalElevation = 3.dp,
        modifier = modifier.fillMaxWidth().testTag(EtiquetasDeSeleccion.BARRA)
    ) {
        Row(
            modifier = Modifier.navigationBarsPadding().padding(
                horizontal = 16.dp,
                vertical = 10.dp
            ),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedButton(
                onClick = alCancelar,
                modifier = Modifier.testTag(EtiquetasDeSeleccion.CANCELAR)
            ) {
                Text("Cancelar")
            }
            Button(
                onClick = alCompartir,
                enabled = cantidad > 0,
                modifier = Modifier.weight(1f).testTag(EtiquetasDeSeleccion.COMPARTIR)
            ) { Text("Compartir ($cantidad)", style = MaterialTheme.typography.labelLarge) }
        }
    }
}

/** Para `rememberSaveable`: la selección sobrevive a girar la pantalla. */
val SeleccionDeProductosSaver: Saver<SeleccionDeProductos, Any> =
    listSaver(save = { it.ids }, restore = { SeleccionDeProductos(it) })
