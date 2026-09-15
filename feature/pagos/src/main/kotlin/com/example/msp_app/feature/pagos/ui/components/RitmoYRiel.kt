package com.example.msp_app.feature.pagos.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.msp_app.core.common.time.BUSINESS_LOCALE
import com.example.msp_app.core.designsystem.component.MspMoneyText
import com.example.msp_app.core.designsystem.theme.MspTheme
import com.example.msp_app.feature.pagos.domain.model.HistorialDePagos
import com.example.msp_app.feature.pagos.domain.model.MesDePagos
import com.example.msp_app.feature.pagos.domain.model.PagoDelHistorial
import com.example.msp_app.feature.pagos.domain.model.RitmoDeSemana
import com.example.msp_app.feature.pagos.domain.model.SemanaDeRitmo
import java.time.format.DateTimeFormatter

/** `testTag` del bloque de ritmo (las doce barras). */
const val RITMO_TAG: String = "pagos_ritmo"

/** `testTag` del nodo cuadrado que abre un mes en el riel. */
const val NODO_DE_MES_TAG: String = "pagos_nodo_mes"

private val MES_CORTO: DateTimeFormatter = DateTimeFormatter.ofPattern("MMM", BUSINESS_LOCALE)
private val DIA_Y_MES: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM", BUSINESS_LOCALE)

/**
 * El **ritmo**: las doce últimas semanas como **doce cuadros iguales** — a
 * tiempo / tarde / sin pago — con sus extremos etiquetados y el pie cumple /
 * promedio / sin pago.
 *
 * ## Por qué cuadros iguales y no barras de altura variable
 *
 * Aquí el mock aprobado y kollect se contradecían: el mock pinta
 * `.weeks{align-items:flex-end}` con altura proporcional al dinero cobrado, y
 * kollect pinta doce `Box` de **22dp fijos** coloreados por estado
 * (`VentaDetalleSections.kt:337-346`, `COMPORTAMIENTO DE PAGO`). **El dueño
 * eligió kollect**, así que la altura deja de codificar el monto y el estado
 * queda como el único eje — que es también el eje que la leyenda explica.
 *
 * Efecto lateral que vale decir: la barra alta ya no dice "esta semana entró
 * más dinero". Ese dato no se pierde —el riel de abajo lista cada pago con su
 * importe— pero deja de leerse de un vistazo en la tira.
 *
 * ## Los cuatro tratamientos, copiados de `weekBarColor`
 *
 * `statusPaid` a tiempo, `statusPartial` tarde, y sin pago como
 * **`progressTrack` mezclado 45% hacia `statusOverdue`**, que es exactamente
 * la fórmula de kollect (`WEEK_MISSED_TINT`). Antes era `statusOverdueTint`
 * con borde `statusOverdue`, y en el golden se leía como una pastilla roja
 * hueca —un error de render— en vez de como un estado.
 *
 * Kollect tiene un cuarto tratamiento, `WeekMark.Idle` → `progressTrack` para
 * la semana **sin dato**, y nosotros no podemos pintarlo: `RitmoDeSemana`
 * tiene tres valores y el dominio no distingue "no cobró" de "no hay dato"
 * (`RitmoDePagos.de` rellena las doce semanas con `Money.ZERO`, así que la
 * ausencia de dato ya entró como SIN_PAGO). Darle un color propio exigiría un
 * cuarto estado en el dominio, y esto es acabado visual.
 *
 * La leyenda de abajo es el portador que hace que el color nunca vaya solo.
 */
@Composable
fun RitmoDeSemanas(historial: HistorialDePagos, modifier: Modifier = Modifier) {
    Column(modifier = modifier.testTag(RITMO_TAG)) {
        TiraDeRitmo(historial.semanas)
        Spacer(Modifier.height(MspTheme.spacing.sm))
        Row(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = historial.semanas.firstOrNull()?.let {
                    MES_CORTO.format(
                        it.inicio
                    )
                }.orEmpty(),
                style = MspTheme.type.caption,
                color = MspTheme.colors.onSurfaceMuted,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = historial.semanas.lastOrNull()?.let {
                    MES_CORTO.format(
                        it.inicio
                    )
                }.orEmpty(),
                style = MspTheme.type.caption,
                color = MspTheme.colors.onSurfaceMuted,
                textAlign = TextAlign.End
            )
        }
        Spacer(Modifier.height(MspTheme.spacing.sm + MspTheme.spacing.xs))
        TresDatos(
            primero = { celda ->
                CeldaDeResumen(
                    clave = "cumple",
                    valor = "${historial.resumen.cumplidas} de ${historial.resumen.totalSemanas}",
                    modifier = celda
                )
            },
            segundo = { celda ->
                CeldaDeResumen(
                    clave = "promedio",
                    monto = historial.resumen.promedio.amount,
                    modifier = celda
                )
            },
            tercero = { celda ->
                CeldaDeResumen(
                    clave = "sin pago",
                    valor = "${historial.resumen.semanasSinPago} sem",
                    modifier = celda
                )
            }
        )
        Spacer(Modifier.height(MspTheme.spacing.sm))
        LeyendaDelRitmo()
    }
}

/**
 * **Solo la tira** de doce cuadros, sin las etiquetas de mes, sin el pie de tres
 * datos y sin la leyenda.
 *
 * Existe porque el detalle del CLIENTE pinta el mismo ritmo con otro marco: ahí
 * arriba va "ritmo · 12 semanas" con el "9 de 12" a la derecha, y abajo el día
 * de la ruta. Copiar los doce cuadros habría dejado dos tiras con dos escalas de
 * color que se pueden despegar; extraerlas deja una sola.
 *
 * [RitmoDeSemanas] —la pantalla de VENTA— la llama y le agrega su propio marco,
 * así que la tira de las dos pantallas es literalmente el mismo código.
 *
 * **Quien la use tiene que poner el portador que no es color** cerca: en la
 * venta es la leyenda, en el cliente es el "N de 12" del encabezado.
 */
@Composable
fun TiraDeRitmo(semanas: List<SemanaDeRitmo>, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.spacedBy(MspTheme.spacing.xs)
    ) {
        semanas.forEach { semana ->
            BarraDeSemana(semana = semana, modifier = Modifier.weight(1f))
        }
    }
}

/** El alto fijo de cada cuadro de semana — los 22dp de kollect. */
private val LADO_DE_LA_SEMANA = 22.dp

/** La forma del cuadro de semana — `RoundedCornerShape(5.dp)` de kollect. */
private val FORMA_DE_LA_SEMANA = RoundedCornerShape(5.dp)

/**
 * Cuánto se mezcla la semana vacía hacia `statusOverdue` sobre el riel — el
 * `WEEK_MISSED_TINT` de kollect, `color-mix(overdue 45%, track)`.
 *
 * El nombre evita la palabra del estado a propósito: `NoDoubleForMoney` lee
 * "PAGO" en el identificador y marca todo `Float` que lo lleve, que es la red
 * correcta (un importe nunca va en `Float`) atrapando un dato que no es dinero
 * — esto es una fracción de mezcla de color.
 */
private const val MEZCLA_DE_LA_SEMANA_VACIA = 0.45f

/** El color de una semana, 1:1 con `weekBarColor` de kollect. */
@Composable
internal fun colorDeLaSemana(ritmo: RitmoDeSemana): Color {
    val colors = MspTheme.colors
    return when (ritmo) {
        RitmoDeSemana.A_TIEMPO -> colors.statusPaid
        RitmoDeSemana.TARDE -> colors.statusPartial
        RitmoDeSemana.SIN_PAGO ->
            lerp(colors.progressTrack, colors.statusOverdue, MEZCLA_DE_LA_SEMANA_VACIA)
    }
}

@Composable
private fun BarraDeSemana(semana: SemanaDeRitmo, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .height(LADO_DE_LA_SEMANA)
            .clip(FORMA_DE_LA_SEMANA)
            .background(colorDeLaSemana(semana.ritmo))
    )
}

@Composable
private fun LeyendaDelRitmo(modifier: Modifier = Modifier) {
    val colors = MspTheme.colors
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(MspTheme.spacing.md)
    ) {
        ItemDeLeyenda("a tiempo", colorDeLaSemana(RitmoDeSemana.A_TIEMPO))
        ItemDeLeyenda("tarde", colorDeLaSemana(RitmoDeSemana.TARDE))
        ItemDeLeyenda("sin pago", colorDeLaSemana(RitmoDeSemana.SIN_PAGO))
    }
}

@Composable
private fun ItemDeLeyenda(texto: String, color: Color) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(MspTheme.spacing.xs)
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(color)
        )
        Text(
            text = texto,
            style = MspTheme.type.caption,
            color = MspTheme.colors.onSurfaceMuted
        )
    }
}

@Composable
private fun CeldaDeResumen(
    clave: String,
    modifier: Modifier = Modifier,
    valor: String? = null,
    monto: java.math.BigDecimal? = null
) {
    Box(
        modifier = modifier
            .clip(MspTheme.shapes.control)
            .background(MspTheme.colors.surface2)
            .padding(MspTheme.spacing.sm + MspTheme.spacing.xs)
    ) {
        Column {
            Text(
                text = clave.uppercase(BUSINESS_LOCALE),
                style = MspTheme.type.overline,
                color = MspTheme.colors.onSurfaceMuted
            )
            Spacer(Modifier.height(MspTheme.spacing.xs))
            if (monto != null) {
                MspMoneyText(
                    amount = monto,
                    style = MspTheme.type.metricSmall,
                    color = MspTheme.colors.onSurface
                )
            } else {
                Text(
                    text = valor.orEmpty(),
                    style = MspTheme.type.metricSmall,
                    color = MspTheme.colors.onSurface
                )
            }
        }
    }
}

/**
 * El **riel** de pagos, agrupado por mes y **visible sin colapsar**: el mes
 * interrumpe el riel con un nodo cuadrado, su nombre y su subtotal; los pagos
 * cuelgan debajo con nodos redondos.
 *
 * No hay estado de expandido/colapsado y no debe haberlo — la decisión del
 * `task-16-brief.md` es que nada quede escondido detrás de un toque.
 */
@Composable
fun RielDeMeses(meses: List<MesDePagos>, modifier: Modifier = Modifier) {
    val riel = MspTheme.colors.outline
    val centro = CENTRO_DEL_RIEL
    Column(
        modifier = modifier
            .fillMaxWidth()
            .drawBehind {
                val x = centro.toPx()
                drawLine(
                    color = riel,
                    start = Offset(x, 0f),
                    end = Offset(x, size.height),
                    strokeWidth = 2.dp.toPx()
                )
            }
    ) {
        meses.forEach { mes ->
            NodoDeMes(mes)
            mes.pagos.forEach { pago -> FilaDePagoDelRiel(pago) }
        }
    }
}

/** El eje vertical del riel: el centro de los nodos, cuadrados y redondos. */
private val CENTRO_DEL_RIEL = 5.dp

@Composable
private fun NodoDeMes(mes: MesDePagos, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = MspTheme.spacing.md, bottom = MspTheme.spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(MspTheme.spacing.sm)
    ) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(MspTheme.colors.onSurfaceMuted)
                .testTag(NODO_DE_MES_TAG)
        )
        Text(
            // `.mm .mn` del mock: `10px/800`, `.15em`, `uppercase` — y
            // "HISTORIAL DE PAGOS · JULIO 2026" en kollect.
            text = mes.nombre.uppercase(BUSINESS_LOCALE),
            style = MspTheme.type.overline,
            color = MspTheme.colors.onSurfaceMuted
        )
        Box(
            modifier = Modifier
                .weight(1f)
                .height(1.dp)
                .background(MspTheme.colors.outline)
        )
        MspMoneyText(
            amount = mes.subtotal.amount,
            style = MspTheme.type.captionStrong,
            color = MspTheme.colors.onSurfaceMuted
        )
    }
}

@Composable
private fun FilaDePagoDelRiel(pago: PagoDelHistorial, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(bottom = MspTheme.spacing.sm + MspTheme.spacing.xs),
        horizontalArrangement = Arrangement.spacedBy(MspTheme.spacing.sm)
    ) {
        Box(
            modifier = Modifier
                .padding(top = MspTheme.spacing.xs + 2.dp)
                .width(10.dp),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(RoundedCornerShape(percent = 50))
                    .background(MspTheme.colors.statusPaid)
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.Bottom) {
                MspMoneyText(
                    amount = pago.importe.amount,
                    style = MspTheme.type.amountRow,
                    color = MspTheme.colors.onSurface
                )
                Spacer(Modifier.width(MspTheme.spacing.sm))
                Text(
                    text = pago.metodo.etiqueta,
                    style = MspTheme.type.caption,
                    color = MspTheme.colors.onSurfaceMuted,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = DIA_Y_MES.format(
                        com.example.msp_app.core.common.time.AppTime.toBusinessDate(pago.fecha)
                    ),
                    style = MspTheme.type.captionStrong,
                    color = MspTheme.colors.onSurfaceMuted
                )
            }
            if (pago.nota != null) {
                Text(
                    text = pago.nota,
                    style = MspTheme.type.caption,
                    color = MspTheme.colors.onSurfaceMuted
                )
            }
        }
    }
}
