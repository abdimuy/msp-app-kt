package com.example.msp_app.core.designsystem.screenshot

import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.example.msp_app.core.designsystem.component.MspInitialsAvatar
import com.example.msp_app.core.designsystem.theme.MspTheme
import org.junit.Test

/**
 * Golden baseline (light+dark @1.0) de [MspInitialsAvatar] con las iniciales
 * "ML" (Minerva López) — cuadro redondeado `shapes.control` fondo `brandTint`,
 * texto `brand` ExtraBold centrado. La matriz Tier×escala completa llega en
 * Task 10.
 */
class MspInitialsAvatarScreenshotTest : MspScreenshotTest() {

    @Test
    fun `initials avatar light`() {
        capture(name = "msp_initials_avatar_light", dark = false) { SampleAvatar() }
    }

    @Test
    fun `initials avatar dark`() {
        capture(name = "msp_initials_avatar_dark", dark = true) { SampleAvatar() }
    }
}

@Composable
private fun SampleAvatar() {
    MspInitialsAvatar(initials = "ML", modifier = Modifier.padding(MspTheme.spacing.md))
}
