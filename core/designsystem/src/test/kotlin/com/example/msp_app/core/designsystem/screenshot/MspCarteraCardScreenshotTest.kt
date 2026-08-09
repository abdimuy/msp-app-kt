package com.example.msp_app.core.designsystem.screenshot

import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.example.msp_app.core.designsystem.component.MspCarteraCard
import com.example.msp_app.core.designsystem.theme.MspTheme
import java.math.BigDecimal
import org.junit.Test

/**
 * Golden baseline (light+dark @1.0) de [MspCarteraCard] con datos de ejemplo
 * (task-8-brief.md: "Fase 2 por datos" — el piloto Plan 5 no la cablea
 * todavía, pero el componente se entrega para completar el catálogo).
 * Cifras del mockup de referencia (`kollect-original.html` `.cart`): total
 * `$186,540`, al corriente `$144,240`, vencido `$42,300`, "18 clientes
 * activos". La matriz Tier×escala completa llega en Task 10.
 */
class MspCarteraCardScreenshotTest : MspScreenshotTest() {

    @Test
    fun `cartera card light`() {
        capture(name = "msp_cartera_card_light", dark = false) { SampleCarteraCard() }
    }

    @Test
    fun `cartera card dark`() {
        capture(name = "msp_cartera_card_dark", dark = true) { SampleCarteraCard() }
    }
}

@Composable
private fun SampleCarteraCard() {
    MspCarteraCard(
        title = "Cartera · por cobrar",
        totalAmount = BigDecimal("186540"),
        collectedAmount = BigDecimal("144240"),
        collectedLabel = "al corriente",
        pendingAmount = BigDecimal("42300"),
        pendingLabel = "vencido",
        caption = "18 clientes activos",
        modifier = Modifier.padding(MspTheme.spacing.md)
    )
}
