package com.example.msp_app.feature.pagos.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.msp_app.core.designsystem.component.MspThemeRevealHost
import com.example.msp_app.core.designsystem.theme.MspTheme
import com.example.msp_app.core.designsystem.theme.rememberMspReducedMotion
import com.example.msp_app.feature.pagos.domain.model.BitacoraCompleta
import com.example.msp_app.feature.pagos.domain.model.UbicacionDelCobro
import com.example.msp_app.feature.pagos.ui.components.BarraDeDetalle
import com.example.msp_app.feature.pagos.ui.components.FilaDeContacto

/** `testTag` del título de la bitácora. */
const val TITULO_DE_BITACORA_TAG: String = "pagos_titulo_bitacora"

/** `testTag` del mensaje de "nunca se ha tocado esta puerta". */
const val BITACORA_VACIA_TAG: String = "pagos_bitacora_vacia"

/**
 * **La bitácora completa de un domicilio.**
 *
 * Existe porque el "⋯" del detalle se fue. Ese botón abría la pantalla legada y
 * era —sin que se notara— el único camino a "ver los N contactos". Quitarlo sin
 * darle casa a la bitácora habría borrado una función, no un botón.
 *
 * Es la pantalla donde el cobrador contesta *"¿qué ha pasado con esta gente?"*
 * antes de tocar: cada visita y cada abono, en una sola línea de tiempo, de lo
 * más reciente a lo más viejo.
 *
 * **Con botón de volver**, al revés que la lista y el detalle: a esta se llega
 * empujada desde el detalle y no se llega de ninguna otra forma, así que la
 * flecha lleva exactamente a donde el cobrador venía.
 *
 * ## El tema lo envuelve [MspThemeRevealHost], no un `MspTheme` pelado
 *
 * Porque si no, **el mismo botón se sentiría distinto en dos pantallas de esta
 * app**: el `MspThemeToggle` del reporte de cobranza anima una reveal circular y
 * el de acá haría un crossfade. Montar el host en la raíz de `:app` está
 * prohibido (Ruling BJ: `MspTheme` alrededor de `AppNavigation` repintaría la app
 * legada), así que **cada pantalla Msp instala el host sobre sí misma**, igual
 * que ya hace con el tema. El mecanismo es UNO ([MspThemeRevealHost],
 * `:core:designsystem`); lo que cambia por pantalla es qué tema envuelve. El
 * razonamiento completo —incluido por qué el `MspTheme` va en el `*Screen` y no
 * en la ruta— está en el KDoc de [ListaDeClientesScreen].
 *
 * Esta pantalla **todavía no pinta el glifo sol/luna**. El host se instala igual:
 * lo que no puede pasar es que el día que lo pinte, lo pinte con otra animación.
 *
 * ## [onVerUbicacion] — el mapa de UN contacto
 *
 * El dueño lo pidió así: *"cuando se dé click en un pago o visita se debe abrir
 * el mapa también, pero solo con la ubicación de ese pago o visita en
 * particular"*. O sea que el punto que viaja es el del renglón tocado y **no** el
 * del último cobro del cliente, que es otra puerta y otro día.
 *
 * Es un destino de `:app` por la misma razón que el del detalle: el mapa completo
 * necesita `play-services-maps`, que se declara allá y no en este módulo
 * (principio 17). Recibe el punto **y la dirección escrita** porque la hoja al pie
 * del mapa tiene que decir de qué puerta se trata; una coordenada suelta no se lo
 * dice a nadie.
 */
@Composable
fun BitacoraScreen(
    viewModel: BitacoraViewModel,
    onAtras: () -> Unit,
    modifier: Modifier = Modifier,
    onVerUbicacion: (UbicacionDelCobro, String) -> Unit = { _, _ -> }
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    MspThemeRevealHost(
        onToggleTheme = viewModel::alternarTema,
        reducedMotion = rememberMspReducedMotion(),
        tema = { animateColors, contenido ->
            // `darkTheme` queda en su default (`appDarkTheme()` → `LocalAppDarkTheme` →
            // `ThemeController.isDarkMode`): el tema lo manda la app, no esta pantalla.
            MspTheme(animateColors = animateColors, content = contenido)
        }
    ) {
        BitacoraContent(
            state = state,
            onAtras = onAtras,
            modifier = modifier,
            // La dirección que viaja al mapa es la del CLIENTE, no una del
            // contacto: la app no guarda una calle por abono ni por visita. Ver
            // el KDoc de `BitacoraCompleta.direccion`.
            onVerUbicacion = { punto ->
                state.bitacora?.let { onVerUbicacion(punto, it.direccion) }
            }
        )
    }
}

/**
 * La bitácora, pura sobre [BitacoraUiState].
 *
 * **La lista va perezosa.** Un cliente de años acumula cientos de contactos
 * —cada visita y cada abono— y un `Column` con scroll los compondría todos de
 * una. Es la misma regla que el repo ya aprendió con `RangeCalculator.cycleDays`.
 */
@Composable
fun BitacoraContent(
    state: BitacoraUiState,
    onAtras: () -> Unit,
    modifier: Modifier = Modifier,
    onVerUbicacion: ((UbicacionDelCobro) -> Unit)? = null
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MspTheme.colors.background)
            // Ruling BR — después del `background`, para que el color se pinte a
            // sangre y el inset solo baje el contenido.
            .systemBarsPadding()
    ) {
        val bitacora = state.bitacora
        when {
            state.cargando -> Cargando()
            bitacora == null -> MensajeDeError(state.error, onAtras)
            else -> Contactos(bitacora, state.montosOcultos, onAtras, onVerUbicacion)
        }
    }
}

@Composable
private fun Contactos(
    bitacora: BitacoraCompleta,
    ocultos: Boolean,
    onAtras: () -> Unit,
    onVerUbicacion: ((UbicacionDelCobro) -> Unit)?
) {
    Column(modifier = Modifier.padding(horizontal = MspTheme.spacing.md)) {
        BarraDeDetalle(onAtras = onAtras)
        Text(
            text = bitacora.nombre,
            style = MspTheme.type.greeting,
            color = MspTheme.colors.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.testTag(TITULO_DE_BITACORA_TAG)
        )
        Text(
            text = if (bitacora.contactos.size == 1) {
                "1 contacto"
            } else {
                "${bitacora.contactos.size} contactos"
            },
            style = MspTheme.type.subtitle,
            color = MspTheme.colors.onSurfaceMuted
        )
    }
    if (bitacora.contactos.isEmpty()) {
        SinContactos()
    } else {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = MspTheme.spacing.md)
        ) {
            items(
                items = bitacora.contactos,
                // La llave es el instante MÁS la etiqueta: dos abonos del mismo
                // día a la misma hora existen (dos cuentas, una visita), así que
                // el instante solo no es único y Compose reciclaría mal la fila.
                key = { "${it.fecha.toEpochMilli()}:${it.etiqueta}:${it.importe?.amount}" }
            ) { contacto ->
                // La fila decide sola si se puede tocar: con `ubicacion` en
                // `null` no monta el `clickable`. Aquí no se repite la pregunta.
                FilaDeContacto(
                    contacto = contacto,
                    ocultos = ocultos,
                    onVerUbicacion = onVerUbicacion
                )
            }
        }
    }
}

/**
 * Nunca se ha tocado esta puerta.
 *
 * Se dice con palabras y no con una lista vacía: una pantalla en blanco se lee
 * como un defecto de la app, no como un hecho del cliente.
 */
@Composable
private fun SinContactos() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(MspTheme.spacing.lg),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "sin visitas ni abonos",
            style = MspTheme.type.body,
            color = MspTheme.colors.onSurfaceMuted,
            modifier = Modifier.testTag(BITACORA_VACIA_TAG)
        )
    }
}
