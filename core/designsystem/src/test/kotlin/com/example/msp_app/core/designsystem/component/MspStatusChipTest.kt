package com.example.msp_app.core.designsystem.component

import androidx.compose.foundation.layout.Column
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import com.example.msp_app.core.designsystem.theme.MspTheme
import com.example.msp_app.core.testing.RobolectricTestBase
import org.junit.Rule
import org.junit.Test

/**
 * Prueba la regla dura "nunca solo color": [MspStatusChip] siempre expone el
 * **ícono + el texto** en el árbol de composición, no solo el matiz de fondo —
 * un usuario con daltonismo distingue el estado por la forma del ícono y por
 * el texto. Compose-test de semántica (el golden visual vive en
 * `screenshot/MspStatusChipScreenshotTest`).
 */
class MspStatusChipTest : RobolectricTestBase() {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun `el chip expone icono y texto, no solo color`() {
        composeTestRule.setContent {
            MspTheme(animateColors = false) {
                MspStatusChip(status = ChipStatus.Paid, text = "Pagado")
            }
        }

        composeTestRule.onNodeWithText("Pagado").assertIsDisplayed()
        composeTestRule
            .onNodeWithTag(STATUS_CHIP_ICON_TAG, useUnmergedTree = true)
            .assertIsDisplayed()
    }

    @Test
    fun `los cinco estados renderizan su texto y cada uno con su icono`() {
        val cases = listOf(
            ChipStatus.Paid to "Pagado",
            ChipStatus.Partial to "Parcial",
            ChipStatus.Overdue to "Vencido",
            ChipStatus.Pending to "Pendiente",
            ChipStatus.Promise to "Promesa"
        )
        composeTestRule.setContent {
            MspTheme(animateColors = false) {
                Column {
                    cases.forEach { (status, label) ->
                        MspStatusChip(status = status, text = label)
                    }
                }
            }
        }

        cases.forEach { (_, label) ->
            composeTestRule.onNodeWithText(label).assertIsDisplayed()
        }
        composeTestRule
            .onAllNodesWithTag(STATUS_CHIP_ICON_TAG, useUnmergedTree = true)
            .assertCountEquals(cases.size)
    }
}
