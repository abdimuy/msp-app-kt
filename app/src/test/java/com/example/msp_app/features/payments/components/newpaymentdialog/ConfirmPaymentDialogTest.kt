package com.example.msp_app.features.payments.components.newpaymentdialog

import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * D2 — el diálogo de pago duplicaba cobros.
 *
 * El 2026-08-17 se registraron 3 cobros duplicados, $600 de más. El
 * `UUID.randomUUID()` se generaba **dentro** del manejador del botón, así que
 * cada toque mandaba una clave de idempotencia distinta y la idempotencia del
 * servidor —que sí funciona— nunca podía actuar. Encima, el botón "Confirmar"
 * del `AlertDialog` no tenía `enabled` y no cerraba el diálogo: un doble toque
 * era trivial.
 *
 * Las tres defensas, cada una con su prueba:
 *
 * 1. la clave de idempotencia se `remember`: una por apertura, no una por toque
 * 2. confirmar cierra el diálogo
 * 3. el botón se deshabilita mientras guarda
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [33], application = android.app.Application::class)
class ConfirmPaymentDialogTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    /** Lo que el diálogo le entrega a la pantalla de cobro. */
    private class Captura {
        var confirmaciones = 0
        var cierres = 0
    }

    /**
     * Monta el diálogo con el mismo contrato que el llamador real: el padre
     * posee el estado y lo reescribe con lo que el diálogo entrega.
     *
     * [padreRecompone] `false` congela al padre en el instante del primer toque
     * —el diálogo sigue montado y `guardando` sigue en `false`—, que es la
     * ventana real en la que cabe el segundo toque de un doble toque.
     */
    private fun montar(padreRecompone: Boolean = true): Captura {
        val captura = Captura()

        composeTestRule.setContent {
            var visible by remember { mutableStateOf(true) }
            var guardando by remember { mutableStateOf(false) }

            if (visible) {
                MaterialTheme {
                    ConfirmPaymentDialog(
                        amount = 200.0,
                        saldoRestante = 1_800.0,
                        isDark = false,
                        guardando = guardando,
                        onConfirm = {
                            captura.confirmaciones++
                            if (padreRecompone) guardando = true
                        },
                        onDismiss = {
                            captura.cierres++
                            if (padreRecompone) visible = false
                        }
                    )
                }
            }
        }

        return captura
    }

    // ─── Defensa 2 y 3: un solo cobro por más toques que haya ────────────────

    @Test
    fun `dos toques rapidos en confirmar producen un solo pago`() {
        // El padre no alcanza a recomponer entre los dos toques: el diálogo
        // sigue en pantalla y `guardando` sigue en false. Es el caso que
        // producía el cobro duplicado.
        val captura = montar(padreRecompone = false)

        composeTestRule.onNodeWithText(CONFIRMAR).performClick()
        composeTestRule.onNodeWithText(CONFIRMAR).performClick()
        composeTestRule.waitForIdle()

        assertEquals(
            "dos toques deben producir UN pago: cada confirmación extra es un " +
                "cobro duplicado a un cliente que ya pagó",
            1,
            captura.confirmaciones
        )
    }

    @Test
    fun `el diálogo cierra al confirmar`() {
        val captura = montar()

        composeTestRule.onNodeWithText(CONFIRMAR).performClick()
        composeTestRule.waitForIdle()

        assertEquals("confirmar debe cerrar el diálogo", 1, captura.cierres)
        assertTrue(
            "el diálogo no puede seguir en pantalla tras confirmar",
            composeTestRule.onAllNodes(hasText(CONFIRMAR)).fetchSemanticsNodes().isEmpty()
        )
        assertEquals(1, captura.confirmaciones)
    }

    @Test
    fun `el boton queda deshabilitado tras confirmar`() {
        // Sin el cierre del padre, la única barrera que queda es el botón.
        val captura = montar(padreRecompone = false)

        composeTestRule.onNodeWithText(CONFIRMAR).performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText(CONFIRMAR).assertIsNotEnabled()
        assertEquals(1, captura.confirmaciones)
    }

    @Test
    fun `mientras guarda no se puede confirmar`() {
        val captura = Captura()

        composeTestRule.setContent {
            MaterialTheme {
                ConfirmPaymentDialog(
                    amount = 200.0,
                    saldoRestante = 1_800.0,
                    isDark = false,
                    guardando = true,
                    onConfirm = { captura.confirmaciones++ },
                    onDismiss = { captura.cierres++ }
                )
            }
        }

        composeTestRule.onNodeWithText(CONFIRMAR).assertIsNotEnabled()
        composeTestRule.onNodeWithText(CONFIRMAR).performClick()
        composeTestRule.waitForIdle()

        assertEquals(0, captura.confirmaciones)
    }

    // ─── Defensa 1: la clave de idempotencia es una por apertura ─────────────

    @Test
    fun `la clave de idempotencia no cambia entre recomposiciones`() {
        val claves = mutableListOf<String>()

        composeTestRule.setContent {
            var recomposiciones by remember { mutableIntStateOf(0) }
            // El contador se lee AQUÍ, en el mismo ámbito que la clave: leerlo
            // sólo dentro del lambda del botón recompondría el `Text` y no esta
            // función, y la prueba se volvería verde con el defecto puesto.
            val etiqueta = "recomponer $recomposiciones"
            val clave = rememberPaymentIdempotencyKey()
            claves += clave

            MaterialTheme {
                Button(onClick = { recomposiciones++ }) {
                    Text(etiqueta)
                }
            }
        }

        // Cada toque recompone, igual que escribir el monto o abrir y cerrar la
        // confirmación. Si la clave se generara en cada pasada —que es lo que
        // hacía el `UUID.randomUUID()` dentro del manejador del botón— cada
        // toque mandaría una clave distinta y el servidor no podría reconocer
        // el reenvío.
        repeat(3) {
            composeTestRule.onNodeWithText("recomponer", substring = true).performClick()
            composeTestRule.waitForIdle()
        }

        assertTrue("la composición no produjo claves", claves.isNotEmpty())
        assertEquals(
            "la clave de idempotencia debe ser UNA por apertura del diálogo, no " +
                "una por recomposición: claves distintas = cobros distintos",
            1,
            claves.distinct().size
        )
    }

    private companion object {
        const val CONFIRMAR = "Confirmar"
    }
}
