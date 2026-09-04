package com.example.msp_app.feature.visitas.ui

import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.navArgument

/**
 * Las rutas de `:feature:visitas`.
 *
 * ## Destino, no diálogo
 *
 * La captura es un **destino de navegación con su propio ViewModel y
 * `SavedStateHandle`**, igual que las Tasks 16-18, y no un `FullScreenDialog`
 * como el `NewVisitDialog` de hoy. La cámara (Task 23) manda al usuario fuera de
 * la app y el proceso puede morir mientras tanto: un destino vuelve con su
 * argumento y con el id de la visita intactos; un diálogo alojado en la
 * composición del llamador no vuelve en absoluto. Por eso los argumentos se leen
 * del `SavedStateHandle` y no de parámetros del Composable.
 *
 * Los nombres de argumento son constantes compartidas entre la ruta y el
 * ViewModel a propósito: una cadena repetida en dos lados es un `null` en
 * producción esperando a que alguien renombre uno de los dos.
 */
object VisitasRutas {

    /** Argumento `CLIENTE_ID`. Obligatorio: se visita una puerta, no una venta. */
    const val ARG_CLIENTE_ID: String = "clienteId"

    /**
     * Argumento `DOCTO_CC_ACR_ID` de la cuenta que el cobrador tenía abierta.
     * Opcional: desde el detalle de cliente no hay una.
     */
    const val ARG_VENTA_ID: String = "ventaId"

    /**
     * El valor de [ARG_VENTA_ID] que significa "sin venta ligada". Es **0**, el
     * mismo centinela que ya usa `Visit.IMPTE_DOCTO_CC_ID` en el schema: un
     * segundo centinela para el mismo concepto es cómo empiezan a divergir.
     */
    const val SIN_VENTA: Int = 0

    /** Registrar visita. */
    const val REGISTRAR: String = "visitas/registrar/{$ARG_CLIENTE_ID}?$ARG_VENTA_ID={$ARG_VENTA_ID}"

    /** La ruta concreta para visitar al cliente [clienteId] desde la venta [ventaId]. */
    fun registrar(clienteId: Int, ventaId: Int? = null): String =
        "visitas/registrar/$clienteId?$ARG_VENTA_ID=${ventaId ?: SIN_VENTA}"
}

/**
 * Registra el destino de **registrar visita** en el grafo de navegación.
 *
 * Se expone como extensión de [NavGraphBuilder] para que `:app` cablee el grafo
 * sin conocer el ViewModel ni los Composables internos del feature — el mismo
 * reparto que ya usan `destinosDePagos` y `destinoDeRegistrarAbono`.
 *
 * [onRegistrada] recibe el id de la visita, una sola vez.
 */
fun NavGraphBuilder.destinoDeRegistrarVisita(onAtras: () -> Unit, onRegistrada: (String) -> Unit) {
    composable(
        route = VisitasRutas.REGISTRAR,
        arguments = listOf(
            navArgument(VisitasRutas.ARG_CLIENTE_ID) { type = NavType.IntType },
            navArgument(VisitasRutas.ARG_VENTA_ID) {
                type = NavType.IntType
                defaultValue = VisitasRutas.SIN_VENTA
            }
        )
    ) {
        RegistrarVisitaScreen(
            viewModel = hiltViewModel(),
            onAtras = onAtras,
            onRegistrada = onRegistrada
        )
    }
}
