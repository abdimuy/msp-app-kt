package com.example.msp_app.core.designsystem.screenshot

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.msp_app.core.designsystem.component.MspHeroTodayCard
import com.example.msp_app.core.designsystem.theme.MspTheme
import java.math.BigDecimal
import org.junit.Test

/**
 * Golden baseline (light+dark @1.0) de [MspHeroTodayCard] con los datos
 * exactos del mockup (task-8-brief.md): `"$18,300"`, delta "▲ 12% vs ayer",
 * insight "32 pagos · vas al 91% de tu meta", barra 91%, meta "$20,000",
 * wells "$12,100"/"$572". La matriz Tier×escala completa llega en Task 10.
 *
 * El segundo par de goldens (`large amount`) es la evidencia visual de que
 * el monto hero NO trunca a escala de millones: se fuerza el card a un ancho
 * angosto (180dp, mismo truco que
 * [com.example.msp_app.core.designsystem.screenshot.MspMoneyTextScreenshotTest])
 * y el número reflowea a varias líneas en vez de perder dígitos — la
 * aserción de layout formal llega en Task 10.
 */
class MspHeroTodayCardScreenshotTest : MspScreenshotTest() {

    @Test
    fun `hero today card light`() {
        capture(name = "msp_hero_today_card_light", dark = false) { SampleHeroTodayCard() }
    }

    @Test
    fun `hero today card dark`() {
        capture(name = "msp_hero_today_card_dark", dark = true) { SampleHeroTodayCard() }
    }

    @Test
    fun `hero today card large amount no trunca light`() {
        capture(name = "msp_hero_today_card_large_amount_light", dark = false) {
            SampleHeroTodayCardLargeAmount()
        }
    }

    @Test
    fun `hero today card large amount no trunca dark`() {
        capture(name = "msp_hero_today_card_large_amount_dark", dark = true) {
            SampleHeroTodayCardLargeAmount()
        }
    }
}

@Composable
private fun SampleHeroTodayCard() {
    MspHeroTodayCard(
        overline = "Cobrado · vie 7 ago",
        delta = "▲ 12% vs ayer",
        amount = BigDecimal("18300"),
        insight = "32 pagos · vas al 91% de tu meta",
        progress = HERO_PROGRESS,
        goalLabel = "meta del día",
        goalAmount = BigDecimal("20000"),
        cashOnHandLabel = "Efectivo en mano",
        cashOnHand = BigDecimal("12100"),
        avgTicketLabel = "Ticket prom.",
        avgTicket = BigDecimal("572"),
        modifier = Modifier.padding(MspTheme.spacing.md)
    )
}

@Composable
private fun SampleHeroTodayCardLargeAmount() {
    MspHeroTodayCard(
        overline = "Cobrado · ciclo actual",
        delta = "▲ 6% vs ciclo",
        amount = BigDecimal("12345678.90"),
        insight = "214 pagos · vas al 91% de la meta",
        progress = HERO_PROGRESS,
        goalLabel = "meta del ciclo",
        goalAmount = BigDecimal("13500000"),
        cashOnHandLabel = "Efectivo en mano",
        cashOnHand = BigDecimal("8450000.50"),
        avgTicketLabel = "Ticket prom.",
        avgTicket = BigDecimal("57680.25"),
        modifier = Modifier
            .padding(MspTheme.spacing.md)
            .width(NARROW_WIDTH)
    )
}

private const val HERO_PROGRESS = 0.91f
private val NARROW_WIDTH = 180.dp
