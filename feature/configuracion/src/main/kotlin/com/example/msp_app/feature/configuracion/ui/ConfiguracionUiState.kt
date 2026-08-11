package com.example.msp_app.feature.configuracion.ui

import com.example.msp_app.core.designsystem.theme.FontSizeLevel
import com.example.msp_app.feature.configuracion.domain.port.AppThemeMode

/**
 * Estado observable único de [ConfiguracionScreen] — refleja 1:1 los tres
 * `Flow` de `SettingsRepository` (tamaño de letra, privacidad, reduce-motion)
 * más el modo de tema vigente (`AppThemePort`). Defaults idénticos a los de
 * `SettingsRepository`/`ThemeController` para que un primer frame (antes de
 * que el primer valor llegue de DataStore) no muestre un estado inconsistente.
 */
data class ConfiguracionUiState(
    val fontSizeLevel: FontSizeLevel = FontSizeLevel.NORMAL,
    val privacyMasked: Boolean = false,
    val reduceMotion: Boolean = false,
    val themeMode: AppThemeMode = AppThemeMode.LIGHT
)
