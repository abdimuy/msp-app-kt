package com.example.msp_app.data.collectionreport

import androidx.compose.runtime.snapshotFlow
import com.example.msp_app.feature.collectionreport.domain.port.ReportThemePort
import com.example.msp_app.ui.theme.ThemeController
import kotlinx.coroutines.flow.Flow

/**
 * Implementación real de [ReportThemePort] (`:feature:collectionReport`), provista en el
 * composition root de `:app` — mismo patrón que
 * [com.example.msp_app.data.configuracion.ThemeControllerAppThemePort] (Configuración) y
 * [FirebaseUserCycleAdapter] (puerto definido en el feature, adapter real en `:app`, cableado
 * por un `@Module` de `:app`, ver `CollectionReportThemeModule`): el tema real de la app
 * (`ThemeController`) vive en `:app`, así que el reporte de cobranza necesita cruzar la
 * frontera del módulo hacia él.
 *
 * A diferencia de [com.example.msp_app.data.configuracion.ThemeControllerAppThemePort] (que
 * expone los 3 modos `AppThemeMode` para la UI de selección de Configuración), este adapter
 * expone directo el booleano YA RESUELTO ([ThemeController.isDarkMode]) — el reporte solo
 * necesita "¿oscuro o no?" para su reveal circular, nunca elige el modo Automático desde aquí.
 *
 * [snapshotFlow] puentea el `mutableStateOf` de [ThemeController.isDarkMode] (Compose `State`,
 * no un `Flow`) a un `Flow` frío sin que `ThemeController` deje de ser un objeto plano
 * no-Composable — mismo mecanismo que
 * [com.example.msp_app.data.configuracion.ThemeControllerAppThemePort.themeMode].
 */
class ThemeControllerReportThemePort : ReportThemePort {

    override val isDark: Flow<Boolean>
        get() = snapshotFlow { ThemeController.isDarkMode }

    override fun currentIsDark(): Boolean = ThemeController.isDarkMode

    override fun toggle() {
        ThemeController.toggle()
    }
}
