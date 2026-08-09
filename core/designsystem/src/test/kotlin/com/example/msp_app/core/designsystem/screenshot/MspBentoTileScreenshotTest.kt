package com.example.msp_app.core.designsystem.screenshot

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.msp_app.core.designsystem.component.MspBentoTile
import com.example.msp_app.core.designsystem.theme.MspTheme
import java.math.BigDecimal
import org.junit.Test

/**
 * Golden baseline (light+dark @1.0) del duo de [MspBentoTile] del mockup
 * (task-8-brief.md): Efectivo `$12,100`/"22 pagos" y Transferencia
 * `$6,200`/"10 pagos", lado a lado como en `.bento` (kollect §8.2). La
 * matriz Tier×escala completa llega en Task 10.
 */
class MspBentoTileScreenshotTest : MspScreenshotTest() {

    @Test
    fun `bento tile light`() {
        capture(name = "msp_bento_tile_light", dark = false) { SampleBentoDuo() }
    }

    @Test
    fun `bento tile dark`() {
        capture(name = "msp_bento_tile_dark", dark = true) { SampleBentoDuo() }
    }
}

@Composable
private fun SampleBentoDuo() {
    Row(
        modifier = Modifier
            .padding(MspTheme.spacing.md)
            .width(DUO_WIDTH),
        horizontalArrangement = Arrangement.spacedBy(MspTheme.spacing.sm)
    ) {
        MspBentoTile(
            dotColor = MspTheme.colors.statusPaid,
            label = "Efectivo",
            amount = BigDecimal("12100"),
            subLine = "22 pagos",
            modifier = Modifier.weight(1f)
        )
        MspBentoTile(
            dotColor = MspTheme.colors.brand,
            label = "Transferencia",
            amount = BigDecimal("6200"),
            subLine = "10 pagos",
            modifier = Modifier.weight(1f)
        )
    }
}

private val DUO_WIDTH = 320.dp
