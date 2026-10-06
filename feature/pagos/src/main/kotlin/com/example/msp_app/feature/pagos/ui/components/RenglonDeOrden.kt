package com.example.msp_app.feature.pagos.ui.components

import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import com.example.msp_app.core.designsystem.theme.MspTheme
import com.example.msp_app.feature.pagos.domain.OrdenDeLaLista

/** `testTag` del renglón completo. */
const val RENGLON_DE_ORDEN_TAG: String = "pagos_renglon_orden"

/** `testTag` de la parte izquierda ("Orden: X ▾") que abre la hoja. */
const val ABRIR_ORDEN_TAG: String = "pagos_renglon_orden_abrir"

/** `testTag` de "✕ Quitar". */
const val QUITAR_ORDEN_TAG: String = "pagos_renglon_orden_quitar"

/** `testTag` del aviso "Sin ubicación: orden de cobranza". */
const val AVISO_SIN_UBICACION_TAG: String = "pagos_renglon_orden_aviso"

/**
 * **El renglón bajo los chips de segmento**: a la izquierda "Orden: `<nombre>`
 * ▾", a la derecha el conteo o "✕ Quitar" (mock "Orden" opción D, decisión
 * del dueño 2026-10-05).
 *
 * ## Texto, sin caja — pero con 50dp de toque
 *
 * Ni "Orden: …" ni "✕ Quitar" dibujan fondo ni borde: son texto suelto, como
 * pide el mock (regla del repo "acción pequeña sin caja" — el área tocable es
 * grande, pero invisible). El mecanismo es el mismo que ya usa
 * `DetailListToggle` de `:feature:collectionReport`: `Modifier.clickable` con
 * `Role.Button` directo sobre el `Row` de texto, `heightIn(min =
 * MspTheme.spacing.touchTarget)` para el alto tocable y SIN `Surface` ni
 * `background` que dibuje una caja — sólo el ripple del toque, que no deja
 * marca cuando no se está tocando.
 *
 * **Sin `wrapContentHeight` después del `heightIn`** — medido:
 * `CadaPantallaDeCobranzaRespetaLaBarraDeEstadoTest` midió 35px (17.5dp) de
 * alto tocable real con esa combinación puesta. `wrapContentHeight()` mide a
 * su hijo con el mínimo entrante AFLOJADO a 0 y reporta el tamaño del HIJO,
 * no el mínimo que `heightIn` acababa de fijar — el `Row` sin ese modificador
 * de más ya respeta el mínimo por su cuenta (es lo que hace `DetailListToggle`,
 * que nunca tuvo el problema por no tenerlo).
 *
 * ## Por qué el conteo y "✕ Quitar" nunca conviven
 *
 * Son la misma esquina preguntando dos cosas distintas según si hay un orden
 * activo: "cuántos hay" cuando el orden es el de siempre, "cómo lo quito"
 * cuando no. Pintar los dos a la vez sería dar dos respuestas a una sola
 * pregunta implícita ("¿qué es esto?").
 */
@Composable
fun RenglonDeOrden(
    orden: OrdenDeLaLista,
    conteo: Int,
    avisoSinUbicacion: Boolean,
    onAbrirHoja: () -> Unit,
    onQuitar: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.testTag(RENGLON_DE_ORDEN_TAG)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
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
            if (orden == OrdenDeLaLista.COBRANZA) {
                Text(
                    text = if (conteo == 1) "1 cliente" else "$conteo clientes",
                    style = MspTheme.type.contextNote,
                    color = MspTheme.colors.onSurfaceMuted
                )
            } else {
                val interactionQuitar = remember { MutableInteractionSource() }
                Row(
                    modifier = Modifier
                        .heightIn(min = MspTheme.spacing.touchTarget)
                        .clickable(
                            interactionSource = interactionQuitar,
                            indication = LocalIndication.current,
                            role = Role.Button,
                            onClickLabel = "Quitar el orden y volver a cobranza",
                            onClick = onQuitar
                        )
                        .testTag(QUITAR_ORDEN_TAG)
                ) {
                    Text(
                        text = "✕ Quitar",
                        style = MspTheme.type.contextNote,
                        color = MspTheme.colors.brand
                    )
                }
            }
        }
        if (avisoSinUbicacion) {
            Text(
                text = "Sin ubicación: orden de cobranza",
                style = MspTheme.type.caption,
                color = MspTheme.colors.onSurfaceMuted,
                modifier = Modifier.testTag(AVISO_SIN_UBICACION_TAG)
            )
        }
    }
}
