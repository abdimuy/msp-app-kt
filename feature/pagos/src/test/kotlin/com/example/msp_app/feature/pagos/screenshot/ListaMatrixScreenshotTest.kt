package com.example.msp_app.feature.pagos.screenshot

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.example.msp_app.core.common.cobranza.domain.EstadoCuenta
import com.example.msp_app.core.designsystem.theme.FontSizeLevel
import com.example.msp_app.core.designsystem.theme.MspTheme
import com.example.msp_app.feature.pagos.domain.OrdenDeLaLista
import com.example.msp_app.feature.pagos.ui.CarteraEnPantalla
import com.example.msp_app.feature.pagos.ui.ListaDeClientesContent
import com.example.msp_app.feature.pagos.ui.ListaDeClientesUiState
import com.example.msp_app.feature.pagos.ui.ListaFixtures
import com.example.msp_app.feature.pagos.ui.SegmentoDeCobranza
import com.example.msp_app.feature.pagos.ui.components.CuerpoDeOrdenarPor
import com.example.msp_app.feature.pagos.ui.components.FilaDeCliente
import com.example.msp_app.feature.pagos.ui.components.RenglonDeOrden
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

    /**
     * **La tarjeta con una venta nueva, por sí sola** (decisión del dueño,
     * 2026-10-05, mock "Decidido · v3"): insignia junto al nombre, punto sobre
     * el nombre del mueble, chip "Primer abono $X" con contorno y "Vendida
     * hace 5 días" donde antes iba la pastilla de atrasos.
     *
     * [ListaFixtures.conVentaNueva] y no [ListaFixtures.ricardo]: Ricardo
     * también es `esNueva`, pero su venta es vieja y cae al escalón de la
     * fecha completa ("Vendida 12 mar 2026") — este golden quiere el escalón
     * de días, que es el que más se va a ver en producción. Una sola cuenta,
     * toda ella nueva: el golden retrata el caso puro, sin una segunda venta
     * (no nueva) al lado que reparta la atención. La matriz de arriba
     * (`lista *`) ya trae el caso mixto de Victoria (una nueva, una no) y el
     * de Ricardo encabezando la ruta; éste existe para que el estado "Nueva"
     * tenga un golden que no dependa de leer una tarjeta ajena.
     *
     * Sólo `{claro, oscuro} × {1.0, 2.0}`: el 1.5 no agrega nada que 1.0 y 2.0
     * no demuestren ya para una tarjeta de un solo renglón —no hay quiebre de
     * dos líneas que medir aquí, a diferencia del control segmentado—.
     */
    @Test
    fun `tarjeta nueva light normal`() = tarjetaNueva(dark = false, nivel = FontSizeLevel.NORMAL)

    @Test
    fun `tarjeta nueva light muy grande`() =
        tarjetaNueva(dark = false, nivel = FontSizeLevel.MUY_GRANDE)

    @Test
    fun `tarjeta nueva dark normal`() = tarjetaNueva(dark = true, nivel = FontSizeLevel.NORMAL)

    @Test
    fun `tarjeta nueva dark muy grande`() =
        tarjetaNueva(dark = true, nivel = FontSizeLevel.MUY_GRANDE)

    // --- Cabecera compacta (bajar la lista), decisión del dueño 2026-10-05 -----

    /**
     * La barra encogida con la píldora flotante visible — se arranca YA
     * desplazada (`listState` hospedado con `initialFirstVisibleItemIndex`) en
     * vez de simular un gesto de scroll: Roborazzi no tiene cómo despachar un
     * `swipeUp()` sobre la captura de un solo frame, así que el `LazyListState`
     * se pide ya en la posición que un scroll real habría dejado.
     *
     * Con la ruta de dos puertas de [estadoDeLaRuta] no alcanza: si el
     * contenido no desborda el viewport, `LazyColumn` ignora el índice inicial
     * y vuelve a 0 — medido, el primer intento salió con la cabecera grande
     * puesta. [clientesParaDesbordar] trae filas de sobra para que el índice
     * inicial sea real.
     */
    @Test
    fun `barra compacta light normal`() = barraCompacta(dark = false, nivel = FontSizeLevel.NORMAL)

    @Test
    fun `barra compacta light muy grande`() =
        barraCompacta(dark = false, nivel = FontSizeLevel.MUY_GRANDE)

    @Test
    fun `barra compacta dark normal`() = barraCompacta(dark = true, nivel = FontSizeLevel.NORMAL)

    @Test
    fun `barra compacta dark muy grande`() =
        barraCompacta(dark = true, nivel = FontSizeLevel.MUY_GRANDE)

    private fun barraCompacta(dark: Boolean, nivel: FontSizeLevel) = capture(
        name = "pagos_barra_compacta_${tema(dark)}_${sufijoDe(nivel)}",
        dark = dark,
        nivel = nivel
    ) {
        Lista(
            estadoDesbordado(dark),
            listState = rememberLazyListState(initialFirstVisibleItemIndex = INDICE_DESBORDADO)
        )
    }

    /** Quince puertas sintéticas — de sobra para desbordar el viewport y forzar un índice inicial real. */
    private fun estadoDesbordado(dark: Boolean): ListaDeClientesUiState {
        val clientes = (1..QUINCE).map { n ->
            ListaFixtures.cliente(
                clienteId = 90_000 + n,
                nombre = "Cliente Desbordado $n",
                ventas = listOf(
                    ListaFixtures.venta(
                        ventaId = 95_000 + n,
                        folio = "V-$n",
                        descripcion = "Mueble $n",
                        saldo = ListaFixtures.dinero("1000"),
                        totalVenta = ListaFixtures.dinero("2000"),
                        enganche = ListaFixtures.dinero("200"),
                        fechaVenta = ListaFixtures.HOY.minusDays(n.toLong()),
                        estado = ListaFixtures.estado(EstadoCuenta.SIN_TOCAR)
                    )
                )
            )
        }
        val proyeccion = CarteraEnPantalla.proyectar(
            clientes = clientes,
            segmento = SegmentoDeCobranza.SIN_VISITAR,
            query = "",
            hoy = ListaFixtures.HOY
        )
        return ListaDeClientesUiState(
            cargando = false,
            clientes = proyeccion.clientes,
            conteos = proyeccion.conteos,
            segmento = SegmentoDeCobranza.SIN_VISITAR,
            temaOscuro = dark,
            hoy = ListaFixtures.HOY
        )
    }

    // --- Hoja "Ordenar por", decisión del dueño 2026-10-05 ----------------------

    /**
     * El CUERPO de la hoja, sin el `ModalBottomSheet` — un `ModalBottomSheet`
     * real se monta en un `Popup`/`Dialog` que `captureRoboImage` no ve
     * (trampa ya documentada en `HojaDeLaFicha`/`HojaDeAbono`): el golden de
     * "la hoja abierta" sale en blanco si se intenta sobre
     * [com.example.msp_app.feature.pagos.ui.components.HojaDeOrdenarPor]
     * completa.
     */
    @Test
    fun `hoja de orden light normal`() = hojaDeOrden(dark = false, nivel = FontSizeLevel.NORMAL)

    @Test
    fun `hoja de orden light muy grande`() =
        hojaDeOrden(dark = false, nivel = FontSizeLevel.MUY_GRANDE)

    @Test
    fun `hoja de orden dark normal`() = hojaDeOrden(dark = true, nivel = FontSizeLevel.NORMAL)

    @Test
    fun `hoja de orden dark muy grande`() =
        hojaDeOrden(dark = true, nivel = FontSizeLevel.MUY_GRANDE)

    private fun hojaDeOrden(dark: Boolean, nivel: FontSizeLevel) = capture(
        name = "pagos_hoja_orden_${tema(dark)}_${sufijoDe(nivel)}",
        dark = dark,
        nivel = nivel
    ) {
        Box(modifier = Modifier.padding(MspTheme.spacing.md)) {
            CuerpoDeOrdenarPor(seleccionado = OrdenDeLaLista.COBRANZA, onElegir = {})
        }
    }

    // --- Renglón de orden con un orden distinto de cobranza ---------------------

    @Test
    fun `renglon de orden distinto light normal`() =
        renglonDistinto(dark = false, nivel = FontSizeLevel.NORMAL)

    @Test
    fun `renglon de orden distinto light muy grande`() =
        renglonDistinto(dark = false, nivel = FontSizeLevel.MUY_GRANDE)

    @Test
    fun `renglon de orden distinto dark normal`() =
        renglonDistinto(dark = true, nivel = FontSizeLevel.NORMAL)

    @Test
    fun `renglon de orden distinto dark muy grande`() =
        renglonDistinto(dark = true, nivel = FontSizeLevel.MUY_GRANDE)

    private fun renglonDistinto(dark: Boolean, nivel: FontSizeLevel) = capture(
        name = "pagos_renglon_orden_distinto_${tema(dark)}_${sufijoDe(nivel)}",
        dark = dark,
        nivel = nivel
    ) {
        Box(modifier = Modifier.padding(MspTheme.spacing.md)) {
            RenglonDeOrden(
                orden = OrdenDeLaLista.MAYOR_SALDO,
                onAbrirHoja = {},
                onQuitar = {}
            )
        }
    }

    private fun tarjetaNueva(dark: Boolean, nivel: FontSizeLevel) = capture(
        name = "pagos_tarjeta_nueva_${tema(dark)}_${sufijoDe(nivel)}",
        dark = dark,
        nivel = nivel
    ) {
        Box(modifier = Modifier.padding(MspTheme.spacing.md)) {
            FilaDeCliente(
                cliente = ListaFixtures.conVentaNueva(),
                hoy = ListaFixtures.HOY,
                onAbrirCliente = {}
            )
        }
    }

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
            temaOscuro = dark,
            // Faltaba: sin esto cada "Vendida …" se leía contra el sentinel de
            // 1970, no contra el "hoy" que de verdad proyectó la cartera — un
            // golden que la app no puede producir, por la misma razón que el
            // comentario de arriba protege `segmento`.
            hoy = ListaFixtures.HOY
        )
    }

    private companion object {
        const val QUINCE = 15
        const val INDICE_DESBORDADO = 6
    }
}

@Composable
private fun Lista(
    state: ListaDeClientesUiState,
    listState: LazyListState = rememberLazyListState()
) {
    ListaDeClientesContent(
        state = state,
        onBuscar = {},
        onElegirSegmento = {},
        onAbrirCliente = {},
        onReintentar = {},
        onAlternarTema = {},
        onAlternarPrivacidad = {},
        onElegirOrden = {},
        onQuitarOrden = {},
        onAbrirMenu = {},
        listState = listState
    )
}
