package com.example.msp_app.feature.pagos.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
import com.example.msp_app.core.designsystem.theme.mspLightColors
import com.example.msp_app.feature.pagos.domain.EtiquetaDeDia
import com.example.msp_app.feature.pagos.domain.EtiquetaDelAcordado
import com.example.msp_app.feature.pagos.domain.FranjaDeCobro

/** `testTag` de la franja entera. */
const val FRANJA_DE_COBRO_TAG: String = "pagos_franja_cobro"

/** `testTag` de la etiqueta del acordado (esquina superior derecha). */
const val ETIQUETA_DEL_ACORDADO_TAG: String = "pagos_franja_cobro_etiqueta"

/** `testTag` del día grande. */
const val DIA_GRANDE_DE_COBRO_TAG: String = "pagos_franja_cobro_dia"

/** `testTag` de la fila de etiquetas de día. */
const val FILA_DE_ETIQUETAS_DE_COBRO_TAG: String = "pagos_franja_cobro_etiquetas"

/** `testTag` de cada etiqueta de día. */
const val ETIQUETA_DE_DIA_TAG: String = "pagos_franja_cobro_etiqueta_dia"

/** `testTag` del texto chico bajo las etiquetas. */
const val PAGOS_POR_DIA_TAG: String = "pagos_franja_cobro_pagos_por_dia"

/**
 * El verde de la etiqueta del día principal: el `statusPaid` del tema CLARO **en
 * los dos temas**. Lleva texto BLANCO y el verde menta del tema oscuro no da
 * contraste con blanco; el de claro da más de 5:1 (ver `ContrasteDeLaEtiquetaTest`).
 */
internal fun verdeDeLaEtiqueta(): Color = mspLightColors().statusPaid

/** Texto de la etiqueta principal: blanco en ambos temas. */
internal val TextoDeLaEtiquetaPrincipal: Color = Color.White

/**
 * **La franja "Día de cobro"** (forma B, "Sin barra"): la etiqueta del acordado
 * arriba a la derecha, el día en grande, una fila de etiquetas redondeadas con
 * los pagos de cada día y el texto chico "Pagos por día, de sus últimos N". Solo
 * pinta lo que decidió `franjaDeCobro`.
 *
 * Colores: la etiqueta del día principal lleva [verdeDeLaEtiqueta] con texto
 * blanco en los dos temas; las demás, borde `outline` y texto del tema; el aviso
 * naranja del acordado es `statusPartial`. Ninguno nuevo.
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
            if (franja.dias.isNotEmpty()) {
                FlowRow(
                    modifier = Modifier.testTag(FILA_DE_ETIQUETAS_DE_COBRO_TAG),
                    horizontalArrangement = Arrangement.spacedBy(MspTheme.spacing.sm),
                    verticalArrangement = Arrangement.spacedBy(MspTheme.spacing.xs)
                ) {
                    franja.dias.forEach { EtiquetaDelDia(it) }
                }
                franja.pagosConsiderados?.let {
                    Text(
                        text = "Pagos por día, de sus últimos $it",
                        style = MspTheme.type.caption,
                        color = MspTheme.colors.onSurfaceMuted,
                        modifier = Modifier.testTag(PAGOS_POR_DIA_TAG)
                    )
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
private fun EtiquetaDelDia(dia: EtiquetaDeDia) {
    val forma = RoundedCornerShape(percent = 50)
    val base = Modifier.clip(forma)
    Text(
        text = "${dia.rotulo} ${dia.pagos}",
        style = MspTheme.type.captionStrong,
        color = if (dia.principal) TextoDeLaEtiquetaPrincipal else MspTheme.colors.onSurface,
        modifier = (
            if (dia.principal) {
                base.background(verdeDeLaEtiqueta())
            } else {
                base.border(1.dp, MspTheme.colors.outline, forma)
            }
            )
            .padding(horizontal = MspTheme.spacing.sm + 2.dp, vertical = 4.dp)
            .testTag(ETIQUETA_DE_DIA_TAG)
    )
}
