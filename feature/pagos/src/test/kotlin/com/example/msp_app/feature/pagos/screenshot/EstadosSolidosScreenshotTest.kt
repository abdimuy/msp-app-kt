package com.example.msp_app.feature.pagos.screenshot

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import com.example.msp_app.core.common.cobranza.domain.EstadoCuenta
import com.example.msp_app.core.designsystem.theme.MspTheme
import com.example.msp_app.feature.pagos.domain.model.EstadoDelPeriodo
import com.example.msp_app.feature.pagos.ui.ListaFixtures
import com.example.msp_app.feature.pagos.ui.components.EstadoEnGrande
import com.example.msp_app.feature.pagos.ui.components.FilaDeCliente
import java.time.LocalDate
import java.time.LocalTime
import org.junit.Test
import org.robolectric.annotation.Config

/**
 * **Los ocho estados, sólidos, en los dos temas** (decisión del dueño del
 * 2026-10-16): el texto y el ícono van SIEMPRE en blanco sobre el color pleno
 * del estado — antes, en oscuro, iban en `surface` (casi negro). "Falta pasar"
 * se queda neutro, con borde.
 *
 * Dos superficies: la tarjeta de la lista (`ChipDeEstado`) y la tarjeta grande
 * del detalle de la venta (`EstadoEnGrande`). Un golden por superficie y tema,
 * con los ocho estados apilados, para que el dueño los compare de un vistazo.
 * La pantalla es alta (`h2000dp`) para que quepan las ocho tarjetas.
 */
@Config(qualifiers = "w360dp-h2000dp-xhdpi")
class EstadosSolidosScreenshotTest : PagosScreenshotTest() {

    @Test
    fun `tarjetas de la lista light`() = tarjetas(dark = false)

    @Test
    fun `tarjetas de la lista dark`() = tarjetas(dark = true)

    @Test
    fun `estado en grande light`() = enGrande(dark = false)

    @Test
    fun `estado en grande dark`() = enGrande(dark = true)

    private fun tarjetas(dark: Boolean) = capture(
        name = "pagos_estados_solidos_tarjeta_${tema(dark)}",
        dark = dark
    ) {
        Column(
            modifier = Modifier.padding(MspTheme.spacing.md),
            verticalArrangement = Arrangement.spacedBy(MspTheme.spacing.sm)
        ) {
            ochoEstados().forEachIndexed { i, estado ->
                FilaDeCliente(
                    cliente = clienteCon(i, estado),
                    hoy = ListaFixtures.HOY,
                    onAbrirCliente = {}
                )
            }
        }
    }

    private fun enGrande(dark: Boolean) = capture(
        name = "pagos_estados_solidos_grande_${tema(dark)}",
        dark = dark
    ) {
        Column(
            modifier = Modifier.padding(MspTheme.spacing.md),
            verticalArrangement = Arrangement.spacedBy(MspTheme.spacing.sm)
        ) {
            ochoEstados().forEach { EstadoEnGrande(estado = it) }
        }
    }

    private fun tema(dark: Boolean) = if (dark) "dark" else "light"

    private fun clienteCon(i: Int, estado: EstadoDelPeriodo) = ListaFixtures.cliente(
        clienteId = BASE_CLIENTE + i,
        nombre = NOMBRES[i],
        ventas = listOf(
            ListaFixtures.venta(
                ventaId = BASE_VENTA + i,
                folio = "V-${BASE_VENTA + i}",
                descripcion = "Refrigerador Mabe 11 pies",
                saldo = ListaFixtures.dinero("4800"),
                totalVenta = ListaFixtures.dinero("9600"),
                enganche = ListaFixtures.dinero("800"),
                fechaVenta = ListaFixtures.HOY.minusMonths(MESES_DE_LA_VENTA),
                estado = estado
            )
        )
    )

    /** En el orden de `TratoDelEstado`: un estado por trato, los ocho. */
    private fun ochoEstados(): List<EstadoDelPeriodo> = listOf(
        ListaFixtures.estado(EstadoCuenta.PAGO, abonoDelPeriodo = ListaFixtures.dinero("350")),
        ListaFixtures.estado(
            EstadoCuenta.ABONO_PARCIAL,
            abonoDelPeriodo = ListaFixtures.dinero("200")
        ),
        ListaFixtures.estado(EstadoCuenta.VISITE_VUELVO),
        ListaFixtures.estado(
            EstadoCuenta.PROMETIO_PROXIMA,
            fechaPromesa = LocalDate.of(2026, 10, 22),
            montoPrometido = ListaFixtures.dinero("600")
        ),
        ListaFixtures.estado(EstadoCuenta.SE_NEGO),
        ListaFixtures.estado(EstadoCuenta.CITA_A_UNA_HORA).copy(
            fechaCita = LocalDate.of(2026, 10, 17),
            horaCita = LocalTime.of(16, 30)
        ),
        ListaFixtures.estado(EstadoCuenta.NO_ESTABA),
        ListaFixtures.estado(EstadoCuenta.SIN_TOCAR)
    )

    private companion object {
        const val BASE_CLIENTE = 9500
        const val BASE_VENTA = 95000
        const val MESES_DE_LA_VENTA = 4L
        val NOMBRES = listOf(
            "Rosa María Sánchez Flores",
            "José Luis Hernández Cruz",
            "Ma. Guadalupe Ortiz Ramos",
            "Gabriela Oropeza Juárez",
            "Juan Carlos Méndez Vega",
            "Leticia Romero Aguilar",
            "Fernando Castillo Pérez",
            "Teresa Domínguez Luna"
        )
    }
}
