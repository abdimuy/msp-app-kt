package com.example.msp_app.feature.pagos.screenshot

import androidx.compose.runtime.Composable
import com.example.msp_app.core.designsystem.theme.FontSizeLevel
import com.example.msp_app.feature.pagos.ui.DetalleClienteContent
import com.example.msp_app.feature.pagos.ui.DetalleClienteUiState
import com.example.msp_app.feature.pagos.ui.DetalleVentaContent
import com.example.msp_app.feature.pagos.ui.DetalleVentaUiState
import com.example.msp_app.feature.pagos.ui.HojaDeContactos
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
        Cliente(
            detalle.copy(
                ultimoCobroAqui = null,
                contactos = detalle.contactos.map { it.copy(ubicacion = null) }
            )
        )
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

    // --- La tarjeta "Últimos contactos" sola — Task 5, hallazgo 5 ------------------------

    /**
     * La tarjeta `HojaDeContactos` del detalle de cliente, fotografiada SOLA
     * —no la pantalla completa—: es la superficie donde el dueño vio el
     * defecto original de la fila de contactos, y la única de las tres
     * (bitácora, detalle de venta, detalle de cliente) que ningún golden
     * fotografiaba — `pagos_cliente_*` la deja bajo el pliegue, sin scroll.
     */
    @Test
    fun `hoja de contactos light normal`() = hojaDeContactos(
        dark = false,
        nivel = FontSizeLevel.NORMAL
    )

    @Test
    fun `hoja de contactos light grande`() = hojaDeContactos(
        dark = false,
        nivel = FontSizeLevel.GRANDE
    )

    @Test
    fun `hoja de contactos light muy grande`() =
        hojaDeContactos(dark = false, nivel = FontSizeLevel.MUY_GRANDE)

    @Test
    fun `hoja de contactos dark normal`() = hojaDeContactos(
        dark = true,
        nivel = FontSizeLevel.NORMAL
    )

    @Test
    fun `hoja de contactos dark grande`() = hojaDeContactos(
        dark = true,
        nivel = FontSizeLevel.GRANDE
    )

    @Test
    fun `hoja de contactos dark muy grande`() =
        hojaDeContactos(dark = true, nivel = FontSizeLevel.MUY_GRANDE)

    /**
     * **Datos reales, no de fixture — Task 5, hallazgo 6.** Cobrador
     * `"RUTA 25 - NOE CORTERO"` (36 caracteres, ya visto en producción) y
     * un producto largo real en vez de `"Refrigerador Mabe 14'"`. El peor
     * caso está medido (`ElCobradorNoSeRecortaTest`,
     * `ElRenglonDeAbajoNoSeSaleTest`) pero nunca visto en esta tarjeta.
     */
    @Test
    fun `hoja de contactos datos reales light`() = hojaDeContactosConDatosReales(dark = false)

    @Test
    fun `hoja de contactos datos reales dark`() = hojaDeContactosConDatosReales(dark = true)

    private fun hojaDeContactos(dark: Boolean, nivel: FontSizeLevel) = capture(
        name = "pagos_hoja_contactos_${tema(dark)}_${sufijoDe(nivel)}",
        dark = dark,
        nivel = nivel
    ) {
        Contactos(PagosFixtures.detalleCliente())
    }

    private fun hojaDeContactosConDatosReales(dark: Boolean) = capture(
        name = "pagos_hoja_contactos_datos_reales_${tema(dark)}",
        dark = dark
    ) {
        val detalle = PagosFixtures.detalleCliente()
        Contactos(
            detalle.copy(
                contactos = detalle.contactos.map {
                    it.copy(
                        cobrador = COBRADOR_REAL,
                        cuenta = it.cuenta?.let { PRODUCTO_REAL }
                    )
                }
            )
        )
    }

    private fun tema(dark: Boolean) = if (dark) "dark" else "light"

    private companion object {
        /** El nombre real de un cobrador, tal como llega de producción — ver `ElCobradorNoSeRecortaTest`. */
        const val COBRADOR_REAL = "RUTA 25 - NOE CORTERO"

        /** 36 caracteres ya normalizados — ver `ElRenglonDeAbajoNoSeSaleTest`. */
        const val PRODUCTO_REAL = "Recamara cantaro king size chocolate"
    }
}

@Composable
private fun Contactos(detalle: com.example.msp_app.feature.pagos.domain.model.DetalleCliente) {
    HojaDeContactos(
        detalle = detalle,
        ocultos = false,
        onVerContactos = {},
        onVerUbicacionDelContacto = null
    )
}

@Composable
private fun Cliente(detalle: com.example.msp_app.feature.pagos.domain.model.DetalleCliente) {
    DetalleClienteContent(
        state = DetalleClienteUiState(cargando = false, detalle = detalle),
        onAtras = {},
        onAbrirVenta = {},
        onRegistrarAbono = {},
        onRegistrarVisita = {},
        onVerContactos = {},
        onAlternarTema = {},
        onAlternarPrivacidad = {}
    )
}

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
