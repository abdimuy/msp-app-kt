package com.example.msp_app.feature.pagos.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertHasNoClickAction
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.height
import com.example.msp_app.core.common.cobranza.domain.EstadoCuenta
import com.example.msp_app.core.common.money.Money
import com.example.msp_app.core.designsystem.theme.FontSizeLevel
import com.example.msp_app.core.designsystem.theme.LocalFontSizeLevel
import com.example.msp_app.core.designsystem.theme.MspTheme
import com.example.msp_app.core.testing.RobolectricTestBase
import com.example.msp_app.feature.pagos.domain.model.BitacoraCompleta
import com.example.msp_app.feature.pagos.domain.model.ContactoDeCobranza
import com.example.msp_app.feature.pagos.domain.model.UbicacionDelCobro
import com.example.msp_app.feature.pagos.ui.components.CONTACTO_EN_LINEA_TAG
import java.math.BigDecimal
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.robolectric.annotation.Config

/**
 * **Tocar un contacto abre el mapa de ESE contacto — y uno sin punto no se toca.**
 *
 * El pedido del dueño fue literal: *"cuando se dé click en un pago o visita se
 * debe abrir el mapa también, pero solo con la ubicación de ese pago o visita en
 * particular"*. "En particular" es la mitad que se puede perder en silencio: una
 * implementación que mandara siempre `ultimoCobroAqui` abriría un mapa, se vería
 * bien, y enseñaría la puerta equivocada. Por eso el contacto que se toca aquí
 * lleva un punto **distinto** del último cobro del cliente.
 *
 * La otra mitad es el renglón SIN punto. No basta con que su toque no haga nada:
 * el `clickable` no puede existir. Un control que se ve tocable y no responde es
 * el defecto que ningún golden fotografía —lo mismo que ya cobra
 * `ElCuadroDeLaPuertaYLosDatosTest` para el cuadro de la puerta—, así que se
 * afirma con `assertHasNoClickAction`.
 *
 * Y se mide el alto: una fila tocable de menos de 50 dp incumple el principio 11
 * del brief, que es más estricto que los 48 de Material y **no se baja**. Eso no
 * lo puede ver un golden.
 */
@Config(qualifiers = "w360dp-h800dp-xhdpi")
class UnContactoAbreSuPropioMapaTest : RobolectricTestBase() {

    @get:Rule
    val composeTestRule = createComposeRule()

    private var abierto: UbicacionDelCobro? = null

    // --- La bitácora completa -------------------------------------------------------------

    @Test
    fun `en la bitacora, la fila con punto abre el mapa en SU punto`() {
        bitacora()

        composeTestRule.onAllNodesWithTag(CONTACTO_EN_LINEA_TAG)[CON_PUNTO]
            .assertHasClickAction()
            .performClick()

        assertEquals("abrió el mapa en otra puerta", PUNTO_DE_LA_VISITA, abierto)
    }

    @Test
    fun `en la bitacora, la fila sin punto no se puede tocar`() {
        bitacora()

        composeTestRule.onAllNodesWithTag(CONTACTO_EN_LINEA_TAG)[SIN_PUNTO]
            .assertHasNoClickAction()
    }

    @Test
    fun `la fila de la bitacora respeta el toque minimo`() {
        bitacora()

        val alto = composeTestRule.onAllNodesWithTag(CONTACTO_EN_LINEA_TAG)[CON_PUNTO]
            .getUnclippedBoundsInRoot()
            .height
        assertTrue(
            "la fila de la bitácora mide $alto y el piso del repo es $TOQUE_MINIMO",
            alto >= TOQUE_MINIMO
        )
    }

    // --- Los tres del detalle -------------------------------------------------------------

    @Test
    fun `en el detalle, el contacto con punto abre el mapa en SU punto`() {
        detalle()

        composeTestRule.onAllNodesWithTag(CONTACTO_EN_LINEA_TAG)[CON_PUNTO]
            .performScrollTo()
            .assertHasClickAction()
            .performClick()

        // El punto de la visita, NO `ultimoCobroAqui`: son dos puertas distintas y
        // el renglón tiene que mandar la suya.
        assertEquals(
            "abrió el mapa en el último cobro y no en el punto de ese contacto",
            PUNTO_DE_LA_VISITA,
            abierto
        )
    }

    @Test
    fun `en el detalle, el contacto sin punto no se puede tocar`() {
        detalle()

        composeTestRule.onAllNodesWithTag(CONTACTO_EN_LINEA_TAG)[SIN_PUNTO]
            .performScrollTo()
            .assertHasNoClickAction()
    }

    @Test
    fun `el contacto del detalle respeta el toque minimo`() {
        detalle()

        val alto = composeTestRule.onAllNodesWithTag(CONTACTO_EN_LINEA_TAG)[CON_PUNTO]
            .performScrollTo()
            .getUnclippedBoundsInRoot()
            .height
        assertTrue(
            "el contacto del detalle mide $alto y el piso del repo es $TOQUE_MINIMO",
            alto >= TOQUE_MINIMO
        )
    }

    // --- Montaje --------------------------------------------------------------------------

    private fun bitacora() {
        composeTestRule.setContent {
            Tema {
                BitacoraContent(
                    state = BitacoraUiState(
                        cargando = false,
                        bitacora = BitacoraCompleta(
                            clienteId = PagosFixtures.CLIENTE_ID,
                            nombre = "Victoria Flores Olmedo",
                            direccion = "C. Hidalgo 214, Centro",
                            contactos = CONTACTOS
                        )
                    ),
                    onAtras = {},
                    onVerUbicacion = { punto -> abierto = punto }
                )
            }
        }
    }

    private fun detalle() {
        composeTestRule.setContent {
            Tema {
                DetalleClienteContent(
                    state = DetalleClienteUiState(
                        cargando = false,
                        detalle = PagosFixtures.detalleCliente().copy(contactos = CONTACTOS)
                    ),
                    onAtras = {},
                    onAbrirVenta = {},
                    onRegistrarAbono = {},
                    onRegistrarVisita = {},
                    onVerContactos = {},
                    onAlternarTema = {},
                    onAlternarPrivacidad = {},
                    onVerUbicacionDelContacto = { punto -> abierto = punto }
                )
            }
        }
    }

    @Composable
    private fun Tema(contenido: @Composable () -> Unit) {
        CompositionLocalProvider(LocalFontSizeLevel provides FontSizeLevel.NORMAL) {
            MspTheme(darkTheme = false, animateColors = false, content = contenido)
        }
    }

    private companion object {
        /** El piso del repo (principio 11), más estricto que los 48 de Material. */
        val TOQUE_MINIMO = 50.dp

        /**
         * El punto de la visita que se toca. **Distinto** del `ultimoCobroAqui` de
         * la fixture a propósito: es lo que separa "abrió el mapa" de "abrió el
         * mapa en la puerta correcta".
         */
        val PUNTO_DE_LA_VISITA = UbicacionDelCobro(lat = 18.4712, lng = -97.4011)

        const val CON_PUNTO = 0
        const val SIN_PUNTO = 1

        /**
         * Una visita con punto y un abono sin él. Los dos estados existen en la
         * app: la visita se registró con señal, el abono se capturó sin ella.
         */
        val CONTACTOS = listOf(
            ContactoDeCobranza(
                fecha = Instant.parse("2026-08-24T17:00:00Z"),
                etiqueta = "no responde aunque está",
                nota = null,
                estado = EstadoCuenta.VISITE_VUELVO,
                importe = null,
                ubicacion = PUNTO_DE_LA_VISITA
            ),
            ContactoDeCobranza(
                fecha = Instant.parse("2026-08-03T17:10:00Z"),
                etiqueta = "Cobré",
                nota = null,
                estado = EstadoCuenta.PAGO,
                importe = Money.of(BigDecimal("350.00")),
                ubicacion = null
            )
        )
    }
}
