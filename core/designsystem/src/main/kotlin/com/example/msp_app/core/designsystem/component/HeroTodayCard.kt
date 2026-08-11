package com.example.msp_app.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.example.msp_app.core.designsystem.theme.MspTheme
import java.math.BigDecimal

/**
 * `testTag` de la barra de progreso del hero — localiza el
 * [MspProgressBar] interno en el compose-test que verifica que se pinta a la
 * fracción [MspHeroTodayCard.progress] dada (vía `SemanticsProperties.ProgressBarRangeInfo`,
 * `progressBarRangeInfo`, no por pixeles).
 */
internal const val HERO_PROGRESS_BAR_TAG = "msp_hero_today_card_progress_bar"

/**
 * Padding interno del hero — 18dp (task-8-brief.md: "kollect §8.2; el mockup
 * usa 17px — 18dp es el valor DS"). No es un token de [MspTheme.spacing]
 * (`xs`/`sm`/`md`/`lg` = 4/8/16/24): es un valor propio de esta tarjeta, igual
 * que `RING_DIAMETER` en [MspProgressRing].
 */
private val HERO_PADDING = 18.dp

/** Alto de la barra de progreso del hero — 9dp (extremo alto del rango "8-9dp" del brief). */
private val HERO_BAR_HEIGHT = 9.dp

/** Radio de los "stat wells" — ~13dp (task-8-brief.md), no es un token de [MspTheme.shapes]. */
private val WELL_SHAPE = RoundedCornerShape(13.dp)
private val WELL_PADDING_HORIZONTAL = 11.dp
private val WELL_PADDING_VERTICAL = 9.dp

/**
 * Hero "cobrado hoy" — la tarjeta protagonista del tablero (kollect §8.2,
 * task-8-brief.md). Gradiente de marca plano 150° ([brandGradientBackground],
 * Task 6) recortado a [MspTheme.shapes.heroCard] (22dp), SIN hairline: a
 * propósito NO se envuelve en [MspCard]/[MspSurface] — ambos agregan
 * incondicionalmente un borde `outline` de 1dp (`MspSurface`: "El hairline va
 * SIEMPRE"), y el mockup del hero (`.hero{background:linear-gradient(...)}`)
 * no lleva borde. Un `Box` propio con [brandGradientBackground] es la
 * composición fiel.
 *
 * Estructura de arriba a abajo: overline + delta chip (fila), monto grande
 * (`amountHero`, 36sp/800), frase-insight, barra de progreso, caption de
 * meta, slot opcional de sparkline (el DS solo pone el contenedor — la
 * sparkline concreta la arma el piloto, Plan 5), fila de dos "stat wells".
 *
 * **Todo monto entra como [BigDecimal] y sale por [MspMoneyText]** — [amount],
 * [goalAmount], [cashOnHand] y [avgTicket] nunca se precomputan a `String` de
 * dinero dentro de este componente (regla anti-`Double`/anti-string-money del
 * brief; el módulo tiene el gate `NoDoubleForMoney` activo).
 *
 * **Regla anti-colapso (spec §6):** este composable NO lleva `fillMaxHeight`/
 * `weight` propios — su altura es por contenido (`wrapContentHeight`
 * implícito de `Column`). Un caller que lo meta en una `Column` con
 * `verticalScroll` NO debe pasarle un `Modifier.weight(...)`: eso lo
 * comprimiría bajo su contenido y el hero "desaparecía" en el mockup (spec
 * §6, `flex-shrink:0`). El `verticalScroll` del caller maneja el overflow;
 * este componente nunca se comprime.
 *
 * [sparkline] es un slot: si se pasa, se pinta debajo de la caption de meta y
 * encima de los wells, con el mismo padding que el resto del hero — el DS
 * deja el contenedor y dicta el estilo de barra (`rgba(255,255,255,.22)` idle
 * / [MspTheme.colors.heroProgressFill] activo es responsabilidad del
 * composable que el piloto inyecte, no de este componente).
 *
 * [onClick] es opcional (abre el sheet de detalle en el piloto, Plan 5); sin
 * él, el hero es un `Box` no clickable.
 *
 * **Barra de progreso / meta / wells OPCIONALES** (fix "Meta de la semana",
 * `:feature:collectionReport`): [progress]/[goalLabel]/[goalAmount] y
 * [cashOnHandLabel]/[cashOnHand]/[avgTicketLabel]/[avgTicket] son nullable, `null` por
 * defecto — cuando faltan, la sección correspondiente NO se pinta (en vez de un `0%`/`$0.00`
 * inventado). El piloto de cobranza dejó de alimentar un goal de mediana y dos wells
 * ("Efectivo en mano"/"Ticket prom.") — esas cifras viven ahora en la tarjeta "Meta de la
 * semana" (`MetaCard`), debajo del hero. Todos los callers previos (que siempre pasaban los
 * siete valores) siguen compilando y renderizando IGUAL: un [BigDecimal]/[Float] no-nulo sigue
 * siendo válido donde el parámetro ahora acepta null.
 */
@Composable
@Suppress("LongParameterList") // Ver el KDoc: parámetros opcionales, no un rediseño del contrato.
fun MspHeroTodayCard(
    overline: String,
    delta: String,
    amount: BigDecimal,
    insight: String,
    modifier: Modifier = Modifier,
    progress: Float? = null,
    goalLabel: String? = null,
    goalAmount: BigDecimal? = null,
    cashOnHandLabel: String? = null,
    cashOnHand: BigDecimal? = null,
    avgTicketLabel: String? = null,
    avgTicket: BigDecimal? = null,
    masked: Boolean = false,
    sparkline: (@Composable () -> Unit)? = null,
    onClick: (() -> Unit)? = null
) {
    val colors = MspTheme.colors
    val type = MspTheme.type
    val fraction = progress?.coerceIn(0f, 1f)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .brandGradientBackground(colors, MspTheme.shapes.heroCard)
            .let { base -> if (onClick != null) base.clickable(onClick = onClick) else base }
            .padding(HERO_PADDING)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                Text(
                    text = overline,
                    style = type.overline,
                    color = colors.onBrand.copy(alpha = OnBrandAlpha.OVERLINE),
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = delta,
                    style = type.captionStrong,
                    color = colors.onBrand,
                    modifier = Modifier
                        .clip(MspTheme.shapes.chip)
                        .background(colors.onBrand.copy(alpha = OnBrandAlpha.DELTA))
                        .padding(horizontal = MspTheme.spacing.sm, vertical = MspTheme.spacing.xs)
                )
            }

            MspMoneyText(
                amount = amount,
                masked = masked,
                style = type.amountHero,
                color = colors.onBrand,
                modifier = Modifier.padding(top = MspTheme.spacing.xs)
            )

            Text(
                text = insight,
                style = type.body,
                color = colors.onBrand.copy(alpha = OnBrandAlpha.BODY),
                modifier = Modifier.padding(top = MspTheme.spacing.sm)
            )

            if (fraction != null) {
                MspProgressBar(
                    progress = fraction,
                    height = HERO_BAR_HEIGHT,
                    fillColor = colors.heroProgressFill,
                    trackColor = colors.onBrand.copy(alpha = OnBrandAlpha.WELL),
                    modifier = Modifier
                        .padding(top = MspTheme.spacing.md)
                        .semantics { progressBarRangeInfo = ProgressBarRangeInfo(fraction, 0f..1f) }
                        .testTag(HERO_PROGRESS_BAR_TAG)
                )
            }

            if (goalLabel != null && goalAmount != null) {
                Row(modifier = Modifier.padding(top = MspTheme.spacing.xs)) {
                    Text(
                        text = goalLabel,
                        style = type.caption,
                        color = colors.onBrand.copy(alpha = OnBrandAlpha.LABEL)
                    )
                    Spacer(modifier = Modifier.width(MspTheme.spacing.xs))
                    MspMoneyText(
                        amount = goalAmount,
                        masked = masked,
                        style = type.caption,
                        color = colors.onBrand.copy(alpha = OnBrandAlpha.LABEL)
                    )
                }
            }

            sparkline?.let { slot ->
                Box(modifier = Modifier.fillMaxWidth().padding(top = MspTheme.spacing.md)) {
                    slot()
                }
            }

            if (cashOnHandLabel != null || avgTicketLabel != null) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = MspTheme.spacing.md),
                    horizontalArrangement = Arrangement.spacedBy(MspTheme.spacing.sm)
                ) {
                    if (cashOnHandLabel != null) {
                        HeroWell(
                            label = cashOnHandLabel,
                            amount = cashOnHand ?: BigDecimal.ZERO,
                            masked = masked,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    if (avgTicketLabel != null) {
                        HeroWell(
                            label = avgTicketLabel,
                            amount = avgTicket ?: BigDecimal.ZERO,
                            masked = masked,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }
    }
}

/**
 * Un "stat well" translúcido del hero (`.well`, kollect §8.2) — fondo
 * `OnBrandAlpha.WELL` (0.12), radio [WELL_SHAPE] (~13dp), label en
 * `type.caption`/[OnBrandAlpha.LABEL] y valor en `type.heroStatValue` a
 * opacidad plena (task-8-brief.md).
 */
@Composable
private fun HeroWell(
    label: String,
    amount: BigDecimal,
    masked: Boolean,
    modifier: Modifier = Modifier
) {
    val colors = MspTheme.colors
    Column(
        modifier = modifier
            .clip(WELL_SHAPE)
            .background(colors.onBrand.copy(alpha = OnBrandAlpha.WELL))
            .padding(horizontal = WELL_PADDING_HORIZONTAL, vertical = WELL_PADDING_VERTICAL)
    ) {
        Text(
            text = label,
            style = MspTheme.type.caption,
            color = colors.onBrand.copy(alpha = OnBrandAlpha.LABEL)
        )
        MspMoneyText(
            amount = amount,
            masked = masked,
            style = MspTheme.type.heroStatValue,
            color = colors.onBrand,
            modifier = Modifier.padding(top = 2.dp)
        )
    }
}
