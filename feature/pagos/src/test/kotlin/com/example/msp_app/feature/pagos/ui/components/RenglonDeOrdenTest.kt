package com.example.msp_app.feature.pagos.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.height
import androidx.compose.ui.unit.width
import com.example.msp_app.core.designsystem.theme.MspTheme
import com.example.msp_app.core.testing.RobolectricTestBase
import com.example.msp_app.feature.pagos.domain.OrdenDeLaLista
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.robolectric.annotation.Config

/**
 * La pieza "Orden: … ▾ / ✕" (mock "Orden" opción D,
 * decisión del dueño 2026-10-05), probado suelto — sin la pantalla completa
 * ni la hoja, que tienen sus propias pruebas.
 */
@Config(qualifiers = "w360dp-h800dp-xhdpi")
class RenglonDeOrdenTest : RobolectricTestBase() {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun `con cobranza no muestra la X de quitar`() {
        montar(orden = OrdenDeLaLista.COBRANZA)

        composeTestRule.onNodeWithTag(QUITAR_ORDEN_TAG).assertDoesNotExist()
    }

    @Test
    fun `con un orden distinto de cobranza la X quita el orden`() {
        var quitado = false
        montar(orden = OrdenDeLaLista.MAYOR_SALDO, onQuitar = { quitado = true })

        composeTestRule.onNodeWithTag(QUITAR_ORDEN_TAG).performClick()

        assertEquals("tocar Quitar no llamó al callback", true, quitado)
    }

    @Test
    fun `tocar Orden abre la hoja`() {
        var abierta = false
        montar(orden = OrdenDeLaLista.COBRANZA, onAbrirHoja = { abierta = true })

        composeTestRule.onNodeWithTag(ABRIR_ORDEN_TAG).performClick()

        assertEquals("tocar el renglón no abrió la hoja", true, abierta)
    }

    @Test
    fun `la X de quitar mide el toque completo de ancho y de alto`() {
        montar(orden = OrdenDeLaLista.MAYOR_SALDO)

        val limites = composeTestRule.onNodeWithTag(QUITAR_ORDEN_TAG).getUnclippedBoundsInRoot()
        assertTrue("ancho ${limites.width}", limites.width >= 50.dp)
        assertTrue("alto ${limites.height}", limites.height >= 50.dp)
    }

    private fun montar(
        orden: OrdenDeLaLista,
        onAbrirHoja: () -> Unit = {},
        onQuitar: () -> Unit = {}
    ) {
        composeTestRule.setContent {
            MspTheme(darkTheme = false, animateColors = false) {
                Box(modifier = Modifier.fillMaxSize()) {
                    RenglonDeOrden(
                        orden = orden,
                        onAbrirHoja = onAbrirHoja,
                        onQuitar = onQuitar
                    )
                }
            }
        }
    }
}
