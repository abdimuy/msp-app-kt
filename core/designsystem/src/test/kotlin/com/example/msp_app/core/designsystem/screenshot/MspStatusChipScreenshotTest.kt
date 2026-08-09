package com.example.msp_app.core.designsystem.screenshot

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.example.msp_app.core.designsystem.component.ChipStatus
import com.example.msp_app.core.designsystem.component.MspStatusChip
import com.example.msp_app.core.designsystem.theme.MspTheme
import org.junit.Test

/**
 * Golden baseline (light+dark @1.0) de los cinco [MspStatusChip] con su texto
 * español corto — cada uno codifica el estado con color + ícono + texto
 * juntos, nunca solo color (spec §2.1 / §5). La matriz Tier×escala completa
 * llega en Task 10.
 */
class MspStatusChipScreenshotTest : MspScreenshotTest() {

    @Test
    fun `status chips light`() {
        capture(name = "msp_status_chip_light", dark = false) { SampleStatusChips() }
    }

    @Test
    fun `status chips dark`() {
        capture(name = "msp_status_chip_dark", dark = true) { SampleStatusChips() }
    }
}

@Composable
private fun SampleStatusChips() {
    Column(
        modifier = Modifier.padding(MspTheme.spacing.md),
        verticalArrangement = Arrangement.spacedBy(MspTheme.spacing.sm)
    ) {
        MspStatusChip(status = ChipStatus.Paid, text = "Pagado")
        MspStatusChip(status = ChipStatus.Partial, text = "Parcial")
        MspStatusChip(status = ChipStatus.Overdue, text = "Vencido")
        MspStatusChip(status = ChipStatus.Pending, text = "Pendiente")
        MspStatusChip(status = ChipStatus.Promise, text = "Promesa")
    }
}
