package com.example.msp_app.feature.pagos.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import com.example.msp_app.core.designsystem.theme.MspTheme
import com.example.msp_app.core.testing.RobolectricTestBase
import com.example.msp_app.feature.pagos.domain.OrdenDeLaLista
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.robolectric.annotation.Config

/**
 * El renglón "Orden: … ▾ / N clientes / ✕ Quitar" (mock "Orden" opción D,
 * decisión del dueño 2026-10-05), probado suelto — sin la pantalla completa
 * ni la hoja, que tienen sus propias pruebas.
 */
@Config(qualifiers = "w360dp-h800dp-xhdpi")
class RenglonDeOrdenTest : RobolectricTestBase() {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun `con cobranza muestra el conteo y no la X de quitar`() {
        montar(orden = OrdenDeLaLista.COBRANZA, conteo = 7)

        composeTestRule.onNodeWithTag(QUITAR_ORDEN_TAG).assertDoesNotExist()
    }

    @Test
    fun `con un orden distinto de cobranza muestra Quitar y no el conteo`() {
        var quitado = false
        montar(orden = OrdenDeLaLista.MAYOR_SALDO, conteo = 7, onQuitar = { quitado = true })

        composeTestRule.onNodeWithTag(QUITAR_ORDEN_TAG).performClick()

        assertEquals("tocar Quitar no llamó al callback", true, quitado)
    }

    @Test
    fun `tocar Orden abre la hoja`() {
        var abierta = false
        montar(orden = OrdenDeLaLista.COBRANZA, conteo = 7, onAbrirHoja = { abierta = true })

        composeTestRule.onNodeWithTag(ABRIR_ORDEN_TAG).performClick()

        assertEquals("tocar el renglón no abrió la hoja", true, abierta)
    }

    @Test
    fun `el aviso sin ubicacion solo aparece cuando se pide`() {
        montar(orden = OrdenDeLaLista.MAS_CERCA, conteo = 7, avisoSinUbicacion = false)
        composeTestRule.onNodeWithTag(AVISO_SIN_UBICACION_TAG).assertDoesNotExist()
    }

    private fun montar(
        orden: OrdenDeLaLista,
        conteo: Int,
        avisoSinUbicacion: Boolean = false,
        onAbrirHoja: () -> Unit = {},
        onQuitar: () -> Unit = {}
    ) {
        composeTestRule.setContent {
            MspTheme(darkTheme = false, animateColors = false) {
                Box(modifier = Modifier.fillMaxSize()) {
                    RenglonDeOrden(
                        orden = orden,
                        conteo = conteo,
                        avisoSinUbicacion = avisoSinUbicacion,
                        onAbrirHoja = onAbrirHoja,
                        onQuitar = onQuitar
                    )
                }
            }
        }
    }
}
