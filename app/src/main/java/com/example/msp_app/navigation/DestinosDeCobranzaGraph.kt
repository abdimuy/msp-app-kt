package com.example.msp_app.navigation

import android.content.Context
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.example.msp_app.core.speech.ui.DescargaDelDictadoConectada
import com.example.msp_app.core.speech.ui.DictadoRutas
import com.example.msp_app.feature.pagos.ui.DineroDeLaCuenta
import com.example.msp_app.feature.pagos.ui.PagosRutas
import com.example.msp_app.feature.pagos.ui.UbicacionEnElDetalle
import com.example.msp_app.feature.pagos.ui.UbicacionEnLaBitacora
import com.example.msp_app.feature.pagos.ui.destinoDeBitacora
import com.example.msp_app.feature.pagos.ui.destinoDeDetalleCliente
import com.example.msp_app.feature.pagos.ui.destinoDeListaDeClientes
import com.example.msp_app.feature.pagos.ui.destinoDeRegistrarAbono
import com.example.msp_app.feature.pagos.ui.destinoDeTicketDePago
import com.example.msp_app.feature.pagos.ui.destinosDePagos
import com.example.msp_app.feature.ubicacion.ui.UbicacionRutas
import com.example.msp_app.feature.ubicacion.ui.destinoDeUbicacion
import com.example.msp_app.feature.visitas.application.VisitasTelemetria
import com.example.msp_app.feature.visitas.ui.VisitasRutas
import com.example.msp_app.feature.visitas.ui.destinoDeRegistrarVisita
import com.example.msp_app.feature.visitas.ui.destinoDeTicketDeVisita
import com.example.msp_app.features.forgiveness.screens.ForgivenessScreen
import com.example.msp_app.ui.pagos.SueloDelUltimoCobro
import com.example.msp_app.ui.theme.ThemeController
import dagger.hilt.android.EntryPointAccessors

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
 *
 * [context] es SOLO para [navegarAlTicketLegadoDeLaVisita]: resolver la venta
 * de una visita recién registrada necesita el grafo de Hilt
 * ([TicketLegadoDeVisitaEntryPoint]), y `EntryPointAccessors.fromApplication`
 * pide un [android.content.Context]. Se recibe como parámetro en vez de leer
 * `navController.context` —que existe, pero está marcado
 * `@RestrictTo(LIBRARY_GROUP)`, de uso interno de `androidx.navigation`— y en
 * vez de `LocalContext.current`, porque esta función no es `@Composable`.
 */
fun NavGraphBuilder.destinosDeCobranza(navController: NavController, context: Context) {
    // Sin `onAtras`: la lista es pantalla de nivel superior y ya no pinta flecha
    // de volver — se llega desde el cajón.
    destinoDeListaDeClientes(
        onAbrirCliente = { navController.navigate(PagosRutas.detalleCliente(it)) }
    )

    // El detalle de cliente va aparte: perdió el "⋯" y su sección "últimos
    // contactos" (decisión del dueño), así que ya no comparte callbacks con el
    // detalle de venta ni conoce la bitácora — la única puerta a ella es hoy
    // "ver los N contactos" del detalle de VENTA, más abajo en
    // `destinosDePagos`.
    destinoDeDetalleCliente(
        onAtras = { navController.popBackStack() },
        onAbrirVenta = { navController.navigate(PagosRutas.detalleVenta(it)) },
        onRegistrarVisita = { clienteId, ventaId ->
            navController.navigate(VisitasRutas.registrar(clienteId, ventaId))
        },
        // Las dos acciones de dinero de una CUENTA. Condonar desde el cliente,
        // por el "⋯" del dock, es la MISMA ruta que el detalle de venta ya
        // usaba y el mismo `NewForgivenessDialog` sin reescribir: lo único
        // nuevo es de dónde sale el `ventaId`, que ahora puede venir de la hoja
        // "¿de cuál cuenta?".
        dinero = DineroDeLaCuenta(
            onRegistrarAbono = { ventaId ->
                navController.navigate(PagosRutas.registrarAbono(ventaId))
            },
            onCondonar = { ventaId ->
                navController.navigate(Screen.Forgiveness.createRoute(ventaId))
            }
        ),
        // Ver la puerta con zoom es un destino de `:app`: el mapa completo
        // necesita `play-services-maps`, que se declara acá y no en el feature.
        //
        // Sin `pagoId`: el detalle de cliente perdió su sección "últimos
        // contactos" (decisión del dueño), así que lo único que abre este mapa
        // hoy es el cuadro de la puerta, que nunca tiene una medición que
        // destacar. Por la misma razón, sin `onVerTicket`: ya no hay hoja que
        // pregunte "¿qué abrir?" desde esta pantalla.
        ubicacion = UbicacionEnElDetalle(
            onVerLugares = { clienteId, direccion ->
                navController.navigate(UbicacionRutas.lugares(clienteId, direccion))
            },
            // El mapa chico del cuadro. Su default es no pintar nada, así el
            // cuadro se queda con su dibujo y los goldens del módulo siguen
            // fotografiando algo que no depende de la red.
            suelo = { punto, tocar -> SueloDelUltimoCobro(punto, tocar) }
        )
    )

    // El mapa de TODOS los lugares del cliente. Reemplazó a la pantalla de un
    // solo punto que vivía en `:app`: ya no contesta "aquí cobraste la última
    // vez" sino "dónde se ha cobrado siempre", que es lo que deja ver si el
    // cliente se mudó.
    //
    // "Cómo llegar" NO entra por ranura: el módulo tiene su propio
    // `AbrirEnMapasPort`, que `:app` ata al MISMO `IntentAccionesExternasAdapter`
    // de siempre (ver `UbicacionPortsModule`). El `geo:` lo sigue armando un
    // solo lugar.
    destinoDeUbicacion(
        onAtras = { navController.popBackStack() },
        onAlternarTema = ThemeController::toggle
    )

    destinoDeBitacora(
        onAtras = { navController.popBackStack() },
        // El MISMO destino que abre el cuadro de la puerta del detalle, con otro
        // punto: el del abono o el de la visita que se tocó. Traducir id → ruta es
        // todo lo que pasa aquí; cuál punto viaja lo decidió la fila.
        ubicacion = UbicacionEnLaBitacora(
            onVerLugares = { clienteId, ventaId, direccion, pagoId ->
                navController.navigate(
                    UbicacionRutas.lugares(
                        clienteId = clienteId ?: 0,
                        direccion = direccion,
                        pagoId = pagoId.ifBlank { null },
                        ventaId = ventaId ?: 0
                    )
                )
            },
            // La opción "Ticket" de la hoja que sale sobre el último cobro de
            // hoy. Va al ticket LEGADO (`Screen.PaymentTicket` →
            // `PaymentTicketScreen`), no al del módulo: por decisión del
            // dueño, el papel migrado perdía el teléfono y el WhatsApp del
            // negocio —a donde llama el cliente que reclama—, el teléfono
            // del agente, la fecha de la venta, los productos, el precio a
            // meses, el de contado, el enganche, los tres vendedores y el
            // estado en la dirección. Con esto convergen los dos papeles que
            // hoy salían del mismo teléfono: un abono imprimía el nuevo y una
            // condonación el viejo (`NewForgivenessDialog` →
            // `payment_ticket/{id}`). Se pierde la marca de reimpresión, el
            // registro de impresiones, la regla del día (el nuevo sólo
            // imprime el día del cobro; el viejo imprime cualquier día, que
            // es lo que el cobrador espera) y el `Cobro` correcto en
            // reimpresiones. `PagosRutas.TICKET_PAGO` /
            // `destinoDeTicketDePago` quedan registrados sin punto de
            // entrada, a propósito, para el día que se reencienda. Sigue
            // siendo la MISMA ruta que la captura de abono usa al terminar
            // (`destinoDeRegistrarAbono`), sin `popUpTo`: aquí el ticket se
            // apila encima, así que volver deja al cobrador donde estaba.
            onVerTicket = { pagoId ->
                navController.navigate(Screen.PaymentTicket.createRoute(pagoId))
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
        // "Ver los N abonos" abre el detalle legado: ahí vive la condonación,
        // cuya lógica este plan declaró intacta, y con ella el mapa de la
        // venta, los productos y el historial completo de abonos. El "⋯" que
        // abría la MISMA pantalla se quitó (el dueño no lo quiere ver más);
        // esta puerta se queda porque nadie pidió cerrarla.
        onVerAbonos = { ventaId ->
            navController.navigate(Screen.SaleDetails.createRoute(ventaId))
        },
        // El flujo de garantías está indexado por CRÉDITO
        // (`DOCTO_CC_ID`), no por el `EXTERNAL_ID` de la garantía: es la
        // misma ruta y el mismo id que ya usa `GuaranteeSection`.
        onVerGarantia = { creditoId ->
            navController.navigate(Screen.Guarantee.createRoute(creditoId.toString()))
        },
        // Puerta NUEVA (el dueño la pidió en el detalle de venta nuevo, no
        // sólo en el legado): condonar sigue siendo `NewForgivenessDialog`,
        // sin reescribir — este destino sólo lo resuelve y lo monta. El
        // `ventaId` es el `DOCTO_CC_ACR_ID`, el mismo espacio que
        // `Screen.Forgiveness` documenta.
        onCondonar = { ventaId ->
            navController.navigate(Screen.Forgiveness.createRoute(ventaId))
        },
        // "Ver los N contactos" de "lo que ha pasado": la bitácora completa de
        // ESTA cuenta. Es el reemplazo de lo que el detalle de cliente ofrecía
        // antes de perder su sección "últimos contactos" (decisión del dueño).
        onVerContactos = { ventaId ->
            navController.navigate(PagosRutas.bitacora(ventaId))
        },
        // El MISMO destino que abre el cuadro de la puerta del detalle de
        // cliente y la bitácora, con el punto de ESE renglón.
        ubicacion = UbicacionEnLaBitacora(
            onVerLugares = { clienteId, ventaId, direccion, pagoId ->
                navController.navigate(
                    UbicacionRutas.lugares(
                        clienteId = clienteId ?: 0,
                        direccion = direccion,
                        pagoId = pagoId.ifBlank { null },
                        ventaId = ventaId ?: 0
                    )
                )
            },
            // La opción "Ticket" de la hoja que sale sobre el último cobro de
            // hoy. Va al ticket LEGADO (`Screen.PaymentTicket` →
            // `PaymentTicketScreen`), no al del módulo: por decisión del
            // dueño, el papel migrado perdía el teléfono y el WhatsApp del
            // negocio —a donde llama el cliente que reclama—, el teléfono
            // del agente, la fecha de la venta, los productos, el precio a
            // meses, el de contado, el enganche, los tres vendedores y el
            // estado en la dirección. Con esto convergen los dos papeles que
            // hoy salían del mismo teléfono: un abono imprimía el nuevo y una
            // condonación el viejo (`NewForgivenessDialog` →
            // `payment_ticket/{id}`). Se pierde la marca de reimpresión, el
            // registro de impresiones, la regla del día (el nuevo sólo
            // imprime el día del cobro; el viejo imprime cualquier día, que
            // es lo que el cobrador espera) y el `Cobro` correcto en
            // reimpresiones. `PagosRutas.TICKET_PAGO` /
            // `destinoDeTicketDePago` quedan registrados sin punto de
            // entrada, a propósito, para el día que se reencienda. Sigue
            // siendo la MISMA ruta que la captura de abono usa al terminar
            // (`destinoDeRegistrarAbono`), sin `popUpTo`: aquí el ticket se
            // apila encima, así que volver deja al cobrador donde estaba.
            onVerTicket = { pagoId ->
                navController.navigate(Screen.PaymentTicket.createRoute(pagoId))
            }
        )
    )

    destinoDeRegistrarAbono(
        onAtras = { navController.popBackStack() },
        // El ticket LEGADO (`Screen.PaymentTicket` → `PaymentTicketScreen`)
        // REEMPLAZA a la captura en la pila: volver desde el ticket tiene
        // que llevar a la venta, nunca a un teclado de montos con el abono
        // ya registrado detrás. Va al legado, no al del módulo, por
        // decisión del dueño: el papel migrado perdía el teléfono y el
        // WhatsApp del negocio —a donde llama el cliente que reclama—, el
        // teléfono del agente, la fecha de la venta, los productos, el
        // precio a meses, el de contado, el enganche, los tres vendedores y
        // el estado en la dirección. Con esto convergen los dos papeles que
        // hoy salían del mismo teléfono: un abono imprimía el nuevo y una
        // condonación el viejo (`NewForgivenessDialog` →
        // `payment_ticket/{id}`). Se pierde la marca de reimpresión, el
        // registro de impresiones, la regla del día (el nuevo sólo imprime
        // el día del cobro; el viejo imprime cualquier día, que es lo que
        // el cobrador espera) y el `Cobro` correcto en reimpresiones.
        // `PagosRutas.TICKET_PAGO` / `destinoDeTicketDePago` quedan
        // registrados sin punto de entrada, a propósito, para el día que se
        // reencienda.
        onRegistrado = { pagoId ->
            navController.navigate(Screen.PaymentTicket.createRoute(pagoId)) {
                popUpTo(PagosRutas.REGISTRAR_ABONO) { inclusive = true }
            }
        }
    )

    // Destino ESTACIONADO: `PagosRutas.TICKET_PAGO` sigue registrado a
    // propósito (borrarlo dejaría `TicketDePagoScreen` sin ningún llamador
    // alcanzable y pondría en rojo `CadaPantallaSeAlcanzaDesdeElGrafoTest`),
    // pero hoy nada navega aquí: los cuatro puntos de entrada que antes lo
    // usaban vuelven al ticket legado (`Screen.PaymentTicket`) por decisión
    // del dueño. Queda listo para el día que se reencienda.
    destinoDeTicketDePago(onAtras = { navController.popBackStack() })

    destinoDeRegistrarVisita(
        onAtras = { navController.popBackStack() },
        // El ticket LEGADO (`Screen.VisitTicket` → `VisitTicketScreen`)
        // REEMPLAZA a la captura en la pila, igual que el abono con
        // `Screen.PaymentTicket` más arriba — mismo `popUpTo`, mismo motivo:
        // volver desde el ticket tiene que llevar a donde se entró a
        // registrar, nunca a un formulario ya enviado. Va al legado por
        // decisión del dueño (2026-09-29): la pantalla nueva de
        // `:feature:visitas` todavía no tiene los tres papeles que Microsip
        // conocía por separado (visita / cliente moroso / no pago), y el
        // legado sí. `VisitasRutas.TICKET` / `destinoDeTicketDeVisita` quedan
        // registrados sin punto de entrada, a propósito, para el día que se
        // reencienda — ver la nota sobre ese destino, abajo.
        onRegistrada = { visitaId ->
            navegarAlTicketLegadoDeLaVisita(
                navController = navController,
                visitaId = visitaId,
                resolverVentaDeLaVisita = { id ->
                    puertosDelTicketLegadoDeVisita(context)
                        .visitaImpresaPort()
                        .visita(id)
                        ?.ventaId
                },
                reportarSinVenta = {
                    puertosDelTicketLegadoDeVisita(context).telemetry().error(
                        code = VisitasTelemetria.CODE_TICKET_VISITA_LEGADO_SIN_VENTA,
                        message = "el ticket legado de la visita no pudo resolver una cuenta",
                        props = emptyMap()
                    )
                }
            )
        }
    )

    // Destino ESTACIONADO, mismo patrón que `destinoDeTicketDePago` más
    // arriba: `VisitasRutas.TICKET` sigue registrado a propósito (borrarlo
    // dejaría `TicketDeVisitaScreen` sin ningún llamador alcanzable y
    // pondría en rojo `CadaPantallaSeAlcanzaDesdeElGrafoTest`), pero hoy nada
    // navega aquí — "registrar visita" vuelve al ticket LEGADO por decisión
    // del dueño, ver la nota de arriba. Queda listo para el día que la
    // pantalla nueva tenga los tres papeles y se reencienda.
    destinoDeTicketDeVisita(onAtras = { navController.popBackStack() })

    destinoDeCondonacion(navController)

    destinosDeDescargas(navController)
}

/**
 * Registra la **condonación** ([Screen.Forgiveness]) en el grafo.
 *
 * Va aparte de [destinosDePagos] —igual que [destinoDeLaUbicacion]— porque no
 * es un destino de `:feature:pagos`: monta [ForgivenessScreen], que resuelve
 * la venta con `SaleDetailsViewModel` (de `:app`) y el diálogo legado
 * `NewForgivenessDialog`, sin reescribir ninguno de los dos.
 *
 * El argumento se lee como `String` y se convierte a `Int` a mano, sin
 * `navArgument(... IntType)` — el mismo molde que ya usan `Screen.SaleDetails`
 * y `Screen.SaleMap`. Una venta que no resuelve **no llega a pintarse**:
 * [ForgivenessScreen] hace `popBackStack()` por su cuenta, así que aquí no
 * hace falta un segundo guardarraíl.
 */
private fun NavGraphBuilder.destinoDeCondonacion(navController: NavController) {
    composable(Screen.Forgiveness.route) { backStackEntry ->
        val saleId = backStackEntry.arguments?.getString("saleId")?.toIntOrNull()
        if (saleId != null) {
            ForgivenessScreen(saleId = saleId, navController = navController)
        } else {
            navController.popBackStack()
        }
    }
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
 * Lleva al ticket LEGADO ([Screen.VisitTicket]) de la venta de la visita
 * [visitaId] recién registrada, con [VisitasRutas.REGISTRAR] fuera de la
 * pila — mismo patrón que el abono usa para llegar a [Screen.PaymentTicket].
 *
 * Separada de la lambda que [destinosDeCobranza] cablea para poder probarla
 * con un [resolverVentaDeLaVisita] falso —sin Hilt, sin Room, sin
 * Compose—: lo único que toca infraestructura de verdad en producción es ese
 * parámetro (ver [puertosDelTicketLegadoDeVisita]).
 *
 * `null` es un caso DEFENSIVO, no esperado: `RegistroDeVisitaAdapter
 * .cuentaDeLaVisita` nunca escribe `IMPTE_DOCTO_CC_ID = 0`, así que una
 * visita recién registrada siempre tiene cuenta. Si aun así no resuelve
 * —la visita ya no está—, no se navega a una ruta sin argumento: se reporta
 * con [reportarSinVenta] y el cobrador se queda donde estaba, en vez de
 * crashear.
 */
internal suspend fun navegarAlTicketLegadoDeLaVisita(
    navController: NavController,
    visitaId: String,
    resolverVentaDeLaVisita: suspend (String) -> Int?,
    reportarSinVenta: () -> Unit = {}
) {
    val ventaId = resolverVentaDeLaVisita(visitaId)
    if (ventaId == null) {
        reportarSinVenta()
        return
    }
    navController.navigate(Screen.VisitTicket.createRoute(ventaId.toString())) {
        popUpTo(VisitasRutas.REGISTRAR) { inclusive = true }
    }
}

/**
 * El único punto donde [navegarAlTicketLegadoDeLaVisita] toca el grafo de
 * Hilt de verdad — ver [TicketLegadoDeVisitaEntryPoint] para por qué un
 * `EntryPoint` y no `@Inject`: este callback no lo construye Hilt, lo
 * construye `destinosDeCobranza` a mano.
 */
private fun puertosDelTicketLegadoDeVisita(context: Context): TicketLegadoDeVisitaEntryPoint =
    EntryPointAccessors.fromApplication(
        context.applicationContext,
        TicketLegadoDeVisitaEntryPoint::class.java
    )
