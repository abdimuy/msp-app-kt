package com.example.msp_app.navigation

import android.net.Uri
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.example.msp_app.core.speech.ui.DescargaDelDictadoConectada
import com.example.msp_app.core.speech.ui.DictadoRutas
import com.example.msp_app.feature.pagos.ui.PagosRutas
import com.example.msp_app.feature.pagos.ui.UbicacionEnElDetalle
import com.example.msp_app.feature.pagos.ui.UbicacionEnLaBitacora
import com.example.msp_app.feature.pagos.ui.destinoDeBitacora
import com.example.msp_app.feature.pagos.ui.destinoDeDetalleCliente
import com.example.msp_app.feature.pagos.ui.destinoDeListaDeClientes
import com.example.msp_app.feature.pagos.ui.destinoDeRegistrarAbono
import com.example.msp_app.feature.pagos.ui.destinoDeTicketDePago
import com.example.msp_app.feature.pagos.ui.destinosDePagos
import com.example.msp_app.feature.visitas.ui.VisitasRutas
import com.example.msp_app.feature.visitas.ui.destinoDeRegistrarVisita
import com.example.msp_app.feature.visitas.ui.destinoDeTicketDeVisita
import com.example.msp_app.ui.pagos.SueloDelUltimoCobro
import com.example.msp_app.ui.pagos.UbicacionDelClienteScreen

/**
 * **Los destinos de arquitectura nueva que `:app` monta** — las ocho pantallas
 * de cobranza y visitas (Tasks 16-20, el trabajo de la Task 21) más la
 * pantalla de descarga opcional.
 *
 * Vive fuera de `AppNavigation` por una razón de prueba, no de estética:
 * `AppNavigation` es un `@Composable` que levanta Firebase, Hilt y los
 * observadores de sincronización, así que ningún test puede montarlo. Esta
 * función, en cambio, se monta entera en un `TestNavHostController` y deja
 * afirmar **el destino y su argumento** sobre EL MISMO código que corre en el
 * teléfono, en vez de sobre una copia del grafo escrita en el test.
 *
 * La **regla del origen** —de dónde se entra al cliente y de dónde a la venta—
 * no vive aquí: vive en [DestinosDeCobranza], junto a los puntos de entrada que
 * la aplican. Aquí solo se traduce id → ruta.
 *
 * `onAtras` es siempre `popBackStack()`: cada pantalla vuelve por donde entró, y
 * es eso lo que permite que la MISMA pantalla de venta sirva desde la lista,
 * desde un pago y desde un recibo sin tener que saber cuál fue.
 */
fun NavGraphBuilder.destinosDeCobranza(navController: NavController) {
    // Sin `onAtras`: la lista es pantalla de nivel superior y ya no pinta flecha
    // de volver — se llega desde el cajón.
    destinoDeListaDeClientes(
        onAbrirCliente = { navController.navigate(PagosRutas.detalleCliente(it)) }
    )

    // El detalle de cliente va aparte: perdió el "⋯" y ganó la bitácora, así que
    // ya no comparte callbacks con el detalle de venta.
    destinoDeDetalleCliente(
        onAtras = { navController.popBackStack() },
        onAbrirVenta = { navController.navigate(PagosRutas.detalleVenta(it)) },
        onRegistrarAbono = { ventaId ->
            navController.navigate(PagosRutas.registrarAbono(ventaId))
        },
        onRegistrarVisita = { clienteId, ventaId ->
            navController.navigate(VisitasRutas.registrar(clienteId, ventaId))
        },
        onVerContactos = { clienteId -> navController.navigate(PagosRutas.bitacora(clienteId)) },
        // Ver la puerta con zoom es un destino de `:app`: el mapa completo
        // necesita `play-services-maps`, que se declara acá y no en el feature.
        ubicacion = UbicacionEnElDetalle(
            onVer = { punto, direccion ->
                navController.navigate(
                    RutasDelMapa.ubicacion(punto.lat, punto.lng, direccion)
                )
            },
            // El mapa chico del cuadro. Su default es no pintar nada, así el
            // cuadro se queda con su dibujo y los goldens del módulo siguen
            // fotografiando algo que no depende de la red.
            suelo = { punto -> SueloDelUltimoCobro(punto) }
        )
    )

    destinoDeLaUbicacion(navController)

    destinoDeBitacora(
        onAtras = { navController.popBackStack() },
        // El MISMO destino que abre el cuadro de la puerta del detalle, con otro
        // punto: el del abono o el de la visita que se tocó. Traducir id → ruta es
        // todo lo que pasa aquí; cuál punto viaja lo decidió la fila.
        ubicacion = UbicacionEnLaBitacora(
            onVer = { punto, direccion ->
                navController.navigate(
                    RutasDelMapa.ubicacion(punto.lat, punto.lng, direccion)
                )
            }
        )
    )

    destinosDePagos(
        onAtras = { navController.popBackStack() },
        onRegistrarAbono = { ventaId ->
            navController.navigate(PagosRutas.registrarAbono(ventaId))
        },
        onRegistrarVisita = { clienteId, ventaId ->
            navController.navigate(VisitasRutas.registrar(clienteId, ventaId))
        },
        // El "⋯" abre el detalle legado: ahí vive la condonación, cuya
        // lógica este plan declaró intacta, y con ella el mapa de la
        // venta, los productos y el historial completo de abonos.
        onMasAcciones = { ventaId ->
            navController.navigate(Screen.SaleDetails.createRoute(ventaId))
        },
        // El flujo de garantías está indexado por CRÉDITO
        // (`DOCTO_CC_ID`), no por el `EXTERNAL_ID` de la garantía: es la
        // misma ruta y el mismo id que ya usa `GuaranteeSection`.
        onVerGarantia = { creditoId ->
            navController.navigate(Screen.Guarantee.createRoute(creditoId.toString()))
        }
    )

    destinoDeRegistrarAbono(
        onAtras = { navController.popBackStack() },
        // El ticket REEMPLAZA a la captura en la pila: volver desde el
        // ticket tiene que llevar a la venta, nunca a un teclado de
        // montos con el abono ya registrado detrás.
        onRegistrado = { pagoId ->
            navController.navigate(PagosRutas.ticketDePago(pagoId)) {
                popUpTo(PagosRutas.REGISTRAR_ABONO) { inclusive = true }
            }
        }
    )

    destinoDeTicketDePago(onAtras = { navController.popBackStack() })

    destinoDeRegistrarVisita(
        onAtras = { navController.popBackStack() },
        onRegistrada = { visitaId ->
            navController.navigate(VisitasRutas.ticketDeVisita(visitaId)) {
                popUpTo(VisitasRutas.REGISTRAR) { inclusive = true }
            }
        }
    )

    destinoDeTicketDeVisita(onAtras = { navController.popBackStack() })

    destinosDeDescargas(navController)
}

/**
 * **La descarga opcional: el modelo de voz de alta fidelidad.**
 *
 * ## El defecto que esto cierra
 *
 * La pantalla existía, estaba probada y con goldens, y **no estaba registrada
 * en el grafo**. O sea: el cobrador no podía llegar a bajar el dictado de alta
 * fidelidad. Una función completa, probada y muerta.
 *
 * Aquí se registraba también la descarga del extracto de mapa. Se fue con
 * `:core:mapas`: sin renderizador dentro de la app no hay qué hacer con el
 * `.pmtiles`, y "cómo llegar" abre la app de mapas del teléfono.
 *
 * ## Por qué se registra acá dentro y no aparte
 *
 * No es cobranza, y sin embargo va dentro de [destinosDeCobranza] a
 * propósito: esa función es la que barren las cuatro redes de `:app`
 * —`CadaDestinoDeCobranzaSeMontaTest` las compone de verdad con Hilt,
 * `CadaPantallaDeCobranzaRespetaLaBarraDeEstadoTest` les mide el inset y los
 * 50 dp de cada control, `CadaPantallaMspProveeSuTemaTest` cuenta los destinos
 * y `ReglaDelOrigenTest` verifica que ninguna ruta quede huérfana—. Un grafo
 * hermano registrado por su cuenta se habría quedado fuera de las cuatro, que
 * es exactamente la clase de hueco que este cambio vino a tapar.
 *
 * ## Por qué el `composable {}` vive en `:app` y no en el módulo
 *
 * Igual que `VersionBlockedScreen` de `:core:appgate`, que `:app` también
 * monta: ningún `:core:*` depende de `androidx.navigation.compose` y ninguno
 * tiene por qué. Lo que sí sale del módulo es **la cadena** (`DictadoRutas`)
 * y el composable ya cableado, para que el ViewModel y el peso anunciado se
 * queden adentro.
 */
private fun NavGraphBuilder.destinosDeDescargas(navController: NavController) {
    composable(DictadoRutas.DESCARGA) {
        DescargaDelDictadoConectada(onAtras = { navController.popBackStack() })
    }
}

/**
 * **Las rutas del mapa grande.** Viven en `:app` y no en `PagosRutas` porque la
 * pantalla que las consume vive en `:app`: ningún `:feature:*` declara
 * `play-services-maps` ni debe hacerlo. Mismo reparto que `DictadoRutas`, que sí
 * sale de su módulo porque la pantalla también es del módulo.
 *
 * Las coordenadas viajan como **texto** y no como `FloatType`: un `Float` tiene
 * ~7 dígitos significativos y una latitud con cinco decimales ya los gasta, así
 * que redondear la ruta movería el pin metros — justo el error que el pin de
 * esta app existe para no cometer.
 */
object RutasDelMapa {

    /** Argumento de latitud del mapa grande. */
    const val ARG_LAT: String = "lat"

    /** Argumento de longitud del mapa grande. */
    const val ARG_LNG: String = "lng"

    /** Argumento opcional con la dirección escrita de la puerta. */
    const val ARG_DIRECCION: String = "direccion"

    /** La ubicación de una puerta, a pantalla completa. */
    const val UBICACION: String =
        "pagos/ubicacion/{$ARG_LAT}/{$ARG_LNG}?$ARG_DIRECCION={$ARG_DIRECCION}"

    /** La ruta concreta hacia [UBICACION]. */
    fun ubicacion(lat: Double, lng: Double, direccion: String): String =
        "pagos/ubicacion/$lat/$lng?$ARG_DIRECCION=" + Uri.encode(direccion)
}

/**
 * Registra el **mapa grande** en el grafo.
 *
 * La ruta lleva las dos coordenadas y **la dirección escrita**, codificada con
 * `Uri.encode` —una ruta es una URL y una calle mexicana trae acentos, comas y
 * a veces diagonales—. La dirección viaja porque la hoja al pie la necesita: una
 * coordenada suelta no le dice a nadie de qué puerta se trata.
 *
 * El **nombre del cliente NO viaja**, y ésa es la diferencia: en esta pantalla no
 * aporta nada —el cobrador acaba de tocar el cuadro dentro del detalle de ese
 * cliente— y es dato personal en un lugar donde no hace falta.
 *
 * Una coordenada ilegible vuelve atrás en vez de abrir un mapa en el meridiano
 * cero. Es el mismo criterio que `UbicacionDelCobro.de` aplica al leer Room:
 * media coordenada es un dato falso, no uno incompleto.
 */
private fun NavGraphBuilder.destinoDeLaUbicacion(navController: NavController) {
    composable(
        route = RutasDelMapa.UBICACION,
        arguments = listOf(
            navArgument(RutasDelMapa.ARG_LAT) { type = NavType.StringType },
            navArgument(RutasDelMapa.ARG_LNG) { type = NavType.StringType },
            navArgument(RutasDelMapa.ARG_DIRECCION) {
                type = NavType.StringType
                defaultValue = ""
            }
        )
    ) { entrada ->
        val lat = entrada.arguments?.getString(RutasDelMapa.ARG_LAT)?.toDoubleOrNull()
        val lng = entrada.arguments?.getString(RutasDelMapa.ARG_LNG)?.toDoubleOrNull()
        if (lat == null || lng == null) {
            navController.popBackStack()
            return@composable
        }
        UbicacionDelClienteScreen(
            lat = lat,
            lng = lng,
            direccion = entrada.arguments?.getString(RutasDelMapa.ARG_DIRECCION).orEmpty(),
            onAtras = { navController.popBackStack() }
        )
    }
}
