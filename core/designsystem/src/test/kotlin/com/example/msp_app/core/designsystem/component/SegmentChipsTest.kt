package com.example.msp_app.core.designsystem.component

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.example.msp_app.core.designsystem.theme.MspTheme
import com.example.msp_app.core.testing.RobolectricTestBase
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/**
 * Compose-test (no golden) de [MspSegmentChips]: cada tap informa su índice
 * vía [onSelect] (task-9-brief.md, "cambia `selectedIndex` al tap"). El
 * componente es stateless — el segundo caso hoistea `selectedIndex` como lo
 * haría un caller real, verificando que el índice activo efectivamente se
 * mueve entre opciones. El golden visual vive en
 * `screenshot/SegmentChipsScreenshotTest`.
 */
class SegmentChipsTest : RobolectricTestBase() {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun `tap en cada opcion informa su indice via onSelect`() {
        val selected = mutableListOf<Int>()
        composeTestRule.setContent {
            MspTheme(animateColors = false) {
                MspSegmentChips(
                    options = listOf("Día", "Semana"),
                    selectedIndex = 0,
                    onSelect = { selected += it }
                )
            }
        }

        composeTestRule.onNodeWithText("Semana").performClick()
        composeTestRule.onNodeWithText("Día").performClick()

        assertEquals(listOf(1, 0), selected)
    }

    @Test
    fun `selectedIndex hoisted por el caller mueve el estado activo entre opciones`() {
        var selectedIndex by mutableStateOf(0)
        composeTestRule.setContent {
            MspTheme(animateColors = false) {
                MspSegmentChips(
                    options = listOf("Día", "Semana"),
                    selectedIndex = selectedIndex,
                    onSelect = { selectedIndex = it }
                )
            }
        }

        composeTestRule.onNodeWithTag("${SEGMENT_CHIP_TAG_PREFIX}1").performClick()
        composeTestRule.waitForIdle()

        assertEquals(1, selectedIndex)
        composeTestRule.onNodeWithText("Semana").assertIsDisplayed()
    }
}
