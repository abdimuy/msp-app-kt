package com.example.msp_app.features.forgiveness

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import com.example.msp_app.features.forgiveness.components.CONFIRMAR_CONDONACION_TAG
import com.example.msp_app.features.forgiveness.components.ConfirmacionDeCondonacion
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * **"Confirmar" se deshabilita mientras la condonación se guarda.**
 *
 * El diálogo viejo llamaba a guardar directo desde el botón, sin ninguna guarda
 * (E-APP-043, #2): cada toque era una fila nueva con su propio UUID. Aquí el
 * botón se apaga en cuanto hay una escritura en vuelo.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [33], application = android.app.Application::class)
class ConfirmarCondonacionTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun `mientras guarda, Confirmar esta deshabilitado`() {
        compose.setContent {
            ConfirmacionDeCondonacion(
                monto = "$2,300",
                guardando = true,
                isDark = false,
                onConfirmar = {},
                onCancelar = {}
            )
        }

        compose.onNodeWithTag(CONFIRMAR_CONDONACION_TAG).assertIsNotEnabled()
    }

    /** Control positivo: sin escritura en vuelo el botón sí se puede tocar. */
    @Test
    fun `sin escritura en vuelo, Confirmar esta habilitado`() {
        compose.setContent {
            ConfirmacionDeCondonacion(
                monto = "$2,300",
                guardando = false,
                isDark = false,
                onConfirmar = {},
                onCancelar = {}
            )
        }

        compose.onNodeWithTag(CONFIRMAR_CONDONACION_TAG).assertIsEnabled()
    }

    /**
     * Dos toques seguidos, con el `guardando` cableado como en el diálogo: el
     * primero enciende la escritura y el segundo ya no llega.
     */
    @Test
    fun `dos toques confirman una sola vez`() {
        var confirmaciones = 0
        compose.setContent {
            var guardando by remember { mutableStateOf(false) }
            ConfirmacionDeCondonacion(
                monto = "$2,300",
                guardando = guardando,
                isDark = false,
                onConfirmar = {
                    confirmaciones += 1
                    guardando = true
                },
                onCancelar = {}
            )
        }

        compose.onNodeWithTag(CONFIRMAR_CONDONACION_TAG).performClick()
        compose.waitForIdle()
        compose.onNodeWithTag(CONFIRMAR_CONDONACION_TAG).performClick()
        compose.waitForIdle()

        assertEquals(1, confirmaciones)
    }
}
