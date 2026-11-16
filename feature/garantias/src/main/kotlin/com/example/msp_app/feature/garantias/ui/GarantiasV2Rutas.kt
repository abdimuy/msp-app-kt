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
 * ## Sólo [INICIO] entra al grafo de `:app`
 *
 * El grafo de `:app` registra un único destino de garantías v2 (`INICIO`); las
 * cuatro pantallas internas viven en el `NavHost` **anidado** que arma
 * [GarantiasV2Screen]. No es capricho: `ReglaDelOrigenTest` fija el conjunto
 * exacto de destinos de `:app` y una ruta nueva registrada ahí lo pone rojo.
 * Las constantes de abajo sólo viajan dentro del `NavHost` anidado.
 *
 * Se expone como extensión de [NavGraphBuilder] —igual que
 * `destinoDeRegistrarVisita`— para que `:app` cablee el grafo sin conocer los
 * Composables internos.
 */
object GarantiasV2Rutas {

    /** Entrada de la captura: la bandeja de "mis folios". Sin argumentos. */
    const val INICIO: String = "garantias-v2"

    /** El alta de un folio nuevo. */
    const val ABRIR: String = "garantias-v2/abrir"

    /** El nombre del argumento con la referencia **local** del folio. */
    const val ARG_FOLIO: String = "folioRef"

    /** El nombre del argumento con la referencia **local** del artículo. */
    const val ARG_ARTICULO: String = "articuloRef"

    /** El detalle de un folio, con su línea de tiempo. */
    const val FOLIO: String = "garantias-v2/folio/{$ARG_FOLIO}"

    /** La recolección de un artículo: fotos y GPS. */
    const val RECOLECTAR: String = "garantias-v2/folio/{$ARG_FOLIO}/recolectar/{$ARG_ARTICULO}"

    /** La entrega de un folio: firma y GPS obligatorio. */
    const val ENTREGAR: String = "garantias-v2/folio/{$ARG_FOLIO}/entregar"

    /** La ruta del detalle de [folioRef]. */
    fun folio(folioRef: String): String = "garantias-v2/folio/$folioRef"

    /** La ruta de la recolección de [articuloRef] dentro de [folioRef]. */
    fun recolectar(folioRef: String, articuloRef: String): String =
        "garantias-v2/folio/$folioRef/recolectar/$articuloRef"

    /** La ruta de la entrega de [folioRef]. */
    fun entregar(folioRef: String): String = "garantias-v2/folio/$folioRef/entregar"
}

/** Registra el destino de la captura de garantías v2 en el grafo de navegación. */
fun NavGraphBuilder.destinoDeGarantiasV2(onAtras: () -> Unit) {
    composable(route = GarantiasV2Rutas.INICIO) {
        GarantiasV2Screen(onAtras = onAtras)
    }
}
