package com.example.msp_app.feature.collectionreport.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import com.example.msp_app.core.designsystem.component.MspThemeRevealHost
import com.example.msp_app.core.designsystem.theme.FontSizeLevel
import com.example.msp_app.core.designsystem.theme.LocalFontSizeLevel
import com.example.msp_app.core.designsystem.theme.MspTheme
import com.example.msp_app.core.designsystem.theme.compressed
import com.example.msp_app.core.designsystem.theme.mspTypography

/**
 * [MspTheme] con la rampa tipográfica comprimida del nivel de letra vigente (Task "Aplicar la
 * rampa comprimida al reporte", opción (a) del brief): construye
 * `mspTypography().compressed(LocalFontSizeLevel.current)` y la pasa como `typography` —
 * SEGURO porque TODO el texto del reporte lee `MspTheme.type.*` (auditado: cero `.sp`
 * hardcodeado fuera de `MspType.kt`), así que no hay ningún `Text` suelto que quede sin
 * comprimir ni que reciba double-scaling por accidente.
 *
 * **Neutraliza el double-scaling** (ver KDoc de [com.example.msp_app.core.designsystem.theme.mspCompressedSp]):
 * la rampa comprimida YA devuelve el tamaño final para el nivel elegido; el `fontScale` lineal
 * que `MainActivity` aplica globalmente (Opción C, `máx(nivel, fontScale del SO)`) lo
 * multiplicaría una segunda vez si se dejara pasar. Por eso este composable reinstala
 * [LocalDensity] con `fontScale = 1f` (conservando `density`, que gobierna `dp`→`px` y es
 * ajeno a esto) alrededor de [content] — DENTRO de [MspTheme], para que solo el subárbol que
 * consume la tipografía comprimida quede neutralizado, nunca el resto de la app.
 *
 * **Cobertura / lo NO cubierto:** esto compensa el `fontScale` LINEAL de la app
 * (`FontSizeLevel.nominalScale`, Opción C) — la rampa comprimida está indexada por el enum
 * discreto [FontSizeLevel] (3 niveles), no por el `fontScale` continuo real del sistema
 * operativo (mismo criterio ya documentado en el KDoc de [LocalFontSizeLevel]: elegir
 * `MUY_GRANDE` en la app "basta para forzar Tier 2 aunque el OS esté en 1.0f"). Si el usuario
 * tiene una accesibilidad del SO MÁS agresiva que el nivel elegido en la app, ese exceso queda
 * neutralizado también dentro de este subárbol — una limitación heredada del diseño de 3
 * niveles, no algo que este composable intente resolver.
 */
@Composable
private fun ReportMspTheme(
    darkTheme: Boolean,
    animateColors: Boolean,
    content: @Composable () -> Unit
) {
    val fontSizeLevel = LocalFontSizeLevel.current
    val typography = remember(fontSizeLevel) { mspTypography().compressed(fontSizeLevel) }
    val baseDensity = LocalDensity.current
    MspTheme(darkTheme = darkTheme, animateColors = animateColors, typography = typography) {
        CompositionLocalProvider(
            LocalDensity provides Density(density = baseDensity.density, fontScale = 1f)
        ) {
            content()
        }
    }
}

/**
 * Composition root del reveal circular de tema del **reporte de cobranza** — envuelve
 * [content] en [ReportMspTheme] (rampa comprimida) y le pone encima el mecanismo de reveal.
 * El caller NO debe volver a envolver en [MspTheme] por su cuenta.
 *
 * **El mecanismo ya no vive acá (Ruling BP).** Estas ~80 líneas —`GraphicsLayer` por frame,
 * `Animatable` del radio, `clipPath(ClipOp.Difference)` del snapshot viejo— se fueron a
 * [MspThemeRevealHost] (`:core:designsystem`) porque mientras estuvieron dentro de este feature
 * **el mismo `MspThemeToggle` se comportaba distinto en dos pantallas de la app**: reveal
 * circular acá, crossfade en cualquier otra, ya que ninguna otra podía instalar un host. Este
 * composable se queda con lo que sí es del reporte —**qué** tema envuelve (la tipografía
 * comprimida) y **qué** cuenta como reduce-motion ([rememberReportReducedMotion]: accesibilidad
 * del SO o la preferencia propia de la app)— y delega el **cómo** de la animación.
 *
 * **Estado hoisted, no propio:** [darkTheme]/[onToggleTheme] son controlados por el caller
 * (mismo criterio que [com.example.msp_app.core.designsystem.component.MspThemeToggle]) — este
 * root no decide DÓNDE vive la fuente de verdad del tema; solo orquesta la animación alrededor
 * de un flip que el caller ya sabe hacer.
 *
 * El mecanismo, sus dos ramas y por qué la de reduce-motion no envuelve en ningún `Box` están
 * documentados en el KDoc de [MspThemeRevealHost].
 */
@Composable
fun ThemeRevealRoot(
    darkTheme: Boolean,
    onToggleTheme: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    MspThemeRevealHost(
        onToggleTheme = onToggleTheme,
        reducedMotion = rememberReportReducedMotion(),
        modifier = modifier,
        tema = { animateColors, contenido ->
            ReportMspTheme(
                darkTheme = darkTheme,
                animateColors = animateColors,
                content = contenido
            )
        },
        content = content
    )
}
