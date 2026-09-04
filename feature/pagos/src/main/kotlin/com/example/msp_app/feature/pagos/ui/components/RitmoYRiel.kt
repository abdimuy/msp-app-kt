package com.example.msp_app.feature.pagos.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.msp_app.core.common.time.BUSINESS_LOCALE
import com.example.msp_app.core.designsystem.component.MspMoneyText
import com.example.msp_app.core.designsystem.theme.MspTheme
import com.example.msp_app.feature.pagos.domain.RitmoDePagos
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
 * El **ritmo**: las doce últimas semanas como barras — a tiempo / tarde / sin
 * pago — con sus extremos etiquetados y el pie cumple / promedio / sin pago.
 *
 * Los tres colores salen de la tabla del Task 2, no de la esmeralda del mock:
 * `statusPaid` a tiempo, `statusPartial` tarde, y sin pago como tocón
 * `statusOverdueTint` con borde `statusOverdue`. La leyenda de abajo es el
 * cuarto portador: el color nunca va solo.
 */
@Composable
fun RitmoDeSemanas(historial: HistorialDePagos, modifier: Modifier = Modifier) {
    Column(modifier = modifier.testTag(RITMO_TAG)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp),
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(MspTheme.spacing.xs)
        ) {
            historial.semanas.forEach { semana ->
                BarraDeSemana(
                    semana = semana,
                    altura = RitmoDePagos.alturaDe(semana, historial.semanas),
                    modifier = Modifier.weight(1f)
                )
            }
        }
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

@Composable
private fun BarraDeSemana(semana: SemanaDeRitmo, altura: Float, modifier: Modifier = Modifier) {
    val colors = MspTheme.colors
    val relleno = when (semana.ritmo) {
        RitmoDeSemana.A_TIEMPO -> colors.statusPaid
        RitmoDeSemana.TARDE -> colors.statusPartial
        RitmoDeSemana.SIN_PAGO -> colors.statusOverdueTint
    }
    Box(
        modifier = modifier
            .fillMaxHeight(altura)
            .clip(RoundedCornerShape(4.dp))
            .background(relleno)
            .then(
                if (semana.ritmo == RitmoDeSemana.SIN_PAGO) {
                    Modifier.border(1.dp, colors.statusOverdue, RoundedCornerShape(4.dp))
                } else {
                    Modifier
                }
            )
    )
}

@Composable
private fun LeyendaDelRitmo(modifier: Modifier = Modifier) {
    val colors = MspTheme.colors
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(MspTheme.spacing.md)
    ) {
        ItemDeLeyenda("a tiempo", colors.statusPaid)
        ItemDeLeyenda("tarde", colors.statusPartial)
        ItemDeLeyenda("sin pago", colors.statusOverdue)
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
                text = clave,
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
            text = mes.nombre,
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
