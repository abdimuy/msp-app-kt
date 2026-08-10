package com.example.msp_app.feature.collectionreport.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
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

// Fracción de alto donde el degradado llega a opacidad SÓLIDA — mockup
// `linear-gradient(to bottom, var(--bg0) 0%, var(--bg) 44%)`: de 0% a 44% transparente→sólido,
// de 44% a 100% se QUEDA sólido (comportamiento implícito de CSS: un gradiente sostiene el
// último color más allá de su última parada). Fix round 1 (Important 1): un `Brush
// .verticalGradient` de SOLO 2 paradas (`0f to alpha0`, `1f to background`) interpola alpha
// en TODA la altura del `Row` — para cuando llega a la zona de los botones, el fondo sigue sin
// ser opaco del todo y el contenido de atrás (chips Condonado/Visitas) se transparenta a
// través de Compartir/Imprimir. Una tercera parada en [SOLID_STOP_FRACTION] repitiendo
// `background` (mismo color, mismo alpha=1) fuerza ese tramo final a opacidad constante.
private const val SOLID_STOP_FRACTION = 0.44f

/**
 * Barra de acciones anclada abajo, difuminada (mockup `.actions`): un `Row` con degradado
 * vertical de 3 paradas — transparente (0%) → sólido ([SOLID_STOP_FRACTION]) → se mantiene
 * sólido (100%) — NO un fondo sólido plano ni un degradado de 2 paradas que nunca termina de
 * cerrar (ver [SOLID_STOP_FRACTION]) — y tres [MspPrimaryFieldButton]: Compartir (`Ghost`),
 * Imprimir (`Ghost`), PDF (`Primary`, brand sólido).
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
 *
 * **Inset de la barra de navegación del sistema (fix defecto visual):** el degradado se pinta
 * ANTES de `navigationBarsPadding()` en la cadena de modificadores, así que sigue edge-to-edge
 * (se extiende detrás de la barra de navegación, igual que el fondo del status bar en
 * [com.example.msp_app.feature.collectionreport.ui.CollectionReportContent]); `.padding()` de
 * los botones va DESPUÉS del inset, así que son los botones (no el fondo) los que suben para
 * quedar arriba de la barra de navegación — nunca tapados por los `||| ◯ ‹` del sistema.
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
                Brush.verticalGradient(
                    colorStops = arrayOf(
                        0f to background.copy(alpha = 0f),
                        SOLID_STOP_FRACTION to background,
                        1f to background
                    )
                )
            )
            .navigationBarsPadding()
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
