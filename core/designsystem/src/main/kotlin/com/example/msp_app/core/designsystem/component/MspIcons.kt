package com.example.msp_app.core.designsystem.component

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Warning
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp

/**
 * Íconos del design system Msp usados por los codificadores de estado
 * ([ChipStatus]/[MspStatusChip]). Fuente única de ícono para estado: los
 * componentes leen de aquí, no de `Icons.Filled.*` disperso.
 *
 * `Paid`/`Overdue` reusan `material-icons-core` (check/warning existen ahí);
 * `Partial`/`Pending`/`Promise` no están en el set core (solo en el
 * `material-icons-extended`, que no dependemos por peso), así que se
 * transcriben 1:1 de los paths SVG oficiales de Material Icons (viewport
 * 24×24) vía [PathParser] — mismos glifos, sin arrastrar la librería extended.
 *
 * `internal`: el punto de consumo soportado es [MspStatusChip]; nada fuera del
 * módulo instancia estos íconos directamente.
 */
internal object MspIcons {
    /** Pagado → check (de material-icons-core). */
    val Paid: ImageVector = Icons.Filled.Check

    /** Vencido → warning/triángulo (de material-icons-core). */
    val Overdue: ImageVector = Icons.Filled.Warning

    /** Parcial → círculo mitad-lleno (Material `contrast`). */
    val Partial: ImageVector = materialVector("Partial", CONTRAST_PATH)

    /** Pendiente → círculo vacío (Material `radio_button_unchecked`). */
    val Pending: ImageVector = materialVector("Pending", CIRCLE_OUTLINE_PATH)

    /** Promesa → reloj (Material `schedule`). */
    val Promise: ImageVector = materialVector("Promise", SCHEDULE_PATH)
}

/** Dimensión estándar de los íconos Material (property declaration → sin MagicNumber). */
private val ICON_DIMENSION = 24.dp

/** Viewport estándar de los paths Material 24×24. */
private const val ICON_VIEWPORT = 24f

/**
 * Construye un [ImageVector] Material 24×24 a partir de un path SVG. El fill
 * base es negro pero es irrelevante: `Icon` aplica `ColorFilter.tint` encima,
 * así el color efectivo lo pone el llamador (el `contentColor` del chip).
 */
private fun materialVector(name: String, pathData: String): ImageVector = ImageVector.Builder(
    name = "msp_$name",
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
