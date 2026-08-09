package com.example.msp_app.core.designsystem.screenshot

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.msp_app.core.designsystem.component.MspMoneyText
import com.example.msp_app.core.designsystem.theme.MspTheme
import java.math.BigDecimal
import org.junit.Test

/**
 * Golden baseline (light+dark @1.0) de [MspMoneyText]:
 * - normal: monto es-MX en `amountRow`.
 * - masked: el glifo de privacidad `$••••`.
 * - large: un monto de millones en un ancho estrecho con `amountDisplay` — el
 *   número reflowea a varias líneas en vez de truncarse (evidencia visual del
 *   contrato "no truncar"; la aserción de layout formal llega en Task 10).
 * La matriz Tier×escala completa llega en Task 10.
 */
class MspMoneyTextScreenshotTest : MspScreenshotTest() {

    @Test
    fun `money text light`() {
        capture(name = "msp_money_text_light", dark = false) { SampleMoneyText() }
    }

    @Test
    fun `money text dark`() {
        capture(name = "msp_money_text_dark", dark = true) { SampleMoneyText() }
    }
}

@Composable
private fun SampleMoneyText() {
    Column(
        modifier = Modifier.padding(MspTheme.spacing.md),
        verticalArrangement = Arrangement.spacedBy(MspTheme.spacing.md)
    ) {
        MspMoneyText(amount = BigDecimal("18300.50"))
        MspMoneyText(amount = BigDecimal("18300.50"), masked = true)
        MspMoneyText(
            amount = BigDecimal("12345678.90"),
            style = MspTheme.type.amountDisplay,
            color = MspTheme.colors.brand,
            modifier = Modifier.width(140.dp)
        )
    }
}
