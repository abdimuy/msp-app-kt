package com.example.msp_app.feature.ubicacion.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.msp_app.core.designsystem.theme.MspTheme
import com.example.msp_app.core.geo.LugarAgrupado
import com.example.msp_app.feature.ubicacion.domain.TipoDeLugar
import com.example.msp_app.feature.ubicacion.ui.UbicacionUiState

/** `testTag` del aviso de mudanza. */
const val MUDANZA_TAG: String = "ubicacion_mudanza"

/** `testTag` del aviso de que nadie se ganó el título. */
const val SIN_PUERTA_TAG: String = "ubicacion_sin_puerta"

/** `testTag` del botón que sale a navegar. */
const val COMO_LLEGAR_TAG: String = "ubicacion_como_llegar"

/**
 * La hoja al pie: qué dirección es, qué se sabe del lugar y la salida a navegar.
 *
 * **No es arrastrable y no tiene asa.** Un asa promete que se puede subir para
 * ver más, y aquí no hay más. Prometer un gesto que no existe es una forma que
 * miente.
 */
@Composable
fun HojaDeLosLugares(
    state: UbicacionUiState,
    onComoLlegar: (LugarAgrupado) -> Unit,
    modifier: Modifier = Modifier
) {
    val puerta = state.lugares.firstOrNull { it.tipo == TipoDeLugar.LA_PUERTA }
    Surface(
        color = MspTheme.colors.surface,
        shape = RoundedCornerShape(topStart = RADIO_DE_LA_HOJA, topEnd = RADIO_DE_LA_HOJA),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().systemBarsPadding().padding(MspTheme.spacing.md),
            verticalArrangement = Arrangement.spacedBy(MspTheme.spacing.xs)
        ) {
            Text(
                text = state.direccion.ifBlank { SIN_DIRECCION },
                style = MspTheme.type.cardTitle,
                color = MspTheme.colors.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            // **El aviso de mudanza sale solo.** El cobrador no sabe que tiene
            // que buscarla, así que esconderla detrás de un filtro equivale a no
            // tenerla. Medido: le pasa a cerca del 5 % de los clientes.
            if (state.pareceMudanza) {
                Aviso(MUDANZA, MUDANZA_TAG, MspTheme.colors.statusPending)
            }

            if (state.sinNingunPunto) {
                Text(
                    text = SIN_PUNTOS,
                    style = MspTheme.type.caption,
                    color = MspTheme.colors.onSurfaceMuted
                )
            } else {
                // Nadie se ganó el título. Se dice —en vez de señalar el grupo
                // mayor como si fuera la puerta, que es la mentira silenciosa
                // que esta pantalla existe para no cometer— y además se explica
                // por qué, en el renglón de abajo.
                if (state.sinPuertaMedida) {
                    Aviso(SIN_PUERTA, SIN_PUERTA_TAG, MspTheme.colors.onSurfaceMuted)
                }
                // **El detalle del lugar principal va SIEMPRE, haya título o no.**
                // Ahí viven las dos cuentas —cuántos clientes y cuántos
                // cobradores comparten el punto—, que es justo lo que la app dice
                // **en vez de nombrar el lugar**. Dejarlas sólo en el globo del
                // marcador las escondía detrás de un toque que el cobrador no
                // tiene por qué adivinar.
                //
                // Lo cazó un golden: sin esto, "punto compartido" y "puras
                // transferencias" producían imágenes **idénticas byte a byte**,
                // porque los dos caían en el mismo aviso genérico.
                val principal = puerta ?: state.lugares.firstOrNull()
                if (principal != null) {
                    Text(
                        text = principal.detalle,
                        style = MspTheme.type.caption,
                        color = MspTheme.colors.onSurfaceMuted
                    )
                }
            }

            // "Cómo llegar" sólo cuando hay una puerta que se ganó el título.
            // Mandar a navegar hacia un punto compartido sería mandar al
            // cobrador a la tienda con cara de certeza.
            if (puerta != null) {
                BotonDeNavegar(onClick = { onComoLlegar(puerta.lugar) })
            }
        }
    }
}

@Composable
private fun Aviso(texto: String, etiqueta: String, color: androidx.compose.ui.graphics.Color) {
    Text(
        text = texto,
        style = MspTheme.type.caption,
        color = color,
        modifier = Modifier.testTag(etiqueta)
    )
}

@Composable
private fun BotonDeNavegar(onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        color = MspTheme.colors.brand,
        shape = MspTheme.shapes.control,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = MspTheme.spacing.xs)
            .testTag(COMO_LLEGAR_TAG)
    ) {
        Box(
            modifier = Modifier.fillMaxWidth().padding(MspTheme.spacing.sm + 2.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = COMO_LLEGAR,
                style = MspTheme.type.buttonSmall,
                color = MspTheme.colors.onBrand
            )
        }
    }
}

private val RADIO_DE_LA_HOJA = 28.dp

/** Dos a cuatro palabras, como manda el registro de UI del repo. */
private const val MUDANZA = "Parece que se mudó"
private const val SIN_PUERTA = "Sin punto propio medido"
private const val SIN_PUNTOS = "Sin ubicación medida"
private const val COMO_LLEGAR = "Cómo llegar"
private const val SIN_DIRECCION = "Sin dirección"
