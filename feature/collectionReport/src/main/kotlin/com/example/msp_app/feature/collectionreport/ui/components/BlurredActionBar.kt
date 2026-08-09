package com.example.msp_app.feature.collectionreport.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.unit.dp
import com.example.msp_app.core.designsystem.component.MspPrimaryFieldButton
import com.example.msp_app.core.designsystem.component.PrimaryFieldButtonVariant
import com.example.msp_app.core.designsystem.theme.MspTheme

private val ACTION_BAR_TOP_PADDING = 32.dp
private val ACTION_BAR_SIDE_PADDING = 16.dp
private val ACTION_BAR_BOTTOM_PADDING = 16.dp

// `MspPrimaryFieldButton` (16sp ExtraBold, sin slot de ícono ni `maxLines`, ver KDoc de
// [BlurredActionBar]) no cabe en una sola línea a peso igual en tres columnas de ~104dp
// (pantalla de 360dp) — "Compartir"/"Imprimir" son las etiquetas más largas del mockup.
// Pesos desiguales (en vez de `weight(1f)` parejo) le dan más ancho de texto a esas dos a
// costa de PDF (la más corta) — mismo total de fila, sin tocar el componente del DS.
private const val WIDE_LABEL_WEIGHT = 1.15f
private const val SHORT_LABEL_WEIGHT = 0.7f

/**
 * Barra de acciones anclada abajo, difuminada (mockup `.actions`): un `Row` con degradado
 * vertical `background.copy(alpha=0f) -> background` (transparente arriba, sólido abajo) —
 * NO un fondo sólido — y tres [MspPrimaryFieldButton]: Compartir (`Ghost`), Imprimir
 * (`Ghost`), PDF (`Primary`, brand sólido).
 *
 * **"pointer-events: none" del contenedor (gotcha del brief):** en Compose esto no requiere
 * ningún modificador especial — a diferencia de CSS, un `Box`/`Row` decorativo (fondo +
 * `Text`, sin `clickable`/`pointerInput` propio) nunca intercepta toques; el hit-testing de
 * Compose solo consume el evento donde SÍ hay un consumidor de puntero en el árbol (aquí, los
 * tres botones). Así que el área del degradado que no cubre ningún botón deja pasar el toque
 * al contenido detrás sin código extra — el equivalente exacto de `pointer-events:none` del
 * mockup es, aquí, la ausencia de un modificador de puntero en el `Row` contenedor.
 *
 * Sin ícono junto al texto (mockup trae `share`/`printer`/`file` SVG): `MspPrimaryFieldButton`
 * (`:core:designsystem`) no expone un slot de ícono — agregarlo sería modificar el design
 * system fuera del alcance de este archivo (Task 8 solo toca `ui/components/` del piloto);
 * se reusa el componente TAL CUAL (texto solo), desviación consciente documentada, no una
 * reinvención del botón.
 *
 * No lleva `contentPadding` para el scroll — eso lo define el caller
 * (`CollectionReportScreen`, ya lo hace vía `SCROLL_BOTTOM_CONTENT_PADDING`).
 */
@Composable
fun BlurredActionBar(
    onCompartirClick: () -> Unit,
    onImprimirClick: () -> Unit,
    onPdfClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val background = MspTheme.colors.background
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(listOf(background.copy(alpha = 0f), background))
            )
            .padding(
                start = ACTION_BAR_SIDE_PADDING,
                end = ACTION_BAR_SIDE_PADDING,
                top = ACTION_BAR_TOP_PADDING,
                bottom = ACTION_BAR_BOTTOM_PADDING
            ),
        horizontalArrangement = Arrangement.spacedBy(MspTheme.spacing.sm)
    ) {
        MspPrimaryFieldButton(
            text = "Compartir",
            variant = PrimaryFieldButtonVariant.Ghost,
            onClick = onCompartirClick,
            modifier = Modifier.weight(WIDE_LABEL_WEIGHT)
        )
        MspPrimaryFieldButton(
            text = "Imprimir",
            variant = PrimaryFieldButtonVariant.Ghost,
            onClick = onImprimirClick,
            modifier = Modifier.weight(WIDE_LABEL_WEIGHT)
        )
        MspPrimaryFieldButton(
            text = "PDF",
            variant = PrimaryFieldButtonVariant.Primary,
            onClick = onPdfClick,
            modifier = Modifier.weight(SHORT_LABEL_WEIGHT)
        )
    }
}
