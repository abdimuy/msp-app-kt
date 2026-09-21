package com.example.msp_app.feature.pagos.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.msp_app.core.designsystem.theme.FontSizeLevel
import com.example.msp_app.core.designsystem.theme.LocalFontSizeLevel
import com.example.msp_app.core.designsystem.theme.MspTheme

/**
 * **El control segmentado, compartido.**
 *
 * `SegmentadoDeCobranza` (`PiezasDeLaLista.kt` — el filtro de la lista de
 * clientes) y `FiltrosDeContacto` (`LineaDeContactos.kt` — el filtro de la
 * bitácora y del detalle de venta, Task 4) delegan aquí. Lo que cambia entre
 * los dos filtros es **cuáles** opciones se enseñan —`SegmentoDeCobranza`
 * esconde "Hoy" mientras `HOY_VISIBLE` esté apagado; `FiltroDeContactos`
 * siempre enseña sus cuatro, cero incluido, por decisión del dueño— y
 * **cómo** se etiqueta y se marca cada una. La tinta, el alto tocable de
 * 50dp y el comportamiento de ancho/scroll son UN solo control: compartirlo
 * no obliga a parametrizar nada que no fuera ya distinto entre los dos
 * filtros — es justo lo que ya recibían por parámetro (`seleccionado`,
 * `conteos`, `onElegir`).
 *
 * Vive en su propio archivo — y no dentro de `PiezasDeLaLista.kt`, donde
 * nació— por dos razones: `TooManyFunctions` de detekt (el archivo de la
 * lista ya rozaba el techo de 11) y porque un control que usan DOS filtros
 * no es "una pieza de la lista", es un componente de nivel de módulo.
 *
 * `internal`: sólo lo usan los dos filtros de `:feature:pagos`; no hace
 * falta que salga del módulo, porque el mecanismo es de este feature, no del
 * design system (la decisión completa, con lo que se consideró y se
 * descartó, está en `task-4-report.md`).
 */
@Composable
internal fun <T> ControlSegmentado(
    opciones: List<T>,
    seleccionado: T,
    conteos: Map<T, Int>,
    etiquetaDe: (T) -> String,
    tagDe: (T) -> String,
    onElegir: (T) -> Unit,
    modifier: Modifier = Modifier
) {
    val reparteElAncho = LocalFontSizeLevel.current == FontSizeLevel.NORMAL
    val rueda = if (reparteElAncho) {
        Modifier
    } else {
        Modifier.horizontalScroll(rememberScrollState())
    }
    Box(modifier = modifier.then(rueda)) {
        Surface(
            modifier = if (reparteElAncho) Modifier.fillMaxWidth() else Modifier,
            color = MspTheme.colors.surface,
            shape = MspTheme.shapes.control,
            border = BorderStroke(GROSOR_DEL_BORDE, MspTheme.colors.outline)
        ) {
            Row(
                // Sin padding vertical a propósito: el alto del control ES el
                // alto tocable del segmento, no una franja pintada alrededor.
                modifier = Modifier.padding(horizontal = SANGRIA_DEL_SEGMENTADO),
                horizontalArrangement = Arrangement.spacedBy(SEPARACION_DE_SEGMENTOS),
                verticalAlignment = Alignment.CenterVertically
            ) {
                opciones.forEach { opcion ->
                    Segmento(
                        etiqueta = etiquetaDe(opcion),
                        cuantos = conteos[opcion] ?: 0,
                        activo = opcion == seleccionado,
                        onElegir = { onElegir(opcion) },
                        tag = tagDe(opcion),
                        modifier = if (reparteElAncho) Modifier.weight(1f) else Modifier
                    )
                }
            }
        }
    }
}

/**
 * Un segmento del control — compartido por `SegmentadoDeCobranza` y
 * `FiltrosDeContacto` a través de [ControlSegmentado].
 *
 * El rótulo admite **dos renglones**: a escala NORMAL "sin visitar" ocupa casi
 * los 79dp que le tocan, y cortarlo con puntos suspensivos escondería justo el
 * filtro que más trabajo agrupa. Con el conteo debajo, dos renglones de rótulo
 * siguen cabiendo dentro de los 48dp tocables, así que partir no agranda el
 * control.
 *
 * A escala grande el control deja de repartir el ancho y rueda en horizontal
 * ([ControlSegmentado]) — ahí cada segmento mide su propio ancho sin
 * restricción, así que en la práctica el segundo renglón nunca hace falta.
 * Es la misma razón por la que la Task 4 no tuvo que resolver un recorte real
 * en las cuatro etiquetas de una palabra de `FiltroDeContactos`: el corte que
 * el límite de dos renglones + elipsis evita es el de NORMAL con ancho
 * repartido, no el de MUY_GRANDE — y a MUY_GRANDE el control rueda en vez de
 * cortar, verificado en los goldens `pagos_bitacora_*_2_0` y
 * `pagos_venta_linea_*_2_0`.
 */
@Composable
private fun Segmento(
    etiqueta: String,
    cuantos: Int,
    activo: Boolean,
    onElegir: () -> Unit,
    tag: String,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .heightIn(min = ALTO_TOCABLE_DEL_SEGMENTO)
            .clip(MspTheme.shapes.chip9)
            // El segmento activo va en `brand` — es el control protagónico de la
            // pantalla. Nunca en `statusPaid`: el verde es solo estado.
            .background(if (activo) MspTheme.colors.brand else Color.Transparent)
            .clickable(onClick = onElegir)
            .padding(horizontal = MspTheme.spacing.xs)
            .testTag(tag),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = etiqueta,
                style = MspTheme.type.chipLabel,
                color = if (activo) MspTheme.colors.onBrand else MspTheme.colors.onSurfaceMuted,
                maxLines = RENGLONES_DEL_ROTULO,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center
            )
            Text(
                text = cuantos.toString(),
                style = MspTheme.type.captionStrong,
                // El conteo acompaña al rótulo, no compite con él: sobre el
                // relleno de marca baja a tres cuartos de opacidad.
                color = if (activo) {
                    MspTheme.colors.onBrand.copy(alpha = OPACIDAD_DEL_CONTEO)
                } else {
                    MspTheme.colors.onSurfaceMuted
                },
                maxLines = 1,
                textAlign = TextAlign.Center
            )
        }
    }
}

/**
 * **50dp, y los pone el toque.** El control pinta exactamente esto de alto
 * porque el contenedor no agrega padding vertical; la pastilla anterior pintaba
 * 56 por fijar el `touchTarget` del tema (56dp) y no quitarse el padding.
 */
private val ALTO_TOCABLE_DEL_SEGMENTO = 50.dp

private val SANGRIA_DEL_SEGMENTADO = 3.dp

private val SEPARACION_DE_SEGMENTOS = 2.dp

private val GROSOR_DEL_BORDE = 1.dp

private const val RENGLONES_DEL_ROTULO = 2

private const val OPACIDAD_DEL_CONTEO = 0.75f
