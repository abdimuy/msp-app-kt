package com.example.msp_app.core.telemetry.compose

import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.example.msp_app.core.telemetry.Telemetry
import com.example.msp_app.core.telemetry.TelemetryEventType
import com.example.msp_app.core.testing.RobolectricTestBase
import com.example.msp_app.core.testing.telemetry.RecordingTelemetry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * `ScreenScope` (Plan 4, Task 4): provee `LocalScreenName` al subárbol y
 * emite `screenView(name)` exactamente una vez por entrada a composición.
 */
class ScreenScopeTest : RobolectricTestBase() {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun `emite screenView una vez y provee LocalScreenName al subarbol`() {
        val telemetry = RecordingTelemetry()
        var screenNameSeenByChild: String? = null

        composeTestRule.setContent {
            CompositionLocalProvider(LocalTelemetry provides telemetry) {
                ScreenScope("reporte") {
                    screenNameSeenByChild = LocalScreenName.current
                    Text("contenido")
                }
            }
        }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("contenido").assertExists()
        assertEquals("reporte", screenNameSeenByChild)
        assertEquals(1, telemetry.recorded.size)
        val event = telemetry.recorded.single()
        assertEquals(TelemetryEventType.SCREEN_VIEW, event.type)
        assertEquals("reporte", event.name)
    }

    @Test
    fun `recomponer sin cambiar el nombre de pantalla no vuelve a emitir screenView`() {
        val telemetry = RecordingTelemetry()
        val counter = mutableIntStateOf(0)

        composeTestRule.setContent {
            CompositionLocalProvider(LocalTelemetry provides telemetry) {
                ScreenScope("reporte") {
                    Text("contador: ${counter.intValue}")
                }
            }
        }
        composeTestRule.waitForIdle()
        assertEquals(1, telemetry.recorded.size)

        // Fuerza una recomposicion real del subarbol sin tocar el `name` de
        // ScreenScope: LaunchedEffect(name) no debe reemitir screenView.
        composeTestRule.runOnIdle { counter.intValue++ }
        composeTestRule.waitForIdle()

        assertEquals(1, telemetry.recorded.size)
    }

    @Test
    fun `un Telemetry que lanza en screenView no rompe el render del contenido, best-effort`() {
        val throwingTelemetry = object : Telemetry {
            override fun screenView(screen: String): Unit = error("boom")
            override fun tap(screen: String, element: String) = Unit
            override fun event(name: String, props: Map<String, String>) = Unit
            override fun error(code: String, message: String, props: Map<String, String>) = Unit
        }

        composeTestRule.setContent {
            CompositionLocalProvider(LocalTelemetry provides throwingTelemetry) {
                ScreenScope("reporte") {
                    Text("contenido")
                }
            }
        }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("contenido").assertExists()
    }

    @Test
    fun `el evento emitido nunca contiene texto de usuario, solo el id estatico de pantalla`() {
        val telemetry = RecordingTelemetry()

        composeTestRule.setContent {
            CompositionLocalProvider(LocalTelemetry provides telemetry) {
                ScreenScope("reporte") { Text("Maria Lopez debe \$500") }
            }
        }
        composeTestRule.waitForIdle()

        val event = telemetry.recorded.single()
        assertEquals("reporte", event.name)
        assertTrue(event.props.values.none { it.contains("Maria Lopez") })
    }
}
