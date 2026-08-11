package com.example.msp_app.feature.configuracion.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.example.msp_app.core.designsystem.component.MspPrivacyEyeToggle
import com.example.msp_app.core.designsystem.component.MspSegmentChips
import com.example.msp_app.core.designsystem.theme.MspTheme
import com.example.msp_app.feature.configuracion.domain.port.AppThemeMode

/** `testTag` del control segmentado de tema (Claro/Automático/Oscuro). */
const val THEME_MODE_SEGMENT_TAG = "msp_configuracion_theme_mode_segment"

/** `testTag` del switch "Ocultar cifras" (wrapper de [MspPrivacyEyeToggle]). */
const val PRIVACY_MASKED_TOGGLE_TAG = "msp_configuracion_privacy_masked_toggle"

/** `testTag` del switch "Deshabilitar animaciones". */
const val REDUCE_MOTION_TOGGLE_TAG = "msp_configuracion_reduce_motion_toggle"

private val THEME_MODE_OPTIONS = listOf("Claro", "Automático", "Oscuro")

private fun AppThemeMode.toIndex(): Int = when (this) {
    AppThemeMode.LIGHT -> 0
    AppThemeMode.SYSTEM -> 1
    AppThemeMode.DARK -> 2
}

private fun Int.toThemeMode(): AppThemeMode = when (this) {
    0 -> AppThemeMode.LIGHT
    2 -> AppThemeMode.DARK
    else -> AppThemeMode.SYSTEM
}

/**
 * Sección "Apariencia" (spec §"pantalla `:feature:configuracion`"): control de
 * Tema (Claro/Automático/Oscuro, escribe [AppThemeMode] vía el ViewModel) +
 * dos toggles globales (ocultar cifras, deshabilitar animaciones). Los tres
 * controles escriben de inmediato — sin paso "aplicar" separado.
 */
@Composable
fun AppearanceSection(
    themeMode: AppThemeMode,
    onThemeModeSelected: (AppThemeMode) -> Unit,
    privacyMasked: Boolean,
    onPrivacyMaskedChanged: (Boolean) -> Unit,
    reduceMotion: Boolean,
    onReduceMotionChanged: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        Text(
            text = "Apariencia",
            style = MspTheme.type.sectionHeader,
            color = MspTheme.colors.onSurfaceMuted
        )
        Spacer(Modifier.height(MspTheme.spacing.sm))
        SettingRow(label = "Tema") {
            MspSegmentChips(
                options = THEME_MODE_OPTIONS,
                selectedIndex = themeMode.toIndex(),
                onSelect = { index -> onThemeModeSelected(index.toThemeMode()) },
                modifier = Modifier.testTag(THEME_MODE_SEGMENT_TAG)
            )
        }
        Spacer(Modifier.height(MspTheme.spacing.md))
        SettingRow(label = "Ocultar cifras") {
            MspPrivacyEyeToggle(
                masked = privacyMasked,
                onToggle = { onPrivacyMaskedChanged(!privacyMasked) },
                modifier = Modifier.testTag(PRIVACY_MASKED_TOGGLE_TAG)
            )
        }
        Spacer(Modifier.height(MspTheme.spacing.md))
        SettingRow(label = "Deshabilitar animaciones") {
            Switch(
                checked = reduceMotion,
                onCheckedChange = onReduceMotionChanged,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = MspTheme.colors.onBrand,
                    checkedTrackColor = MspTheme.colors.brand,
                    checkedBorderColor = MspTheme.colors.brand
                ),
                modifier = Modifier.testTag(REDUCE_MOTION_TOGGLE_TAG)
            )
        }
    }
}

@Composable
private fun SettingRow(
    label: String,
    modifier: Modifier = Modifier,
    control: @Composable () -> Unit
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = label, style = MspTheme.type.body, color = MspTheme.colors.onSurface)
        control()
    }
}
