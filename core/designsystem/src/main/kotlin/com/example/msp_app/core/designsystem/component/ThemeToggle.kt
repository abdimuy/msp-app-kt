package com.example.msp_app.core.designsystem.component

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.isSpecified
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.msp_app.core.designsystem.theme.MspTheme

/** `testTag` de [MspThemeToggle] — localiza el control en compose-tests. */
internal const val THEME_TOGGLE_TAG = "msp_theme_toggle"

private val THEME_TOGGLE_SIZE = 40.dp

/** Lo que anuncia el botón cuando el tema YA es oscuro: tocarlo lleva a claro. */
const val DESCRIPCION_A_CLARO: String = "cambiar a tema claro"

/** Lo que anuncia el botón cuando el tema YA es claro: tocarlo lleva a oscuro. */
const val DESCRIPCION_A_OSCURO: String = "cambiar a tema oscuro"

/**
 * Botón sol/luna del design system: icon-surface de 40dp, shape
 * [MspTheme.shapes.control] (1:1 kollect §7.2, `ThemeToggle`). Decide entre
 * dos mecanismos de flip de tema:
 *
 * 1. **Reveal circular** (Mecanismo B): si [LocalThemeReveal] tiene un
 *    controller instalado (el `ThemeRevealRoot` de Plan 5 provee uno) Y este
 *    botón ya reportó su propio centro en pantalla (`onGloballyPositioned` →
 *    `boundsInRoot().center`), pide `reveal.requestRevealFrom(center)` — el
 *    dueño de la reveal (fuera de este módulo) hace el resto.
 * 2. **Crossfade fallback** (Mecanismo A): sin host instalado, o mientras el
 *    centro todavía no se reportó (`Offset.Unspecified` en el primer frame),
 *    cae directo a [onToggle] — el flip que anima
 *    [com.example.msp_app.core.designsystem.theme.MspTheme] vía
 *    `lerpMspColors`.
 *
 * Sin animación propia del ícono (swap instantáneo moon/sun); la única
 * animación asociada a este control vive en quien la maneje (la reveal o el
 * crossfade), ambas ya con su propio escape hatch — no hay nada extra que
 * gatear aquí por `rememberReducedMotionEnabled()`.
 *
 * ## El `contentDescription` que el port había perdido
 *
 * El `ThemeToggle` de kollect anuncia **la acción, no el estado**
 * (`"Cambiar a tema claro"` cuando ya está oscuro, y al revés), y este port lo
 * traía en `null`: un botón invisible para TalkBack. Se restituye. En
 * minúscula, como los otros controles de encabezado del repo (`"atrás"`,
 * `"borrar búsqueda"`) — la única desviación del 1:1, y es la convención de
 * texto de la app.
 *
 * Que describa la ACCIÓN es también lo que lo vuelve medible: un compose-test
 * puede afirmar que el tap cambió el tema mirando cómo cambió esta cadena, sin
 * leer píxeles.
 */
@Composable
fun MspThemeToggle(darkTheme: Boolean, onToggle: () -> Unit, modifier: Modifier = Modifier) {
    val reveal = LocalThemeReveal.current
    var center by remember { mutableStateOf(Offset.Unspecified) }
    MspSurface(
        modifier = modifier
            .minimumInteractiveComponentSize()
            .size(THEME_TOGGLE_SIZE)
            .onGloballyPositioned { center = it.boundsInRoot().center }
            .testTag(THEME_TOGGLE_TAG),
        shape = MspTheme.shapes.control,
        onClick = {
            if (reveal != null && center.isSpecified) {
                reveal.requestRevealFrom(center)
            } else {
                onToggle()
            }
        }
    ) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Icon(
                imageVector = if (darkTheme) MspIcons.Moon else MspIcons.Sun,
                contentDescription = if (darkTheme) DESCRIPCION_A_CLARO else DESCRIPCION_A_OSCURO,
                tint = MspTheme.colors.onSurface
            )
        }
    }
}
