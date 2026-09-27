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

    // --- El cuadro de ubicación: sus DOS estados ------------------------------------------

    /**
     * Sin punto medido: el respaldo dibujado, sin pin y sin la pastilla.
     *
     * Es la mitad que los `pagos_cliente_*` no fotografían, porque su fixture SÍ
     * trae `ultimoCobroAqui`. Los dos estados existen en la app y los dos tienen
     * que verse: uno dice "aquí se cobró", el otro dice "esta puerta no se midió".
     *
     * El estado con punto se fotografía con el MISMO respaldo, no con el mapa: un
     * mapa real trae red y bitmaps, y ninguno entra a `captureRoboImage`. Lo que
     * cambia entre los dos goldens es la pastilla, que es lo que este módulo
     * dibuja encima del suelo. El suelo de verdad lo cablea `:app`.
     */
    @Test
    fun `cliente sin punto medido light`() = clienteSinPunto(dark = false)

    @Test
    fun `cliente sin punto medido dark`() = clienteSinPunto(dark = true)

    private fun clienteSinPunto(dark: Boolean) = capture(
        name = "pagos_cliente_sin_punto_${tema(dark)}",
        dark = dark
    ) {
        // Sin punto es sin punto en toda la puerta, y por eso también se le
        // quita el pin al contacto del abono: `ultimoCobroAqui` sale del abono
        // MÁS RECIENTE que traiga coordenadas
        // (`CargarDetalleCliente.kt`), así que un cliente con ese campo en `null`
        // no puede tener un contacto de abono con punto. Dejárselo sembraría un
        // estado que el caso de uso no puede producir — que es cómo un fixture
        // esconde un defecto en vez de destaparlo.
        //
        // (Una VISITA con punto sí convive con `ultimoCobroAqui` nulo: las visitas
        // no alimentan ese campo. La fixture no tiene ninguna, así que aquí el
        // único pin en juego es el del abono.)
        val detalle = PagosFixtures.detalleCliente()
        Cliente(detalle.copy(ultimoCobroAqui = null))
    }

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
        Venta(ventaDelMock())
    }

    // --- El rediseño aprobado: `detalle-de-venta-final.html` -------------------

    /** (e) del mock: el ojo cerrado tapa toda cantidad y el CTA queda en "Abonar". */
    @Test
    fun `venta montos ocultos light`() = capture(name = "pagos_venta_ocultos_light") {
        Venta(ventaDelMock(), ocultos = true)
    }

    @Test
    fun `venta montos ocultos dark`() = capture(name = "pagos_venta_ocultos_dark", dark = true) {
        Venta(ventaDelMock(), ocultos = true)
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
        Venta(ventaDelMock().copy(estado = PagosFixtures.estadoPromesaSinFecha()))
    }

    // La tarjeta "Últimos contactos" (`pagos_hoja_contactos_*`) se quitó: el
    // detalle de cliente ya no pinta esa sección (decisión del dueño). Los PNG
    // que sólo esta tarjeta producía se borran junto con esta clase — no queda
    // ningún llamador que los regrabe.

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
        onAlternarTema = {},
        onAlternarPrivacidad = {}
    )
}

/**
 * La venta con las MISMAS cifras del mock aprobado (`detalle-de-venta-final.html`),
 * para poder comparar golden y mock lado a lado sin que los números distraigan.
 */
internal fun ventaDelMock(): com.example.msp_app.feature.pagos.domain.model.DetalleVenta {
    fun pesos(p: String) = com.example.msp_app.core.common.money.Money.of(java.math.BigDecimal(p))
    return PagosFixtures.detalleVenta().copy(
        totalVenta = pesos("4400"),
        abonado = pesos("2950"),
        precioContado = pesos("3600"),
        enganche = pesos("400"),
        montoACortoPlazo = pesos("3900"),
        mesesACortoPlazo = 3,
        avance = 2950f / 4400f,
        telefono = "238 104 5521",
        direccion = "C. Miguel Hidalgo y Costilla 214, Col. Emiliano Zapata, Tehuacán",
        zona = "Ruta 25",
        vendedores = listOf("Luis Ángel Mora", "Karina Sosa"),
        ultimoPago = java.time.LocalDate.of(2026, 9, 17)
    )
}

@Composable
private fun Venta(
    detalle: com.example.msp_app.feature.pagos.domain.model.DetalleVenta,
    ocultos: Boolean = false
) {
    DetalleVentaContent(
        state = DetalleVentaUiState(cargando = false, detalle = detalle, montosOcultos = ocultos),
        onAtras = {},
        onRegistrarAbono = {},
        onRegistrarVisita = {},
        onUsarLiquidacion = {},
        onVerAbonos = {},
        onVerGarantia = {}
    )
}
