package com.example.msp_app.feature.pagos.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.example.msp_app.core.common.cobranza.domain.EstadoCuenta
import com.example.msp_app.core.common.money.Money
import com.example.msp_app.core.designsystem.theme.MspTheme
import com.example.msp_app.core.testing.RobolectricTestBase
import com.example.msp_app.feature.pagos.domain.BitacoraDelCliente
import com.example.msp_app.feature.pagos.domain.model.ContactoDeCobranza
import com.example.msp_app.feature.pagos.domain.model.DetalleVenta
import com.example.msp_app.feature.pagos.domain.model.TipoDeContacto
import com.example.msp_app.feature.pagos.ui.components.COBRADO_DEL_GRUPO_TAG
import com.example.msp_app.feature.pagos.ui.components.CONTACTO_EN_LINEA_TAG
import java.math.BigDecimal
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.robolectric.annotation.Config

/**
 * **La vista previa de "lo que ha pasado" del detalle de venta: sólo la propia
 * cuenta, sólo los cinco más recientes, sin subtotal.**
 *
 * Tres garantías del mismo arreglo — la línea dejó de mezclar TODAS las cuentas
 * del cliente y de traer pastillas de alcance/filtro (decisión del dueño):
 *
 * 1. Nunca pinta más de
 *    [BitacoraDelCliente.VISIBLES_EN_LA_VENTA] filas, y ninguna es de otra
 *    cuenta del mismo cliente — aunque la venta tenga más y el cliente tenga
 *    contactos ajenos.
 * 2. Nunca pinta subtotal de mes ([COBRADO_DEL_GRUPO_TAG]): es una MUESTRA, y
 *    un subtotal sobre una muestra es una cifra falsa.
 * 3. "Ver los N contactos" cuenta sólo los de esta cuenta (no los cinco que se
 *    alcanzan a ver) y llama a `onVerContactos`; "Ver los N abonos" sigue
 *    llamando a `onVerAbonos`, aparte.
 */
@Config(qualifiers = "w360dp-h800dp-xhdpi")
class LaLineaDeLaVentaSoloPintaCincoDeSuCuentaTest : RobolectricTestBase() {

    @get:Rule
    val composeTestRule = createComposeRule()

    private var contactosVistos: Boolean = false
    private var abonosVistos: Boolean = false

    /**
     * **La garantía principal, con su control positivo integrado.** Siete
     * contactos PROPIOS y dos AJENOS (de otra venta del mismo cliente): si el
     * recorte o el filtro por cuenta se rompiera, aparecerían más de cinco filas
     * o el texto de la cuenta ajena. Afirmar exactamente
     * [BitacoraDelCliente.VISIBLES_EN_LA_VENTA] —y no sólo "más de cero"— es lo
     * que impide que un `testTag` mal escrito (que diera cero en los dos casos)
     * pase por error: cero no es cinco, así que un tag roto se ve tan rojo como
     * un recorte roto.
     */
    @Test
    fun `pinta como maximo 5 contactos, todos de la propia cuenta`() {
        montar(detalleConMuchosContactos())

        val filas = composeTestRule.onAllNodesWithTag(CONTACTO_EN_LINEA_TAG).fetchSemanticsNodes()
        assertEquals(
            "la vista previa tiene que topar en VISIBLES_EN_LA_VENTA aunque la cuenta tenga más",
            BitacoraDelCliente.VISIBLES_EN_LA_VENTA,
            filas.size
        )
        assertEquals(
            "una fila mostró el texto de la cuenta AJENA: la línea mezcló otra cuenta del cliente",
            0,
            composeTestRule.onAllNodesWithText(CUENTA_AJENA).fetchSemanticsNodes().size
        )
    }

    /**
     * **Control positivo del de arriba.** Con menos de cinco contactos propios
     * (y ninguno ajeno) se pintan TODOS, no cero. Sin esto la aserción de arriba
     * no probaría que la lista pinta algo — sólo que no pinta seis.
     */
    @Test
    fun `control positivo - con menos de 5 contactos propios pinta todos`() {
        val base = PagosFixtures.detalleVenta()
        val dos = listOf(
            contacto("propio-1", Instant.parse("2026-08-20T17:00:00Z"), base.ventaId),
            contacto("propio-2", Instant.parse("2026-08-10T17:00:00Z"), base.ventaId)
        )
        montar(base.copy(contactos = dos))

        val filas = composeTestRule.onAllNodesWithTag(CONTACTO_EN_LINEA_TAG).fetchSemanticsNodes()
        assertEquals(2, filas.size)
    }

    /** La muestra NUNCA reporta subtotal, ni con varios cobros adentro. */
    @Test
    fun `no pinta subtotal de mes, aunque haya cobros`() {
        montar(detalleConMuchosContactos())

        assertEquals(
            "la vista previa pintó un subtotal: es una muestra, no la historia completa",
            0,
            composeTestRule.onAllNodesWithTag(COBRADO_DEL_GRUPO_TAG).fetchSemanticsNodes().size
        )
    }

    /**
     * **"Ver los N contactos" cuenta sólo los propios y llama a
     * `onVerContactos`.** Con siete propios y dos ajenos, "N" tiene que decir
     * 7 —el total de la CUENTA, no los cinco visibles ni los nueve del cliente
     * entero— y el enlace tiene que existir con ESE texto exacto para poder
     * tocarlo: si "N" contara mal, la búsqueda por texto no lo encontraría y
     * el `performClick` de abajo reventaría antes de llegar al assert.
     */
    @Test
    fun `ver los N contactos cuenta solo los propios y llama a onVerContactos`() {
        montar(detalleConMuchosContactos())

        composeTestRule.onNodeWithText("Ver los 7 contactos").performClick()

        assertTrue("el enlace no llamó a onVerContactos", contactosVistos)
    }

    /** "Ver los N abonos" sigue siendo el enlace legado, aparte, y con lo suyo. */
    @Test
    fun `ver los N abonos sigue llamando a onVerAbonos`() {
        montar(detalleConMuchosContactos())

        val detalle = detalleConMuchosContactos()
        composeTestRule.onNodeWithText("Ver los ${detalle.historial.totalPagos} abonos")
            .performClick()

        assertTrue(abonosVistos)
    }

    // --- Montaje --------------------------------------------------------------

    private fun montar(detalle: DetalleVenta) {
        contactosVistos = false
        abonosVistos = false
        composeTestRule.setContent {
            MspTheme(darkTheme = false, animateColors = false) {
                Seccion(detalle)
            }
        }
    }

    @Composable
    private fun Seccion(detalle: DetalleVenta) {
        Column {
            LineaDeLaVenta(
                detalle = detalle,
                onVerAbonos = { abonosVistos = true },
                onVerContactos = { contactosVistos = true }
            )
        }
    }

    private companion object {
        /** Otra cuenta del mismo cliente — su nombre no puede aparecer aquí. */
        const val CUENTA_AJENA = "Bocina profesional 8'' audiobahn"

        fun dinero(pesos: String) = Money.of(BigDecimal(pesos))

        fun contacto(
            id: String,
            fecha: Instant,
            ventaId: Int?,
            cuenta: String? = null
        ): ContactoDeCobranza = ContactoDeCobranza(
            id = id,
            fecha = fecha,
            etiqueta = "Abono",
            nota = null,
            estado = EstadoCuenta.PAGO,
            importe = dinero("100"),
            tipo = TipoDeContacto.COBRO,
            cobrador = "Marisol Vega",
            ventaId = ventaId,
            cuenta = cuenta
        )

        /**
         * Siete contactos de la venta de la fixture y dos de OTRA venta del
         * mismo cliente — el caso que decide si el filtro por cuenta es real o
         * de adorno.
         */
        fun detalleConMuchosContactos(): DetalleVenta {
            val base = PagosFixtures.detalleVenta()
            val propios = (1..7).map { i ->
                contacto(
                    id = "propio-$i",
                    fecha = Instant.parse("2026-08-${20 + i}T17:00:00Z"),
                    ventaId = base.ventaId
                )
            }
            val ajenos = listOf(
                contacto(
                    id = "ajeno-1",
                    fecha = Instant.parse("2026-08-30T17:00:00Z"),
                    ventaId = OTRA_VENTA,
                    cuenta = CUENTA_AJENA
                ),
                contacto(
                    id = "ajeno-2",
                    fecha = Instant.parse("2026-08-29T17:00:00Z"),
                    ventaId = OTRA_VENTA,
                    cuenta = CUENTA_AJENA
                )
            )
            return base.copy(contactos = (propios + ajenos).sortedByDescending { it.fecha })
        }

        const val OTRA_VENTA = 14_431_255
    }
}
