package com.example.msp_app.data.pagos

import androidx.compose.runtime.snapshotFlow
import com.example.msp_app.feature.pagos.domain.port.TemaDeLaAppPort
import com.example.msp_app.ui.theme.ThemeController
import kotlinx.coroutines.flow.Flow

/**
 * Implementación real de [TemaDeLaAppPort] (`:feature:pagos`), cableada por
 * `PagosPortsModule` en `:app` — calcada de
 * [com.example.msp_app.data.collectionreport.ThemeControllerReportThemePort],
 * que hace exactamente esto para el reporte de cobranza: el tema real de la app
 * (`ThemeController`) vive en `:app`, así que el feature cruza la frontera del
 * módulo hacia él.
 *
 * Expone el booleano **YA RESUELTO** ([ThemeController.isDarkMode]) y no el
 * enum de 3 modos: el encabezado de la lista solo necesita "¿oscuro o no?" para
 * pintar el glifo del toggle, y su tap es el toggle binario de siempre. Elegir
 * *Automático* sigue siendo cosa de Configuración.
 *
 * [snapshotFlow] puentea el `mutableStateOf` de [ThemeController.isDarkMode]
 * (Compose `State`, no un `Flow`) sin que `ThemeController` deje de ser un
 * objeto plano no-`@Composable` — mismo mecanismo que los otros dos adaptadores
 * de tema del repo. No requiere estar dentro de un `@Composable`, solo un
 * `CoroutineScope` vivo, que aquí es el `viewModelScope` de
 * `ListaDeClientesViewModel`.
 */
class ThemeControllerTemaDeLaAppAdapter : TemaDeLaAppPort {

    override val oscuro: Flow<Boolean>
        get() = snapshotFlow { ThemeController.isDarkMode }

    override fun oscuroAhora(): Boolean = ThemeController.isDarkMode

    override fun alternar() {
        ThemeController.toggle()
    }
}
