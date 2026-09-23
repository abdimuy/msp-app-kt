package com.example.msp_app.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.example.msp_app.core.designsystem.theme.MspTheme

/**
 * Las piezas de una pantalla de ticket imprimible.
 *
 * Viven aquí y no en cada feature porque **el ticket de pago
 * (`:feature:pagos`) y el de visita (`:feature:visitas`) son la misma pantalla
 * con otro papel adentro**. La primera versión las duplicó: 529 líneas casi
 * idénticas en dos archivos, incluido el porqué del tamaño fijo del facsímil —
 * que existe porque fue un bug una vez. Un arreglo tendría que aterrizar dos
 * veces, y la segunda se olvidaría.
 *
 * Ninguna pone `testTag` por dentro: cada feature tiene sus propias etiquetas
 * (`pagos_ticket_*` / `visitas_ticket_*`) y las cuelga del [Modifier] que pasa.
 */
private val TOQUE_DEL_TICKET = 56.dp

/**
 * Alto de letra del facsímil, en dp (constante ante `fontScale`). A 11dp, 32
 * columnas monoespaciadas ocupan ~211dp: caben de sobra en los 296dp útiles de
 * una pantalla de 360dp con los paddings de la tarjeta.
 */
private val ALTO_DE_LETRA_DEL_FACSIMIL = 11.dp

/** Interlineado del facsímil, en múltiplos del tamaño de letra. */
private const val ALTURA_DE_RENGLON = 1.4f

/**
 * Una banda de estado sobre el ticket: título arriba, detalle abajo, sobre un
 * `tint` del color que le toca.
 *
 * **Nunca solo color**: el significado lo lleva el texto y el color solo lo
 * acompaña — la misma regla dura que el resto del design system. Un cobrador
 * que no distingue el rojo del ámbar tiene que poder leer "fuera del día".
 */
@Composable
fun MspTicketBanner(
    titulo: String,
    detalle: String,
    fondo: Color,
    contenido: Color,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(MspTheme.shapes.tile)
            .background(fondo)
            .padding(MspTheme.spacing.md)
    ) {
        Text(text = titulo, style = MspTheme.type.cardTitle, color = contenido)
        Text(text = detalle, style = MspTheme.type.caption, color = contenido)
    }
}

/**
 * El resumen legible: las cifras del ticket en la tipografía del design system,
 * que **sí** crece con la preferencia de tamaño de letra del usuario.
 *
 * Existe por lo que se vio en el golden `pagos_ticket_primera_dark_2_0` antes de
 * este componente: a `FontSizeLevel.MUY_GRANDE` el facsímil monoespaciado de 32
 * columnas ya no cabía en 360dp y los importes de la derecha quedaban fuera de
 * pantalla. Es el mismo defecto que se arregló en `TresDatos`: un dato que se
 * sale no es un detalle visual, es información perdida justo para el usuario que
 * MÁS ayuda necesita.
 *
 * El reparto que resuelve el choque: **aquí van las cifras, legibles a cualquier
 * escala; abajo va el facsímil del papel**, que es una imagen de cómo se verá el
 * rollo y no el lugar donde se leen los montos.
 */
@Composable
fun MspTicketSummary(
    cliente: String,
    etiquetaPrincipal: String,
    montoPrincipal: String,
    etiquetaSecundaria: String,
    montoSecundario: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(MspTheme.shapes.card)
            .background(MspTheme.colors.surface)
            .padding(MspTheme.spacing.md),
        verticalArrangement = Arrangement.spacedBy(MspTheme.spacing.xs)
    ) {
        Text(text = cliente, style = MspTheme.type.cardTitle, color = MspTheme.colors.onSurface)
        CifraDelResumen(etiquetaPrincipal, montoPrincipal, MspTheme.colors.brand)
        CifraDelResumen(etiquetaSecundaria, montoSecundario, MspTheme.colors.onSurface)
    }
}

@Composable
private fun CifraDelResumen(etiqueta: String, monto: String, color: Color) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = etiqueta,
            style = MspTheme.type.kvLabel,
            color = MspTheme.colors.onSurfaceMuted,
            modifier = Modifier.weight(1f)
        )
        Text(text = monto, style = MspTheme.type.amountRow, color = color)
    }
}

/**
 * El **facsímil del papel**: el MISMO texto de 32 columnas que recibiría la
 * impresora, en monoespaciada.
 *
 * ## Por qué su letra NO crece con la preferencia del usuario
 *
 * Un ticket térmico son treinta y dos columnas de ancho fijo: si la letra crece,
 * el renglón deja de caber y los importes de la derecha se salen de pantalla —
 * exactamente lo que mostraba el golden a escala 2.0 antes de este cambio. Y
 * envolver los renglones no es opción: destruiría la única cosa que este bloque
 * existe para enseñar, que es cómo va a quedar el papel.
 *
 * Así que el tamaño se fija en **dp convertidos a sp** (`11.dp.toSp()`), que es
 * constante ante `fontScale`. Las cifras legibles no se pierden: viven arriba,
 * en [MspTicketSummary], donde sí crecen con la preferencia del usuario.
 *
 * El [horizontalScroll] se queda como red de seguridad para pantallas más
 * angostas que las 360dp del golden.
 */
@Composable
fun MspTicketFacsimile(texto: String, modifier: Modifier = Modifier) {
    val tamanoDelFacsimil = with(LocalDensity.current) { ALTO_DE_LETRA_DEL_FACSIMIL.toSp() }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MspTheme.shapes.card)
            .background(MspTheme.colors.surface)
    ) {
        Text(
            text = texto,
            style = MspTheme.type.caption.copy(
                fontFamily = FontFamily.Monospace,
                fontSize = tamanoDelFacsimil,
                lineHeight = tamanoDelFacsimil * ALTURA_DE_RENGLON
            ),
            color = MspTheme.colors.onSurface,
            modifier = modifier
                .horizontalScroll(rememberScrollState())
                .padding(MspTheme.spacing.md)
        )
    }
}

/**
 * Un renglón del picker de impresoras: nombre arriba, MAC abajo.
 *
 * Recibe cadenas y no un `PrinterDevice` a propósito: es lo único que le
 * impedía vivir aquí, y `:core:designsystem` no tiene por qué conocer el dominio
 * de impresión para pintar dos líneas de texto.
 */
@Composable
fun MspPrinterRow(
    nombre: String,
    direccion: String,
    elegida: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = TOQUE_DEL_TICKET),
        shape = MspTheme.shapes.tile,
        color = MspTheme.colors.surface
    ) {
        Column(
            modifier = Modifier.padding(
                horizontal = MspTheme.spacing.md,
                vertical = MspTheme.spacing.sm
            )
        ) {
            Text(
                text = nombre,
                style = MspTheme.type.name,
                color = if (elegida) MspTheme.colors.brand else MspTheme.colors.onSurface
            )
            Text(
                text = direccion,
                style = MspTheme.type.subtitle,
                color = MspTheme.colors.onSurfaceMuted
            )
        }
    }
}

/** La barra superior del ticket: "atrás" y el título de la pantalla. */
@Composable
fun MspTicketTopBar(
    titulo: String,
    onAtras: () -> Unit,
    modifier: Modifier = Modifier,
    atrasModifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = MspTheme.spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(MspTheme.spacing.sm)
    ) {
        Surface(
            onClick = onAtras,
            modifier = atrasModifier.heightIn(min = TOQUE_DEL_TICKET),
            shape = MspTheme.shapes.chip,
            color = MspTheme.colors.surface
        ) {
            Box(
                modifier = Modifier.padding(horizontal = MspTheme.spacing.md),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "atrás",
                    style = MspTheme.type.buttonSmall,
                    color = MspTheme.colors.onSurface
                )
            }
        }
        Text(text = titulo, style = MspTheme.type.detailTitle, color = MspTheme.colors.onSurface)
    }
}
