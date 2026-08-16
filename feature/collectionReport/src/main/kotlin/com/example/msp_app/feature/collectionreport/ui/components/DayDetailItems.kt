package com.example.msp_app.feature.collectionreport.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.msp_app.core.designsystem.component.MspCard
import com.example.msp_app.core.designsystem.component.MspInitialsAvatar
import com.example.msp_app.core.designsystem.component.MspMoneyText
import com.example.msp_app.core.designsystem.theme.MspTheme
import com.example.msp_app.feature.collectionreport.ui.DayRowUi
import com.example.msp_app.feature.collectionreport.ui.DetailUi

/** Avatar de iniciales del día — 34dp, 1:1 `.srow .sa` del mockup (el de pago es 38dp). */
private val DAY_AVATAR_SIZE = 34.dp

/** Grosor del hairline de `MspSurface` — el mismo 1dp que la tarjeta real. */
private val HAIRLINE = 1.dp

/**
 * Cuánto se extiende el rectángulo de la tarjeta más allá de la rebanada que se está pintando.
 * Sólo tiene que superar el radio de `shapes.sectionCard` (18dp) para que las esquinas del
 * rectángulo caigan FUERA del recorte del ítem; 64dp deja margen de sobra sin costar nada
 * (queda recortado igual).
 */
private val CARD_SEGMENT_OVERDRAW = 64.dp

/** Tipo de contenido de las filas de día — deja que la lista recicle sus composiciones. */
private const val DAY_DETAIL_ROW_CONTENT_TYPE = "collection_report_day_row"

/** Llave/tipo del estado vacío del resumen por día (un solo ítem, nunca junto a filas). */
private const val DAY_DETAIL_EMPTY_KEY = "collection_report_day_detail_empty"

/**
 * Detalle de SEMANA ([DetailUi.Days]) como ítems de la `LazyColumn` del tablero: un ítem por
 * día del ciclo, más el `topGap` que lo separa del [DetailHeader] de arriba.
 *
 * **Por qué perezoso.** El KDoc anterior de [DetailList] afirmaba que Semana "son 5 filas por
 * definición del ciclo, nunca hay overflow que esconder". No es cierto y nada lo garantiza: el
 * ciclo va de `FECHA_CARGA_INICIAL` (Firestore, puede quedar vieja) a hoy, sin tope, así que
 * `RangeCalculator.cycleDays` devuelve tantos días como hayan pasado — 182 con una carga de
 * hace seis meses. Pintados de corrido, el salto Día↔Semana medía 212 frames perdidos y frames
 * de 1.7 s en dispositivo (≈1.2 s de ellos en DIBUJADO). Como ítems, sólo se compone y dibuja
 * lo que cabe en pantalla.
 *
 * **Llave.** [DayRowUi.date] convertida a día epoch: identifica el renglón sin ambigüedad, es
 * un `Long` (tipo que el `Bundle` de la lista sabe guardar) y sobrevive a que el ciclo crezca
 * por delante o se recorte por detrás. [DayRowUi.label] NO sirve de llave — se repite en
 * ciclos de más de un año y una llave duplicada revienta el `LazyColumn` en runtime.
 *
 * **La tarjeta sigue siendo UNA.** Cada ítem pinta su rebanada del mismo rectángulo redondeado
 * ([daySectionCardSegment]) en vez de su propia tarjeta: por fuera se ve idéntico a la
 * [MspCard] única de antes, con las esquinas sólo arriba del primer día y abajo del último.
 */
fun LazyListScope.dayDetailItems(
    rows: List<DayRowUi>,
    masked: Boolean,
    onDayClick: (Int) -> Unit,
    topGap: Dp
) {
    if (rows.isEmpty()) {
        item(key = DAY_DETAIL_EMPTY_KEY, contentType = DAY_DETAIL_EMPTY_KEY) {
            MspCard(
                modifier = Modifier
                    .padding(top = topGap)
                    .fillMaxWidth(),
                shape = MspTheme.shapes.sectionCard
            ) {
                EmptyDetail()
            }
        }
        return
    }
    itemsIndexed(
        items = rows,
        key = { _, row -> row.date.toEpochDay() },
        contentType = { _, _ -> DAY_DETAIL_ROW_CONTENT_TYPE }
    ) { index, row ->
        Column(
            modifier = Modifier
                .padding(top = if (index == 0) topGap else 0.dp)
                .daySectionCardSegment(isFirst = index == 0, isLast = index == rows.lastIndex)
                .padding(horizontal = MspTheme.spacing.md)
        ) {
            // El divisor va ARRIBA de cada fila menos la primera: mismo resultado visual que el
            // `if (index != lastIndex) DetailDivider()` de la versión no-perezosa, pero decidible
            // sin conocer al vecino de abajo (que puede no estar compuesto todavía).
            if (index != 0) DetailDivider()
            DayRow(row = row, masked = masked, onClick = { onDayClick(index) })
        }
    }
}

/**
 * Pinta la rebanada de [MspTheme.shapes.sectionCard] que le toca a un ítem del resumen por día:
 * relleno `surface` + hairline 1dp `outline`, la MISMA geometría que [MspCard]/`MspSurface`
 * dibujan para la tarjeta completa.
 *
 * El truco es dibujar el rectángulo redondeado ENTERO y recortarlo a los límites del ítem: los
 * ítems intermedios lo extienden [CARD_SEGMENT_OVERDRAW] hacia arriba y hacia abajo (más que el
 * radio, así que sus esquinas caen fuera del recorte y sólo quedan las dos líneas laterales),
 * el primero no extiende hacia arriba y el último no extiende hacia abajo. La unión de todas
 * las rebanadas es, píxel a píxel, el mismo trazo que una sola tarjeta — sin costuras, porque
 * es literalmente la misma figura recortada por bandas.
 */
@Composable
private fun Modifier.daySectionCardSegment(isFirst: Boolean, isLast: Boolean): Modifier {
    val fill = MspTheme.colors.surface
    val stroke = MspTheme.colors.outline
    val shape = MspTheme.shapes.sectionCard
    return this
        .fillMaxWidth()
        .clipToBounds()
        .drawBehind {
            val overdraw = CARD_SEGMENT_OVERDRAW.toPx()
            val top = if (isFirst) 0f else -overdraw
            val bottom = if (isLast) size.height else size.height + overdraw
            val cardSize = Size(size.width, bottom - top)
            // El radio se lee del token del design system (no se copia el 18dp a mano) para
            // que la rebanada siga a `shapes.sectionCard` si el token cambia.
            val radius = (shape as? CornerBasedShape)?.topStart?.toPx(cardSize, this) ?: 0f
            drawRoundRect(
                color = fill,
                topLeft = Offset(0f, top),
                size = cardSize,
                cornerRadius = CornerRadius(radius, radius)
            )
            // Mismo encuadre que `Modifier.border` sobre una forma redondeada: el trazo va
            // CENTRADO en el borde, o sea metido media línea, y el radio baja media línea.
            val hairline = HAIRLINE.toPx()
            val half = hairline / 2
            val innerRadius = (radius - half).coerceAtLeast(0f)
            drawRoundRect(
                color = stroke,
                topLeft = Offset(half, top + half),
                size = Size(cardSize.width - hairline, cardSize.height - hairline),
                cornerRadius = CornerRadius(innerRadius, innerRadius),
                style = Stroke(hairline)
            )
        }
}

/**
 * Fila de un día del ciclo (mockup `.srow`): avatar de iniciales del día (ya calculadas por
 * `ReportAggregator.dailyTrend`, p. ej. "L3") + nombre del día + subline "N pagos" a la
 * izquierda; monto a la derecha. Avatar más chico ([DAY_AVATAR_SIZE], 34dp) que el de
 * [PaymentRow] (38dp default) — 1:1 `.srow .sa` vs `.prow .ava` del mockup.
 */
@Composable
private fun DayRow(
    row: DayRowUi,
    masked: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = MspTheme.spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(MspTheme.spacing.sm)
    ) {
        MspInitialsAvatar(initials = row.initials, size = DAY_AVATAR_SIZE)
        Column(modifier = Modifier.weight(1f)) {
            Text(text = row.label, style = MspTheme.type.name, color = MspTheme.colors.onSurface)
            Text(
                text = "${row.count} pagos",
                style = MspTheme.type.subtitle,
                color = MspTheme.colors.onSurfaceMuted
            )
        }
        MspMoneyText(
            amount = row.amount.amount,
            masked = masked,
            style = MspTheme.type.amountRow,
            color = MspTheme.colors.onSurface
        )
    }
}
