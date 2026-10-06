package com.example.msp_app.data.pagos

import androidx.compose.runtime.snapshotFlow
import com.example.msp_app.core.navegacion.domain.port.TemaDelMenuPort
import com.example.msp_app.feature.pagos.domain.port.TemaDeLaAppPort
import com.example.msp_app.ui.theme.ThemeController
import kotlinx.coroutines.flow.Flow

/**
 * Implementación real de [TemaDeLaAppPort] (`:feature:pagos`) **y** de
 * [TemaDelMenuPort] (`:core:navegacion`, Task 8, ruling R2 del controlador,
 * 2026-10-06), cableada por `PagosPortsModule` y `MenuLateralModule` en
 * `:app` — calcada de
 * [com.example.msp_app.data.collectionreport.ThemeControllerReportThemePort],
 * que hace exactamente esto para el reporte de cobranza: el tema real de la app
 * (`ThemeController`) vive en `:app`, así que el feature cruza la frontera del
 * módulo hacia él.
 *
 * **No es una cuarta copia.** Ya había tres adaptadores idénticos sobre
 * `ThemeController` (`data/pagos`, `data/visitas`, `data/speech`); en vez de
 * sumar un cuarto para el menú, esta misma clase también implementa
 * [TemaDelMenuPort] porque las dos interfaces tienen la MISMA forma
 * (`oscuro`, `oscuroAhora()`, `alternar()`) — un solo `override` de cada
 * miembro satisface a las dos. No hace falta que sea el mismo *objeto*: cada
 * instancia delega TODO en el `object` [ThemeController], que es el singleton
 * real, así que una lista de clientes con su propia instancia y un menú con
 * la suya ven exactamente el mismo estado sin coordinarse entre ellas.
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
 * `ListaDeClientesViewModel` o del `MenuLateralViewModel`.
 */
class ThemeControllerTemaDeLaAppAdapter : TemaDeLaAppPort, TemaDelMenuPort {

    override val oscuro: Flow<Boolean>
        get() = snapshotFlow { ThemeController.isDarkMode }

    override fun oscuroAhora(): Boolean = ThemeController.isDarkMode

    override fun alternar() {
        ThemeController.toggle()
    }
}
