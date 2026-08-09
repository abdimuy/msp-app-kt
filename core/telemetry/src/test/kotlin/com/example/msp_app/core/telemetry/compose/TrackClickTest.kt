package com.example.msp_app.core.telemetry.compose

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import com.example.msp_app.core.telemetry.Telemetry
import com.example.msp_app.core.telemetry.TelemetryEventType
import com.example.msp_app.core.testing.RobolectricTestBase
import com.example.msp_app.core.testing.telemetry.RecordingTelemetry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

private const val NODE_TAG = "trackable_node"

/**
 * `Modifier.trackClick` (Plan 4, Task 4): un click debe grabar un `tap` con
 * etiqueta estática Y seguir ejecutando el `onClick` de negocio. Robolectric
 * (JVM) vía `createComposeRule`, mismo patrón que
 * `MspStatusChipTest`/`ReducedMotionTest` de `:core:designsystem`.
 */
class TrackClickTest : RobolectricTestBase() {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun `un click registra tap una vez con etiqueta estatica y sigue invocando el onClick original`() {
        val telemetry = RecordingTelemetry()
        var clicked = false

        composeTestRule.setContent {
            CompositionLocalProvider(
                LocalTelemetry provides telemetry,
                LocalScreenName provides "cobranza_detalle"
            ) {
                Box(
                    modifier = Modifier
                        .testTag(NODE_TAG)
                        .size(48.dp)
                        .trackClick("guardar_pago") { clicked = true }
                )
            }
        }

        composeTestRule.onNodeWithTag(NODE_TAG).performClick()

        assertTrue("el onClick original debe seguir ejecutandose", clicked)
        assertEquals(1, telemetry.recorded.size)
        val event = telemetry.recorded.single()
        assertEquals(TelemetryEventType.TAP, event.type)
        assertEquals("guardar_pago", event.name)
        assertEquals("cobranza_detalle", event.props["screen"])
    }

    @Test
    fun `dos clicks registran exactamente dos taps`() {
        val telemetry = RecordingTelemetry()

        composeTestRule.setContent {
            CompositionLocalProvider(
                LocalTelemetry provides telemetry,
                LocalScreenName provides "cobranza_detalle"
            ) {
                Box(
                    modifier = Modifier
                        .testTag(NODE_TAG)
                        .size(48.dp)
                        .trackClick("guardar_pago") { }
                )
            }
        }

        composeTestRule.onNodeWithTag(NODE_TAG).performClick()
        composeTestRule.onNodeWithTag(NODE_TAG).performClick()

        assertEquals(2, telemetry.recorded.size)
    }

    @Test
    fun `un Telemetry que lanza no rompe el click de negocio, best-effort`() {
        val throwingTelemetry = object : Telemetry {
            override fun screenView(screen: String) = error("boom")
            override fun tap(screen: String, element: String): Unit = error("boom")
            override fun event(name: String, props: Map<String, String>) = error("boom")
            override fun error(code: String, message: String, props: Map<String, String>) =
                error("boom")
        }
        var clicked = false

        composeTestRule.setContent {
            CompositionLocalProvider(
                LocalTelemetry provides throwingTelemetry,
                LocalScreenName provides "cobranza_detalle"
            ) {
                Box(
                    modifier = Modifier
                        .testTag(NODE_TAG)
                        .size(48.dp)
                        .trackClick("guardar_pago") { clicked = true }
                )
            }
        }

        composeTestRule.onNodeWithTag(NODE_TAG).performClick()

        assertTrue("el click de negocio debe ejecutarse aunque la telemetria falle", clicked)
    }

    @Test
    fun `sin ScreenScope activo (LocalScreenName por defecto), el tap se descarta sin crashear`() {
        val telemetry = RecordingTelemetry()
        var clicked = false

        composeTestRule.setContent {
            CompositionLocalProvider(LocalTelemetry provides telemetry) {
                Box(
                    modifier = Modifier
                        .testTag(NODE_TAG)
                        .size(48.dp)
                        .trackClick("guardar_pago") { clicked = true }
                )
            }
        }

        composeTestRule.onNodeWithTag(NODE_TAG).performClick()

        assertTrue("el click de negocio debe ejecutarse igual", clicked)
        assertTrue(
            "un screen invalido (vacio) debe descartar el evento, no crashear",
            telemetry.recorded.isEmpty()
        )
    }
}
