package com.example.msp_app.core.designsystem.screenshot

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.example.msp_app.core.designsystem.component.MspThemeToggle
import com.example.msp_app.core.designsystem.theme.MspTheme
import org.junit.Test

/**
 * Golden baseline (light+dark @1.0) de [MspThemeToggle]: los dos glifos
 * (sol/luna) uno al lado del otro, sin host de reveal instalado — render
 * puramente estático, sin ninguna reveal en curso. La matriz Tier×escala
 * completa llega en Task 10.
 */
class ThemeToggleScreenshotTest : MspScreenshotTest() {

    @Test
    fun `theme toggle light`() {
        capture(name = "msp_theme_toggle_light", dark = false) { SampleThemeToggles() }
    }

    @Test
    fun `theme toggle dark`() {
        capture(name = "msp_theme_toggle_dark", dark = true) { SampleThemeToggles() }
    }
}

@Composable
private fun SampleThemeToggles() {
    Row(
        modifier = Modifier.padding(MspTheme.spacing.md),
        horizontalArrangement = Arrangement.spacedBy(MspTheme.spacing.sm)
    ) {
        MspThemeToggle(darkTheme = false, onToggle = {})
        MspThemeToggle(darkTheme = true, onToggle = {})
    }
}
