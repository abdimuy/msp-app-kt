package com.example.msp_app.core.designsystem.component

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Shape
import com.example.msp_app.core.designsystem.theme.MspColors
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/**
 * Ángulo CSS exacto del mockup del hero: `.hero{background:linear-gradient(150deg,brand,brand2)}`
 * (spec §6: gradiente plano, sin glow radial).
 */
const val HERO_GRADIENT_ANGLE_DEG: Double = 150.0

/**
 * Extremos (start, end) de un gradiente lineal por ángulo CSS, calculados a
 * mano para un [size] dado — extraído de [brandGradientBackground] como
 * función pura testeable ([com.example.msp_app.core.designsystem.component.BrandGradientMathTest]).
 * `Size`/`Offset` son clases de datos de `compose-ui` sin dependencia de
 * Robolectric/Android, por lo que esta función corre en un test JVM plano.
 *
 * La convención CSS mide el ángulo desde el eje +Y (arriba) en sentido
 * horario: a 0° el vector de dirección es `(0, -1)` (hacia arriba), a 150°
 * apunta hacia abajo-derecha. `length` es la proyección del rectángulo
 * `size` sobre esa dirección — la longitud de línea de gradiente que exige
 * la spec CSS (`|width·sin θ| + |height·cos θ|`) para que el primer y último
 * stop caigan exactamente en las esquinas correspondientes del rectángulo.
 */
internal fun brandGradientEndpoints(
    size: Size,
    angleDeg: Double = HERO_GRADIENT_ANGLE_DEG
): Pair<Offset, Offset> {
    val radians = Math.toRadians(angleDeg)
    val direction = Offset(sin(radians).toFloat(), -cos(radians).toFloat())
    val length = abs(size.width * direction.x) + abs(size.height * direction.y)
    val center = Offset(size.width / 2f, size.height / 2f)
    val half = Offset(direction.x * length / 2f, direction.y * length / 2f)
    return (center - half) to (center + half)
}

/**
 * Fondo de gradiente de marca 150° — gradiente PLANO de dos stops
 * (`colors.brand` → `colors.brand2`), sin glow radial (regla dura de spec
 * §6). Calculado a mano vía [brandGradientEndpoints], NO
 * `Brush.horizontalGradient`, para preservar el ángulo CSS exacto del
 * mockup (1:1 kollect §8.2). Clipa a [shape] antes de dibujar.
 */
fun Modifier.brandGradientBackground(colors: MspColors, shape: Shape): Modifier =
    this.clip(shape).drawBehind {
        val (start, end) = brandGradientEndpoints(size)
        drawRect(Brush.linearGradient(listOf(colors.brand, colors.brand2), start, end))
    }

/**
 * Alfas translúcidos consistentes para contenido apoyado sobre
 * [brandGradientBackground] — overline, body y label del hero, más el fondo
 * de los "stat wells" translúcidos (1:1 kollect §8.2).
 */
object OnBrandAlpha {
    const val OVERLINE: Float = 0.72f
    const val BODY: Float = 0.82f
    const val LABEL: Float = 0.75f
    const val WELL: Float = 0.12f
}
