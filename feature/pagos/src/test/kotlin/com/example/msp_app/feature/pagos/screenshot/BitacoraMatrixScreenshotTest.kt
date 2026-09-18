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

    private fun deLaFixture(): BitacoraCompleta {
        val detalle = PagosFixtures.detalleCliente()
        return BitacoraCompleta(
            clienteId = detalle.clienteId,
            nombre = detalle.nombre,
            direccion = detalle.direccion,
            contactos = detalle.contactos
        )
    }

    private fun tema(dark: Boolean) = if (dark) "dark" else "light"
}
