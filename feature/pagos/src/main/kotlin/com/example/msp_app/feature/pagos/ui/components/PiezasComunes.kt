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
import com.example.msp_app.core.common.time.BUSINESS_LOCALE
import com.example.msp_app.core.designsystem.component.MspCard
import com.example.msp_app.core.designsystem.component.MspStatusChip
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

/**
 * El estado como **chip con texto dentro** — `⚠ Vencido 12d` en kollect,
 * `.mstate` en el mock.
 *
 * Antes era el cuadro de color más una etiqueta suelta al lado. Los dos
 * portadores estaban, pero la textura no: kollect mete ícono y texto DENTRO de
 * una pastilla con tint, y pone dos o tres por pantalla. Ahora reusa
 * [MspStatusChip] del design system —el componente compartido, no una copia
 * local— por el overload de terna resuelta, porque nuestro catálogo tiene ocho
 * estados y [ChipStatus] cinco (ver el KDoc de ese overload).
 */
@Composable
fun ChipDeEstado(estado: EstadoDelPeriodo, modifier: Modifier = Modifier) {
    val visual = estadoVisualDe(estado)
    MspStatusChip(
        icon = visual.icono,
        text = visual.etiqueta,
        contentColor = visual.contenido,
        containerColor = visual.fondo,
        modifier = modifier.testTag(ETIQUETA_DE_ESTADO_TAG)
    )
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

/**
 * El label de sección del mock (`.sl` = `10px/700`, `letter-spacing:.16em`,
 * `text-transform:uppercase`) y de kollect ("ÚLTIMOS PAGOS",
 * "COMPORTAMIENTO DE PAGO").
 *
 * Dos cosas que antes no estaban:
 *
 * 1. **Las versalitas.** El KDoc anterior decía "overline en mayúsculas
 *    ópticas" pero el `.uppercase()` no existía, así que la pantalla decía
 *    "sus ventas" donde el mock y kollect dicen "SUS VENTAS". Va con la
 *    locale de negocio y no la del teléfono: en es-MX los acentos se
 *    conservan ("LIQUIDACIÓN"), que es como los escribe kollect.
 * 2. **El rol tipográfico correcto es `eyebrow`, no `overline`.** Verificado
 *    en `CampoType.kt:215-218`: `overline` (12/600, +0.05em) es de los labels
 *    DENTRO de una tarjeta (`.hero .lab`, `.capa .k`), y `eyebrow` (11/700,
 *    +0.09em, "caller uppercases") es el del encabezado de sección — el que
 *    `ClienteDetalleScreen.kt:337` y `VentaDetalleSections.kt:325` usan para
 *    "ÚLTIMOS PAGOS" y "COMPORTAMIENTO DE PAGO". El tracking ancho es la
 *    firma; con `overline` se queda a la mitad.
 */
@Composable
fun LabelDeSeccion(texto: String, modifier: Modifier = Modifier) {
    Text(
        text = texto.uppercase(BUSINESS_LOCALE),
        style = MspTheme.type.eyebrow,
        color = MspTheme.colors.onSurfaceMuted,
        modifier = modifier.padding(top = MspTheme.spacing.md, bottom = MspTheme.spacing.sm)
    )
}

/**
 * Tarjeta base de las secciones (`.money`, `.liq`, `.know`, `.stbig`).
 *
 * **Es un envoltorio de [MspCard], no una tarjeta propia.** Antes era un
 * `Box.clip(shapes.card).background(surface)` y por eso las siete pantallas se
 * veían más suaves que kollect: les faltaba el **hairline de 1dp `outline`**
 * que `MspCard` → `MspSurface` pone en TODA tarjeta del sistema (1:1
 * `CampoCard`/`CampoSurface`). Lo único que esta función agrega encima es lo
 * que las siete pantallas comparten y `MspCard` deliberadamente no asume: el
 * ancho completo, el padding interior de `spacing.md` y `shapes.card` (20dp)
 * en vez del `shapes.tile` (16dp) por default.
 *
 * Nota de encuadre: el hairline sale de **kollect**, no del mock. Los cuatro
 * HTML no ponen borde en ninguna tarjeta (`grep border:` devuelve solo la marca
 * de semana sin pago), así que aquí manda la app de referencia.
 */
@Composable
fun Tarjeta(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    content: @Composable () -> Unit
) {
    MspCard(
        modifier = modifier.fillMaxWidth(),
        shape = MspTheme.shapes.card,
        onClick = onClick
    ) {
        Box(modifier = Modifier.padding(MspTheme.spacing.md)) { content() }
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
