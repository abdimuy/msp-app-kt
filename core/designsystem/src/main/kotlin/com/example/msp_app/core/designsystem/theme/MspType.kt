package com.example.msp_app.core.designsystem.theme

import androidx.compose.material3.Typography
import androidx.compose.runtime.Immutable
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.example.msp_app.core.designsystem.R

/**
 * Tipografía del design system Msp. Fuente única de tipo: ningún otro
 * archivo del módulo (ni de la app, una vez migrada) hardcodea un
 * `TextStyle(fontFamily = ...)` de marca — todo lector pasa por
 * `MspTheme.typography` (`theme/MspTheme.kt`, Task 5), nunca por
 * `MaterialTheme.typography` directamente.
 *
 * Transcrito 1:1 de `CampoType` (kollect-app, ver
 * `.superpowers/research/kollect-app-designsystem.md` §2.1-§2.4) — mismo
 * font (Manrope variable), misma escala completa, mismo mapeo M3. No hay
 * reskin aquí: la tipografía no lleva color de marca.
 */
@OptIn(ExperimentalTextApi::class)
private fun manropeFont(weight: FontWeight): Font = Font(
    R.font.manrope_variable,
    weight = weight,
    variationSettings = FontVariation.Settings(FontVariation.weight(weight.weight))
)

/**
 * Familia Manrope: UNA sola TTF variable (`res/font/manrope_variable.ttf`,
 * Task 1), los 5 pesos usados por la escala se derivan vía
 * `FontVariation.weight`, no son 5 archivos distintos.
 */
val Manrope: FontFamily = FontFamily(
    manropeFont(FontWeight.Normal),
    manropeFont(FontWeight.Medium),
    manropeFont(FontWeight.SemiBold),
    manropeFont(FontWeight.Bold),
    manropeFont(FontWeight.ExtraBold)
)

/**
 * Modo de cifras (OpenType `fontFeatureSettings`) para dinero/numerales:
 * - [TABULAR]: dígitos de ancho fijo, para montos que se alinean en columna
 *   (tarjetas, celdas KV, filas de cartera, keypad, anillo de progreso).
 * - [PROPORTIONAL]: dígitos lining pero de ancho proporcional, para montos
 *   grandes independientes (hero, totales de cuenta, captura de abono) —
 *   la coma de miles kerne pegada en vez de ocupar una celda completa.
 * - [NONE]: sin feature settings (texto que no son cifras de dinero).
 */
internal enum class NumberFigures { NONE, TABULAR, PROPORTIONAL }

private const val TABULAR_FIGURES = "tnum, lnum"
private const val PROPORTIONAL_FIGURES = "lnum"

/**
 * Constructor de estilo compartido por toda la escala. Line-height fijo
 * **1.4×** el tamaño en cada estilo (1:1 kollect §2.2).
 */
private fun campoStyle(
    size: Double,
    weight: FontWeight,
    trackingEm: Double = 0.0,
    figures: NumberFigures = NumberFigures.NONE
): TextStyle = TextStyle(
    fontFamily = Manrope,
    fontSize = size.sp,
    fontWeight = weight,
    letterSpacing = trackingEm.em,
    lineHeight = (size * 1.4).sp,
    fontFeatureSettings = when (figures) {
        NumberFigures.NONE -> null
        NumberFigures.TABULAR -> TABULAR_FIGURES
        NumberFigures.PROPORTIONAL -> PROPORTIONAL_FIGURES
    }
)

/**
 * Escala tipográfica completa, 1:1 de `CampoTypography` (kollect §2.3).
 * Cada campo documenta tamaño/peso/tracking/figuras en su KDoc de una línea;
 * la tabla completa vive en el brief de esta tarea
 * (`task-3-brief.md`) y en el research doc citado arriba.
 */
@Immutable
data class MspTypography(
    // --- Dinero / numerales (ExtraBold salvo keypad*) ----------------------
    val heroAmount: TextStyle,
    val amountDisplay: TextStyle,
    val amountHero: TextStyle,
    val amountLarge: TextStyle,
    val amountCard: TextStyle,
    val amountMedium: TextStyle,
    val amountSale: TextStyle,
    val amountRow: TextStyle,
    val amountInline: TextStyle,
    val amountSplit: TextStyle,
    val metricLarge: TextStyle,
    val metricSmall: TextStyle,
    val kvValue: TextStyle,
    val heroStatValue: TextStyle,
    val keypadKey: TextStyle,
    val keypadKeyAlt: TextStyle,
    val ringValue: TextStyle,
    // --- Títulos -------------------------------------------------------------
    val greeting: TextStyle,
    val detailTitle: TextStyle,
    val screenTitle: TextStyle,
    val cardTitle: TextStyle,
    val listTitle: TextStyle,
    val saleTitle: TextStyle,
    val name: TextStyle,
    // --- Botones / inputs ----------------------------------------------------
    val buttonLarge: TextStyle,
    val buttonSmall: TextStyle,
    val input: TextStyle,
    // --- Body / captions -------------------------------------------------------
    val body: TextStyle,
    val bodyStrong: TextStyle,
    val methodLabel: TextStyle,
    val subtitle: TextStyle,
    val contextNote: TextStyle,
    val segmentLabel: TextStyle,
    val chipLabel: TextStyle,
    val sectionHeader: TextStyle,
    val sectionLabel: TextStyle,
    val overline: TextStyle,
    val eyebrow: TextStyle,
    val syncLabel: TextStyle,
    val trendLabel: TextStyle,
    val tileLabel: TextStyle,
    val nextStopLabel: TextStyle,
    val saleMeta: TextStyle,
    val caption: TextStyle,
    val captionStrong: TextStyle,
    val kvLabel: TextStyle,
    val navLabel: TextStyle,
    val ringCaption: TextStyle
)

/**
 * Construye la escala completa. Valores verbatim kollect §2.3 (brief
 * `task-3-brief.md`) — no inventar tamaños, no redondear.
 */
@Suppress(
    "MagicNumber"
) // tabla de tamaño/tracking del brief — nombrar cada literal no aporta legibilidad.
fun mspTypography(): MspTypography = MspTypography(
    // --- Dinero / numerales --------------------------------------------------
    heroAmount = campoStyle(46.0, FontWeight.ExtraBold, -0.02, NumberFigures.PROPORTIONAL),
    amountDisplay = campoStyle(44.0, FontWeight.ExtraBold, -0.03, NumberFigures.PROPORTIONAL),
    amountHero = campoStyle(36.0, FontWeight.ExtraBold, -0.03, NumberFigures.PROPORTIONAL),
    amountLarge = campoStyle(34.0, FontWeight.ExtraBold, -0.03, NumberFigures.PROPORTIONAL),
    amountCard = campoStyle(21.0, FontWeight.ExtraBold, -0.02, NumberFigures.TABULAR),
    amountMedium = campoStyle(20.0, FontWeight.ExtraBold, -0.02, NumberFigures.PROPORTIONAL),
    amountSale = campoStyle(19.0, FontWeight.ExtraBold, -0.02, NumberFigures.TABULAR),
    amountRow = campoStyle(18.0, FontWeight.ExtraBold, -0.02, NumberFigures.PROPORTIONAL),
    amountInline = campoStyle(14.0, FontWeight.ExtraBold, figures = NumberFigures.PROPORTIONAL),
    amountSplit = campoStyle(12.0, FontWeight.ExtraBold, figures = NumberFigures.TABULAR),
    metricLarge = campoStyle(26.0, FontWeight.ExtraBold, -0.02, NumberFigures.TABULAR),
    metricSmall = campoStyle(22.0, FontWeight.ExtraBold, -0.02, NumberFigures.TABULAR),
    kvValue = campoStyle(16.0, FontWeight.ExtraBold, figures = NumberFigures.TABULAR),
    heroStatValue = campoStyle(15.0, FontWeight.ExtraBold, figures = NumberFigures.TABULAR),
    keypadKey = campoStyle(22.0, FontWeight.Bold, figures = NumberFigures.TABULAR),
    keypadKeyAlt = campoStyle(18.0, FontWeight.Bold, figures = NumberFigures.TABULAR),
    ringValue = campoStyle(16.0, FontWeight.ExtraBold, figures = NumberFigures.TABULAR),
    // --- Títulos ---------------------------------------------------------------
    greeting = campoStyle(22.0, FontWeight.ExtraBold, -0.02),
    detailTitle = campoStyle(18.0, FontWeight.ExtraBold, -0.02),
    screenTitle = campoStyle(16.5, FontWeight.Bold, -0.01),
    cardTitle = campoStyle(17.0, FontWeight.ExtraBold, -0.01),
    listTitle = campoStyle(15.0, FontWeight.Bold, -0.01),
    saleTitle = campoStyle(14.0, FontWeight.ExtraBold, -0.01),
    name = campoStyle(15.5, FontWeight.ExtraBold, -0.01),
    // --- Botones / inputs --------------------------------------------------------
    buttonLarge = campoStyle(16.0, FontWeight.ExtraBold, -0.01),
    buttonSmall = campoStyle(14.0, FontWeight.ExtraBold),
    input = campoStyle(15.0, FontWeight.Normal),
    // --- Body / captions -----------------------------------------------------------
    body = campoStyle(13.0, FontWeight.Normal),
    bodyStrong = campoStyle(13.0, FontWeight.SemiBold),
    methodLabel = campoStyle(13.0, FontWeight.Bold),
    subtitle = campoStyle(12.5, FontWeight.Normal),
    contextNote = campoStyle(12.5, FontWeight.SemiBold),
    segmentLabel = campoStyle(12.5, FontWeight.Bold),
    chipLabel = campoStyle(12.0, FontWeight.Bold, 0.01),
    sectionHeader = campoStyle(12.0, FontWeight.Bold, 0.04),
    sectionLabel = campoStyle(11.0, FontWeight.ExtraBold, 0.08),
    overline = campoStyle(12.0, FontWeight.SemiBold, 0.05),
    eyebrow = campoStyle(11.0, FontWeight.Bold, 0.09),
    syncLabel = campoStyle(12.0, FontWeight.Bold),
    trendLabel = campoStyle(12.0, FontWeight.Bold, figures = NumberFigures.TABULAR),
    tileLabel = campoStyle(11.5, FontWeight.SemiBold, 0.02),
    nextStopLabel = campoStyle(11.5, FontWeight.Bold, 0.05),
    saleMeta = campoStyle(11.5, FontWeight.Normal, figures = NumberFigures.TABULAR),
    caption = campoStyle(11.0, FontWeight.Normal, figures = NumberFigures.TABULAR),
    captionStrong = campoStyle(11.0, FontWeight.Bold, figures = NumberFigures.TABULAR),
    kvLabel = campoStyle(11.0, FontWeight.SemiBold),
    navLabel = campoStyle(10.5, FontWeight.Bold),
    ringCaption = campoStyle(9.0, FontWeight.Normal)
)

/**
 * Mapea [MspTypography] a un `Typography` de M3 (idéntico a
 * `CampoTypography.toMaterialTypography`, kollect §2.4) para que los
 * componentes stock de Material 3 hereden valores sanos si algún composable
 * de terceros los consulta. No es la fuente de verdad: los componentes
 * propios de este design system leen `MspTheme.typography.*` (Task 5), nunca
 * `MaterialTheme.typography.*` directamente.
 */
internal fun MspTypography.toMaterialTypography(): Typography = Typography(
    displayLarge = amountDisplay,
    displayMedium = amountHero,
    displaySmall = amountLarge,
    headlineLarge = greeting,
    headlineMedium = detailTitle,
    headlineSmall = cardTitle,
    titleLarge = listTitle,
    titleMedium = saleTitle,
    titleSmall = sectionHeader,
    bodyLarge = input,
    bodyMedium = body,
    bodySmall = subtitle,
    labelLarge = buttonLarge,
    labelMedium = chipLabel,
    labelSmall = caption
)
