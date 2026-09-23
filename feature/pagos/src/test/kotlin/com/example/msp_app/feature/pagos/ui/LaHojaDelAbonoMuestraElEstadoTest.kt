package com.example.msp_app.feature.pagos.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.example.msp_app.core.common.cobranza.domain.EstadoCuenta
import com.example.msp_app.core.common.money.Money
import com.example.msp_app.core.designsystem.theme.MspTheme
import com.example.msp_app.core.testing.RobolectricTestBase
import com.example.msp_app.feature.pagos.domain.CuentaDelAbono
import com.example.msp_app.feature.pagos.domain.model.EstadoDelPeriodo
import com.example.msp_app.feature.pagos.domain.model.VentaDelCliente
import com.example.msp_app.feature.pagos.ui.components.HojaDeAbono
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalTime
import org.junit.Rule
import org.junit.Test
import org.robolectric.annotation.Config

/**
 * **La hoja "¿A cuál cuenta?" ya no solo dice el nombre: dice qué pasó ahí.**
 *
 * El pedido del dueño, textual: poder diferenciar de un vistazo "esta ya pagó
 * esta semana" de "en ésta no estaba" de "ésta tiene cita el jueves" — hoy cada
 * opción solo traía descripción, abonos y parcialidad, y dos cuentas en estados
 * opuestos se veían idénticas salvo por el monto.
 *
 * El dato no se deriva aquí: [EstadoDelPeriodo] ya lo trae y `OpcionDeCuenta`
 * —privado en `HojaDeAbono.kt`— solo lo pinta con la misma pieza (`ChipDeEstado`)
 * que ya usan el detalle de cliente y el de venta, para que el mismo estado no
 * se vea distinto en dos pantallas.
 */
@Config(qualifiers = "w360dp-h800dp-xhdpi")
class LaHojaDelAbonoMuestraElEstadoTest : RobolectricTestBase() {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun `una cuenta que ya pago esta semana muestra su estado en la hoja`() {
        montar(PagosFixtures.detalleCliente().ventas)

        composeTestRule.onNodeWithText("Pagó esta semana").assertIsDisplayed()
    }

    @Test
    fun `una cuenta con cita muestra el dia y la hora acordados`() {
        montar(PagosFixtures.detalleCliente(estadoDeLaSegunda = estadoConCita()).ventas)

        composeTestRule.onNodeWithText("Cita 24 sept 16:30").assertIsDisplayed()
    }

    /**
     * El punto entero de lo que pidió el dueño: poder diferenciar cuentas por su
     * estado de un vistazo. Los DOS textos se afirman a la vez — afirmar uno
     * solo pasaría igual si la hoja pintara nada más el primero.
     */
    @Test
    fun `dos cuentas con estados distintos se ven distintas en la misma hoja`() {
        montar(PagosFixtures.detalleCliente().ventas)

        composeTestRule.onNodeWithText("Pagó esta semana").assertIsDisplayed()
        composeTestRule.onNodeWithText("Prometió \$220 el 15 sept").assertIsDisplayed()
    }

    private fun estadoConCita(): EstadoDelPeriodo = EstadoDelPeriodo(
        estado = EstadoCuenta.CITA_A_UNA_HORA,
        abonoDelPeriodo = Money.ZERO,
        parcialidad = Money.of(BigDecimal("220")),
        fechaCita = LocalDate.of(2026, 9, 24),
        horaCita = LocalTime.of(16, 30)
    )

    private fun montar(cuentas: List<VentaDelCliente>) {
        val cobrables = CuentaDelAbono.cobrables(cuentas)
        composeTestRule.setContent {
            MspTheme(darkTheme = false, animateColors = false) {
                Box(modifier = Modifier.fillMaxSize()) {
                    HojaDeAbono(
                        cuentas = cobrables,
                        elegida = cobrables.first().ventaId,
                        onElegir = {},
                        onContinuar = {},
                        onCerrar = {}
                    )
                }
            }
        }
    }
}
