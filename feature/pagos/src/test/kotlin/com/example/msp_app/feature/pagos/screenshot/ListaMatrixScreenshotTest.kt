package com.example.msp_app.feature.pagos.screenshot

import androidx.compose.runtime.Composable
import com.example.msp_app.core.designsystem.theme.FontSizeLevel
import com.example.msp_app.feature.pagos.ui.CarteraEnPantalla
import com.example.msp_app.feature.pagos.ui.ListaDeClientesContent
import com.example.msp_app.feature.pagos.ui.ListaDeClientesUiState
import com.example.msp_app.feature.pagos.ui.ListaFixtures
import com.example.msp_app.feature.pagos.ui.SegmentoDeCobranza
import org.junit.Test

/**
 * La matriz de la lista de clientes: `{light, dark} × {NORMAL 1.0, GRANDE 1.5,
 * MUY_GRANDE 2.0}` = 6 goldens, más el estado vacío en los dos temas.
 *
 * Las tres escalas son las tres reales de `FontSizeLevel`; el `1.3` de
 * `CollectionReportMatrixScreenshotTest` no corresponde a ningún nivel que un
 * usuario pueda elegir y no se replica (ver el KDoc de [PagosScreenshotTest]).
 *
 * El fixture es la ruta con **Victoria y sus dos cuentas**: es el caso que la
 * lista vieja pintaba como dos personas distintas, así que el golden es también
 * la prueba visual de que ahora es una sola puerta.
 */
class ListaMatrixScreenshotTest : PagosScreenshotTest() {

    @Test
    fun `lista light normal`() = lista(dark = false, nivel = FontSizeLevel.NORMAL)

    @Test
    fun `lista light grande`() = lista(dark = false, nivel = FontSizeLevel.GRANDE)

    @Test
    fun `lista light muy grande`() = lista(dark = false, nivel = FontSizeLevel.MUY_GRANDE)

    @Test
    fun `lista dark normal`() = lista(dark = true, nivel = FontSizeLevel.NORMAL)

    @Test
    fun `lista dark grande`() = lista(dark = true, nivel = FontSizeLevel.GRANDE)

    @Test
    fun `lista dark muy grande`() = lista(dark = true, nivel = FontSizeLevel.MUY_GRANDE)

    @Test
    fun `lista vacia light`() = vacia(dark = false)

    @Test
    fun `lista vacia dark`() = vacia(dark = true)

    private fun lista(dark: Boolean, nivel: FontSizeLevel) = capture(
        name = "pagos_lista_${tema(dark)}_${sufijoDe(nivel)}",
        dark = dark,
        nivel = nivel
    ) {
        Lista(estadoDeLaRuta())
    }

    private fun vacia(dark: Boolean) = capture(
        name = "pagos_lista_vacia_${tema(dark)}",
        dark = dark
    ) {
        Lista(ListaDeClientesUiState(cargando = false, segmento = SegmentoDeCobranza.VENCIDOS))
    }

    private fun tema(dark: Boolean) = if (dark) "dark" else "light"

    /**
     * El mismo estado que produciría el ViewModel: proyectado, no armado a mano.
     *
     * Se usa la ruta **con la promesa que cae hoy** desde la Task 19: con
     * `HOY_VISIBLE` encendido, el golden tiene que retratar el chip *hoy* con un
     * conteo real, no con el 0 estructural que tenía cuando nadie escribía
     * `PROMESA_FECHA`.
     */
    private fun estadoDeLaRuta(): ListaDeClientesUiState {
        val proyeccion = CarteraEnPantalla.proyectar(
            clientes = ListaFixtures.rutaConPromesaDeHoy(),
            segmento = SegmentoDeCobranza.TODOS,
            query = "",
            hoy = ListaFixtures.HOY
        )
        return ListaDeClientesUiState(
            cargando = false,
            clientes = proyeccion.clientes,
            conteos = proyeccion.conteos
        )
    }
}

@Composable
private fun Lista(state: ListaDeClientesUiState) {
    ListaDeClientesContent(
        state = state,
        onAtras = {},
        onBuscar = {},
        onElegirSegmento = {},
        onAbrirCliente = {},
        onAbrirVenta = {},
        onReintentar = {}
    )
}
