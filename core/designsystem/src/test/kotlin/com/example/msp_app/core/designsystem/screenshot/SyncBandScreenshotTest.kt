package com.example.msp_app.core.designsystem.screenshot

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.example.msp_app.core.designsystem.component.MspSyncBand
import com.example.msp_app.core.designsystem.component.SyncBandState
import com.example.msp_app.core.designsystem.theme.MspTheme
import org.junit.Test

/**
 * Golden baseline (light+dark @1.0) de [MspSyncBand]: los dos estados
 * (`Pending` ámbar, `Ok` verde) apilados, mismo texto de ejemplo del mockup
 * ("3 pagos por subir"). La matriz Tier×escala completa llega en Task 10.
 */
class SyncBandScreenshotTest : MspScreenshotTest() {

    @Test
    fun `sync band light`() {
        capture(name = "msp_sync_band_light", dark = false) { SampleSyncBands() }
    }

    @Test
    fun `sync band dark`() {
        capture(name = "msp_sync_band_dark", dark = true) { SampleSyncBands() }
    }
}

@Composable
private fun SampleSyncBands() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(MspTheme.spacing.md),
        verticalArrangement = Arrangement.spacedBy(MspTheme.spacing.sm)
    ) {
        MspSyncBand(
            state = SyncBandState.Pending,
            message = "3 pagos por subir",
            hint = "se sube solo"
        )
        MspSyncBand(
            state = SyncBandState.Ok,
            message = "Todo al día",
            hint = "última sync hace 2 min"
        )
    }
}
