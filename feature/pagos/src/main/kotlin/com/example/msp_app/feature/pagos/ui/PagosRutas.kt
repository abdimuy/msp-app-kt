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

    /** Argumento `Payment.ID` del ticket de pago. */
    const val ARG_PAGO_ID: String = "pagoId"

    /**
     * La lista de cobranza por cliente (Task 17) — el reemplazo de las dos
     * listas de hoy. Sin argumentos: es la ruta del cobrador completa.
     */
    const val LISTA_CLIENTES: String = "pagos/clientes"

    /** Detalle de cliente. Desde la lista y el mapa se entra por aquí (Task 21). */
    const val DETALLE_CLIENTE: String = "pagos/cliente/{$ARG_CLIENTE_ID}"

    /** Detalle de venta. Desde un pago o un recibo se entra directo aquí (Task 21). */
    const val DETALLE_VENTA: String = "pagos/venta/{$ARG_VENTA_ID}"

    /**
     * Registrar abono (Task 18). **La pantalla del dinero**, y por eso un
     * destino propio y no un diálogo: su `SavedStateHandle` sostiene la clave de
     * idempotencia y el guard anti-duplicado, que tienen que sobrevivir a la
     * rotación y a la muerte del proceso mientras la cámara (Task 22) está
     * encima. Un diálogo alojado en la composición del llamador no vuelve.
     */
    const val REGISTRAR_ABONO: String = "pagos/abono/{$ARG_VENTA_ID}"

    /** La ruta concreta del cliente [clienteId]. */
    fun detalleCliente(clienteId: Int): String = "pagos/cliente/$clienteId"

    /** La ruta concreta de la venta [ventaId]. */
    fun detalleVenta(ventaId: Int): String = "pagos/venta/$ventaId"

    /**
     * Ticket de pago (Task 20). Lleva **solo** el `pagoId`: la pantalla resuelve
     * la venta desde el abono, así que vuelve entera después de una rotación o
     * de que el proceso muera con el picker de Bluetooth encima, sin depender de
     * quién navegó hasta ella.
     */
    const val TICKET_PAGO: String = "pagos/ticket/{$ARG_PAGO_ID}"

    /** La ruta concreta para abonar a la venta [ventaId]. */
    fun registrarAbono(ventaId: Int): String = "pagos/abono/$ventaId"

    /** La ruta concreta del ticket del abono [pagoId]. */
    fun ticketDePago(pagoId: String): String = "pagos/ticket/$pagoId"
}

/**
 * Registra los dos destinos de detalle en el grafo de navegación de la app.
 *
 * Se expone como extensión de [NavGraphBuilder] para que `:app` cablee el grafo
 * sin conocer los ViewModels ni los Composables internos del feature — el mismo
 * reparto que ya usa el resto de la app.
 *
 * ## Por qué los callbacks llevan el id que llevan (Task 21)
 *
 * Los dos destinos comparten esta función, así que un callback que significara
 * una cosa desde el cliente y otra desde la venta sería imposible de cablear en
 * `:app`: ahí no se sabe por cuál de los dos entró la llamada. Por eso:
 *
 * - [onRegistrarAbono] recibe **siempre un `ventaId`**. El abono es de una
 *   cuenta, nunca de una persona: `pagos/abono/{ventaId}` no tiene forma de
 *   cobrarle "al cliente". Desde el detalle de cliente lo resuelve
 *   [DetalleClienteScreen] con la cuenta que encabeza "sus ventas".
 * - [onRegistrarVisita] recibe **`clienteId` y `ventaId`**, el segundo nulo
 *   cuando se entró por el cliente: se visita una puerta, y la cuenta abierta
 *   es contexto opcional (`VisitasRutas.SIN_VENTA`).
 * - [onMasAcciones] recibe el `ventaId` de la cuenta cuyo "⋯" se abrió: ahí
 *   vive la condonación, que es dinero de UNA cuenta.
 * - [onVerGarantia] recibe el `DOCTO_CC_ID` de la venta —no el id de la
 *   garantía—, porque el flujo de garantías de `:app` está indexado por venta
 *   (`GuaranteesViewModel.getGuaranteeSaleById`), no por `EXTERNAL_ID`.
 */
fun NavGraphBuilder.destinosDePagos(
    onAtras: () -> Unit,
    onAbrirVenta: (Int) -> Unit,
    onRegistrarAbono: (Int) -> Unit,
    onRegistrarVisita: (Int, Int?) -> Unit,
    onMasAcciones: (Int) -> Unit,
    onVerGarantia: (Int) -> Unit
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

/**
 * Registra el destino de **registrar abono** (Task 18) en el grafo.
 *
 * Va aparte de [destinosDePagos] por la misma razón que la lista: cada registro
 * se queda con los callbacks que le tocan, en vez de una función de nueve
 * lambdas sueltas. La Task 21, que cablea los puntos de entrada, llama a los
 * tres.
 *
 * [onRegistrado] recibe el id del abono — la Task 20 lo lleva al ticket.
 */
fun NavGraphBuilder.destinoDeRegistrarAbono(onAtras: () -> Unit, onRegistrado: (String) -> Unit) {
    composable(
        route = PagosRutas.REGISTRAR_ABONO,
        arguments = listOf(navArgument(PagosRutas.ARG_VENTA_ID) { type = NavType.IntType })
    ) {
        RegistrarAbonoScreen(
            viewModel = hiltViewModel(),
            onAtras = onAtras,
            onRegistrado = onRegistrado
        )
    }
}

/**
 * Registra el destino del **ticket de pago** (Task 20) en el grafo.
 *
 * Aparte de los demás por el mismo criterio: cada registro se queda con los
 * callbacks que le tocan. La Task 21, que cablea los puntos de entrada, es la
 * que lleva el `onRegistrado` del abono hasta [PagosRutas.ticketDePago].
 */
fun NavGraphBuilder.destinoDeTicketDePago(onAtras: () -> Unit) {
    composable(
        route = PagosRutas.TICKET_PAGO,
        arguments = listOf(navArgument(PagosRutas.ARG_PAGO_ID) { type = NavType.StringType })
    ) {
        TicketDePagoScreen(viewModel = hiltViewModel(), onAtras = onAtras)
    }
}
