package com.example.msp_app.feature.pagos.screenshot

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import com.example.msp_app.core.common.cobranza.domain.EstadoCuenta
import com.example.msp_app.core.designsystem.theme.MspTheme
import com.example.msp_app.feature.pagos.ui.ListaFixtures
import com.example.msp_app.feature.pagos.ui.components.ChipDeEstado
import java.time.LocalDate
import java.time.LocalTime
import org.junit.Test
import org.robolectric.annotation.Config

/**
 * **Sólo los ocho badges de la tarjeta**, en un lienzo chico. Existe porque en
 * el golden de la tarjeta entera ([EstadosSolidosScreenshotTest]) el texto del
 * badge es menos del 1 % de los píxeles: con el umbral del proyecto
 * (`RoborazziConfig.CHANGE_THRESHOLD`) un cambio de color del texto pasaría el
 * `verify` sin que nadie lo viera. Aquí el badge es casi todo el lienzo, así que
 * el blanco en oscuro (decisión del dueño del 2026-10-16) sí se compara.
 */
@Config(qualifiers = "w220dp-h400dp-xhdpi")
class ChipsSolidosScreenshotTest : PagosScreenshotTest() {

    @Test
    fun `chips solidos light`() = chips(dark = false)

    @Test
    fun `chips solidos dark`() = chips(dark = true)

    private fun chips(dark: Boolean) = capture(
        name = "pagos_estados_solidos_chip_${if (dark) "dark" else "light"}",
        dark = dark
    ) {
        Column(
            modifier = Modifier.padding(MspTheme.spacing.sm),
            verticalArrangement = Arrangement.spacedBy(MspTheme.spacing.xs)
        ) {
            listOf(
                ListaFixtures.estado(
                    EstadoCuenta.PAGO,
                    abonoDelPeriodo = ListaFixtures.dinero("350")
                ),
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
            ).forEach { ChipDeEstado(estado = it) }
        }
    }
}
