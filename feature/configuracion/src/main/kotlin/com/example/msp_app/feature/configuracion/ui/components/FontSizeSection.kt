package com.example.msp_app.feature.configuracion.ui.components

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.msp_app.core.designsystem.component.MspCard
import com.example.msp_app.core.designsystem.theme.FontSizeLevel
import com.example.msp_app.core.designsystem.theme.MspTheme

/** `testTag` de cada tarjeta de opción — `"${FONT_SIZE_OPTION_TAG_PREFIX}${level.name}"`. */
const val FONT_SIZE_OPTION_TAG_PREFIX = "msp_configuracion_font_size_option_"

private val OPTION_CARD_BORDER_WIDTH = 2.dp
private const val AA_GLYPH_BASE_SP = 20f

/** Etiqueta es-MX de cada [FontSizeLevel] (spec §"Decisiones" punto 3: Normal / Grande / Muy grande). */
fun FontSizeLevel.displayLabel(): String = when (this) {
    FontSizeLevel.NORMAL -> "Normal"
    FontSizeLevel.GRANDE -> "Grande"
    FontSizeLevel.MUY_GRANDE -> "Muy grande"
}

/**
 * Sección "Tamaño de letra" (spec §"pantalla `:feature:configuracion`"): tres
 * tarjetas seleccionables (Normal/Grande/Muy grande, un "Aa" creciente cada
 * una) + el preview en vivo ([MiniReportPreview]) + la leyenda de alcance.
 * Al tocar una tarjeta se escribe de inmediato (`onSelect`, sin paso
 * "aplicar" separado — mismo criterio que el resto de los toggles globales).
 */
@Composable
fun FontSizeSection(
    selected: FontSizeLevel,
    onSelect: (FontSizeLevel) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        Text(
            text = "Tamaño de letra",
            style = MspTheme.type.sectionHeader,
            color = MspTheme.colors.onSurfaceMuted
        )
        Spacer(Modifier.height(MspTheme.spacing.sm))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(MspTheme.spacing.sm)
        ) {
            FontSizeLevel.entries.forEach { level ->
                FontSizeOptionCard(
                    level = level,
                    selected = level == selected,
                    onClick = { onSelect(level) },
                    modifier = Modifier.weight(1f)
                )
            }
        }
        Spacer(Modifier.height(MspTheme.spacing.md))
        Text(
            text = "Así se verá",
            style = MspTheme.type.contextNote,
            color = MspTheme.colors.onSurface
        )
        Spacer(Modifier.height(MspTheme.spacing.sm))
        MiniReportPreview(level = selected, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(MspTheme.spacing.sm))
        Text(
            text = "Cambia el tamaño en toda la app. Nunca se hace más chico de lo que tu teléfono ya usa.",
            style = MspTheme.type.caption,
            color = MspTheme.colors.onSurfaceMuted
        )
    }
}

@Composable
private fun FontSizeOptionCard(
    level: FontSizeLevel,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val ambientDensity = LocalDensity.current
    val emphasisColor = if (selected) MspTheme.colors.brand else MspTheme.colors.onSurface
    val labelColor = if (selected) MspTheme.colors.brand else MspTheme.colors.onSurfaceMuted
    MspCard(
        modifier = modifier
            .testTag("$FONT_SIZE_OPTION_TAG_PREFIX${level.name}")
            .then(
                if (selected) {
                    Modifier.border(
                        OPTION_CARD_BORDER_WIDTH,
                        MspTheme.colors.brand,
                        MspTheme.shapes.tile
                    )
                } else {
                    Modifier
                }
            ),
        color = if (selected) MspTheme.colors.brandTint else MspTheme.colors.surface,
        onClick = onClick
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = MspTheme.spacing.md),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(MspTheme.spacing.xs)
        ) {
            // El "Aa" de cada tarjeta se dibuja a la densidad de ESE nivel (no la
            // seleccionada) para que las tres tarjetas muestren su propio tamaño
            // relativo lado a lado — mismo mecanismo que MiniReportPreview.
            CompositionLocalProvider(
                LocalDensity provides previewDensity(ambientDensity, level)
            ) {
                Text(
                    text = "Aa",
                    fontSize = AA_GLYPH_BASE_SP.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = emphasisColor
                )
            }
            Text(text = level.displayLabel(), style = MspTheme.type.caption, color = labelColor)
        }
    }
}
