package com.example.msp_app.feature.pagos.screenshot

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.example.msp_app.core.designsystem.theme.MspTheme
import com.example.msp_app.feature.pagos.domain.DiaDeCobroDeLaVenta
import com.example.msp_app.feature.pagos.domain.franjaDeCobro
import com.example.msp_app.feature.pagos.ui.CuerpoDeLosDatosDeLaVenta
import com.example.msp_app.feature.pagos.ui.DetalleVentaContent
import com.example.msp_app.feature.pagos.ui.DetalleVentaUiState
import com.example.msp_app.feature.pagos.ui.DiaDeCobroFixtures
import com.example.msp_app.feature.pagos.ui.components.FranjaDiaDeCobro
import org.junit.Test

/**
 * La franja "Día de cobro" en **cada estado de la tabla del spec §7**, en claro y
 * en oscuro, aislada (para ver cada estado sin ruido); la hoja con el grupo "El
 * cobro"; y la pantalla entera con la franja en su lugar.
 */
class DiaDeCobroScreenshotTest : PagosScreenshotTest() {

    @Test fun `franja coincide light`() = franja("coincide", DiaDeCobroFixtures.coincide(), false)

    @Test fun `franja coincide dark`() = franja("coincide", DiaDeCobroFixtures.coincide(), true)

    @Test fun `franja distinto light`() = franja("distinto", DiaDeCobroFixtures.distinto(), false)

    @Test fun `franja distinto dark`() = franja("distinto", DiaDeCobroFixtures.distinto(), true)

    @Test fun `franja solo acordado light`() =
        franja("solo_acordado", DiaDeCobroFixtures.soloAcordado(), false)

    @Test fun `franja solo acordado dark`() =
        franja("solo_acordado", DiaDeCobroFixtures.soloAcordado(), true)

    @Test fun `franja solo estimacion light`() =
        franja("solo_estimacion", DiaDeCobroFixtures.soloEstimacion(), false)

    @Test fun `franja solo estimacion dark`() =
        franja("solo_estimacion", DiaDeCobroFixtures.soloEstimacion(), true)

    @Test fun `franja empate con acordado light`() =
        franja("empate_con_acordado", DiaDeCobroFixtures.empateConAcordado(), false)

    @Test fun `franja empate con acordado dark`() =
        franja("empate_con_acordado", DiaDeCobroFixtures.empateConAcordado(), true)

    @Test fun `franja empate sin acordado light`() =
        franja("empate_sin_acordado", DiaDeCobroFixtures.empateSinAcordado(), false)

    @Test fun `franja empate sin acordado dark`() =
        franja("empate_sin_acordado", DiaDeCobroFixtures.empateSinAcordado(), true)

    @Test fun `franja mensual light`() = franja("mensual", DiaDeCobroFixtures.mensual(), false)

    @Test fun `franja mensual dark`() = franja("mensual", DiaDeCobroFixtures.mensual(), true)

    @Test fun `hoja con el grupo El cobro light`() = hoja(false)

    @Test fun `hoja con el grupo El cobro dark`() = hoja(true)

    @Test fun `detalle con la franja light`() = detalle(false)

    @Test fun `detalle con la franja dark`() = detalle(true)

    private fun franja(nombre: String, dia: DiaDeCobroDeLaVenta, dark: Boolean) = capture(
        name = "pagos_dia_de_cobro_${nombre}_${tema(dark)}",
        dark = dark
    ) {
        Box(Modifier.padding(MspTheme.spacing.md)) {
            FranjaDiaDeCobro(franjaDeCobro(dia)!!)
        }
    }

    private fun hoja(dark: Boolean) = capture(
        name = "pagos_dia_de_cobro_hoja_${tema(dark)}",
        dark = dark
    ) {
        CuerpoDeLosDatosDeLaVenta(
            detalle = ventaDelMock().copy(diaDeCobro = DiaDeCobroFixtures.distinto()),
            ocultos = false
        )
    }

    private fun detalle(dark: Boolean) = capture(
        name = "pagos_dia_de_cobro_detalle_${tema(dark)}",
        dark = dark
    ) {
        Venta(ventaDelMock().copy(diaDeCobro = DiaDeCobroFixtures.distinto()))
    }

    private fun tema(dark: Boolean) = if (dark) "dark" else "light"

    @Composable
    private fun Venta(detalle: com.example.msp_app.feature.pagos.domain.model.DetalleVenta) {
        DetalleVentaContent(
            state = DetalleVentaUiState(cargando = false, detalle = detalle),
            onAtras = {},
            onRegistrarAbono = {},
            onRegistrarVisita = {},
            onUsarLiquidacion = {},
            onVerAbonos = {},
            onVerGarantia = {}
        )
    }
}
