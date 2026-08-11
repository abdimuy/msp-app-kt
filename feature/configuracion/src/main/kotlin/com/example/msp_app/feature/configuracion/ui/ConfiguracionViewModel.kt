package com.example.msp_app.feature.configuracion.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.msp_app.core.designsystem.theme.FontSizeLevel
import com.example.msp_app.core.settings.SettingsRepository
import com.example.msp_app.feature.configuracion.domain.port.AppThemeMode
import com.example.msp_app.feature.configuracion.domain.port.AppThemePort
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Orquesta [SettingsRepository] (tamaño de letra, privacidad, reduce-motion) +
 * [AppThemePort] (tema global) en un único [StateFlow] observable, y expone
 * los setters que la pantalla llama al tocar cada control. Todos los setters
 * escriben de inmediato (sin un paso "aplicar" separado) — el mismo criterio
 * que el resto de los toggles globales de la app (spec §"la pantalla togglea
 * `ThemeController`… ya global").
 */
@HiltViewModel
class ConfiguracionViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val themePort: AppThemePort
) : ViewModel() {

    val state: StateFlow<ConfiguracionUiState> = combine(
        settingsRepository.fontSizeLevel,
        settingsRepository.privacyMasked,
        settingsRepository.reduceMotion,
        themePort.themeMode
    ) { fontSizeLevel, privacyMasked, reduceMotion, themeMode ->
        ConfiguracionUiState(
            fontSizeLevel = fontSizeLevel,
            privacyMasked = privacyMasked,
            reduceMotion = reduceMotion,
            themeMode = themeMode
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
        initialValue = ConfiguracionUiState(themeMode = themePort.currentThemeMode())
    )

    /** Elige el nivel de tamaño de letra — escrito de inmediato a [SettingsRepository]. */
    fun selectFontSizeLevel(level: FontSizeLevel) {
        viewModelScope.launch { settingsRepository.setFontSizeLevel(level) }
    }

    /** Cambia el modo de tema global (Claro/Automático/Oscuro) vía [AppThemePort]. */
    fun selectThemeMode(mode: AppThemeMode) {
        themePort.setThemeMode(mode)
    }

    /** Alterna "Ocultar cifras" (preferencia global de privacidad). */
    fun setPrivacyMasked(masked: Boolean) {
        viewModelScope.launch { settingsRepository.setPrivacyMasked(masked) }
    }

    /** Alterna "Deshabilitar animaciones" (reduce-motion global). */
    fun setReduceMotion(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setReduceMotion(enabled) }
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
