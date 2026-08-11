package com.example.msp_app.feature.collectionreport.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.msp_app.core.designsystem.component.MspCard
import com.example.msp_app.core.designsystem.theme.MspTheme
import com.example.msp_app.feature.collectionreport.domain.CobranzaPorcentaje

/**
 * "Meta de la semana" (mockup aprobado, fix — reemplaza el hero de meta por mediana): dos
 * anillos con las métricas REALES de cobranza calculadas offline desde Room
 * ([CobranzaPorcentaje], puerto fiel del backend Go `internal/rutas/domain`/`internal/rutas/app`
 * de `msp-api`) — **Porcentaje cobro** (ponderado, izquierda, marca) y **Porcentaje cuentas**
 * (cobertura, derecha, verde `statusPaid`). Los nombres de dominio (ponderado/cobertura) se
 * quedan internos; la UI SIEMPRE usa las etiquetas exactas "Porcentaje cobro"/"Porcentaje
 * cuentas".
 *
 * **Solo SEMANA** (ver KDoc de `HeroUi`/`CollectionReportStateBuilder.buildHero`): el caller
 * (`CollectionReportScreen`/`CollectionReportScreenTier2`) monta esta tarjeta ÚNICAMENTE cuando
 * `period == ReportPeriod.SEMANA` — DÍA no tiene ventana de ciclo que reportar.
 *
 * **Sin `masked`:** a diferencia del resto del tablero, esta tarjeta no muestra ningún
 * [com.example.msp_app.feature.collectionreport.domain.model.Money] — los porcentajes NO se
 * enmascaran (regla de privacidad del piloto: solo montos en pesos se ocultan).
 *
 * **Anillo capado visualmente a 100%** (el trazo nunca da más de una vuelta) aunque
 * [porcentajeCobro] pueda EXCEDER 100% (una venta puede aportar más de una cuota, ver KDoc de
 * `CobranzaPorcentaje.resumenPonderado`) — el número en el centro del anillo SIEMPRE muestra el
 * valor real, sin capar.
 */
@Composable
fun MetaCard(
    porcentajeCobro: Float,
    porcentajeCuentas: Float,
    clientesPagaron: Int,
    clientesTotal: Int,
    modifier: Modifier = Modifier
) {
    MspCard(modifier = modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(MspTheme.spacing.lg)) {
            Text(
                text = META_CARD_TITLE,
                style = MspTheme.type.cardTitle,
                color = MspTheme.colors.onSurface
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = MspTheme.spacing.md),
                horizontalArrangement = Arrangement.spacedBy(MspTheme.spacing.lg)
            ) {
                RingStat(
                    color = MspTheme.colors.brand,
                    label = PORCENTAJE_COBRO_LABEL,
                    percent = porcentajeCobro,
                    subtitle = cobroSubtitle(porcentajeCobro),
                    modifier = Modifier.weight(1f)
                )
                RingStat(
                    color = MspTheme.colors.statusPaid,
                    label = PORCENTAJE_CUENTAS_LABEL,
                    percent = porcentajeCuentas,
                    subtitle = cuentasSubtitle(clientesPagaron, clientesTotal),
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

/**
 * Variante Tier 2 (Muy grande, spec §5) de [MetaCard]: los dos anillos se APILAN en vez de ir
 * lado a lado (no caben — "una idea por vista", mismo criterio que `Tier2Tile`/`Tier2Chip` en
 * [com.example.msp_app.feature.collectionreport.ui.tier2.CollectionReportScreenTier2]) — cada
 * fila es un [RingRow] de ancho completo: anillo + etiqueta + valor (en el centro del anillo) +
 * subtítulo.
 */
@Composable
fun MetaCardTier2(
    porcentajeCobro: Float,
    porcentajeCuentas: Float,
    clientesPagaron: Int,
    clientesTotal: Int,
    modifier: Modifier = Modifier
) {
    MspCard(modifier = modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(MspTheme.spacing.lg)) {
            Text(
                text = META_CARD_TITLE,
                style = MspTheme.type.cardTitle,
                color = MspTheme.colors.onSurface
            )
            RingRow(
                color = MspTheme.colors.brand,
                label = PORCENTAJE_COBRO_LABEL,
                percent = porcentajeCobro,
                subtitle = cobroSubtitle(porcentajeCobro),
                modifier = Modifier.padding(top = MspTheme.spacing.lg)
            )
            RingRow(
                color = MspTheme.colors.statusPaid,
                label = PORCENTAJE_CUENTAS_LABEL,
                percent = porcentajeCuentas,
                subtitle = cuentasSubtitle(clientesPagaron, clientesTotal),
                modifier = Modifier.padding(top = MspTheme.spacing.md)
            )
        }
    }
}

/** Anillo + etiqueta + subtítulo apilados VERTICALMENTE — usado lado a lado en Tier 1. */
@Composable
private fun RingStat(
    color: Color,
    label: String,
    percent: Float,
    subtitle: String,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Ring(percent = percent, color = color)
        Text(
            text = label,
            style = MspTheme.type.body,
            color = MspTheme.colors.onSurfaceMuted,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = MspTheme.spacing.sm)
        )
        Text(
            text = subtitle,
            style = MspTheme.type.caption,
            color = MspTheme.colors.onSurfaceMuted,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = MspTheme.spacing.xs)
        )
    }
}

/** Anillo a la izquierda + etiqueta/subtítulo a la derecha — fila de ancho completo (Tier 2). */
@Composable
private fun RingRow(
    color: Color,
    label: String,
    percent: Float,
    subtitle: String,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(MspTheme.spacing.md)
    ) {
        Ring(percent = percent, color = color)
        Column {
            Text(text = label, style = MspTheme.type.cardTitle, color = MspTheme.colors.onSurface)
            Text(
                text = subtitle,
                style = MspTheme.type.caption,
                color = MspTheme.colors.onSurfaceMuted,
                modifier = Modifier.padding(top = MspTheme.spacing.xs)
            )
        }
    }
}

/**
 * Anillo dibujado con [Canvas] (`drawArc`): pista completa en [MspTheme.colors.outline] +
 * trazo [color] proporcional a `(percent/100).coerceIn(0f,1f)` (capado visualmente a una
 * vuelta — ver KDoc de [MetaCard]), arrancando arriba (`-90°`) en sentido horario. El valor
 * (SIN capar) se pinta al centro.
 */
@Composable
private fun Ring(percent: Float, color: Color, modifier: Modifier = Modifier) {
    val fraction = (percent / PERCENT_SCALE).coerceIn(0f, 1f)
    val trackColor = MspTheme.colors.outline
    Box(modifier = modifier.size(RING_DIAMETER), contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.size(RING_DIAMETER)) {
            val stroke = Stroke(width = RING_STROKE.toPx(), cap = StrokeCap.Round)
            drawArc(
                color = trackColor,
                startAngle = RING_START_ANGLE,
                sweepAngle = RING_FULL_SWEEP,
                useCenter = false,
                style = stroke
            )
            if (fraction > 0f) {
                drawArc(
                    color = color,
                    startAngle = RING_START_ANGLE,
                    sweepAngle = RING_FULL_SWEEP * fraction,
                    useCenter = false,
                    style = stroke
                )
            }
        }
        Text(
            text = percentText(percent),
            style = MspTheme.type.cardTitle,
            color = MspTheme.colors.onSurface
        )
    }
}

/** "meta 60% ✓" cuando ya se alcanzó la meta de cobro, "meta 60%" en caso contrario. */
private fun cobroSubtitle(porcentajeCobro: Float): String {
    val check = if (porcentajeCobro >= CobranzaPorcentaje.META_COBRO_PCT) " $CHECK_MARK" else ""
    return "meta ${CobranzaPorcentaje.META_COBRO_PCT}%$check"
}

/** "N de M clientes" — en rigor cuenta VENTAS activas, no clientes únicos (ver KDoc de HeroUi). */
private fun cuentasSubtitle(clientesPagaron: Int, clientesTotal: Int): String =
    "$clientesPagaron de $clientesTotal clientes"

private fun percentText(percent: Float): String = "${"%.0f".format(percent)}%"

private const val META_CARD_TITLE = "Meta de la semana"
private const val PORCENTAJE_COBRO_LABEL = "Porcentaje cobro"
private const val PORCENTAJE_CUENTAS_LABEL = "Porcentaje cuentas"
private const val CHECK_MARK = "✓"
private const val PERCENT_SCALE = 100f
private val RING_DIAMETER = 76.dp
private val RING_STROKE = 8.dp
private const val RING_START_ANGLE = -90f
private const val RING_FULL_SWEEP = 360f
