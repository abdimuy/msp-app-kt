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
 * ([ChipStatus]/[MspStatusChip]) y por los toggles interactivos (`MspThemeToggle`,
 * `MspPrivacyEyeToggle`, Task 9). Fuente única de ícono: los componentes leen
 * de aquí, no de `Icons.Filled.*` disperso.
 *
 * `Paid`/`Overdue` reusan `material-icons-core` (check/warning existen ahí);
 * el resto (`Partial`/`Pending`/`Promise`/`Moon`/`Sun`/`Eye`/`EyeOff`) no está
 * en el set core (solo en el `material-icons-extended`, que no dependemos por
 * peso), así que se transcriben 1:1 de los paths SVG oficiales de Material
 * Icons (viewport 24×24) vía [PathParser] — mismos glifos, sin arrastrar la
 * librería extended. Set mínimo por contrato (task-9-brief.md gotchas): Moon,
 * Sun, Eye, EyeOff, Check, Warning, Clock, Circle, HalfCircle — los últimos 5
 * ya existían con otro nombre de rol (`Overdue`/`Promise`/`Pending`/`Partial`).
 *
 * `internal`: el punto de consumo soportado son los composables de este
 * módulo (`MspStatusChip`, `MspThemeToggle`, `MspPrivacyEyeToggle`); nada
 * fuera del módulo instancia estos íconos directamente.
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

    /** Tema oscuro activo → luna creciente (Material `brightness_2`). */
    val Moon: ImageVector = materialVector("Moon", MOON_PATH)

    /** Tema claro activo → sol (Material `wb_sunny`). */
    val Sun: ImageVector = materialVector("Sun", SUN_PATH)

    /** Cifras visibles → ojo (Material `visibility`). */
    val Eye: ImageVector = materialVector("Eye", EYE_PATH)

    /** Cifras ocultas → ojo tachado (Material `visibility_off`). */
    val EyeOff: ImageVector = materialVector("EyeOff", EYE_OFF_PATH)
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

/** Material `brightness_2` — luna creciente (media luna por sustracción de círculos). */
private const val MOON_PATH =
    "M9,2c-1.05,0 -2.05,0.16 -3,0.46c4.06,1.27 7,5.06 7,9.54s-2.94,8.27 -7,9.54" +
        "c0.95,0.3 1.95,0.46 3,0.46c5.52,0 10,-4.48 10,-10S14.52,2 9,2z"

/** Material `wb_sunny` — disco central + 8 rayos cortos. */
private const val SUN_PATH =
    "M6.76,4.84L4.96,3.05 3.55,4.46l1.79,1.79L6.76,4.84z" +
        "M4,10.5H1v2h3V10.5z" +
        "M13,0.55h-2V3.5h2V0.55z" +
        "M20.45,4.46l-1.41,-1.41 -1.79,1.79 1.41,1.41L20.45,4.46z" +
        "M17.24,18.16l1.79,1.8 1.41,-1.41 -1.8,-1.79L17.24,18.16z" +
        "M20,10.5v2h3v-2H20z" +
        "M12,5.5c-3.31,0 -6,2.69 -6,6s2.69,6 6,6 6,-2.69 6,-6S15.31,5.5 12,5.5z" +
        "M11,19.5h2v2.95h-2V19.5z" +
        "M3.55,19.54l1.41,1.41 1.79,-1.8 -1.41,-1.41L3.55,19.54z"

/** Material `visibility` — almendra del ojo + anillo de iris + pupila (3 subpaths, nonzero fill). */
private const val EYE_PATH =
    "M12,4.5C7,4.5 2.73,7.61 1,12c1.73,4.39 6,7.5 11,7.5s9.27,-3.11 11,-7.5" +
        "c-1.73,-4.39 -6,-7.5 -11,-7.5z" +
        "M12,17c-2.76,0 -5,-2.24 -5,-5s2.24,-5 5,-5 5,2.24 5,5 -2.24,5 -5,5z" +
        "M12,9c-1.66,0 -3,1.34 -3,3s1.34,3 3,3 3,-1.34 3,-3S13.66,9 12,9z"

/** Material `visibility_off` — misma almendra recortada + trazo diagonal tachando el ojo. */
private const val EYE_OFF_PATH =
    "M12,7c2.76,0 5,2.24 5,5c0,0.65 -0.13,1.26 -0.36,1.83l2.92,2.92" +
        "c1.51,-1.26 2.7,-2.89 3.43,-4.75c-1.73,-4.39 -6,-7.5 -11,-7.5" +
        "c-1.4,0 -2.74,0.25 -3.98,0.7l2.16,2.16C10.74,7.13 11.35,7 12,7z" +
        "M2,4.27l2.28,2.28 0.46,0.46C3.08,8.3 1.78,10.02 1,12c1.73,4.39 6,7.5 11,7.5" +
        "c1.55,0 3.03,-0.3 4.38,-0.84l0.42,0.42L19.73,22 21,20.73 3.27,3 2,4.27z" +
        "M7.53,9.8l1.55,1.55c-0.05,0.21 -0.08,0.42 -0.08,0.65c0,1.66 1.34,3 3,3" +
        "c0.22,0 0.44,-0.03 0.65,-0.08l1.55,1.55c-0.67,0.33 -1.41,0.53 -2.2,0.53" +
        "c-2.76,0 -5,-2.24 -5,-5c0,-0.79 0.2,-1.53 0.53,-2.2z" +
        "M11.84,9.02l3.15,3.15 0.02,-0.16c0,-1.66 -1.34,-3 -3,-3l-0.17,0.01z"
