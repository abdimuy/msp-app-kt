package com.example.msp_app.feature.collectionreport.screenshot

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.example.msp_app.core.designsystem.theme.MspTheme
import com.example.msp_app.core.testing.roborazzi.RoborazziConfig
import com.example.msp_app.feature.collectionreport.ui.CollectionReportContent
import com.example.msp_app.feature.collectionreport.ui.MockupFixtures
import com.example.msp_app.feature.collectionreport.ui.theme.ThemeRevealRoot
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.RoborazziOptions
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Test

/**
 * Golden baseline de [ThemeRevealRoot] envolviendo el tablero real (task-9-brief.md:
 * "Roborazzi: ... pre/post theme"; goldens "pre-reveal y post-reveal") — los dos estados
 * ASENTADOS del flip de tema: `pre` (light, antes de tocar el toggle) y `post` (dark, después).
 * `disableAnimationsForDeterministicGoldens` (heredado de [CollectionReportScreenshotTest]) fija
 * `ANIMATOR_DURATION_SCALE = 0`, así que [ThemeRevealRoot] SIEMPRE toma la rama reduce-motion
 * (crossfade instantáneo, sin `GraphicsLayer`/`Animatable`) — exactamente lo que un golden
 * necesita: el frame final asentado, nunca un frame intermedio de la reveal circular real.
 *
 * No reutiliza [CollectionReportScreenshotTest.capture] a propósito: ese helper envuelve en su
 * propio [MspTheme]; [ThemeRevealRoot] YA provee el suyo (task-9-brief.md: "Envuelve
 * `CollectionReportScreen` en `MspTheme`") — envolver dos veces sería redundante, no incorrecto,
 * pero este archivo captura la pieza REAL (el root, no el `MspTheme` que ya prueba todo el
 * resto del catálogo).
 */
class ThemeRevealRootScreenshotTest : CollectionReportScreenshotTest() {

    @Test
    fun `theme reveal root pre-flip light`() {
        capture("theme_reveal_root_pre_light", darkTheme = false)
    }

    @Test
    fun `theme reveal root post-flip dark`() {
        capture("theme_reveal_root_post_dark", darkTheme = true)
    }

    @OptIn(ExperimentalRoborazziApi::class)
    private fun capture(name: String, darkTheme: Boolean) {
        captureRoboImage(
            filePath = "src/test/screenshots/$name.png",
            roborazziOptions = RoborazziOptions(
                compareOptions = RoborazziOptions.CompareOptions(
                    changeThreshold = RoborazziConfig.CHANGE_THRESHOLD
                )
            )
        ) {
            ThemeRevealRoot(darkTheme = darkTheme, onToggleTheme = {}) {
                ReportBoard()
            }
        }
    }
}

@Composable
private fun ReportBoard() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MspTheme.colors.background)
    ) {
        CollectionReportContent(
            state = MockupFixtures.stateDia(),
            onMenuClick = {},
            onPrivacyToggle = {},
            onThemeToggle = {},
            onPeriodSelect = {},
            onHeroClick = {},
            onSparkBarClick = {},
            onEfectivoClick = {},
            onTransferenciaClick = {},
            onCondonadoClick = {},
            onVisitasClick = {},
            onSortSelect = {},
            onPaymentRowClick = {},
            onDayRowClick = {}
        )
    }
}
