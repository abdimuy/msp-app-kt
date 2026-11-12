package com.example.msp_app.feature.pagos.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.msp_app.core.common.time.BUSINESS_LOCALE
import com.example.msp_app.core.designsystem.theme.MspTheme
import com.example.msp_app.feature.pagos.domain.ColorDelTramo
import com.example.msp_app.feature.pagos.domain.EtiquetaDelAcordado
import com.example.msp_app.feature.pagos.domain.FranjaDeCobro
import com.example.msp_app.feature.pagos.domain.TramoDeLaBarra

/** `testTag` de la franja entera. */
const val FRANJA_DE_COBRO_TAG: String = "pagos_franja_cobro"

/** `testTag` de la etiqueta del acordado (esquina superior derecha). */
const val ETIQUETA_DEL_ACORDADO_TAG: String = "pagos_franja_cobro_etiqueta"

/** `testTag` del día grande. */
const val DIA_GRANDE_DE_COBRO_TAG: String = "pagos_franja_cobro_dia"

/** `testTag` de la barra repartida. */
const val BARRA_DE_COBRO_TAG: String = "pagos_franja_cobro_barra"

/** `testTag` de cada tramo de la barra. */
const val TRAMO_DE_COBRO_TAG: String = "pagos_franja_cobro_tramo"

/** `testTag` de la leyenda. */
const val LEYENDA_DE_COBRO_TAG: String = "pagos_franja_cobro_leyenda"

private const val OPACIDAD_VERDE_SUAVE = 0.4f

/**
 * **La franja "Día de cobro"** (forma A, barra repartida): la etiqueta del acordado
 * arriba a la derecha, el día en grande, la barra con lo que pagó cada día y la
 * leyenda con sus números. Solo pinta lo que decidió `franjaDeCobro`.
 *
 * Colores: verde fuerte = `statusPaid`; verde suave = el mismo con 40 % de
 * opacidad; gris = `progressTrack`; el aviso naranja es `statusPartial`, el de
 * "pago parcial". Ninguno nuevo.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FranjaDiaDeCobro(franja: FranjaDeCobro, modifier: Modifier = Modifier) {
    Tarjeta(modifier = modifier.testTag(FRANJA_DE_COBRO_TAG)) {
        Column(verticalArrangement = Arrangement.spacedBy(MspTheme.spacing.sm)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Día de cobro".uppercase(BUSINESS_LOCALE),
                    style = MspTheme.type.eyebrow,
                    color = MspTheme.colors.onSurfaceMuted
                )
                if (franja.etiqueta != null && franja.etiquetaTexto != null) {
                    EtiquetaDelAcordado(franja.etiqueta, franja.etiquetaTexto)
                }
            }
            Text(
                text = franja.diaGrande,
                style = MspTheme.type.screenTitleLarge,
                color = MspTheme.colors.onSurface,
                modifier = Modifier.testTag(DIA_GRANDE_DE_COBRO_TAG)
            )
            if (franja.tramos.isNotEmpty()) {
                BarraRepartida(franja.tramos)
                FlowRow(
                    modifier = Modifier.testTag(LEYENDA_DE_COBRO_TAG),
                    horizontalArrangement = Arrangement.spacedBy(MspTheme.spacing.md),
                    verticalArrangement = Arrangement.spacedBy(MspTheme.spacing.xs)
                ) {
                    franja.tramos.forEach { LeyendaDelTramo(it) }
                }
            }
        }
    }
}

@Composable
private fun EtiquetaDelAcordado(etiqueta: EtiquetaDelAcordado, texto: String) {
    val colores = MspTheme.colors
    val forma = RoundedCornerShape(percent = 50)
    val aviso = etiqueta == EtiquetaDelAcordado.DISTINTO
    val base = Modifier.clip(forma)
    Text(
        text = texto,
        style = MspTheme.type.captionStrong,
        color = if (aviso) colores.statusPartial else colores.onSurfaceMuted,
        modifier = (
            if (aviso) {
                base.border(1.5.dp, colores.statusPartial, forma)
            } else {
                base.background(colores.progressTrack)
            }
            )
            .padding(horizontal = MspTheme.spacing.sm, vertical = 3.dp)
            .testTag(ETIQUETA_DEL_ACORDADO_TAG)
    )
}

@Composable
private fun colorDelTramo(color: ColorDelTramo): Color = when (color) {
    ColorDelTramo.FUERTE -> MspTheme.colors.statusPaid
    ColorDelTramo.SUAVE -> MspTheme.colors.statusPaid.copy(alpha = OPACIDAD_VERDE_SUAVE)
    ColorDelTramo.OTROS -> MspTheme.colors.progressTrack
}

@Composable
private fun BarraRepartida(tramos: List<TramoDeLaBarra>) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(8.dp)
            .clip(RoundedCornerShape(4.dp))
            .testTag(BARRA_DE_COBRO_TAG),
        horizontalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        tramos.forEach { tramo ->
            Box(
                modifier = Modifier
                    .weight(tramo.pagos.toFloat())
                    .height(8.dp)
                    .background(colorDelTramo(tramo.color))
                    .testTag(TRAMO_DE_COBRO_TAG)
            )
        }
    }
}

@Composable
private fun LeyendaDelTramo(tramo: TramoDeLaBarra) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(MspTheme.spacing.xs + 2.dp)
    ) {
        Box(
            Modifier
                .size(8.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(colorDelTramo(tramo.color))
        )
        Text(
            text = "${tramo.rotulo} ${tramo.pagos}",
            style = MspTheme.type.captionStrong,
            color = MspTheme.colors.onSurfaceMuted
        )
    }
}
