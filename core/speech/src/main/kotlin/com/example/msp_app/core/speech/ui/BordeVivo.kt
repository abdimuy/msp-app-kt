package com.example.msp_app.core.speech.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.dp
import com.example.msp_app.core.designsystem.theme.LocalReduceMotion
import com.example.msp_app.core.designsystem.theme.MspTheme
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * **El borde vivo**: un cuadrado con gradiente cónico que gira detrás del
 * campo, recortado a la forma del campo por el `clip` de quien llama.
 *
 * Es la receta del mock (`.vivo::before` con `conic-gradient` + `rotate`), con
 * su misma advertencia escrita al lado: **sin el recorte, el cuadrado que gira
 * se sale y se come la pantalla**. Acá el recorte es el `clip(shapes.field)`
 * que este `Modifier` exige antes de sí mismo.
 *
 * En reposo pinta un anillo liso de `outline`: el mismo grosor, el mismo lugar,
 * y por eso el campo no cambia de tamaño al empezar a dictar.
 */
@Composable
internal fun Modifier.bordeVivo(activo: Boolean): Modifier {
    val colors = MspTheme.colors
    val quieto = LocalReduceMotion.current
    val giro = if (activo && !quieto) {
        rememberInfiniteTransition(label = "borde_vivo").animateFloat(
            initialValue = 0f,
            targetValue = VUELTA,
            animationSpec = infiniteRepeatable(
                animation = tween(DURACION_DEL_GIRO, easing = LinearEasing),
                repeatMode = RepeatMode.Restart
            ),
            label = "borde_vivo_angulo"
        ).value
    } else {
        0f
    }
    val colores = listOf(colors.brand, colors.statusTeal, colors.promise, colors.brand)
    return this.drawBehind {
        if (!activo) {
            drawRect(colors.outline)
            return@drawBehind
        }
        // El lado del cuadrado es la diagonal del campo: así, gire lo que gire,
        // nunca deja una esquina sin pintar.
        val lado = max(size.width, size.height) * DIAGONAL
        val esquina = Offset((size.width - lado) / 2f, (size.height - lado) / 2f)
        rotate(degrees = giro) {
            drawRect(
                brush = Brush.sweepGradient(colores, Offset(size.width / 2f, size.height / 2f)),
                topLeft = esquina,
                size = Size(lado, lado)
            )
        }
    }
}

/**
 * El alto de la barrita [indice] para un [nivel] dado.
 *
 * Pura y con su test: el perfil (centro más alto que los extremos) y el piso
 * (nunca cero, para que las barritas no desaparezcan en silencio) son las dos
 * cosas que se pueden equivocar acá.
 */
internal fun altoDeBarrita(indice: Int, nivel: Float) = (
    MIN_DE_BARRITA + (ALTO_DE_LAS_BARRITAS - MIN_DE_BARRITA) * perfil(indice) * nivel.coerceIn(
        0f,
        1f
    )
    )

/** 0f en los extremos, 1f al centro. Nueve barritas, centro en el índice 4. */
private fun perfil(indice: Int): Float {
    val centro = (BARRITAS - 1) / 2f
    return 1f - min(1f, abs(indice - centro) / centro) * CAIDA
}

/** `"0:06"`. Minutos sin cero a la izquierda, segundos con dos dígitos. */
internal fun cronometro(ms: Long): String {
    val totalSegundos = (ms / MILIS_POR_SEGUNDO).coerceAtLeast(0L)
    val minutos = totalSegundos / SEGUNDOS_POR_MINUTO
    val segundos = totalSegundos % SEGUNDOS_POR_MINUTO
    return "$minutos:${segundos.toString().padStart(2, '0')}"
}

/** El alto máximo de una barrita. También es el alto reservado de la fila. */
internal val ALTO_DE_LAS_BARRITAS = 18.dp

/** El piso: una barrita nunca desaparece, ni con el micrófono en silencio. */
private val MIN_DE_BARRITA = 4.dp

/** Nueve, como el mock. */
internal const val BARRITAS = 9

/** Cuánto bajan las barritas de los extremos respecto de la del centro. */
private const val CAIDA = 0.72f

private const val VUELTA = 360f

/** 3.4 s por vuelta, el del mock. */
private const val DURACION_DEL_GIRO = 3_400

/** Factor para que el cuadrado que gira tape las esquinas del campo. */
private const val DIAGONAL = 1.6f

private const val MILIS_POR_SEGUNDO = 1_000L

private const val SEGUNDOS_POR_MINUTO = 60L
