package com.example.msp_app.navigation

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.example.msp_app.core.mapas.ui.DescargaDelMapaConectada
import com.example.msp_app.core.mapas.ui.MapasRutas
import com.example.msp_app.core.speech.ui.DescargaDelDictadoConectada
import com.example.msp_app.core.speech.ui.DictadoRutas
import com.example.msp_app.feature.pagos.ui.PagosRutas
import com.example.msp_app.feature.pagos.ui.destinoDeBitacora
import com.example.msp_app.feature.pagos.ui.destinoDeDetalleCliente
import com.example.msp_app.feature.pagos.ui.destinoDeListaDeClientes
import com.example.msp_app.feature.pagos.ui.destinoDeRegistrarAbono
import com.example.msp_app.feature.pagos.ui.destinoDeTicketDePago
import com.example.msp_app.feature.pagos.ui.destinosDePagos
import com.example.msp_app.feature.visitas.ui.VisitasRutas
import com.example.msp_app.feature.visitas.ui.destinoDeRegistrarVisita
import com.example.msp_app.feature.visitas.ui.destinoDeTicketDeVisita

/**
 * **Los destinos de arquitectura nueva que `:app` monta** — las ocho pantallas
 * de cobranza y visitas (Tasks 16-20, el trabajo de la Task 21) más las dos
 * pantallas de descarga opcional.
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
        onVerContactos = { clienteId -> navController.navigate(PagosRutas.bitacora(clienteId)) }
    )

    destinoDeBitacora(onAtras = { navController.popBackStack() })

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
 * **Las dos descargas opcionales: el modelo de voz y el extracto de mapa.**
 *
 * ## El defecto que esto cierra
 *
 * Las dos pantallas existían, estaban probadas y con goldens, y **ninguna
 * estaba registrada en el grafo**. O sea: el cobrador no podía llegar a bajar
 * ni el mapa ni el dictado de alta fidelidad. Dos funciones completas y
 * muertas, cada una copiando el precedente de la otra.
 *
 * ## Por qué se registran acá dentro y no aparte
 *
 * No son cobranza, y sin embargo van dentro de [destinosDeCobranza] a
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
 * tiene por qué. Lo que sí sale del módulo es **la cadena** (`DictadoRutas`,
 * `MapasRutas`) y el composable ya cableado, para que el ViewModel y el peso
 * anunciado se queden adentro.
 */
private fun NavGraphBuilder.destinosDeDescargas(navController: NavController) {
    composable(DictadoRutas.DESCARGA) {
        DescargaDelDictadoConectada(onAtras = { navController.popBackStack() })
    }

    composable(MapasRutas.DESCARGA) {
        DescargaDelMapaConectada(onAtras = { navController.popBackStack() })
    }
}
