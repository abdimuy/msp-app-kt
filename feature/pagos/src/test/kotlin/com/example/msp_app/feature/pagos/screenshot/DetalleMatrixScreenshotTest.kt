package com.example.msp_app.feature.pagos.screenshot

import androidx.compose.runtime.Composable
import com.example.msp_app.core.designsystem.theme.FontSizeLevel
import com.example.msp_app.feature.pagos.ui.DetalleClienteContent
import com.example.msp_app.feature.pagos.ui.DetalleClienteUiState
import com.example.msp_app.feature.pagos.ui.DetalleVentaContent
import com.example.msp_app.feature.pagos.ui.DetalleVentaUiState
import com.example.msp_app.feature.pagos.ui.PagosFixtures
import org.junit.Test

/**
 * La matriz completa de las dos pantallas de detalle:
 * `{cliente, venta} × {light, dark} × {NORMAL 1.0, GRANDE 1.5, MUY_GRANDE 2.0}`
 * = 12 goldens, más 4 del estado que esta tarea existe para proteger — la
 * **promesa SIN fecha**, que no puede verse como diferida.
 *
 * Las tres escalas son las tres reales de `FontSizeLevel`; el `1.3` de
 * `CollectionReportMatrixScreenshotTest` no corresponde a ningún nivel y no se
 * replica (ver el KDoc de [PagosScreenshotTest]).
 */
class DetalleMatrixScreenshotTest : PagosScreenshotTest() {

    @Test
    fun `cliente light normal`() = cliente(dark = false, nivel = FontSizeLevel.NORMAL)

    @Test
    fun `cliente light grande`() = cliente(dark = false, nivel = FontSizeLevel.GRANDE)

    @Test
    fun `cliente light muy grande`() = cliente(dark = false, nivel = FontSizeLevel.MUY_GRANDE)

    @Test
    fun `cliente dark normal`() = cliente(dark = true, nivel = FontSizeLevel.NORMAL)

    @Test
    fun `cliente dark grande`() = cliente(dark = true, nivel = FontSizeLevel.GRANDE)

    @Test
    fun `cliente dark muy grande`() = cliente(dark = true, nivel = FontSizeLevel.MUY_GRANDE)

    @Test
    fun `venta light normal`() = venta(dark = false, nivel = FontSizeLevel.NORMAL)

    @Test
    fun `venta light grande`() = venta(dark = false, nivel = FontSizeLevel.GRANDE)

    @Test
    fun `venta light muy grande`() = venta(dark = false, nivel = FontSizeLevel.MUY_GRANDE)

    @Test
    fun `venta dark normal`() = venta(dark = true, nivel = FontSizeLevel.NORMAL)

    @Test
    fun `venta dark grande`() = venta(dark = true, nivel = FontSizeLevel.GRANDE)

    @Test
    fun `venta dark muy grande`() = venta(dark = true, nivel = FontSizeLevel.MUY_GRANDE)

    // --- El estado que esta tarea protege: promesa SIN fecha -------------------------------

    @Test
    fun `cliente promesa sin fecha light`() = clientePromesaSinFecha(dark = false)

    @Test
    fun `cliente promesa sin fecha dark`() = clientePromesaSinFecha(dark = true)

    @Test
    fun `venta promesa sin fecha light`() = ventaPromesaSinFecha(dark = false)

    @Test
    fun `venta promesa sin fecha dark`() = ventaPromesaSinFecha(dark = true)

    private fun cliente(dark: Boolean, nivel: FontSizeLevel) = capture(
        name = "pagos_cliente_${tema(dark)}_${sufijoDe(nivel)}",
        dark = dark,
        nivel = nivel
    ) {
        Cliente(PagosFixtures.detalleCliente())
    }

    private fun venta(dark: Boolean, nivel: FontSizeLevel) = capture(
        name = "pagos_venta_${tema(dark)}_${sufijoDe(nivel)}",
        dark = dark,
        nivel = nivel
    ) {
        Venta(PagosFixtures.detalleVenta())
    }

    private fun clientePromesaSinFecha(dark: Boolean) = capture(
        name = "pagos_cliente_promesa_sin_fecha_${tema(dark)}",
        dark = dark
    ) {
        Cliente(PagosFixtures.detalleCliente(PagosFixtures.estadoPromesaSinFecha()))
    }

    private fun ventaPromesaSinFecha(dark: Boolean) = capture(
        name = "pagos_venta_promesa_sin_fecha_${tema(dark)}",
        dark = dark
    ) {
        Venta(PagosFixtures.detalleVenta(PagosFixtures.estadoPromesaSinFecha()))
    }

    private fun tema(dark: Boolean) = if (dark) "dark" else "light"
}

@Composable
private fun Cliente(detalle: com.example.msp_app.feature.pagos.domain.model.DetalleCliente) {
    DetalleClienteContent(
        state = DetalleClienteUiState(cargando = false, detalle = detalle),
        onAtras = {},
        onAbrirVenta = {},
        onRegistrarAbono = {},
        onRegistrarVisita = {},
        onMasAcciones = {},
        onUsarLiquidacion = {},
        onVerContactos = {}
    )
}

@Composable
private fun Venta(detalle: com.example.msp_app.feature.pagos.domain.model.DetalleVenta) {
    DetalleVentaContent(
        state = DetalleVentaUiState(cargando = false, detalle = detalle),
        onAtras = {},
        onRegistrarAbono = {},
        onRegistrarVisita = {},
        onMasAcciones = {},
        onUsarLiquidacion = {},
        onVerAbonos = {},
        onVerGarantia = {}
    )
}
