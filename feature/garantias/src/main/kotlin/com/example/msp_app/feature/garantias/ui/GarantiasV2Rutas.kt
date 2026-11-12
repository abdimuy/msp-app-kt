package com.example.msp_app.feature.garantias.ui

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable

/**
 * Las rutas de `:feature:garantias` (captura de garantías v2).
 *
 * Es un sistema **distinto** del legado que vive en `:app`
 * (`features/guarantees`, rutas `garantias/nueva`, sin `Idempotency-Key`).
 * Ese sigue funcionando como hoy y esta pantalla no lee ni escribe sus tablas.
 *
 * Se expone como extensión de [NavGraphBuilder] —igual que `destinoDeRegistrarVisita`—
 * para que `:app` cablee el grafo sin conocer los Composables internos.
 */
object GarantiasV2Rutas {

    /** Entrada de la captura. Sin argumentos todavía: el folio lo genera el servidor. */
    const val INICIO: String = "garantias-v2"
}

/** Registra el destino de la captura de garantías v2 en el grafo de navegación. */
fun NavGraphBuilder.destinoDeGarantiasV2(onAtras: () -> Unit) {
    composable(route = GarantiasV2Rutas.INICIO) {
        GarantiasV2Screen(onAtras = onAtras)
    }
}
