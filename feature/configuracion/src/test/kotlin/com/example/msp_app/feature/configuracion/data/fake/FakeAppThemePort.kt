package com.example.msp_app.feature.configuracion.data.fake

import com.example.msp_app.feature.configuracion.domain.port.AppThemeMode
import com.example.msp_app.feature.configuracion.domain.port.AppThemePort
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

/** Fake de [AppThemePort] (estado + spy) — mismo patrón que [FakeSettingsRepository]. */
class FakeAppThemePort(initialThemeMode: AppThemeMode = AppThemeMode.LIGHT) : AppThemePort {

    private val themeModeFlow = MutableStateFlow(initialThemeMode)

    val setThemeModeCalls: MutableList<AppThemeMode> = mutableListOf()

    override val themeMode: Flow<AppThemeMode> = themeModeFlow

    override fun currentThemeMode(): AppThemeMode = themeModeFlow.value

    override fun setThemeMode(mode: AppThemeMode) {
        setThemeModeCalls += mode
        themeModeFlow.value = mode
    }
}
