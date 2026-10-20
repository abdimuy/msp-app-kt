package com.example.msp_app.feature.visitas.ui

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import com.example.msp_app.core.testing.RobolectricTestBase
import com.example.msp_app.feature.visitas.domain.model.PendientesDelTelefono
import com.example.msp_app.feature.visitas.ui.components.AVISO_DE_PENDIENTES_TAG
import com.example.msp_app.feature.visitas.ui.components.AvisoDePendientes
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/** E-APP-096: el aviso de Inicio se pinta sólo con pendientes, con el texto exacto, y al tocarlo avisa. */
class AvisoDePendientesTest : RobolectricTestBase() {

    @get:Rule
    val compose = createComposeRule()

    private var toques = 0

    private fun pinta(pendientes: PendientesDelTelefono) {
        compose.setContent { AvisoDePendientes(pendientes = pendientes, onClick = { toques++ }) }
    }

    @Test
    fun `E-APP-096 con cero pendientes no se pinta nada`() {
        pinta(PendientesDelTelefono())

        compose.onAllNodesWithTag(AVISO_DE_PENDIENTES_TAG).assertCountEquals(0)
    }

    @Test
    fun `E-APP-096 con pendientes se lee el texto exacto`() {
        pinta(PendientesDelTelefono(visitas = 1, fotos = 2, pagos = 3, ventas = 1))

        compose.onNodeWithText("7 sin enviar · no cambies de teléfono").assertIsDisplayed()
    }

    @Test
    fun `E-APP-096 tocarlo lleva a pendientes y el area es tocable`() {
        pinta(PendientesDelTelefono(fotos = 1))

        compose.onNodeWithTag(AVISO_DE_PENDIENTES_TAG)
            .assertHasClickAction()
            .assertHeightIsAtLeast(48.dp)
        // Control positivo: antes del toque nadie llamó.
        assertEquals(0, toques)
        compose.onNodeWithTag(AVISO_DE_PENDIENTES_TAG).performClick()
        assertEquals(1, toques)
    }
}
