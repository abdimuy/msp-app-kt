package com.example.msp_app.core.navegacion.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.example.msp_app.core.designsystem.component.MspThemeRevealHost
import com.example.msp_app.core.designsystem.theme.MspTheme
import com.example.msp_app.core.designsystem.theme.rememberMspReducedMotion
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

/**
 * El cajón alrededor de una pantalla. Lo que pone:
 *
 * - **MspTheme sólo dentro de la hoja**: la pantalla de [contenido] puede ser
 *   legada (`MaterialTheme` de `MspappTheme`) y no debe cambiar de colores.
 * - **Reveal de tema sólo en la hoja (Ruling R2)**: [hoja] va envuelta en
 *   [MspThemeRevealHost] (no en un `MspTheme` pelado) para que el botón
 *   sol/luna del encabezado del menú anime igual que en el resto de la
 *   arquitectura nueva. Costo aceptado (decisión del controlador): la
 *   revelación cubre sólo la hoja — la pantalla de [contenido] detrás del
 *   velo cambia de tema sin animación, porque no es Msp y no se le puede
 *   envolver sin repintar la app legada entera (mismo Ruling BJ que ya
 *   documenta [MspThemeRevealHost]). [onAlternarTema] es el `onToggleTheme`
 *   real que el dueño del estado (el ViewModel del menú) expone — **sin
 *   default**: es el único eslabón de la cadena (`EncabezadoDelMenu`,
 *   `MenuLateral`, [MspThemeRevealHost]) que lo exige. Con default `{}` el
 *   botón de tema de la hoja quedaba mudo sin error de compilación ni
 *   prueba roja si quien compone este contenedor olvidaba conectarlo
 *   (hallazgo de la revisión de esta tarea; el par de pruebas
 *   `(con/sin) movimiento reducido ... MspThemeRevealHost (R2)` cobra que
 *   [hoja] siga envuelta en el host de verdad). Cada caller que no pinte el
 *   toggle de tema dentro de [hoja] pasa `{}` explícito.
 * - **Atrás**: `ModalDrawerSheet(drawerState, …)` de material3 1.3.0 registra el
 *   manejador de atrás (predictivo en Android 14+) que cierra el menú — medido
 *   que la sobrecarga existe (decisión D10 del plan).
 * - **Reinicio al cerrar**: [AperturaDelMenu.reiniciar] (Review Focus 3).
 * - **Movimiento reducido**: `snapTo`, sin animación (spec §2.1.7); el mismo
 *   criterio ([rememberMspReducedMotion]) gatea también la reveal de tema.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MenuLateralContenedor(
    hoja: @Composable (cerrar: () -> Unit, apertura: AperturaDelMenu) -> Unit,
    onAbierto: () -> Unit,
    onAlternarTema: () -> Unit,
    gesto: AperturaPorGesto = AperturaPorGesto.DESLIZAR_EN_TODA_LA_PANTALLA,
    contenido: @Composable (abrir: () -> Unit) -> Unit
) {
    val estadoDelCajon = rememberDrawerState(DrawerValue.Closed)
    val alcance = rememberCoroutineScope()
    val apertura = rememberAperturaDelMenu()
    val movimientoReducido = rememberMspReducedMotion()
    val alAbrir by rememberUpdatedState(onAbierto)

    val abrir: () -> Unit = {
        alcance.launch {
            if (movimientoReducido) {
                estadoDelCajon.snapTo(
                    DrawerValue.Open
                )
            } else {
                estadoDelCajon.open()
            }
        }
    }
    val cerrar: () -> Unit = {
        alcance.launch {
            if (movimientoReducido) {
                estadoDelCajon.snapTo(
                    DrawerValue.Closed
                )
            } else {
                estadoDelCajon.close()
            }
        }
    }

    LaunchedEffect(estadoDelCajon) {
        snapshotFlow { estadoDelCajon.currentValue }
            .distinctUntilChanged()
            .collect { valor -> if (valor == DrawerValue.Open) alAbrir() else apertura.reiniciar() }
    }

    ModalNavigationDrawer(
        drawerState = estadoDelCajon,
        gesturesEnabled = gesto == AperturaPorGesto.DESLIZAR_EN_TODA_LA_PANTALLA || estadoDelCajon.isOpen,
        drawerContent = {
            MspThemeRevealHost(
                onToggleTheme = onAlternarTema,
                reducedMotion = movimientoReducido,
                tema = { animateColors, contenidoDelTema ->
                    MspTheme(
                        animateColors = animateColors,
                        content = contenidoDelTema
                    )
                }
            ) {
                ModalDrawerSheet(
                    drawerState = estadoDelCajon,
                    modifier = Modifier
                        .width(ANCHO_DEL_MENU)
                        .testTag(MENU_LATERAL_TAG),
                    drawerContainerColor = MspTheme.colors.surface,
                    drawerContentColor = MspTheme.colors.onSurface
                ) {
                    hoja(cerrar, apertura)
                }
            }
        }
    ) {
        if (gesto == AperturaPorGesto.DESDE_LA_BARRA) {
            // `fillMaxSize`: la franja cubre el tope de la pantalla aunque el contenido
            // todavía esté en su estado de carga y mida menos.
            ZonaQueAbreElMenu(
                onAbrir = abrir,
                modifier = Modifier.fillMaxSize()
            ) { contenido(abrir) }
        } else {
            contenido(abrir)
        }
    }
}
