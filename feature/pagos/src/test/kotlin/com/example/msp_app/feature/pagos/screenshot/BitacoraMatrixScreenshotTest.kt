package com.example.msp_app.feature.pagos.screenshot

import com.example.msp_app.core.designsystem.theme.FontSizeLevel
import com.example.msp_app.feature.pagos.domain.model.BitacoraCompleta
import com.example.msp_app.feature.pagos.ui.BitacoraContent
import com.example.msp_app.feature.pagos.ui.BitacoraUiState
import com.example.msp_app.feature.pagos.ui.PagosFixtures
import org.junit.Test

/**
 * La matriz de la **bitácora**: `{light, dark} × {NORMAL, GRANDE, MUY_GRANDE}`,
 * más el caso vacío en los dos temas.
 *
 * El vacío tiene golden propio porque es el que más fácil se degrada sin que
 * nadie lo note: una pantalla en blanco se lee como un defecto de la app, no
 * como "nunca se ha tocado esta puerta". La foto obliga a que siga diciéndolo
 * con palabras.
 */
class BitacoraMatrixScreenshotTest : PagosScreenshotTest() {

    @Test
    fun `bitacora light normal`() = bitacora(dark = false, nivel = FontSizeLevel.NORMAL)

    @Test
    fun `bitacora light grande`() = bitacora(dark = false, nivel = FontSizeLevel.GRANDE)

    @Test
    fun `bitacora light muy grande`() = bitacora(dark = false, nivel = FontSizeLevel.MUY_GRANDE)

    @Test
    fun `bitacora dark normal`() = bitacora(dark = true, nivel = FontSizeLevel.NORMAL)

    @Test
    fun `bitacora dark grande`() = bitacora(dark = true, nivel = FontSizeLevel.GRANDE)

    @Test
    fun `bitacora dark muy grande`() = bitacora(dark = true, nivel = FontSizeLevel.MUY_GRANDE)

    @Test
    fun `bitacora vacia light`() = vacia(dark = false)

    @Test
    fun `bitacora vacia dark`() = vacia(dark = true)

    /**
     * **Datos reales, no de fixture — Task 5, hallazgo 6.** Hoy los goldens
     * usan `"Marisol Vega"` y `"Refrigerador Mabe 14'"`; lo real es un
     * cobrador como `"RUTA 25 - NOE CORTERO"` (36 caracteres) y productos
     * como `"Recamara cantaro king size chocolate"` (36 caracteres, ya
     * normalizados). El peor caso está medido (`ElCobradorNoSeRecortaTest`,
     * `ElRenglonDeAbajoNoSeSaleTest`) pero nunca visto en la lista completa.
     */
    @Test
    fun `bitacora datos reales light`() = bitacoraConDatosReales(dark = false)

    private fun bitacoraConDatosReales(dark: Boolean) = capture(
        name = "pagos_bitacora_datos_reales_${tema(dark)}",
        dark = dark
    ) {
        val bitacora = deLaFixture()
        BitacoraContent(
            state = BitacoraUiState(
                cargando = false,
                bitacora = bitacora.copy(
                    contactos = bitacora.contactos.map {
                        it.copy(
                            cobrador = "RUTA 25 - NOE CORTERO",
                            cuenta = it.cuenta?.let { _ -> "Recamara cantaro king size chocolate" }
                        )
                    }
                )
            ),
            onAtras = {}
        )
    }

    private fun bitacora(dark: Boolean, nivel: FontSizeLevel) = capture(
        name = "pagos_bitacora_${tema(dark)}_${sufijoDe(nivel)}",
        dark = dark,
        nivel = nivel
    ) {
        BitacoraContent(
            state = BitacoraUiState(cargando = false, bitacora = deLaFixture()),
            onAtras = {}
        )
    }

    private fun vacia(dark: Boolean) = capture(
        name = "pagos_bitacora_vacia_${tema(dark)}",
        dark = dark
    ) {
        BitacoraContent(
            state = BitacoraUiState(
                cargando = false,
                bitacora = deLaFixture().copy(contactos = emptyList())
            ),
            onAtras = {}
        )
    }

    // La bitácora es por VENTA, no por cliente (decisión del dueño: el detalle
    // de cliente perdió su sección "últimos contactos"), así que se deriva con
    // el MISMO filtro que `CargarBitacoraDeLaVenta` —sólo los contactos de esa
    // cuenta— y no de `detalleCliente()`, que ya no trae contactos.
    //
    // `detalleVentaConHistoriaLarga()` y no `detalleVenta()`: ésta sólo trae UN
    // contacto de la venta, y la bitácora completa —a diferencia de la vista
    // previa del detalle— es la lista COMPLETA, así que necesita algo que
    // partir en más de un mes con subtotal.
    private fun deLaFixture(): BitacoraCompleta {
        val detalle = PagosFixtures.detalleVentaConHistoriaLarga()
        return BitacoraCompleta(
            clienteId = detalle.clienteId,
            ventaId = detalle.ventaId,
            nombre = detalle.clienteNombre,
            titulo = detalle.titulo,
            direccion = detalle.direccion,
            contactos = detalle.contactos.filter { it.ventaId == detalle.ventaId },
            hoy = detalle.hoy
        )
    }

    private fun tema(dark: Boolean) = if (dark) "dark" else "light"
}
