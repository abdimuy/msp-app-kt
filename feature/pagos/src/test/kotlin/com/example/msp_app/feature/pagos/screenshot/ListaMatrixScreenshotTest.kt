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
 *
 * La ruta lleva además **la promesa de Esperanza**, que cae hoy. No se ve en la
 * lista —cae en *volver*, y el golden se toma bajo *sin visitar*— pero sí en el
 * conteo de su chip, que es justo lo que se quiere retratar: **los cuatro chips
 * con una cifra distinta de cero cada uno**. Una fila de filtros con un chip que
 * siempre marca 0 le enseña al cobrador que la fila miente, y cuando deja de
 * leerla se pierden también los chips que sí sirven.
 *
 * ## Por qué el golden va bajo *sin visitar*, y no bajo un chip que lo enseñe todo
 *
 * Porque ya no hay ninguno: los cuatro **particionan** el catálogo de ocho desde
 * que `TODOS` se retiró. *Sin visitar* es el chip con el que la pantalla abre
 * (`ListaDeClientesUiState.segmento`), así que el golden retrata lo primero que
 * ve el cobrador al entrar, que es lo que un golden de matriz debería retratar.
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
        Lista(estadoDeLaRuta(dark))
    }

    private fun vacia(dark: Boolean) = capture(
        name = "pagos_lista_vacia_${tema(dark)}",
        dark = dark
    ) {
        // Con *volver*: el estado vacío se retrata bajo un chip que el cobrador
        // toca todos los días, no bajo el de arranque —que es el único que el
        // resto de la matriz ya enseña.
        Lista(
            ListaDeClientesUiState(
                cargando = false,
                segmento = SegmentoDeCobranza.VOLVER_A_VISITAR,
                temaOscuro = dark
            )
        )
    }

    private fun tema(dark: Boolean) = if (dark) "dark" else "light"

    /**
     * El mismo estado que produciría el ViewModel: proyectado, no armado a mano.
     *
     * [dark] viaja al estado además del tema de la captura **a propósito**: es lo
     * que decide el glifo del toggle del encabezado (sol en claro, luna en
     * oscuro), y en producción los dos salen del mismo booleano
     * (`ThemeController.isDarkMode`, vía `TemaDeLaAppPort` y `LocalAppDarkTheme`).
     * Pasar uno sin el otro retrataría una combinación que la app no puede
     * producir: luna sobre fondo blanco.
     */
    private fun estadoDeLaRuta(dark: Boolean): ListaDeClientesUiState {
        val proyeccion = CarteraEnPantalla.proyectar(
            clientes = ListaFixtures.rutaConPromesaDeHoy(),
            segmento = SegmentoDeCobranza.SIN_VISITAR,
            query = "",
            hoy = ListaFixtures.HOY
        )
        return ListaDeClientesUiState(
            cargando = false,
            clientes = proyeccion.clientes,
            conteos = proyeccion.conteos,
            // Explícito aunque coincida con el default: el chip pintado y el que
            // filtró la lista tienen que ser el mismo, o el golden retrataría una
            // pantalla que la app no puede producir.
            segmento = SegmentoDeCobranza.SIN_VISITAR,
            temaOscuro = dark
        )
    }
}

@Composable
private fun Lista(state: ListaDeClientesUiState) {
    ListaDeClientesContent(
        state = state,
        onBuscar = {},
        onElegirSegmento = {},
        onAbrirCliente = {},
        onReintentar = {},
        onAlternarTema = {},
        onAlternarPrivacidad = {}
    )
}
