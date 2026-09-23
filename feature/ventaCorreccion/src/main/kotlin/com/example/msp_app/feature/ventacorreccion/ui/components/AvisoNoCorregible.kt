package com.example.msp_app.feature.ventacorreccion.ui.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * Aviso de por qué una venta NO se puede corregir ahora mismo — [mensaje] es siempre una de las
 * cadenas de [com.example.msp_app.feature.ventacorreccion.domain.TextosCorreccion]
 * (`SE_ESTA_ENVIANDO`/`YA_SE_ENVIO`/`LA_REVISA_LA_OFICINA`, y desde el nivel 2 también
 * `CORRECCION_EN_CAMINO`/`LA_APLICO_LA_OFICINA`), decidida por el llamador (ver
 * [EntradaCorreccion]) — este componente es tonto a propósito, no conoce
 * [com.example.msp_app.feature.ventacorreccion.domain.EstadoCorreccion].
 */
@Composable
fun AvisoNoCorregible(mensaje: String, modifier: Modifier = Modifier) {
    Text(
        text = mensaje,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier
    )
}
