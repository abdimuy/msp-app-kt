package com.example.msp_app.feature.ubicacion.ui

import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.navArgument

/**
 * La ruta del mapa de lugares.
 *
 * **Viaja el `clienteId`, no una coordenada.** La pantalla anterior recibía
 * `lat`/`lng` porque enseñaba un punto; ésta enseña *todos* los del cliente, así
 * que lo que necesita es a quién mirar. De paso desaparece la fragilidad de
 * mandar coordenadas por la URL (iban como texto justamente para que un `Float`
 * no las redondeara).
 *
 * [ARG_PAGO] es opcional y es lo que distingue las tres entradas: vacío desde el
 * cuadro del detalle de cliente, y con el id del abono desde la bitácora o el
 * detalle de venta — que es el que entra **resaltado**.
 */
object UbicacionRutas {

    const val ARG_CLIENTE: String = "clienteId"
    const val ARG_DIRECCION: String = "direccion"
    const val ARG_PAGO: String = "pagoId"

    /** La cuenta desde la que se entró, cuando el `clienteId` no se conoce. */
    const val ARG_VENTA: String = "ventaId"

    const val LUGARES: String =
        "ubicacion/{$ARG_CLIENTE}?$ARG_DIRECCION={$ARG_DIRECCION}" +
            "&$ARG_PAGO={$ARG_PAGO}&$ARG_VENTA={$ARG_VENTA}"

    /**
     * La ruta concreta.
     *
     * [clienteId] en 0 con [ventaId] puesto es la entrada del detalle de venta,
     * que conoce la cuenta y no al cliente; lo resuelve el ViewModel.
     */
    fun lugares(
        clienteId: Int,
        direccion: String,
        pagoId: String? = null,
        ventaId: Int = 0
    ): String = "ubicacion/$clienteId" +
        "?$ARG_DIRECCION=${Uri.encode(direccion)}" +
        "&$ARG_PAGO=${Uri.encode(pagoId.orEmpty())}" +
        "&$ARG_VENTA=$ventaId"
}

/**
 * Registra el mapa de lugares en el grafo.
 *
 * [onComoLlegar] es una **ranura**: armar el `geo:` vive en un solo lugar de la
 * app (`IntentAccionesExternasAdapter`, en `:feature:pagos`) y este módulo no
 * puede —ni debe— alcanzar otro feature. Quien la cierra es `:app`, que ya tiene
 * los dos módulos y el adaptador cableado. Es el mismo patrón con el que el
 * cuadro chico del detalle recibe su mapa.
 */
fun NavGraphBuilder.destinoDeUbicacion(onAtras: () -> Unit, onAlternarTema: () -> Unit = {}) {
    composable(
        route = UbicacionRutas.LUGARES,
        arguments = listOf(
            navArgument(UbicacionRutas.ARG_CLIENTE) { type = NavType.IntType },
            navArgument(UbicacionRutas.ARG_DIRECCION) {
                type = NavType.StringType
                defaultValue = ""
            },
            navArgument(UbicacionRutas.ARG_PAGO) {
                type = NavType.StringType
                defaultValue = ""
            },
            navArgument(UbicacionRutas.ARG_VENTA) {
                type = NavType.IntType
                defaultValue = 0
            }
        )
    ) { entrada ->
        val clienteId = entrada.arguments?.getInt(UbicacionRutas.ARG_CLIENTE) ?: return@composable
        val direccion = entrada.arguments?.getString(UbicacionRutas.ARG_DIRECCION).orEmpty()
        val pagoId = entrada.arguments?.getString(UbicacionRutas.ARG_PAGO).orEmpty()
        val ventaId = entrada.arguments?.getInt(UbicacionRutas.ARG_VENTA) ?: 0
        UbicacionConectada(
            clienteId = clienteId,
            ventaId = ventaId,
            direccion = direccion,
            pagoResaltado = pagoId.ifBlank { null },
            onAtras = onAtras,
            onAlternarTema = onAlternarTema
        )
    }
}

/**
 * Ata el ViewModel a la pantalla pura **y provee el tema**.
 *
 * ## Por qué el tema se provee aquí y no lo hereda del `NavHost`
 *
 * `:app` monta `MspappTheme` —el Material legado—, **nunca `MspTheme`**. Una
 * pantalla Msp que no se envuelva a sí misma revienta al abrirse desde el
 * `NavHost` de `:app`. Lo cobra automáticamente
 * `CadaPantallaMspProveeSuTemaTest`, que barre las fuentes de todos los módulos
 * y exige que toda pantalla que lea `MspTheme.` la provea por un composable con
 * ranura de contenido — no vale reusar un `*Content` de otro archivo.
 *
 * Va por [MspThemeRevealHost] y no por un `MspTheme` pelado porque el mismo
 * control no puede sentirse distinto en dos pantallas de la misma app: es el
 * mecanismo que ya usan las ocho de cobranza.
 *
 * [onAlternarTema] es una **ranura**: `ThemeController` vive en `:app` y este
 * módulo no lo alcanza. Su default no hace nada, que es lo que deja montar la
 * pantalla en un test o en un `@Preview` sin saber que existe un controlador.
 */
@Composable
private fun UbicacionConectada(
    clienteId: Int,
    ventaId: Int,
    direccion: String,
    pagoResaltado: String?,
    onAtras: () -> Unit,
    onAlternarTema: () -> Unit,
    viewModel: UbicacionViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(clienteId, ventaId, pagoResaltado) {
        viewModel.cargar(clienteId, direccion, pagoResaltado, ventaId.takeIf { it > 0 })
    }
    UbicacionScreen(
        state = state,
        onAtras = onAtras,
        onComoLlegar = { lugar -> viewModel.comoLlegar(lugar.lugar, direccion) },
        onFiltro = viewModel::cambiarFiltro,
        onTocarLugar = viewModel::tocarLugar,
        onAlternarTema = onAlternarTema
    )
}
