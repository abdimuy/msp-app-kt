package com.example.msp_app.feature.garantias.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.msp_app.core.designsystem.component.MspThemeRevealHost
import com.example.msp_app.core.designsystem.theme.MspTheme
import com.example.msp_app.core.designsystem.theme.rememberMspReducedMotion

/**
 * El mínimo tocable del repo (**50dp**, más estricto que los 48 de Material).
 *
 * No hay constante compartida en `:core:designsystem`: la compuerta
 * `CadaPantallaDeCobranzaRespetaLaBarraDeEstadoTest` la tiene escrita de su lado
 * y la cobra sobre el grafo real. Un `Button` de Material mide 40dp y el
 * cobrador toca el borde sin que pase nada.
 */
private val ALTO_TOCABLE_MINIMO = 50.dp

/**
 * Andamio de la captura de garantías v2.
 *
 * Existe para que el módulo tenga un destino real desde el primer commit: el
 * esqueleto se compila, se navega y pasa las compuertas antes de que exista la
 * primera regla de negocio. Las cuatro capturas del brief (abrir folio,
 * recolección, mis folios y entrega con firma) sustituyen este contenido.
 *
 * **Provee el tema, y lo provee animado.** `:app` monta `MspappTheme`, no
 * `MspTheme`; una pantalla de un módulo `feature` que lea tokens Msp sin
 * envolverlos revienta al abrirse. Y el envoltorio es [MspThemeRevealHost] y no
 * un `MspTheme` pelado: el sol/luna de la app tiene que sentirse igual en todas
 * las pantallas nuevas, y `CadaPantallaConTemaAnimaElCambioTest` lo exige por
 * escaneo de fuentes, no por lista. Hoy el andamio no pinta ningún toggle —de
 * ahí el `onToggleTheme` inerte—; el día que lo pinte, ese lambda pasa a
 * pedirle el cambio al ViewModel.
 *
 * **Y se pinta a sangre.** El fondo va antes del inset para que el color llegue
 * debajo de la barra de estado, y el inset corre sólo el contenido: con
 * `enableEdgeToEdge()` la ventana del sistema queda encima y se come los taps
 * del encabezado.
 */
@Composable
fun GarantiasV2Screen(onAtras: () -> Unit, modifier: Modifier = Modifier) {
    MspThemeRevealHost(
        onToggleTheme = {},
        reducedMotion = rememberMspReducedMotion(),
        tema = { animateColors, contenido ->
            MspTheme(animateColors = animateColors, content = contenido)
        }
    ) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .background(MspTheme.colors.background)
                .statusBarsPadding()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(MspTheme.spacing.lg),
                verticalArrangement = Arrangement.spacedBy(
                    MspTheme.spacing.md,
                    Alignment.CenterVertically
                ),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Captura de garantías",
                    style = MspTheme.type.screenTitle,
                    color = MspTheme.colors.onSurface
                )
                Text(
                    text = "Pantalla en construcción",
                    style = MspTheme.type.caption,
                    color = MspTheme.colors.onSurfaceMuted
                )
                Button(
                    onClick = onAtras,
                    modifier = Modifier.heightIn(min = ALTO_TOCABLE_MINIMO)
                ) {
                    Text(text = "Volver")
                }
            }
        }
    }
}
