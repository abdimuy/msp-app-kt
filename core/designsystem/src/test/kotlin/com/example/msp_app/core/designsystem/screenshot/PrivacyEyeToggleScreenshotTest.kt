package com.example.msp_app.core.designsystem.screenshot

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.example.msp_app.core.designsystem.component.MspPrivacyEyeToggle
import com.example.msp_app.core.designsystem.theme.MspTheme
import org.junit.Test

/**
 * Golden baseline (light+dark @1.0) de [MspPrivacyEyeToggle]: ojo abierto
 * (`masked = false`, muted) y ojo tachado (`masked = true`, `brand`) uno al
 * lado del otro. La matriz Tier×escala completa llega en Task 10.
 */
class PrivacyEyeToggleScreenshotTest : MspScreenshotTest() {

    @Test
    fun `privacy eye toggle light`() {
        capture(name = "msp_privacy_eye_toggle_light", dark = false) { SamplePrivacyEyeToggles() }
    }

    @Test
    fun `privacy eye toggle dark`() {
        capture(name = "msp_privacy_eye_toggle_dark", dark = true) { SamplePrivacyEyeToggles() }
    }
}

@Composable
private fun SamplePrivacyEyeToggles() {
    Row(
        modifier = Modifier.padding(MspTheme.spacing.md),
        horizontalArrangement = Arrangement.spacedBy(MspTheme.spacing.sm)
    ) {
        MspPrivacyEyeToggle(masked = false, onToggle = {})
        MspPrivacyEyeToggle(masked = true, onToggle = {})
    }
}
