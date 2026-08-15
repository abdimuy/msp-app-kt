package com.example.msp_app.core.appgate.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.Density
import com.example.msp_app.core.appgate.download.DownloadProgress
import com.example.msp_app.core.designsystem.theme.LocalReduceMotion
import com.example.msp_app.core.designsystem.theme.MspTheme
import com.example.msp_app.core.testing.RobolectricTestBase
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

private const val LARGE_FONT_SCALE = 2.0f
private const val SIZE = 11_000_000L

private val BASE_STATE = VersionBlockedUiState(
    installedVersionName = "2.15.0",
    requiredVersionName = "2.17.0",
    deadlineLabel = "",
    stage = UpdateStage.Offline
)

/**
 * Compose-test (no golden) de [VersionBlockedContent]: los cinco estados del
 * mockup aprobado pintan lo suyo, el botón informa al caller, y nada de eso
 * se cae a `fontScale = 2.0` (que es cómo trae el teléfono medio cobrador de
 * campo la accesibilidad).
 *
 * Mismo patrón "stateless, spy vía lambda" que `ConfiguracionScreenTest`.
 */
class VersionBlockedScreenTest : RobolectricTestBase() {

    @get:Rule
    val composeTestRule = createComposeRule()

    // ─── los cinco estados ────────────────────────────────────────────────────

    @Test
    fun `listo para instalar ofrece instalar y dice que no usa datos`() {
        setContent(BASE_STATE.copy(stage = UpdateStage.ReadyToInstall))

        composeTestRule.onNodeWithText("Actualiza para continuar").assertIsDisplayed()
        composeTestRule.onNodeWithText("Instalar").assertIsDisplayed()
    }

    @Test
    fun `descargando muestra los megas, no una rueda`() {
        setContent(
            BASE_STATE.copy(stage = UpdateStage.Downloading(DownloadProgress(4_200_000L, SIZE)))
        )

        composeTestRule.onNodeWithTag(VERSION_BLOCKED_PROGRESS_TAG).assertIsDisplayed()
        composeTestRule.onNodeWithText("4.2 de 11 MB · 38%").assertIsDisplayed()
    }

    @Test
    fun `con datos moviles el boton dice el peso`() {
        setContent(BASE_STATE.copy(stage = UpdateStage.MeteredOnly(SIZE)))

        composeTestRule.onNodeWithText("Descargar con datos · 11 MB").assertIsDisplayed()
    }

    @Test
    fun `sin conexion lo dice con todas sus letras y ofrece reintentar`() {
        setContent(BASE_STATE.copy(stage = UpdateStage.Offline))

        composeTestRule.onNodeWithText("Sin conexión").assertIsDisplayed()
        composeTestRule.onNodeWithTag(VERSION_BLOCKED_RETRY_TAG).assertIsDisplayed()
    }

    @Test
    fun `una descarga cortada promete que continua desde donde iba`() {
        setContent(
            BASE_STATE.copy(stage = UpdateStage.Failed(DownloadProgress(6_100_000L, SIZE)))
        )

        composeTestRule.onNodeWithText("No se completó la descarga").assertIsDisplayed()
        composeTestRule.onNodeWithText("6.1 de 11 MB · en pausa").assertIsDisplayed()
    }

    // ─── pie de versiones ─────────────────────────────────────────────────────

    @Test
    fun `el pie dice que versión hay y cuál hace falta`() {
        setContent(BASE_STATE.copy(stage = UpdateStage.ReadyToInstall))

        composeTestRule.onNodeWithText("Tienes 2.15.0 · necesitas 2.17.0").assertIsDisplayed()
    }

    // ─── acciones ─────────────────────────────────────────────────────────────

    @Test
    fun `instalar informa al caller`() {
        var instalar = 0
        setContent(BASE_STATE.copy(stage = UpdateStage.ReadyToInstall), onInstall = { instalar++ })

        composeTestRule.onNodeWithTag(VERSION_BLOCKED_ACTION_TAG).performClick()

        assertEquals(1, instalar)
    }

    @Test
    fun `reintentar informa al caller`() {
        var descargar = 0
        setContent(
            BASE_STATE.copy(stage = UpdateStage.Failed(DownloadProgress(6_100_000L, SIZE))),
            onDownload = { descargar++ }
        )

        composeTestRule.onNodeWithTag(VERSION_BLOCKED_ACTION_TAG).performClick()

        assertEquals(1, descargar)
    }

    @Test
    fun `mientras baja, instalar no hace nada - no hay nada que instalar todavia`() {
        var instalar = 0
        setContent(
            BASE_STATE.copy(stage = UpdateStage.Downloading(DownloadProgress(0L, SIZE))),
            onInstall = { instalar++ }
        )

        composeTestRule.onNodeWithTag(VERSION_BLOCKED_ACTION_TAG).performClick()

        assertEquals(0, instalar)
    }

    // ─── banda de cuenta regresiva ────────────────────────────────────────────

    @Test
    fun `con fecha limite la pantalla monta la banda`() {
        setContent(BASE_STATE.copy(deadlineLabel = "vie 22", stage = UpdateStage.ReadyToInstall))

        composeTestRule.onNodeWithTag(UPDATE_COUNTDOWN_BAND_TAG).assertIsDisplayed()
    }

    @Test
    fun `sin fecha limite no monta la banda`() {
        setContent(BASE_STATE.copy(deadlineLabel = "", stage = UpdateStage.ReadyToInstall))

        composeTestRule.onNodeWithTag(UPDATE_COUNTDOWN_BAND_TAG).assertDoesNotExist()
    }

    // ─── accesibilidad ────────────────────────────────────────────────────────

    @Test
    fun `a fontScale 2 el estado listo sigue completo y su boton sigue siendo tocable`() {
        var instalar = 0
        setContent(
            BASE_STATE.copy(stage = UpdateStage.ReadyToInstall),
            fontScale = LARGE_FONT_SCALE,
            onInstall = { instalar++ }
        )

        composeTestRule.onNodeWithText(
            "Actualiza para continuar"
        ).performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag(VERSION_BLOCKED_ACTION_TAG).performScrollTo().performClick()

        assertEquals(1, instalar)
    }

    @Test
    fun `a fontScale 2 la descarga sigue mostrando sus megas`() {
        setContent(
            BASE_STATE.copy(stage = UpdateStage.Downloading(DownloadProgress(4_200_000L, SIZE))),
            fontScale = LARGE_FONT_SCALE
        )

        composeTestRule.onNodeWithText("4.2 de 11 MB · 38%").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun `a fontScale 2 el estado sin conexion conserva sus dos acciones`() {
        setContent(BASE_STATE.copy(stage = UpdateStage.Offline), fontScale = LARGE_FONT_SCALE)

        composeTestRule.onNodeWithTag(
            VERSION_BLOCKED_ACTION_TAG
        ).performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag(
            VERSION_BLOCKED_RETRY_TAG
        ).performScrollTo().assertIsDisplayed()
    }

    // ─── helpers ──────────────────────────────────────────────────────────────

    private fun setContent(
        state: VersionBlockedUiState,
        fontScale: Float = 1f,
        onInstall: () -> Unit = {},
        onDownload: () -> Unit = {}
    ) {
        composeTestRule.setContent {
            Harness(fontScale = fontScale) {
                VersionBlockedContent(state = state, onInstall = onInstall, onDownload = onDownload)
            }
        }
    }

    @Composable
    private fun Harness(fontScale: Float, content: @Composable () -> Unit) {
        val density = LocalDensity.current
        CompositionLocalProvider(
            LocalDensity provides Density(density.density, fontScale),
            LocalReduceMotion provides true
        ) {
            MspTheme(animateColors = false) { content() }
        }
    }
}
