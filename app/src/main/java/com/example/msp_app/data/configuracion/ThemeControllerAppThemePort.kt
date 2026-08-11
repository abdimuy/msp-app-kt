package com.example.msp_app.data.configuracion

import androidx.compose.runtime.snapshotFlow
import com.example.msp_app.feature.configuracion.domain.port.AppThemeMode
import com.example.msp_app.feature.configuracion.domain.port.AppThemePort
import com.example.msp_app.ui.theme.ThemeController
import com.example.msp_app.ui.theme.ThemeMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Implementación real de [AppThemePort] (`:feature:configuracion`), provista en el composition
 * root de `:app` — mismo patrón que [com.example.msp_app.data.collectionreport.FirebaseUserCycleAdapter]
 * (puerto definido en el feature, adapter real en `:app`, cableado por un `@Module` de `:app`,
 * ver `ConfiguracionThemeModule`): el tema real de la app (`ThemeController`) vive en `:app`, así
 * que la pantalla de Configuración necesita cruzar la frontera del módulo hacia él.
 *
 * [snapshotFlow] puentea el `mutableStateOf` de [ThemeController.themeMode] (Compose `State`, no
 * un `Flow`) a un `Flow` frío sin que `ThemeController` deje de ser un objeto plano no-Composable
 * — el mismo mecanismo que Compose usa internamente para observar snapshots fuera de la
 * composición (no requiere estar dentro de un `@Composable` para funcionar, solo un
 * `CoroutineScope` vivo, que aquí es el `viewModelScope` de `ConfiguracionViewModel`).
 */
class ThemeControllerAppThemePort : AppThemePort {

    override val themeMode: Flow<AppThemeMode>
        get() = snapshotFlow { ThemeController.themeMode }.map { it.toPort() }

    override fun currentThemeMode(): AppThemeMode = ThemeController.themeMode.toPort()

    override fun setThemeMode(mode: AppThemeMode) {
        ThemeController.applyThemeMode(mode.toControllerMode())
    }
}

private fun ThemeMode.toPort(): AppThemeMode = when (this) {
    ThemeMode.LIGHT -> AppThemeMode.LIGHT
    ThemeMode.DARK -> AppThemeMode.DARK
    ThemeMode.SYSTEM -> AppThemeMode.SYSTEM
}

private fun AppThemeMode.toControllerMode(): ThemeMode = when (this) {
    AppThemeMode.LIGHT -> ThemeMode.LIGHT
    AppThemeMode.DARK -> ThemeMode.DARK
    AppThemeMode.SYSTEM -> ThemeMode.SYSTEM
}
