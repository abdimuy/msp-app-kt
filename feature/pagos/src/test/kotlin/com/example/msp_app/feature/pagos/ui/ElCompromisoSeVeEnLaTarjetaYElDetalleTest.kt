package com.example.msp_app.feature.pagos.ui

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import com.example.msp_app.core.common.cobranza.domain.EstadoCuenta
import com.example.msp_app.core.designsystem.component.MASKED_MONEY
import com.example.msp_app.core.designsystem.theme.MspTheme
import com.example.msp_app.core.testing.RobolectricTestBase
import com.example.msp_app.feature.pagos.domain.model.ClienteEnLista
import com.example.msp_app.feature.pagos.domain.model.CompromisoDeLaVenta
import com.example.msp_app.feature.pagos.domain.model.EstadoDeCompromiso
import com.example.msp_app.feature.pagos.domain.model.EstadoDelPeriodo
import com.example.msp_app.feature.pagos.domain.model.TipoDeCompromiso
import com.example.msp_app.feature.pagos.ui.components.CHIP_DE_PRIMER_ABONO_TAG
import com.example.msp_app.feature.pagos.ui.components.COMPROMISO_DE_LA_VENTA_TAG
import com.example.msp_app.feature.pagos.ui.components.ETIQUETA_DE_ESTADO_TAG
import com.example.msp_app.feature.pagos.ui.components.FilaDeCliente
import com.example.msp_app.feature.pagos.ui.components.RENGLON_DE_COMPROMISO_TAG
import java.time.LocalTime
import org.junit.Rule
import org.junit.Test
import org.robolectric.annotation.Config

/**
 * El compromiso llega a la pantalla (plan visitas P2, Tarea 20; spec §5.7 B,
 * C, E; E-APP-115): la tarjeta de la lista lo pinta junto al chip de dinero y
 * junto a "Primer abono", y el detalle de la venta tiene su renglón. Las
 * pruebas puras de [presentacionDe] no ven el cableado; éstas sí.
 */
@Config(qualifiers = "w360dp-h2400dp-xhdpi")
class ElCompromisoSeVeEnLaTarjetaYElDetalleTest : RobolectricTestBase() {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val hoy = ListaFixtures.HOY // martes 1-sep-2026

    private val citaSinHora = CompromisoDeLaVenta(
        tipo = TipoDeCompromiso.CITA,
        estado = EstadoDeCompromiso.VIGENTE_FUTURA,
        dia = hoy.plusDays(3)
    )

    private val promesa = CompromisoDeLaVenta(
        tipo = TipoDeCompromiso.PROMESA,
        estado = EstadoDeCompromiso.VIGENTE_FUTURA,
        dia = hoy.plusDays(3),
        montoCentavos = 50_000
    )

    private fun ClienteEnLista.conCompromiso(c: CompromisoDeLaVenta?) =
        copy(ventas = ventas.map { it.copy(venta = it.venta.copy(compromiso = c)) })

    /** Pone el chip de la semana de cada venta del cliente en [estado]. */
    private fun ClienteEnLista.conEstado(estado: EstadoDelPeriodo) =
        copy(ventas = ventas.map { it.copy(venta = it.venta.copy(estado = estado)) })

    /** Abonó parcial esta semana: el chip DE DINERO del que habla B. */
    private val abonoParcial = ListaFixtures.estado(
        EstadoCuenta.ABONO_PARCIAL,
        abonoDelPeriodo = ListaFixtures.dinero("100")
    )

    private fun fila(cliente: ClienteEnLista, ocultos: Boolean = false) {
        composeTestRule.setContent {
            MspTheme(darkTheme = false, animateColors = false) {
                FilaDeCliente(
                    cliente = cliente,
                    hoy = hoy,
                    montosOcultos = ocultos,
                    onAbrirCliente = {}
                )
            }
        }
    }

    /** B: Guadalupe abonó parcial esta semana; el chip de dinero sigue y la cita se ve con él. */
    @Test
    fun chipDeDineroYCompromisoJuntos() {
        fila(ListaFixtures.guadalupe().conEstado(abonoParcial).conCompromiso(citaSinHora))

        composeTestRule.onAllNodesWithTag(
            ETIQUETA_DE_ESTADO_TAG,
            useUnmergedTree = true
        ).assertCountEquals(1)
        composeTestRule.onNodeWithTag(COMPROMISO_DE_LA_VENTA_TAG, useUnmergedTree = true)
            .assertTextEquals("Cita · Vie 4 sept, sin hora")
    }

    /** C: una venta sin abonos conserva "Primer abono" y además pinta la promesa. */
    @Test
    fun ventaNuevaMuestraCompromiso() {
        fila(ListaFixtures.conVentaNueva().conCompromiso(promesa))

        composeTestRule.onAllNodesWithTag(
            CHIP_DE_PRIMER_ABONO_TAG,
            useUnmergedTree = true
        ).assertCountEquals(1)
        composeTestRule.onNodeWithTag(COMPROMISO_DE_LA_VENTA_TAG, useUnmergedTree = true)
            .assertTextEquals("Promesa · Vie 4 sept, $500")
    }

    @Test
    fun enLaTarjetaElOjoTapaElMontoPrometido() {
        fila(ListaFixtures.conVentaNueva().conCompromiso(promesa), ocultos = true)

        composeTestRule.onNodeWithTag(COMPROMISO_DE_LA_VENTA_TAG, useUnmergedTree = true)
            .assertTextEquals("Promesa · Vie 4 sept, $MASKED_MONEY")
    }

    /**
     * rev-t20: cuando el chip de la semana YA es esta promesa ("Prometió $500
     * el 4 sept"), la tarjeta no la repite debajo. Si se duplica, esta prueba
     * se pone roja.
     */
    @Test
    fun laTarjetaNoDuplicaLaPromesaDelChip() {
        val prometio = ListaFixtures.estado(
            EstadoCuenta.PROMETIO_PROXIMA,
            fechaPromesa = promesa.dia,
            montoPrometido = ListaFixtures.dinero("500")
        )
        fila(ListaFixtures.guadalupe().conEstado(prometio).conCompromiso(promesa))

        composeTestRule.onAllNodesWithText("Prometió", substring = true, useUnmergedTree = true)
            .assertCountEquals(1)
        composeTestRule.onAllNodesWithTag(COMPROMISO_DE_LA_VENTA_TAG, useUnmergedTree = true)
            .assertCountEquals(0)
    }

    /** Control: sin compromiso la tarjeta no reserva nada. */
    @Test
    fun sinCompromisoLaTarjetaNoPintaNada() {
        fila(ListaFixtures.guadalupe())

        composeTestRule.onAllNodesWithTag(
            ETIQUETA_DE_ESTADO_TAG,
            useUnmergedTree = true
        ).assertCountEquals(1)
        composeTestRule.onAllNodesWithTag(
            COMPROMISO_DE_LA_VENTA_TAG,
            useUnmergedTree = true
        ).assertCountEquals(0)
    }

    private fun detalle(c: CompromisoDeLaVenta?, estado: EstadoDelPeriodo = abonoParcial) {
        composeTestRule.setContent {
            MspTheme(darkTheme = false, animateColors = false) {
                DetalleVentaContent(
                    state = DetalleVentaUiState(
                        cargando = false,
                        detalle = PagosFixtures.detalleVenta().copy(estado = estado, compromiso = c)
                    ),
                    onAtras = {},
                    onRegistrarAbono = {},
                    onRegistrarVisita = {},
                    onUsarLiquidacion = {},
                    onVerAbonos = {},
                    onVerGarantia = {}
                )
            }
        }
    }

    /** E: el detalle de la venta tiene el renglón con día y hora. */
    @Test
    fun detalleTieneRenglonDeCompromiso() {
        detalle(citaSinHora.copy(hora = LocalTime.of(16, 30)))

        composeTestRule.onNodeWithTag(RENGLON_DE_COMPROMISO_TAG)
            .assertTextEquals("Cita", "Vie 4 sept, 16:30")
    }

    /** rev-t20: el estado en grande ya dice "Prometió $500 el 4 sept": no se repite abajo. */
    @Test
    fun detalleNoDuplicaLaPromesaDelEstado() {
        val prometio = ListaFixtures.estado(
            EstadoCuenta.PROMETIO_PROXIMA,
            fechaPromesa = promesa.dia,
            montoPrometido = ListaFixtures.dinero("500")
        )
        detalle(promesa, prometio)

        composeTestRule.onAllNodesWithTag(RENGLON_DE_COMPROMISO_TAG).assertCountEquals(0)
    }

    @Test
    fun detalleSinCompromisoNoTieneRenglon() {
        detalle(null)

        composeTestRule.onAllNodesWithTag(
            RENGLON_DE_COMPROMISO_TAG,
            useUnmergedTree = true
        ).assertCountEquals(0)
    }

    @Test
    fun detalleNoPintaLaCerradaSinCumplir() {
        detalle(promesa.copy(estado = EstadoDeCompromiso.CERRADA_SIN_CUMPLIR))

        composeTestRule.onAllNodesWithTag(
            RENGLON_DE_COMPROMISO_TAG,
            useUnmergedTree = true
        ).assertCountEquals(0)
    }
}
