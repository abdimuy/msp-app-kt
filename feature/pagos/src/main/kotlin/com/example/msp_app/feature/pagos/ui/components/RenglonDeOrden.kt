package com.example.msp_app.feature.pagos.ui.components

import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.example.msp_app.core.designsystem.theme.MspTheme
import com.example.msp_app.feature.pagos.domain.OrdenDeLaLista

/** `testTag` de la pieza de orden completa. */
const val RENGLON_DE_ORDEN_TAG: String = "pagos_renglon_orden"

/** `testTag` de la parte izquierda ("Orden: X ▾") que abre la hoja. */
const val ABRIR_ORDEN_TAG: String = "pagos_renglon_orden_abrir"

/** `testTag` de "✕ Quitar". */
const val QUITAR_ORDEN_TAG: String = "pagos_renglon_orden_quitar"

/** `testTag` del aviso "Sin ubicación: orden de cobranza". */
const val AVISO_SIN_UBICACION_TAG: String = "pagos_renglon_orden_aviso"

/**
 * **El orden de la lista, en una sola pieza discreta** a la derecha del título grande:
 * "Orden: `<nombre>` ▾" y, si el orden no es el de siempre, una "✕" para quitarlo
 * (mock "Orden" opción D, decisión del dueño 2026-10-05; compactado 2026-10-07).
 *
 * Antes era un renglón propio bajo los chips, con el conteo de clientes a la derecha.
 * El dueño lo vio en su Galaxy A25 y pidió que ocupara menos: ahora comparte la fila
 * del título y el conteo se fue — era el mismo número que ya trae el chip de segmento
 * elegido (con la búsqueda vacía, `state.clientes.size` ES el conteo del chip).
 *
 * ## Texto, sin caja — pero con el alto del toque
 *
 * Ni "Orden: …" ni la "✕" dibujan fondo ni borde (regla del repo "acción pequeña sin
 * caja": el área tocable es grande, pero invisible). `Modifier.clickable` con
 * `Role.Button` directo sobre el texto y `heightIn(min = touchTarget)`, sin `Surface`.
 * La "✕" mide el toque completo de ancho y de alto aunque se dibuje como un glifo.
 *
 * **Sin `wrapContentHeight` después del `heightIn`** — medido:
 * `CadaPantallaDeCobranzaRespetaLaBarraDeEstadoTest` midió 35px (17.5dp) de alto
 * tocable real con esa combinación puesta.
 */
@Composable
fun RenglonDeOrden(
    orden: OrdenDeLaLista,
    onAbrirHoja: () -> Unit,
    onQuitar: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.testTag(RENGLON_DE_ORDEN_TAG),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val interactionAbrir = remember { MutableInteractionSource() }
        Row(
            modifier = Modifier
                .heightIn(min = MspTheme.spacing.touchTarget)
                .clickable(
                    interactionSource = interactionAbrir,
                    indication = LocalIndication.current,
                    role = Role.Button,
                    onClickLabel = "Cambiar el orden de la lista",
                    onClick = onAbrirHoja
                )
                .padding(horizontal = MspTheme.spacing.xs)
                .testTag(ABRIR_ORDEN_TAG),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Orden: ",
                style = MspTheme.type.contextNote,
                color = MspTheme.colors.onSurfaceMuted
            )
            Text(
                text = "${orden.etiqueta} ▾",
                style = MspTheme.type.contextNote,
                color = if (orden == OrdenDeLaLista.COBRANZA) {
                    MspTheme.colors.onSurfaceMuted
                } else {
                    MspTheme.colors.brand
                }
            )
        }
        if (orden != OrdenDeLaLista.COBRANZA) {
            val interactionQuitar = remember { MutableInteractionSource() }
            Box(
                modifier = Modifier
                    .size(MspTheme.spacing.touchTarget)
                    .clickable(
                        interactionSource = interactionQuitar,
                        indication = LocalIndication.current,
                        role = Role.Button,
                        onClickLabel = "Quitar el orden y volver a cobranza",
                        onClick = onQuitar
                    )
                    .semantics { contentDescription = "Quitar orden" }
                    .testTag(QUITAR_ORDEN_TAG),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "✕",
                    style = MspTheme.type.contextNote,
                    color = MspTheme.colors.brand
                )
            }
        }
    }
}

/**
 * "Sin ubicación: orden de cobranza" — el aviso de que "Más cerca" no pudo ordenar.
 * Vive bajo la fila del título, a todo lo ancho, para no apretar al orden.
 */
@Composable
fun AvisoSinUbicacion(modifier: Modifier = Modifier) {
    Text(
        text = "Sin ubicación: orden de cobranza",
        style = MspTheme.type.caption,
        color = MspTheme.colors.onSurfaceMuted,
        modifier = modifier
            .fillMaxWidth()
            .testTag(AVISO_SIN_UBICACION_TAG)
    )
}
