package com.example.msp_app.feature.visitas.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
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

    /**
     * Los tres orígenes de la hoja del «+», **a trazo**.
     *
     * A trazo y no de relleno porque el mock los dibuja así (`fill="none"`,
     * `stroke-width="2"`), y porque en un recuadro tintado de 42dp un glifo
     * relleno se lee como una mancha. Mismo método que el resto del archivo: el
     * path se transcribe en vez de arrastrar `material-icons-extended`, que pesa
     * por tres glifos.
     */
    val Camara: ImageVector = trazado("camara", CUERPO_DE_CAMARA, LENTE_DE_CAMARA)

    /** Galería → marco con paisaje. */
    val Galeria: ImageVector = trazado("galeria", MARCO, SOL_DEL_MARCO, CERROS_DEL_MARCO)

    /** Archivo → hoja con la esquina doblada. Es el que alcanza un PDF. */
    val Archivo: ImageVector = trazado("archivo", HOJA, ESQUINA_DE_LA_HOJA)

    /** La punta que cierra cada renglón de la hoja de orígenes. */
    val Chevron: ImageVector = trazado("chevron", PUNTA)
}

/** El grosor del trazo de los glifos de origen, copiado del mock. */
private const val GROSOR_DEL_TRAZO = 2f

private fun trazado(nombre: String, vararg lineas: String): ImageVector {
    val builder = ImageVector.Builder(
        name = "visitas_$nombre",
        defaultWidth = ICON_DIMENSION,
        defaultHeight = ICON_DIMENSION,
        viewportWidth = ICON_VIEWPORT,
        viewportHeight = ICON_VIEWPORT
    )
    lineas.forEach { linea ->
        builder.addPath(
            pathData = PathParser().parsePathString(linea).toNodes(),
            stroke = SolidColor(Color.Black),
            strokeLineWidth = GROSOR_DEL_TRAZO,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round
        )
    }
    return builder.build()
}

private const val CUERPO_DE_CAMARA =
    "M3 8.5A2 2 0 0 1 5 6.5h2l1.2-2h7.6L17 6.5h2a2 2 0 0 1 2 2v9a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2z"

private const val LENTE_DE_CAMARA = "M8.4 13a3.6 3.6 0 1 0 7.2 0a3.6 3.6 0 1 0-7.2 0"

private const val MARCO =
    "M5.5 4.5h13a2.5 2.5 0 0 1 2.5 2.5v10a2.5 2.5 0 0 1-2.5 2.5h-13A2.5 2.5 0 0 1 3 17V7" +
        "a2.5 2.5 0 0 1 2.5-2.5z"

private const val SOL_DEL_MARCO = "M6.9 10a1.6 1.6 0 1 0 3.2 0a1.6 1.6 0 1 0-3.2 0"

private const val CERROS_DEL_MARCO = "M3.5 17l5-5 4.5 4.5 3-2.5 4.5 4"

private const val HOJA = "M14 3H7a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h10a2 2 0 0 0 2-2V8z"

private const val ESQUINA_DE_LA_HOJA = "M14 3v5h5"

private const val PUNTA = "M9 5l7 7-7 7"

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
