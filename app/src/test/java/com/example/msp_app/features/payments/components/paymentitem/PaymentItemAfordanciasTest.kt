package com.example.msp_app.features.payments.components.paymentitem

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.example.msp_app.data.models.payment.Payment
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * **Cuál afordancia dispara cuál destino** en la fila de pago.
 *
 * La regla del origen dice dos cosas distintas sobre esta misma fila y por eso
 * la fila tiene dos puertas:
 *
 * - tocar **la fila** es tocar un **pago** → se entra a **su venta**;
 * - tocar **"⋯ → ver cliente"** es tocar a la **persona** → se entra al
 *   **cliente**. Es la puerta al cliente desde el mapa de rutas.
 *
 * Este test no mira rutas —de eso se encarga `ReglaDelOrigenTest`, que las
 * afirma sobre el grafo real—: mira que el toque de arriba no dispare la acción
 * de abajo. Sin él, cablear las dos lambdas al revés pasaría los dos suites.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [33], application = android.app.Application::class)
class PaymentItemAfordanciasTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private class Toques {
        var venta = 0
        var cliente = 0
    }

    private fun pinta(variant: PaymentItemVariant): Toques {
        val toques = Toques()
        composeTestRule.setContent {
            PaymentItem(
                payment = pago(),
                variant = variant,
                onVerCliente = { toques.cliente++ },
                onClick = { toques.venta++ }
            )
        }
        return toques
    }

    @Test
    fun `tocar la fila abre la venta del pago, no el cliente`() {
        val toques = pinta(PaymentItemVariant.DEFAULT)
        composeTestRule.onNodeWithText("María Guadalupe Rentería").performClick()
        assertEquals(1, toques.venta)
        assertEquals(0, toques.cliente)
    }

    @Test
    fun `ver cliente abre el cliente, no la venta`() {
        val toques = pinta(PaymentItemVariant.DEFAULT)
        composeTestRule.onNodeWithTag(PAYMENT_ITEM_MENU_TAG).performClick()
        composeTestRule.onNodeWithText("Ver cliente").performClick()
        assertEquals(1, toques.cliente)
        assertEquals(0, toques.venta)
    }

    /**
     * La variante compacta —la de los pagos del día en Home— tiene las mismas
     * dos puertas. Antes solo tenía el menú, y el menú llevaba al detalle de
     * **venta** legado mientras decía "cliente".
     */
    @Test
    fun `la variante compacta tiene las mismas dos puertas`() {
        val toques = pinta(PaymentItemVariant.COMPACT)
        composeTestRule.onNodeWithText("María Guadalupe Rentería").performClick()
        assertEquals(1, toques.venta)
        composeTestRule.onNodeWithTag(PAYMENT_ITEM_MENU_TAG).performClick()
        composeTestRule.onNodeWithText("Ver cliente").performClick()
        assertEquals(1, toques.cliente)
        assertEquals(1, toques.venta)
    }

    private fun pago(): Payment = Payment(
        ID = "b3f1a0d2-7c44-4f1e-9a2b-5d6e7f801234",
        COBRADOR = "Esperanza Villalobos",
        DOCTO_CC_ACR_ID = 4477,
        DOCTO_CC_ID = 3312,
        FECHA_HORA_PAGO = "2026-09-01T10:15:00Z",
        GUARDADO_EN_MICROSIP = false,
        IMPORTE = 350.0,
        LAT = 20.6736,
        LNG = -103.344,
        CLIENTE_ID = 9011,
        COBRADOR_ID = 41,
        FORMA_COBRO_ID = 1,
        ZONA_CLIENTE_ID = 7,
        NOMBRE_CLIENTE = "María Guadalupe Rentería"
    )
}
