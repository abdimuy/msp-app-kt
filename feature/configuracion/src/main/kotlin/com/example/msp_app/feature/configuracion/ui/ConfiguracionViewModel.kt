package com.example.msp_app.feature.configuracion.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.msp_app.core.designsystem.theme.FontSizeLevel
import com.example.msp_app.core.settings.SettingsRepository
import com.example.msp_app.core.speech.domain.EstadoDelModelo
import com.example.msp_app.core.speech.domain.ModeloDeDictado
import com.example.msp_app.core.speech.domain.port.ModeloDeDictadoPort
import com.example.msp_app.feature.configuracion.domain.port.AppThemeMode
import com.example.msp_app.feature.configuracion.domain.port.AppThemePort
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Orquesta [SettingsRepository] (tamaño de letra, privacidad, reduce-motion) +
 * [AppThemePort] (tema global) + los dos puertos de descarga opcional en un
 * único [StateFlow] observable, y expone los setters que la pantalla llama al
 * tocar cada control. Todos los setters escriben de inmediato (sin un paso
 * "aplicar" separado) — el mismo criterio que el resto de los toggles globales
 * de la app (spec §"la pantalla togglea `ThemeController`… ya global").
 *
 * ## Por qué este ViewModel ve `:core:speech`
 *
 * Ve el **puerto**, que vive en el `domain/` del módulo, y jamás sus
 * adaptadores —donde están WorkManager, OkHttp y el `File`—. Es exactamente lo
 * que el contrato hexagonal permite y para lo que ese puerto existe: su KDoc
 * dice que está justificado por el caso 3 del Ruling BF, "el consumidor vive
 * en una capa que no puede importar la de la implementación". No hace falta un
 * puerto nuevo en `:feature:configuracion` que lo envuelva: sería una segunda
 * interfaz con una sola implementación cuyo único trabajo sería delegar, o sea
 * el triple-map ritual que el mismo Ruling prohíbe.
 */
@HiltViewModel
class ConfiguracionViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val themePort: AppThemePort,
    private val modeloPort: ModeloDeDictadoPort,
    private val modelo: ModeloDeDictado
) : ViewModel() {

    /**
     * Los renglones de la sección "Descargas", derivados **antes** del resto y
     * como lista: la sección pinta las descargas que haya, y hoy hay una. Fueron
     * dos hasta que `:core:mapas` se fue, y el día que entre otra este flujo es
     * el único lugar que cambia.
     */
    /**
     * Los renglones de "Descargas".
     *
     * **El del dictado se pinta SOLO si el motor existe** ([ModeloDeDictadoPort.motorDisponible]).
     * Hoy no existe —whisper.cpp no se vendoró, y el porqué está en el KDoc de ese
     * puerto y en `MotorWhisperNativo`— así que la lista sale vacía y la sección
     * entera no se pinta.
     *
     * Ofrecer bajar 43.5 MB para un motor que no puede cargar nada no es una
     * función a medias: es ofrecer una mentira, y el cobrador gastaría sus datos
     * para no ganar nada. El dictado **sigue funcionando** sin eso, con el
     * reconocedor que Android ya trae — medido en el SM-A256E del dueño, dicta en
     * español sin señal.
     *
     * No se esconde con una bandera: se le pregunta al puerto, así que el renglón
     * **aparece solo** el día que la librería nativa viaje en el APK.
     */
    private val descargas: Flow<List<FilaDeDescarga>> =
        modeloPort.estado().map { dictado ->
            if (modeloPort.motorDisponible) listOf(filaDelDictado(modelo, dictado)) else emptyList()
        }

    val state: StateFlow<ConfiguracionUiState> = combine(
        settingsRepository.fontSizeLevel,
        settingsRepository.privacyMasked,
        settingsRepository.reduceMotion,
        themePort.themeMode,
        descargas
    ) { fontSizeLevel, privacyMasked, reduceMotion, themeMode, filas ->
        ConfiguracionUiState(
            fontSizeLevel = fontSizeLevel,
            privacyMasked = privacyMasked,
            reduceMotion = reduceMotion,
            themeMode = themeMode,
            descargas = filas
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
        initialValue = ConfiguracionUiState(
            themeMode = themePort.currentThemeMode(),
            // El renglón desde el primer frame, y no una lista vacía: el peso
            // sale del paquete, que se conoce sin preguntarle nada a nadie. Lo
            // único que falta es el estado, y "Sin descargar" es el estado del
            // que no bajó nada.
            descargas = if (modeloPort.motorDisponible) {
                listOf(filaDelDictado(modelo, EstadoDelModelo.Ausente))
            } else {
                emptyList()
            }
        )
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
