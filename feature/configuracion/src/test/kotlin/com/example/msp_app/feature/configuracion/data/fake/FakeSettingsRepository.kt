package com.example.msp_app.feature.configuracion.data.fake

import com.example.msp_app.core.designsystem.theme.FontSizeLevel
import com.example.msp_app.core.settings.SettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Fake de [SettingsRepository] (estado + spy) — mismo patrón que
 * `FakePorts.kt` de `:feature:collectionReport`: cada `Flow` está respaldado
 * por un [MutableStateFlow] mutable directamente desde el test (para simular
 * un cambio "desde otra pantalla") y cada setter registra sus llamadas para
 * poder aseverar la interacción, no solo el resultado.
 */
class FakeSettingsRepository(
    initialFontSizeLevel: FontSizeLevel = FontSizeLevel.NORMAL,
    initialPrivacyMasked: Boolean = false,
    initialReduceMotion: Boolean = false
) : SettingsRepository {

    private val fontSizeLevelFlow = MutableStateFlow(initialFontSizeLevel)
    private val privacyMaskedFlow = MutableStateFlow(initialPrivacyMasked)
    private val reduceMotionFlow = MutableStateFlow(initialReduceMotion)

    val setFontSizeLevelCalls: MutableList<FontSizeLevel> = mutableListOf()
    val setPrivacyMaskedCalls: MutableList<Boolean> = mutableListOf()
    val setReduceMotionCalls: MutableList<Boolean> = mutableListOf()

    override val fontSizeLevel: Flow<FontSizeLevel> = fontSizeLevelFlow

    override suspend fun setFontSizeLevel(level: FontSizeLevel) {
        setFontSizeLevelCalls += level
        fontSizeLevelFlow.value = level
    }

    override val privacyMasked: Flow<Boolean> = privacyMaskedFlow

    override suspend fun setPrivacyMasked(masked: Boolean) {
        setPrivacyMaskedCalls += masked
        privacyMaskedFlow.value = masked
    }

    override val reduceMotion: Flow<Boolean> = reduceMotionFlow

    override suspend fun setReduceMotion(enabled: Boolean) {
        setReduceMotionCalls += enabled
        reduceMotionFlow.value = enabled
    }
}
