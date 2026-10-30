package com.example.msp_app.core.ubicacion

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.example.msp_app.core.common.location.captura.EstadoDeUbicacion
import com.example.msp_app.core.common.location.captura.FixDeUbicacion
import com.example.msp_app.feature.pagos.ui.PagosRutas
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** E-APP-144: aviso minimalista, sin punto final, que no bloquea. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = android.app.Application::class)
class AvisoDeUbicacionTest {

    @get:Rule
    val compose = createComposeRule()

    private val fix = FixDeUbicacion(20.5, -100.3, 10.0, Instant.EPOCH, 1L)

    @Test
    fun `E-APP-144 sin permiso muestra el texto y tocarlo avisa`() {
        var toques = 0
        compose.setContent {
            MaterialTheme { AvisoDeUbicacion(EstadoDeUbicacion.SinPermiso, alTocar = { toques++ }) }
        }
        compose.onNodeWithText(
            "Activa la ubicación para registrar dónde cobras"
        ).assertIsDisplayed().performClick()
        assertEquals(1, toques)
    }

    @Test
    fun `E-APP-144 con el GPS apagado muestra el texto del GPS`() {
        compose.setContent {
            MaterialTheme { AvisoDeUbicacion(EstadoDeUbicacion.GpsApagado, alTocar = {}) }
        }
        compose.onNodeWithText("Enciende el GPS para registrar dónde cobras").assertIsDisplayed()
    }

    @Test
    fun `E-APP-144 con ubicacion esperando o sin fix no muestra nada`() {
        listOf(
            EstadoDeUbicacion.Esperando,
            EstadoDeUbicacion.SinFix,
            EstadoDeUbicacion.Disponible(fix)
        ).forEach {
            assertNull(textoDelAviso(it))
        }
        compose.setContent {
            MaterialTheme { AvisoDeUbicacion(EstadoDeUbicacion.Disponible(fix), alTocar = {}) }
        }
        compose.onNodeWithText(TEXTO_SIN_PERMISO).assertDoesNotExist()
        compose.onNodeWithText(TEXTO_GPS_APAGADO).assertDoesNotExist()
    }

    @Test
    fun `E-APP-144 los textos van con mayuscula inicial y sin punto final`() {
        listOf(TEXTO_SIN_PERMISO, TEXTO_GPS_APAGADO).forEach {
            assertTrue(it.first().isUpperCase())
            assertFalse(it.endsWith("."))
        }
    }

    @Test
    fun `E-APP-142 solo el cliente la venta y la captura de abono piden ubicacion`() {
        assertTrue(rutaPideUbicacion(PagosRutas.DETALLE_CLIENTE))
        assertTrue(rutaPideUbicacion(PagosRutas.DETALLE_VENTA))
        assertTrue(rutaPideUbicacion(PagosRutas.REGISTRAR_ABONO))
        assertFalse(rutaPideUbicacion(PagosRutas.LISTA_CLIENTES))
        assertFalse(rutaPideUbicacion(PagosRutas.TICKET_PAGO))
        assertFalse(rutaPideUbicacion(null))
    }
}
