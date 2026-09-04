package com.example.msp_app.feature.visitas.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp

/**
 * Los cinco glifos de los desenlaces de una visita.
 *
 * Mismo método que `PagosIconos` (Task 16) y por la misma razón: `MspIcons`
 * (`:core:designsystem`) es `internal` por decisión explícita de la Task 9 y no
 * se relitiga desde aquí, así que el glifo que no está en `material-icons-core`
 * se transcribe 1:1 del path SVG oficial de Material Icons (viewport 24×24) en
 * vez de arrastrar `material-icons-extended`, que pesa.
 *
 * **Color + ícono + texto, nunca solo color:** cada desenlace lleva su glifo
 * propio, y los dos que comparten familia de color —prometió y se negó, los dos
 * en `statusOverdue` por la tabla del Task 2— no comparten ni ícono ni texto.
 */
internal object VisitasIconos {

    /** No estaba → casa. De `material-icons-core`. */
    val NoEstaba: ImageVector = Icons.Filled.Home

    /** Visité, vuelvo → flecha de regreso. De `material-icons-core`. */
    val Vuelvo: ImageVector = Icons.Filled.Refresh

    /** Prometió → calendario. De `material-icons-core`. */
    val Prometio: ImageVector = Icons.Filled.DateRange

    /** Cita → reloj (Material `schedule`). */
    val Cita: ImageVector = materialVector("cita", SCHEDULE_PATH)

    /** Se negó → tache. De `material-icons-core`. */
    val Negado: ImageVector = Icons.Filled.Clear
}

/** Dimensión estándar de los íconos Material (property declaration → sin MagicNumber). */
private val ICON_DIMENSION = 24.dp

/** Viewport estándar de los paths Material 24×24. */
private const val ICON_VIEWPORT = 24f

private fun materialVector(name: String, pathData: String): ImageVector = ImageVector.Builder(
    name = "visitas_$name",
    defaultWidth = ICON_DIMENSION,
    defaultHeight = ICON_DIMENSION,
    viewportWidth = ICON_VIEWPORT,
    viewportHeight = ICON_VIEWPORT
).addPath(
    pathData = PathParser().parsePathString(pathData).toNodes(),
    fill = SolidColor(Color.Black)
).build()

/** Material `schedule` — reloj con manecillas. */
private const val SCHEDULE_PATH =
    "M11.99,2C6.47,2 2,6.48 2,12s4.47,10 9.99,10C17.52,22 22,17.52 22,12S17.52,2 11.99,2z" +
        "M12,20c-4.42,0 -8,-3.58 -8,-8s3.58,-8 8,-8 8,3.58 8,8 -3.58,8 -8,8z" +
        "M12.5,7H11v6l5.25,3.15 0.75,-1.23 -4.5,-2.67z"
