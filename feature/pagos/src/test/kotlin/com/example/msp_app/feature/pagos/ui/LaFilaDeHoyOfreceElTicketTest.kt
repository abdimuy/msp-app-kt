package com.example.msp_app.feature.pagos.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertHasNoClickAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.example.msp_app.core.common.cobranza.domain.EstadoCuenta
import com.example.msp_app.core.common.money.Money
import com.example.msp_app.core.designsystem.theme.FontSizeLevel
import com.example.msp_app.core.designsystem.theme.LocalFontSizeLevel
import com.example.msp_app.core.designsystem.theme.MspTheme
import com.example.msp_app.core.testing.RobolectricTestBase
import com.example.msp_app.feature.pagos.domain.model.BitacoraCompleta
import com.example.msp_app.feature.pagos.domain.model.ContactoDeCobranza
import com.example.msp_app.feature.pagos.domain.model.TipoDeContacto
import com.example.msp_app.feature.pagos.domain.model.UbicacionDelCobro
import com.example.msp_app.feature.pagos.ui.components.CONTACTO_EN_LINEA_TAG
import com.example.msp_app.feature.pagos.ui.components.ELEGIR_QUE_ABRIR
import com.example.msp_app.feature.pagos.ui.components.HOJA_DEL_CONTACTO_TAG
import com.example.msp_app.feature.pagos.ui.components.OPCION_TICKET_TAG
import com.example.msp_app.feature.pagos.ui.components.OPCION_UBICACION_TAG
import com.example.msp_app.feature.pagos.ui.components.VELO_DEL_CONTACTO_TAG
import com.example.msp_app.feature.pagos.ui.components.VER_DONDE_FUE
import com.example.msp_app.feature.pagos.ui.components.VER_EL_TICKET
import java.math.BigDecimal
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.robolectric.annotation.Config

/**
 * **El toque se comporta igual en las TRES pantallas que pintan contactos.**
 *
 * La bitácora, el detalle de cliente y el detalle de venta pintan la misma fila
 * ([com.example.msp_app.feature.pagos.ui.components.ContactoEnLinea]) y cada una
 * la cablea por su cuenta. Arreglar el gesto en una sola dejaría que el cobrador
 * encontrara la reimpresión o no **según por dónde entró**, que es peor que no
 * tenerla: una función que aparece y desaparece no se aprende.
 *
 * Por eso cada caso se afirma tres veces, contra los tres `*Content`. El
 * veredicto en sí —cuándo pregunta y cuándo no— ya lo cobra
 * `ToqueDelContactoTest` sin pantalla; lo que se mide aquí es el **cableado**:
 * que las tres le pasen a la fila el `hoy` y la lista, que las tres monten la
 * hoja, y que las dos opciones lleven a donde dicen.
 */
@Config(qualifiers = "w360dp-h800dp-xhdpi")
class LaFilaDeHoyOfreceElTicketTest : RobolectricTestBase() {

    @get:Rule
    val composeTestRule = createComposeRule()

    private var mapa: UbicacionDelCobro? = null
    private var ticket: String? = null

    // --- La bitácora ----------------------------------------------------------

    @Test
    fun `en la bitacora, el cobro de hoy pregunta antes de abrir nada`() {
        bitacora()

        tocar(EL_DE_HOY)

        composeTestRule.onNodeWithTag(HOJA_DEL_CONTACTO_TAG).assertExists()
        assertNull("abrió el mapa en vez de preguntar", mapa)
        assertNull("abrió el ticket sin preguntar", ticket)
    }

    @Test
    fun `en la bitacora, el ticket de la hoja es el del abono que se toco`() {
        bitacora()

        tocar(EL_DE_HOY)
        composeTestRule.onNodeWithTag(OPCION_TICKET_TAG).performClick()

        assertEquals(ID_DE_HOY, ticket)
        assertNull("no tenía que abrir también el mapa", mapa)
    }

    @Test
    fun `en la bitacora, la ubicacion de la hoja es la de ese abono`() {
        bitacora()

        tocar(EL_DE_HOY)
        composeTestRule.onNodeWithTag(OPCION_UBICACION_TAG).performClick()

        assertEquals(PUNTO_DE_HOY, mapa)
        assertNull("no tenía que abrir también el ticket", ticket)
    }

    @Test
    fun `en la bitacora, el velo cierra la hoja sin abrir nada`() {
        bitacora()

        tocar(EL_DE_HOY)
        composeTestRule.onNodeWithTag(VELO_DEL_CONTACTO_TAG).performClick()

        composeTestRule.onNodeWithTag(HOJA_DEL_CONTACTO_TAG).assertDoesNotExist()
        assertNull(mapa)
        assertNull(ticket)
    }

    @Test
    fun `en la bitacora, el cobro de ayer abre el mapa directo`() {
        bitacora()

        tocar(EL_DE_AYER)

        composeTestRule.onNodeWithTag(HOJA_DEL_CONTACTO_TAG).assertDoesNotExist()
        assertEquals(PUNTO_DE_AYER, mapa)
    }

    // --- El detalle de cliente ------------------------------------------------

    @Test
    fun `en el detalle de cliente, el cobro de hoy pregunta`() {
        detalleCliente()

        tocar(EL_DE_HOY, desplazando = true)

        composeTestRule.onNodeWithTag(HOJA_DEL_CONTACTO_TAG).assertExists()
        assertNull(mapa)
    }

    @Test
    fun `en el detalle de cliente, el ticket de la hoja es el del abono que se toco`() {
        detalleCliente()

        tocar(EL_DE_HOY, desplazando = true)
        composeTestRule.onNodeWithTag(OPCION_TICKET_TAG).performClick()

        assertEquals(ID_DE_HOY, ticket)
    }

    @Test
    fun `en el detalle de cliente, el cobro de ayer abre el mapa directo`() {
        detalleCliente()

        tocar(EL_DE_AYER, desplazando = true)

        composeTestRule.onNodeWithTag(HOJA_DEL_CONTACTO_TAG).assertDoesNotExist()
        assertEquals(PUNTO_DE_AYER, mapa)
    }

    // --- El detalle de venta --------------------------------------------------

    @Test
    fun `en el detalle de venta, el cobro de hoy pregunta`() {
        detalleVenta()

        tocar(EL_DE_HOY, desplazando = true)

        composeTestRule.onNodeWithTag(HOJA_DEL_CONTACTO_TAG).assertExists()
        assertNull(mapa)
    }

    @Test
    fun `en el detalle de venta, el ticket de la hoja es el del abono que se toco`() {
        detalleVenta()

        tocar(EL_DE_HOY, desplazando = true)
        composeTestRule.onNodeWithTag(OPCION_TICKET_TAG).performClick()

        assertEquals(ID_DE_HOY, ticket)
    }

    @Test
    fun `en el detalle de venta, el cobro de ayer abre el mapa directo`() {
        detalleVenta()

        tocar(EL_DE_AYER, desplazando = true)

        composeTestRule.onNodeWithTag(HOJA_DEL_CONTACTO_TAG).assertDoesNotExist()
        assertEquals(PUNTO_DE_AYER, mapa)
    }

    // --- El cobro de hoy capturado SIN señal ----------------------------------

    /**
     * El renglón que hasta la ronda de arreglo no se podía tocar. Sin punto no
     * hay hoja —no habría qué elegir—, así que el ticket sale derecho; y la fila
     * **tiene** que ser tocable, que es el cambio de fondo. Se afirma en las
     * tres pantallas por lo mismo que todo lo demás de este archivo.
     */
    @Test
    fun `en la bitacora, el cobro de hoy sin punto abre el ticket directo`() {
        bitacora(SIN_PUNTO)

        tocar(EL_DE_HOY)

        composeTestRule.onNodeWithTag(HOJA_DEL_CONTACTO_TAG).assertDoesNotExist()
        assertEquals(ID_DE_HOY, ticket)
        assertNull("no había punto que abrir", mapa)
    }

    @Test
    fun `en el detalle de cliente, el cobro de hoy sin punto abre el ticket directo`() {
        detalleCliente(SIN_PUNTO)

        tocar(EL_DE_HOY, desplazando = true)

        composeTestRule.onNodeWithTag(HOJA_DEL_CONTACTO_TAG).assertDoesNotExist()
        assertEquals(ID_DE_HOY, ticket)
    }

    @Test
    fun `en el detalle de venta, el cobro de hoy sin punto abre el ticket directo`() {
        detalleVenta(SIN_PUNTO)

        tocar(EL_DE_HOY, desplazando = true)

        composeTestRule.onNodeWithTag(HOJA_DEL_CONTACTO_TAG).assertDoesNotExist()
        assertEquals(ID_DE_HOY, ticket)
    }

    /**
     * El cobro de AYER sin punto sigue sin tocarse. Abrir el ticket es la
     * excepción del cobro del día, no una puerta nueva para cualquier renglón
     * mudo: una fila que se ve tocable y no hace nada es el defecto que ningún
     * golden fotografía.
     */
    @Test
    fun `en la bitacora, el cobro de ayer sin punto sigue sin tocarse`() {
        bitacora(SIN_PUNTO)

        composeTestRule.onAllNodesWithTag(CONTACTO_EN_LINEA_TAG)[EL_DE_AYER]
            .assertHasNoClickAction()
    }

    // --- Lo que la fila le dice a quien no ve la pantalla ----------------------

    /**
     * Los tres toques significan cosas distintas y por eso se anuncian distinto.
     * Un `clickable` que dice *"Ver dónde fue"* y abre una hoja —o un ticket—
     * miente, y a quien navega con TalkBack la mentira es lo único que le queda.
     */
    @Test
    fun `cada toque anuncia lo que de verdad hace`() {
        bitacora()
        composeTestRule.onAllNodesWithTag(CONTACTO_EN_LINEA_TAG)[EL_DE_HOY]
            .assertContentDescriptionEquals(ELEGIR_QUE_ABRIR)
        composeTestRule.onAllNodesWithTag(CONTACTO_EN_LINEA_TAG)[EL_DE_AYER]
            .assertContentDescriptionEquals(VER_DONDE_FUE)
    }

    @Test
    fun `la fila sin punto que abre el ticket lo anuncia como ticket`() {
        bitacora(SIN_PUNTO)

        composeTestRule.onAllNodesWithTag(CONTACTO_EN_LINEA_TAG)[EL_DE_HOY]
            .assertContentDescriptionEquals(VER_EL_TICKET)
    }

    // --- Montaje --------------------------------------------------------------

    /**
     * Toca la fila número [indice] de la línea de contactos.
     *
     * [desplazando] en los dos detalles: ahí la línea vive muy por debajo del
     * pliegue —después del saldo, el ritmo y la liquidación— y sin llevarla a la
     * pantalla el toque no llega. La bitácora es una `LazyColumn` que arranca en
     * las filas.
     */
    private fun tocar(indice: Int, desplazando: Boolean = false) {
        val fila = composeTestRule.onAllNodesWithTag(CONTACTO_EN_LINEA_TAG)[indice]
        if (desplazando) fila.performScrollTo()
        fila.assertHasClickAction().performClick()
    }

    private fun bitacora(contactos: List<ContactoDeCobranza> = CONTACTOS) {
        composeTestRule.setContent {
            Tema {
                BitacoraContent(
                    state = BitacoraUiState(
                        cargando = false,
                        bitacora = BitacoraCompleta(
                            clienteId = PagosFixtures.CLIENTE_ID,
                            nombre = "Victoria Flores Olmedo",
                            direccion = "C. Hidalgo 214, Centro",
                            contactos = contactos,
                            hoy = PagosFixtures.HOY
                        )
                    ),
                    onAtras = {},
                    onVerUbicacion = { punto -> mapa = punto },
                    onVerTicket = { pagoId -> ticket = pagoId }
                )
            }
        }
    }

    private fun detalleCliente(contactos: List<ContactoDeCobranza> = CONTACTOS) {
        composeTestRule.setContent {
            Tema {
                DetalleClienteContent(
                    state = DetalleClienteUiState(
                        cargando = false,
                        detalle = PagosFixtures.detalleCliente().copy(
                            contactos = contactos,
                            hoy = PagosFixtures.HOY
                        )
                    ),
                    onAtras = {},
                    onAbrirVenta = {},
                    onRegistrarAbono = {},
                    onRegistrarVisita = {},
                    onVerContactos = {},
                    onAlternarTema = {},
                    onAlternarPrivacidad = {},
                    onVerUbicacionDelContacto = { punto -> mapa = punto },
                    onVerTicket = { pagoId -> ticket = pagoId }
                )
            }
        }
    }

    private fun detalleVenta(contactos: List<ContactoDeCobranza> = CONTACTOS) {
        composeTestRule.setContent {
            Tema {
                DetalleVentaContent(
                    state = DetalleVentaUiState(
                        cargando = false,
                        detalle = PagosFixtures.detalleVenta().copy(
                            contactos = contactos,
                            hoy = PagosFixtures.HOY
                        )
                    ),
                    onAtras = {},
                    onRegistrarAbono = {},
                    onRegistrarVisita = {},
                    onUsarLiquidacion = {},
                    onVerAbonos = {},
                    onVerGarantia = {},
                    onVerUbicacionDelContacto = { punto -> mapa = punto },
                    onVerTicket = { pagoId -> ticket = pagoId }
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
        /** Índices en [CONTACTOS], que va de lo más reciente a lo más viejo. */
        const val EL_DE_HOY = 0
        const val EL_DE_AYER = 1

        const val ID_DE_HOY = "abono-de-hoy"

        /**
         * Dos puntos distintos a propósito: es lo que separa "abrió el mapa" de
         * "abrió el mapa en la puerta correcta".
         */
        val PUNTO_DE_HOY = UbicacionDelCobro(lat = 18.9061, lng = -98.4376)
        val PUNTO_DE_AYER = UbicacionDelCobro(lat = 18.4712, lng = -97.4011)

        /**
         * Dos abonos de la MISMA cuenta —la del detalle de venta, para que la
         * línea los enseñe con el alcance angostado que esa pantalla trae de
         * fábrica— el de hoy y el de ayer.
         */
        val CONTACTOS = listOf(
            abono(
                id = ID_DE_HOY,
                cuando = "2026-09-01T23:00:00Z",
                donde = PUNTO_DE_HOY
            ),
            abono(
                id = "abono-de-ayer",
                cuando = "2026-08-31T18:00:00Z",
                donde = PUNTO_DE_AYER
            )
        )

        /**
         * Los mismos dos abonos, capturados **sin señal**: es el estado real de
         * un cobro tomado en un patio sin cobertura o con el permiso de
         * ubicación apagado, y de todo el histórico anterior a que se guardara
         * el punto.
         */
        val SIN_PUNTO = CONTACTOS.map { it.copy(ubicacion = null) }

        fun abono(id: String, cuando: String, donde: UbicacionDelCobro) = ContactoDeCobranza(
            id = id,
            fecha = Instant.parse(cuando),
            etiqueta = "Abono",
            nota = null,
            estado = EstadoCuenta.PAGO,
            importe = Money.of(BigDecimal("220.00")),
            tipo = TipoDeContacto.COBRO,
            cobrador = "Gabriel Roque",
            ventaId = PagosFixtures.VENTA_EN_PROMESA,
            cuenta = "Refrigerador Mabe 14'",
            ubicacion = donde
        )
    }
}
