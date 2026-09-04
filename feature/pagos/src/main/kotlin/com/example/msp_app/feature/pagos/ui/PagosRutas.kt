package com.example.msp_app.feature.pagos.ui

import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.navArgument

/**
 * Las rutas de `:feature:pagos`.
 *
 * ## Por qué destinos y no diálogos
 *
 * El detalle —y, en las Tasks 18-19, la captura— son **destinos de navegación
 * con su propio ViewModel y `SavedStateHandle`**, no `FullScreenDialog`. La
 * cámara (Tasks 22-23) manda al usuario fuera de la app y el proceso puede
 * morir mientras tanto: un destino con `SavedStateHandle` vuelve con su
 * argumento intacto, un diálogo alojado en la composición del llamador no
 * vuelve en absoluto. Por eso el argumento se lee del `SavedStateHandle` y no
 * de un parámetro del Composable.
 *
 * Los nombres de argumento son constantes compartidas entre la ruta y el
 * ViewModel a propósito: una cadena repetida en dos lados es un `null` en
 * producción esperando a que alguien renombre uno de los dos.
 */
object PagosRutas {

    /** Argumento `CLIENTE_ID` del detalle de cliente. */
    const val ARG_CLIENTE_ID: String = "clienteId"

    /** Argumento `DOCTO_CC_ACR_ID` del detalle de venta. */
    const val ARG_VENTA_ID: String = "ventaId"

    /**
     * La lista de cobranza por cliente (Task 17) — el reemplazo de las dos
     * listas de hoy. Sin argumentos: es la ruta del cobrador completa.
     */
    const val LISTA_CLIENTES: String = "pagos/clientes"

    /** Detalle de cliente. Desde la lista y el mapa se entra por aquí (Task 21). */
    const val DETALLE_CLIENTE: String = "pagos/cliente/{$ARG_CLIENTE_ID}"

    /** Detalle de venta. Desde un pago o un recibo se entra directo aquí (Task 21). */
    const val DETALLE_VENTA: String = "pagos/venta/{$ARG_VENTA_ID}"

    /** La ruta concreta del cliente [clienteId]. */
    fun detalleCliente(clienteId: Int): String = "pagos/cliente/$clienteId"

    /** La ruta concreta de la venta [ventaId]. */
    fun detalleVenta(ventaId: Int): String = "pagos/venta/$ventaId"
}

/**
 * Registra los dos destinos de detalle en el grafo de navegación de la app.
 *
 * Se expone como extensión de [NavGraphBuilder] para que `:app` cablee el grafo
 * sin conocer los ViewModels ni los Composables internos del feature — el mismo
 * reparto que ya usa el resto de la app.
 */
fun NavGraphBuilder.destinosDePagos(
    onAtras: () -> Unit,
    onAbrirVenta: (Int) -> Unit,
    onRegistrarAbono: (Int) -> Unit,
    onRegistrarVisita: (Int) -> Unit,
    onMasAcciones: () -> Unit,
    onVerGarantia: (String) -> Unit
) {
    composable(
        route = PagosRutas.DETALLE_CLIENTE,
        arguments = listOf(navArgument(PagosRutas.ARG_CLIENTE_ID) { type = NavType.IntType })
    ) {
        DetalleClienteScreen(
            viewModel = hiltViewModel(),
            onAtras = onAtras,
            onAbrirVenta = onAbrirVenta,
            onRegistrarAbono = onRegistrarAbono,
            onRegistrarVisita = onRegistrarVisita,
            onMasAcciones = onMasAcciones
        )
    }
    composable(
        route = PagosRutas.DETALLE_VENTA,
        arguments = listOf(navArgument(PagosRutas.ARG_VENTA_ID) { type = NavType.IntType })
    ) {
        DetalleVentaScreen(
            viewModel = hiltViewModel(),
            onAtras = onAtras,
            onRegistrarAbono = onRegistrarAbono,
            onRegistrarVisita = onRegistrarVisita,
            onMasAcciones = onMasAcciones,
            onVerGarantia = onVerGarantia
        )
    }
}

/**
 * Registra la lista de clientes (Task 17) en el grafo de navegación.
 *
 * Va **aparte** de [destinosDePagos] y no dentro: sumarle un séptimo callback a
 * aquella función la pondría exactamente en el umbral de `LongParameterList`
 * (7) que detekt aplica a este módulo, y una lista de siete lambdas sueltas ya
 * es difícil de leer en la llamada. Son dos registros cohesivos en vez de uno
 * largo; la Task 21, que cablea los puntos de entrada, llama a los dos.
 */
fun NavGraphBuilder.destinoDeListaDeClientes(
    onAtras: () -> Unit,
    onAbrirCliente: (Int) -> Unit,
    onAbrirVenta: (Int) -> Unit
) {
    composable(route = PagosRutas.LISTA_CLIENTES) {
        ListaDeClientesScreen(
            viewModel = hiltViewModel(),
            onAtras = onAtras,
            onAbrirCliente = onAbrirCliente,
            onAbrirVenta = onAbrirVenta
        )
    }
}
