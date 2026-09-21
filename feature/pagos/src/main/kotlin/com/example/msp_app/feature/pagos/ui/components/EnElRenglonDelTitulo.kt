package com.example.msp_app.feature.pagos.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.FirstBaseline
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import com.example.msp_app.core.designsystem.theme.MspTheme
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * **Una caja del alto de la primera línea del título, con su línea base.**
 *
 * Es la pieza que hace que lo que no tiene letra —el punto, el pin— obedezca la
 * misma regla que lo que sí tiene. Mide un renglón con el estilo del título
 * (mismo `fontScale`, misma fuente: lo que el `TextMeasurer` lee de la
 * composición) y reporta su `FirstBaseline`; el contenido va centrado en ese
 * renglón. Con `alignBy(FirstBaseline)` en el `Row`, la caja cae exactamente
 * sobre la primera línea del título, a cualquier escala y aunque el título se
 * vaya a dos renglones — sin un solo número a mano.
 */
@Composable
internal fun EnElRenglonDelTitulo(
    modifier: Modifier = Modifier,
    contenido: @Composable () -> Unit
) {
    val medidor = rememberTextMeasurer()
    val estilo = estiloDelTitulo()
    val renglon =
        remember(medidor, estilo) { medidor.measure(text = "", style = estilo, maxLines = 1) }
    Layout(content = contenido, modifier = modifier) { medibles, restricciones ->
        val colocable = medibles.single().measure(restricciones.copy(minWidth = 0, minHeight = 0))
        val alto = renglon.size.height
        layout(
            width = colocable.width,
            height = max(alto, colocable.height),
            alignmentLines = mapOf(FirstBaseline to renglon.firstBaseline.roundToInt())
        ) {
            colocable.place(0, (alto - colocable.height) / 2)
        }
    }
}

/** El estilo del título de la fila; uno solo para el texto y para [EnElRenglonDelTitulo]. */
@Composable
internal fun estiloDelTitulo(): TextStyle = MspTheme.type.bodyStrong
