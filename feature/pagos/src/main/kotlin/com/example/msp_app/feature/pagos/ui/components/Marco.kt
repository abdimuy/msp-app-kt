package com.example.msp_app.feature.pagos.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.msp_app.core.designsystem.component.MspPrimaryFieldButton
import com.example.msp_app.core.designsystem.theme.MspTheme

/** `testTag` del botón "atrás". */
const val ATRAS_TAG: String = "pagos_atras"

/**
 * `testTag` del "⋯" del dock — donde vive la condonación, cuya lógica NO se
 * toca. Lo lleva **un solo nodo** en toda la pantalla: dos nodos con el mismo
 * tag hacen que `onNodeWithTag` truene por ambigüedad, y además la condonación
 * es dinero y no debe tener dos puertas.
 */
const val MAS_ACCIONES_TAG: String = "pagos_mas_acciones"

/** `testTag` del CTA primario del dock. */
const val CTA_PRIMARIO_TAG: String = "pagos_cta_primario"

/** `testTag` del CTA de visita del dock. */
const val CTA_VISITA_TAG: String = "pagos_cta_visita"

/** Alto mínimo tocable. El plan pide >=50px; el design system ya pide 56dp. */
private val TOQUE = 56.dp

/**
 * La fila de navegación del mock (`.nav`): solo "atrás".
 *
 * **No lleva "⋯".** El `.nav` del mock trae atrás/ojo/menú, no el "⋯" — ese vive
 * en el dock y en ningún otro lugar. Tenerlo aquí también hacía dos puertas a la
 * condonación, que el diseño no tiene, y ponía el mismo `testTag` en dos nodos
 * compuestos a la vez (`onNodeWithTag` truena por ambigüedad).
 *
 * El ojo de privacidad y el menú del mock no se cablean aquí: el primero es una
 * preferencia global que hoy vive en el reporte de cobranza y el segundo es
 * navegación de la app, ninguno propiedad de esta pantalla.
 *
 * ## El hueco de la derecha es una sede de cero dp verticales
 *
 * Esta fila mide 56dp por el botón redondo y **le sobran ~280dp de ancho**. Es
 * el mismo hallazgo con el que la Task 22 colgó la cámara de la fila del método
 * de cobro: un afordante que viaja con una fila que ya existía **no cuesta un
 * solo dp de alto**, y en esta pantalla el alto es lo escaso — abajo está el
 * dinero, que es por lo que el cobrador la abrió.
 *
 * [accion] es opcional a propósito: el detalle de VENTA la deja en `null` y su
 * barra queda exactamente como estaba (sus goldens no se mueven, y ése es el
 * control positivo de que este cambio solo toca la pantalla de cliente).
 * Cualquier cosa que se cuelgue aquí debe respetar `maxLines = 1`: dos
 * renglones harían crecer la fila y el afordante dejaría de ser gratis.
 */
@Composable
fun BarraDeDetalle(
    onAtras: () -> Unit,
    modifier: Modifier = Modifier,
    accion: (@Composable () -> Unit)? = null
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = MspTheme.spacing.sm),
        verticalAlignment = Alignment.CenterVertically
    ) {
        BotonCircular(
            icono = Icons.Filled.ArrowBack,
            descripcion = "Atrás",
            onClick = onAtras,
            modifier = Modifier.testTag(ATRAS_TAG)
        )
        Box(
            modifier = Modifier
                .weight(1f)
                .padding(start = MspTheme.spacing.sm),
            contentAlignment = Alignment.CenterEnd
        ) {
            accion?.invoke()
        }
    }
}

@Composable
private fun BotonCircular(
    icono: androidx.compose.ui.graphics.vector.ImageVector,
    descripcion: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        modifier = modifier.size(TOQUE),
        shape = MspTheme.shapes.chip,
        color = MspTheme.colors.surface
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = icono,
                contentDescription = descripcion,
                tint = MspTheme.colors.onSurface,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

/**
 * El dock del mock (`.dock`): CTA primario en `brand` (nunca en verde — el
 * verde `statusPaid` es solo estado), acción de visita en superficie y el "⋯"
 * donde vive la condonación.
 *
 * **El slot primario es [MspPrimaryFieldButton], no [BotonDelDock]
 * (corrección de la ronda 1).** Se pintaba a mano con la receta exacta del
 * componente compartido —`heightIn(min = 56dp)` + `shapes.button` +
 * `type.buttonLarge` + `brand`/`onBrand`— y al hacerlo se perdía lo que no es
 * cosmético: **el haptic**. El CTA de esta pantalla es "abonar $NNN", una
 * acción de dinero, y el design system lo declara regla dura — *"las acciones
 * de dinero deben sentirse físicas"* (spec §8.4, KDoc de
 * `PrimaryFieldButton`): cada tap dispara `HapticFeedbackType.LongPress`. Con
 * el `Surface` local no vibraba. También llegan la sombra de 8dp tintada a
 * marca y el estado apagado plano del sistema.
 *
 * [BotonDelDock] se queda para los otros dos slots, que **no** son variantes
 * del compartido: van rellenos de `surface` y `MspPrimaryFieldButton` solo
 * ofrece `Primary` (fill marca), `Danger` (fill rojo) y `Ghost` (outline sin
 * relleno). Forzar `Ghost` cambiaría el peso visual del dock.
 */
@Composable
fun DockDeAcciones(
    textoPrimario: String,
    onPrimario: () -> Unit,
    onVisita: () -> Unit,
    onMasAcciones: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(MspTheme.colors.outline)
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(MspTheme.colors.background)
                .padding(MspTheme.spacing.md),
            horizontalArrangement = Arrangement.spacedBy(MspTheme.spacing.sm),
            verticalAlignment = Alignment.CenterVertically
        ) {
            MspPrimaryFieldButton(
                text = textoPrimario,
                onClick = onPrimario,
                modifier = Modifier
                    .weight(1.7f)
                    .testTag(CTA_PRIMARIO_TAG)
            )
            BotonDelDock(
                texto = "visita",
                relleno = MspTheme.colors.surface,
                contenido = MspTheme.colors.onSurface,
                onClick = onVisita,
                modifier = Modifier
                    .weight(1f)
                    .testTag(CTA_VISITA_TAG)
            )
            BotonDelDock(
                texto = "⋯",
                relleno = MspTheme.colors.surface,
                contenido = MspTheme.colors.onSurfaceMuted,
                onClick = onMasAcciones,
                modifier = Modifier
                    .size(TOQUE)
                    .testTag(MAS_ACCIONES_TAG)
            )
        }
    }
}

@Composable
private fun BotonDelDock(
    texto: String,
    relleno: Color,
    contenido: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        modifier = modifier.heightIn(min = TOQUE),
        shape = MspTheme.shapes.button,
        color = relleno
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = texto,
                style = MspTheme.type.buttonLarge,
                color = contenido,
                modifier = Modifier.padding(horizontal = MspTheme.spacing.sm)
            )
        }
    }
}
