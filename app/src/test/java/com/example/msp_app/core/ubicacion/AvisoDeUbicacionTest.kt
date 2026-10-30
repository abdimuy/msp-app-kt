package com.example.msp_app.core.ubicacion

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.height
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
    fun `E-APP-144 sin permiso muestra el texto y Activar avisa`() {
        var activar = 0
        compose.setContent {
            MaterialTheme {
                AvisoDeUbicacion(
                    EstadoDeUbicacion.SinPermiso,
                    alActivar = { activar++ },
                    alDescartar = {}
                )
            }
        }
        compose.onNodeWithText(
            "Activa la ubicación para registrar dónde cobras"
        ).assertIsDisplayed()
        compose.onNodeWithText("Activar").assertIsDisplayed().performClick()
        assertEquals(1, activar)
    }

    @Test
    fun `E-APP-144 Ahora no avisa que se descarta`() {
        var descartar = 0
        compose.setContent {
            MaterialTheme {
                AvisoDeUbicacion(
                    EstadoDeUbicacion.GpsApagado,
                    alActivar = {},
                    alDescartar = { descartar++ }
                )
            }
        }
        compose.onNodeWithText("Ahora no").assertIsDisplayed().performClick()
        assertEquals(1, descartar)
    }

    @Test
    fun `E-APP-149 H2 el aviso flotante no tapa la barra superior ni se come sus toques`() {
        var atras = 0
        var abajo = 0
        val descarte = DescarteDelAviso()
        compose.setContent {
            MaterialTheme {
                Box(Modifier.fillMaxSize()) {
                    Button(
                        onClick = { atras++ },
                        modifier = Modifier.align(Alignment.TopStart).testTag("atras")
                    ) { Text("Atras") }
                    Button(
                        onClick = { abajo++ },
                        modifier = Modifier.align(Alignment.Center).testTag("centro")
                    ) { Text("Centro") }
                    AvisoFlotanteDeUbicacion(EstadoDeUbicacion.SinPermiso, descarte, alActivar = {})
                }
            }
        }
        compose.onNodeWithTag("atras").performClick()
        compose.onNodeWithTag("centro").performClick()
        assertEquals(1, atras)
        assertEquals(1, abajo)
        val atrasInferior = compose.onNodeWithTag("atras").getUnclippedBoundsInRoot().bottom
        val avisoSuperior = compose.onNodeWithTag(
            TAG_AVISO_UBICACION
        ).getUnclippedBoundsInRoot().top
        val centroInferior = compose.onNodeWithTag("centro").getUnclippedBoundsInRoot().bottom
        assertTrue(
            "el aviso debe quedar debajo de la barra superior",
            avisoSuperior >= atrasInferior
        )
        assertTrue("el aviso no debe tapar el contenido central", avisoSuperior >= centroInferior)
    }

    @Test
    fun `E-APP-149 H2 el aviso no ocupa el ancho y alto de la pantalla`() {
        val descarte = DescarteDelAviso()
        compose.setContent {
            MaterialTheme {
                Box(Modifier.fillMaxSize().testTag("raiz")) {
                    AvisoFlotanteDeUbicacion(EstadoDeUbicacion.GpsApagado, descarte, alActivar = {})
                }
            }
        }
        val raiz = compose.onNodeWithTag("raiz").getUnclippedBoundsInRoot()
        val aviso = compose.onNodeWithTag(TAG_AVISO_UBICACION).getUnclippedBoundsInRoot()
        assertTrue(aviso.height < raiz.height / 4)
        assertTrue(aviso.top > raiz.height / 2)
    }

    @Test
    fun `E-APP-149 H2 Ahora no lo oculta por la sesion y Activar lo deja`() {
        val descarte = DescarteDelAviso()
        compose.setContent {
            MaterialTheme {
                Box(Modifier.fillMaxSize()) {
                    AvisoFlotanteDeUbicacion(EstadoDeUbicacion.SinPermiso, descarte, alActivar = {})
                }
            }
        }
        compose.onNodeWithText("Activar").performClick()
        compose.onNodeWithTag(TAG_AVISO_UBICACION).assertIsDisplayed()
        compose.onNodeWithText("Ahora no").performClick()
        compose.waitForIdle()
        compose.onNodeWithTag(TAG_AVISO_UBICACION).assertDoesNotExist()
        assertTrue(descarte.descartado)
    }

    @Test
    fun `E-APP-149 H2 sin aviso que mostrar no hay nodo`() {
        val descarte = DescarteDelAviso()
        compose.setContent {
            MaterialTheme {
                Box(Modifier.fillMaxSize()) {
                    AvisoFlotanteDeUbicacion(
                        EstadoDeUbicacion.Disponible(fix),
                        descarte,
                        alActivar = {}
                    )
                }
            }
        }
        compose.onNodeWithTag(TAG_AVISO_UBICACION).assertDoesNotExist()
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
            MaterialTheme {
                AvisoDeUbicacion(
                    EstadoDeUbicacion.Disponible(fix),
                    alActivar = {},
                    alDescartar = {}
                )
            }
        }
        compose.onNodeWithText(TEXTO_SIN_PERMISO).assertDoesNotExist()
        compose.onNodeWithText(TEXTO_GPS_APAGADO).assertDoesNotExist()
    }

    @Test
    fun `E-APP-144 los textos van con mayuscula inicial y sin punto final`() {
        listOf(TEXTO_SIN_PERMISO, TEXTO_GPS_APAGADO, TEXTO_ACTIVAR, TEXTO_AHORA_NO).forEach {
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
