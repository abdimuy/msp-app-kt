package com.example.msp_app.feature.pagos.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp

/**
 * Los ocho glifos de estado de las pantallas de detalle.
 *
 * ## Por qué existe este objeto y no se reusa `MspIcons`
 *
 * `MspIcons` (`:core:designsystem`) es **`internal` por decisión explícita de
 * la Task 9** —su KDoc dice "nada fuera del módulo instancia estos íconos
 * directamente"— y esa decisión no se relitiga desde aquí. Lo que sí se copia
 * es su método: los glifos que no están en `material-icons-core` se transcriben
 * 1:1 del path SVG oficial de Material Icons (viewport 24×24) en vez de
 * arrastrar `material-icons-extended`, que pesa.
 *
 * Los tres paths transcritos son literalmente los mismos que `MspIcons` ya
 * transcribió (`contrast`, `schedule`, `radio_button_unchecked`). Esa
 * duplicación está reportada como preocupación de la Task 16: la salida
 * correcta es que un trabajo del design system publique `MspIcons`, no que
 * cada feature vuelva a copiar paths.
 */
internal object PagosIconos {

    /** Pagó → check. De `material-icons-core`. */
    val Pago: ImageVector = Icons.Filled.Check

    /** Abonó parcial → círculo medio lleno (Material `contrast`). */
    val Parcial: ImageVector = materialVector("parcial", CONTRAST_PATH)

    /** Visité, vuelvo → flecha de regreso. De `material-icons-core`. */
    val Vuelvo: ImageVector = Icons.Filled.Refresh

    /** Prometió CON fecha → calendario. De `material-icons-core`. */
    val Prometio: ImageVector = Icons.Filled.DateRange

    /**
     * Prometió SIN fecha → advertencia. No es el calendario a propósito: una
     * promesa sin fecha no es una cita, es un pendiente.
     */
    val PromesaSinFecha: ImageVector = Icons.Filled.Warning

    /** Se negó → tache. De `material-icons-core`. */
    val Negado: ImageVector = Icons.Filled.Clear

    /** Cita → reloj (Material `schedule`). */
    val Cita: ImageVector = materialVector("cita", SCHEDULE_PATH)

    /** No estaba → casa. De `material-icons-core`. */
    val NoEstaba: ImageVector = Icons.Filled.Home

    /** Sin trabajar → anillo vacío (Material `radio_button_unchecked`). */
    val SinTocar: ImageVector = materialVector("sin_tocar", CIRCLE_OUTLINE_PATH)
}

/** Dimensión estándar de los íconos Material (property declaration → sin MagicNumber). */
private val ICON_DIMENSION = 24.dp

/** Viewport estándar de los paths Material 24×24. */
private const val ICON_VIEWPORT = 24f

private fun materialVector(name: String, pathData: String): ImageVector = ImageVector.Builder(
    name = "pagos_$name",
    defaultWidth = ICON_DIMENSION,
    defaultHeight = ICON_DIMENSION,
    viewportWidth = ICON_VIEWPORT,
    viewportHeight = ICON_VIEWPORT
).addPath(
    pathData = PathParser().parsePathString(pathData).toNodes(),
    fill = SolidColor(Color.Black)
).build()

/** Material `contrast` — círculo con la mitad derecha llena. */
private const val CONTRAST_PATH =
    "M12,22c5.52,0 10,-4.48 10,-10S17.52,2 12,2 2,6.48 2,12s4.48,10 10,10z" +
        "M13,4.07c3.94,0.49 7,3.85 7,7.93s-3.05,7.44 -7,7.93L13,4.07z"

/** Material `radio_button_unchecked` — anillo (círculo vacío). */
private const val CIRCLE_OUTLINE_PATH =
    "M12,2C6.48,2 2,6.48 2,12s4.48,10 10,10 10,-4.48 10,-10S17.52,2 12,2z" +
        "M12,20c-4.42,0 -8,-3.58 -8,-8s3.58,-8 8,-8 8,3.58 8,8 -3.58,8 -8,8z"

/** Material `schedule` — reloj con manecillas. */
private const val SCHEDULE_PATH =
    "M11.99,2C6.47,2 2,6.48 2,12s4.47,10 9.99,10C17.52,22 22,17.52 22,12S17.52,2 11.99,2z" +
        "M12,20c-4.42,0 -8,-3.58 -8,-8s3.58,-8 8,-8 8,3.58 8,8 -3.58,8 -8,8z" +
        "M12.5,7H11v6l5.25,3.15 0.75,-1.23 -4.5,-2.67z"
