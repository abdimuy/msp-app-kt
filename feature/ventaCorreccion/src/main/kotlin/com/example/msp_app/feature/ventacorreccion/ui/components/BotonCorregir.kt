package com.example.msp_app.feature.ventacorreccion.ui.components

import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.example.msp_app.feature.ventacorreccion.domain.TextosCorreccion

/**
 * Entrada a la corrección de una venta local — sólo debe pintarse cuando el estado es
 * [com.example.msp_app.feature.ventacorreccion.domain.EstadoCorreccion.Corregible] (decisión del
 * llamador, ver [EntradaCorreccion]). Material3 liso, sin `MspTheme`: el mismo criterio que ya
 * usa el resto de `SaleDescriptionScreen`/`EditSaleScreen` en `:app`, donde este componente se
 * monta — hereda el `MaterialTheme` que envuelva al llamador (el de `:app` en producción, el de
 * `MspTheme` en los goldens de este módulo, que también expone un `MaterialTheme` M3 por debajo).
 */
@Composable
fun BotonCorregir(onClick: () -> Unit, modifier: Modifier = Modifier) {
    Button(onClick = onClick, modifier = modifier) {
        Text(TextosCorreccion.CORREGIR_VENTA)
    }
}
