package com.example.msp_app.feature.pagos.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.msp_app.core.designsystem.theme.FontSizeLevel
import com.example.msp_app.core.designsystem.theme.LocalFontSizeLevel
import com.example.msp_app.core.designsystem.theme.MspTheme
import com.example.msp_app.feature.pagos.domain.model.EstadoDelPeriodo
import com.example.msp_app.feature.pagos.ui.EstadoVisual
import com.example.msp_app.feature.pagos.ui.estadoVisualDe

/** `testTag` del cuadro de estado — lo localiza el test de "nunca solo color". */
const val CUADRO_DE_ESTADO_TAG: String = "pagos_cuadro_estado"

/** `testTag` de la etiqueta de estado que acompaña al cuadro. */
const val ETIQUETA_DE_ESTADO_TAG: String = "pagos_etiqueta_estado"

/**
 * El cuadro de estado del mock (`.st`): ícono sobre fondo tint, o sobre relleno
 * sólido en "se negó".
 *
 * Va SIEMPRE acompañado de texto en su fila; el cuadro solo nunca es el
 * portador del significado (regla dura de accesibilidad).
 */
@Composable
fun CuadroDeEstado(visual: EstadoVisual, lado: Dp = 28.dp, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(lado)
            .clip(RoundedCornerShape(lado / 3))
            .background(visual.fondo)
            .testTag(CUADRO_DE_ESTADO_TAG),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = visual.icono,
            contentDescription = null,
            tint = visual.contenido,
            modifier = Modifier.size(lado * 0.54f)
        )
    }
}

/** El cuadro más el texto del estado, en una fila — la unidad mínima legible. */
@Composable
fun EstadoEnFila(estado: EstadoDelPeriodo, modifier: Modifier = Modifier) {
    val visual = estadoVisualDe(estado)
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(MspTheme.spacing.sm)
    ) {
        CuadroDeEstado(visual)
        Text(
            text = visual.etiqueta,
            style = MspTheme.type.captionStrong,
            color = visual.contenido,
            modifier = Modifier.testTag(ETIQUETA_DE_ESTADO_TAG)
        )
    }
}

/**
 * El estado en grande de la pantalla de venta (`.stbig` del mock): cuadro de
 * 44dp, etiqueta y la línea que dice qué hacer.
 */
@Composable
fun EstadoEnGrande(estado: EstadoDelPeriodo, modifier: Modifier = Modifier) {
    val visual = estadoVisualDe(estado)
    Tarjeta(modifier = modifier) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(MspTheme.spacing.sm + MspTheme.spacing.xs)
        ) {
            CuadroDeEstado(visual, lado = 44.dp)
            Column {
                Text(
                    text = visual.etiqueta,
                    style = MspTheme.type.cardTitle,
                    color = MspTheme.colors.onSurface,
                    modifier = Modifier.testTag(ETIQUETA_DE_ESTADO_TAG)
                )
                Text(
                    text = visual.detalle,
                    style = MspTheme.type.caption,
                    color = MspTheme.colors.onSurfaceMuted
                )
            }
        }
    }
}

/** El label de sección del mock (`.sl`): overline en mayúsculas ópticas. */
@Composable
fun LabelDeSeccion(texto: String, modifier: Modifier = Modifier) {
    Text(
        text = texto,
        style = MspTheme.type.overline,
        color = MspTheme.colors.onSurfaceMuted,
        modifier = modifier.padding(top = MspTheme.spacing.md, bottom = MspTheme.spacing.sm)
    )
}

/** Tarjeta base de las secciones (`.money`, `.liq`, `.know`, `.stbig`). */
@Composable
fun Tarjeta(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    content: @Composable () -> Unit
) {
    val cuerpo: @Composable () -> Unit = {
        Box(modifier = Modifier.padding(MspTheme.spacing.md)) { content() }
    }
    if (onClick == null) {
        Box(
            modifier = modifier
                .fillMaxWidth()
                .clip(MspTheme.shapes.card)
                .background(MspTheme.colors.surface)
        ) { cuerpo() }
    } else {
        androidx.compose.material3.Surface(
            onClick = onClick,
            modifier = modifier.fillMaxWidth(),
            shape = MspTheme.shapes.card,
            color = MspTheme.colors.surface,
            content = cuerpo
        )
    }
}

/** Una fila clave/valor de "datos del cliente" / "datos de la venta" (`.kv`). */
@Composable
fun FilaClaveValor(clave: String, valor: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = MspTheme.spacing.sm + MspTheme.spacing.xs),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = clave,
                style = MspTheme.type.kvLabel,
                color = MspTheme.colors.onSurfaceMuted,
                modifier = Modifier.width(140.dp)
            )
            Text(
                text = valor.ifBlank { SIN_DATO },
                style = MspTheme.type.kvValue,
                color = MspTheme.colors.onSurface,
                modifier = Modifier.fillMaxWidth()
            )
        }
        Separador()
    }
}

/** El hairline de 1px que separa filas (`border-top:1px solid var(--line)`). */
@Composable
fun Separador(modifier: Modifier = Modifier, color: Color = MspTheme.colors.outline) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(color)
    )
}

/** Lo que se pinta donde el teléfono no tiene el dato. */
const val SIN_DATO: String = "—"

/**
 * Tres datos en fila cuando caben, apilados cuando no.
 *
 * Con `FontSizeLevel.NORMAL` los tres van en una fila de tercios, como el mock.
 * Con `GRANDE` (1.5) o `MUY_GRANDE` (2.0) la fila deja de caber en 360dp y el
 * tercer dato se sale de la pantalla — comprobado en el golden
 * `pagos_venta_light_2_0` antes de este cambio: "frecuencia" desaparecía. Un
 * dato que se sale no es un detalle visual, es información perdida para el
 * usuario que MÁS ayuda necesita, así que a esas escalas se apilan.
 *
 * Se lee [LocalFontSizeLevel] (la preferencia elegida en la app) y no el
 * `fontScale` del sistema: es el mismo criterio que ya usa el reporte de
 * cobranza para escoger su layout curado.
 */
@Composable
fun TresDatos(
    primero: @Composable (Modifier) -> Unit,
    segundo: @Composable (Modifier) -> Unit,
    tercero: @Composable (Modifier) -> Unit,
    modifier: Modifier = Modifier
) {
    if (LocalFontSizeLevel.current == FontSizeLevel.NORMAL) {
        Row(
            modifier = modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(MspTheme.spacing.sm)
        ) {
            primero(Modifier.weight(1f))
            segundo(Modifier.weight(1f))
            tercero(Modifier.weight(1f))
        }
    } else {
        Column(
            modifier = modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(MspTheme.spacing.sm)
        ) {
            primero(Modifier.fillMaxWidth())
            segundo(Modifier.fillMaxWidth())
            tercero(Modifier.fillMaxWidth())
        }
    }
}
