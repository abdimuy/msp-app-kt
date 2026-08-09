package com.example.msp_app.core.designsystem.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp

/**
 * Tokens de color del design system Msp. Fuente única de color: ningún otro
 * archivo del módulo (ni de la app, una vez migrada) hardcodea un
 * `Color(0xFF……)` de marca — todo lector pasa por [MspTheme.colors]
 * (`theme/MspTheme.kt`, Task 5), nunca por `MaterialTheme.colorScheme`
 * directamente.
 *
 * Transcrito 1:1 de `CampoColors` (kollect-app, ver
 * `.superpowers/research/kollect-app-designsystem.md` §1.1-§1.3) con **solo
 * la marca reskineada a azul** (`brand`, `brand2`, `brandTint` en ambos
 * temas) y `heroProgressFill` cambiado a un mint-teal dedicado — ver
 * [mspLightColors] / [mspDarkColors] para el detalle valor-a-valor.
 *
 * Los tokens `status*` codifican estado con su propio matiz semántico
 * (pagado=verde, vencido=rojo, parcial=ámbar, promesa=violeta, etc.),
 * independiente de la marca. Regla dura de accesibilidad para quien consuma
 * estos tokens (el componente `MspStatusChip`, Task 7): el color NUNCA es el
 * único portador de significado — siempre va acompañado de ícono + texto,
 * nunca color solo.
 */
@Immutable
data class MspColors(
    val brand: Color,
    val brand2: Color,
    val onBrand: Color,
    val brandTint: Color,
    val background: Color,
    val surface: Color,
    val surface2: Color,
    val onSurface: Color,
    val onSurfaceMuted: Color,
    val outline: Color,
    val statusPaid: Color,
    val statusPaidTint: Color,
    val statusPartial: Color,
    val statusPartialTint: Color,
    val statusOverdue: Color,
    val statusOverdueTint: Color,
    val statusPending: Color,
    val statusPendingTint: Color,
    val statusInfo: Color,
    val statusInfoTint: Color,
    val statusTeal: Color,
    val statusTealTint: Color,
    val danger: Color,
    val dangerTint: Color,
    val onDanger: Color,
    val promise: Color,
    val promiseTint: Color,
    val navSurface: Color,
    val heroProgressFill: Color,
    val progressTrack: Color,
    val chartTrack: Color
)

/**
 * Paleta light. Únicos 4 valores que se apartan de `campoLightColors()`:
 * `brand` (`#0D4A45` verde → `#2563EB` azul), `brand2` (`#0A3B37` →
 * `#1D4ED8`), `brandTint` (`#EEF5F4` → `#EAF0FE`) y `heroProgressFill`
 * (`#7FE0A6` mint-verde → `#6FE3C2` mint-teal). Todo lo demás (neutros y
 * `status*`) es 1:1 kollect.
 */
@Suppress("MagicNumber") // tabla de hex de marca — nombrar cada literal no aporta legibilidad.
fun mspLightColors(): MspColors = MspColors(
    brand = Color(0xFF2563EB),
    brand2 = Color(0xFF1D4ED8),
    onBrand = Color(0xFFFFFFFF),
    brandTint = Color(0xFFEAF0FE),
    background = Color(0xFFF4F6F5),
    surface = Color(0xFFFFFFFF),
    surface2 = Color(0xFFFBFCFC),
    onSurface = Color(0xFF141A18),
    onSurfaceMuted = Color(0xFF5C6863),
    outline = Color(0xFFE4E8E6),
    statusPaid = Color(0xFF177245),
    statusPaidTint = Color(0xFFE4F1E9),
    statusPartial = Color(0xFFB26A00),
    statusPartialTint = Color(0xFFFBEEDC),
    statusOverdue = Color(0xFFB42318),
    statusOverdueTint = Color(0xFFFBE7E4),
    statusPending = Color(0xFF5C6863),
    statusPendingTint = Color(0xFFEDF0EF),
    statusInfo = Color(0xFF2F5EA8),
    statusInfoTint = Color(0xFFE9EEF8),
    statusTeal = Color(0xFF0E7C8A),
    statusTealTint = Color(0xFFDFF0F2),
    danger = Color(0xFF9F1239),
    dangerTint = Color(0xFFFCE7EF),
    onDanger = Color(0xFFFFFFFF),
    promise = Color(0xFF7A5AF8),
    promiseTint = Color(0xFFEEEAFD),
    navSurface = Color(0xFFFFFFFF),
    heroProgressFill = Color(0xFF6FE3C2),
    progressTrack = Color(0xFFE2E8E5),
    chartTrack = Color(0xFFDDE7E3)
)

/**
 * Paleta dark (OLED puro: `background = #000000`). Únicos 4 valores que se
 * apartan de `campoDarkColors()`: `brand` (`#1E9E86` → `#3B82F6`), `brand2`
 * (`#14705C` → `#1D5FB0`), `brandTint` (`#123029` → `#0E2440`) y
 * `heroProgressFill` (`#7FE0A6` → `#6FE3C2`, **el mismo mint-teal que
 * light** — no cambia entre temas). Todo lo demás es 1:1 kollect.
 */
@Suppress("MagicNumber") // tabla de hex de marca — nombrar cada literal no aporta legibilidad.
fun mspDarkColors(): MspColors = MspColors(
    brand = Color(0xFF3B82F6),
    brand2 = Color(0xFF1D5FB0),
    onBrand = Color(0xFFFFFFFF),
    brandTint = Color(0xFF0E2440),
    background = Color(0xFF000000),
    surface = Color(0xFF141917),
    surface2 = Color(0xFF1C2320),
    onSurface = Color(0xFFE9EFEC),
    onSurfaceMuted = Color(0xFF8B968F),
    outline = Color(0xFF28322C),
    statusPaid = Color(0xFF40CB84),
    statusPaidTint = Color(0xFF0F2A1C),
    statusPartial = Color(0xFFE3AC4E),
    statusPartialTint = Color(0xFF2C220F),
    statusOverdue = Color(0xFFF26A5C),
    statusOverdueTint = Color(0xFF2E1613),
    statusPending = Color(0xFF96A19A),
    statusPendingTint = Color(0xFF1D2521),
    statusInfo = Color(0xFF74A2E8),
    statusInfoTint = Color(0xFF14243A),
    statusTeal = Color(0xFF33B6C9),
    statusTealTint = Color(0xFF0B2A30),
    danger = Color(0xFFFB7185),
    dangerTint = Color(0xFF2E1119),
    onDanger = Color(0xFF210A07),
    promise = Color(0xFFAD9BFB),
    promiseTint = Color(0xFF1E1A33),
    navSurface = Color(0xFF111614),
    heroProgressFill = Color(0xFF6FE3C2),
    progressTrack = Color(0xFF28322C),
    chartTrack = Color(0xFF28322C)
)

/**
 * Interpola cada campo de [MspColors] independientemente vía
 * `androidx.compose.ui.graphics.lerp`. Motor del crossfade de tema (fallback
 * de reduce-motion) que arma `ThemeRevealController` en Task 9 — a
 * `fraction = 0f` devuelve [start] exacto, a `fraction = 1f` devuelve [stop]
 * exacto, y en valores intermedios cada canal de color se mueve linealmente.
 */
fun lerpMspColors(start: MspColors, stop: MspColors, fraction: Float): MspColors = MspColors(
    brand = lerp(start.brand, stop.brand, fraction),
    brand2 = lerp(start.brand2, stop.brand2, fraction),
    onBrand = lerp(start.onBrand, stop.onBrand, fraction),
    brandTint = lerp(start.brandTint, stop.brandTint, fraction),
    background = lerp(start.background, stop.background, fraction),
    surface = lerp(start.surface, stop.surface, fraction),
    surface2 = lerp(start.surface2, stop.surface2, fraction),
    onSurface = lerp(start.onSurface, stop.onSurface, fraction),
    onSurfaceMuted = lerp(start.onSurfaceMuted, stop.onSurfaceMuted, fraction),
    outline = lerp(start.outline, stop.outline, fraction),
    statusPaid = lerp(start.statusPaid, stop.statusPaid, fraction),
    statusPaidTint = lerp(start.statusPaidTint, stop.statusPaidTint, fraction),
    statusPartial = lerp(start.statusPartial, stop.statusPartial, fraction),
    statusPartialTint = lerp(start.statusPartialTint, stop.statusPartialTint, fraction),
    statusOverdue = lerp(start.statusOverdue, stop.statusOverdue, fraction),
    statusOverdueTint = lerp(start.statusOverdueTint, stop.statusOverdueTint, fraction),
    statusPending = lerp(start.statusPending, stop.statusPending, fraction),
    statusPendingTint = lerp(start.statusPendingTint, stop.statusPendingTint, fraction),
    statusInfo = lerp(start.statusInfo, stop.statusInfo, fraction),
    statusInfoTint = lerp(start.statusInfoTint, stop.statusInfoTint, fraction),
    statusTeal = lerp(start.statusTeal, stop.statusTeal, fraction),
    statusTealTint = lerp(start.statusTealTint, stop.statusTealTint, fraction),
    danger = lerp(start.danger, stop.danger, fraction),
    dangerTint = lerp(start.dangerTint, stop.dangerTint, fraction),
    onDanger = lerp(start.onDanger, stop.onDanger, fraction),
    promise = lerp(start.promise, stop.promise, fraction),
    promiseTint = lerp(start.promiseTint, stop.promiseTint, fraction),
    navSurface = lerp(start.navSurface, stop.navSurface, fraction),
    heroProgressFill = lerp(start.heroProgressFill, stop.heroProgressFill, fraction),
    progressTrack = lerp(start.progressTrack, stop.progressTrack, fraction),
    chartTrack = lerp(start.chartTrack, stop.chartTrack, fraction)
)

/**
 * Mapea [MspColors] a un `ColorScheme` de M3 (idéntico a
 * `CampoColors.toColorScheme`, kollect §1.5) para que los componentes stock
 * de Material 3 (`Surface`, `Icon`, indicadores por defecto...) hereden
 * valores sanos si algún composable de terceros los consulta. No es la
 * fuente de verdad: los componentes propios de este design system leen
 * `MspTheme.colors.*` (Task 5), nunca `MaterialTheme.colorScheme.*`. Sin
 * dynamic color, sin Material purple.
 */
internal fun MspColors.toColorScheme(darkTheme: Boolean): ColorScheme {
    val base = if (darkTheme) darkColorScheme() else lightColorScheme()
    return base.copy(
        primary = brand,
        onPrimary = onBrand,
        primaryContainer = brandTint,
        onPrimaryContainer = brand,
        secondary = brand2,
        onSecondary = onBrand,
        secondaryContainer = brandTint,
        onSecondaryContainer = brand,
        tertiary = promise,
        onTertiary = onBrand,
        tertiaryContainer = promiseTint,
        onTertiaryContainer = promise,
        background = background,
        onBackground = onSurface,
        surface = surface,
        onSurface = onSurface,
        surfaceVariant = surface2,
        onSurfaceVariant = onSurfaceMuted,
        surfaceContainer = surface,
        surfaceContainerLow = surface2,
        surfaceContainerLowest = background,
        surfaceContainerHigh = surface2,
        surfaceContainerHighest = surface2,
        error = statusOverdue,
        onError = onBrand,
        errorContainer = statusOverdueTint,
        onErrorContainer = statusOverdue,
        outline = outline,
        outlineVariant = outline
    )
}
