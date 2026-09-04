package com.example.msp_app.navigation

import androidx.navigation.NavDestination
import androidx.navigation.compose.ComposeNavigator
import androidx.navigation.compose.composable
import androidx.navigation.createGraph
import androidx.navigation.testing.TestNavHostController
import androidx.test.core.app.ApplicationProvider
import com.example.msp_app.core.database.dao.sale.EstadoCobranza
import com.example.msp_app.data.models.payment.Payment
import com.example.msp_app.data.models.sale.FrecuenciaPago
import com.example.msp_app.data.models.sale.Sale
import com.example.msp_app.feature.pagos.ui.PagosRutas
import com.example.msp_app.feature.visitas.ui.VisitasRutas
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * **La regla del origen, medida sobre el grafo real.**
 *
 * > Desde la **lista** y el **mapa** se entra al **cliente**.
 * > Desde un **pago** o un **recibo existente** se entra directo a **su venta**.
 *
 * Cada test navega con la MISMA cadena que produce el punto de entrada en
 * producción ([DestinosDeCobranza]) sobre el MISMO grafo que monta la app
 * ([destinosDeCobranza]), y afirma dos cosas, no una:
 *
 * 1. **el destino** — `currentDestination.route`, o sea que la ruta existe y
 *    está registrada; una cadena que nadie registró no llega a ningún lado;
 * 2. **su argumento** — el id que viaja adentro, leído del `Bundle` del back
 *    stack, que es exactamente lo que el `SavedStateHandle` del ViewModel lee.
 *
 * Afirmar solo "se navegó" dejaría pasar los dos defectos que este plan ya
 * pagó: una ruta escrita a mano que nadie registró, y un id del espacio
 * equivocado (`DOCTO_CC_ID` donde iba `DOCTO_CC_ACR_ID`, commit `721c5551`).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = android.app.Application::class)
class ReglaDelOrigenTest {

    private lateinit var nav: TestNavHostController

    /**
     * Los tres destinos **legados** a los que la regla del origen todavía manda.
     * Se declaran aquí con las MISMAS constantes que `AppNavigation` registra:
     * si alguien renombra la ruta, este test y la app cambian juntos.
     */
    private val legados = listOf(
        Screen.SaleDetails.route,
        Screen.Guarantee.route,
        Screen.PaymentTicket.route
    )

    @Before
    fun montarElGrafo() {
        nav = TestNavHostController(ApplicationProvider.getApplicationContext())
        nav.navigatorProvider.addNavigator(ComposeNavigator())
        nav.graph = nav.createGraph(startDestination = RAIZ) {
            composable(RAIZ) {}
            destinosDeCobranza(nav)
            legados.forEach { ruta -> composable(ruta) {} }
        }
    }

    private fun ruta(): String? = nav.currentDestination?.route

    private fun argInt(nombre: String): Int? = nav.currentBackStackEntry?.arguments?.getInt(nombre)

    private fun argString(nombre: String): String? =
        nav.currentBackStackEntry?.arguments?.getString(nombre)

    // -----------------------------------------------------------------------
    // Dirección 1 de la regla: desde la LISTA y el MAPA se entra al CLIENTE
    // -----------------------------------------------------------------------

    /** Punto de entrada: la fila de cliente de la lista de cobranza (Task 17). */
    @Test
    fun `desde la lista, tocar una puerta entra al CLIENTE`() {
        nav.navigate(PagosRutas.detalleCliente(CLIENTE))
        assertEquals(PagosRutas.DETALLE_CLIENTE, ruta())
        assertEquals(CLIENTE, argInt(PagosRutas.ARG_CLIENTE_ID))
    }

    /** Punto de entrada: `RouteMapScreen`, "⋯ → ver cliente" sobre un pago. */
    @Test
    fun `desde el mapa, ver cliente entra al CLIENTE`() {
        nav.navigate(DestinosDeCobranza.clienteDeUnPago(pago()))
        assertEquals(PagosRutas.DETALLE_CLIENTE, ruta())
        assertEquals(CLIENTE, argInt(PagosRutas.ARG_CLIENTE_ID))
    }

    // -----------------------------------------------------------------------
    // Dirección 2 de la regla: desde un PAGO o un RECIBO se entra a SU VENTA
    // -----------------------------------------------------------------------

    /** Punto de entrada: `PaymentItem` (pagos del día en Home, mapa de rutas). */
    @Test
    fun `desde un pago se entra a SU VENTA`() {
        nav.navigate(DestinosDeCobranza.ventaDeUnPago(pago()))
        assertEquals(PagosRutas.DETALLE_VENTA, ruta())
        // El `DOCTO_CC_ACR_ID`, NO el `DOCTO_CC_ID`: son dos espacios de id y
        // confundirlos ya costó un defecto de producción en este plan.
        assertEquals(VENTA, argInt(PagosRutas.ARG_VENTA_ID))
    }

    /** Punto de entrada: `PaymentCard` del historial de pagos de una venta. */
    @Test
    fun `desde un recibo existente se entra a SU VENTA`() {
        nav.navigate(DestinosDeCobranza.ventaDeUnRecibo(pago()))
        assertEquals(PagosRutas.DETALLE_VENTA, ruta())
        assertEquals(VENTA, argInt(PagosRutas.ARG_VENTA_ID))
    }

    /**
     * **Control negativo de la regla**: un pago cuyo cliente y cuya venta tienen
     * ids distintos no puede aterrizar en los dos lados con el mismo número. Sin
     * esto, un fixture con `CLIENTE == VENTA` haría pasar los cuatro tests de
     * arriba aunque las dos ramas estuvieran cruzadas.
     */
    @Test
    fun `cliente y venta del mismo pago NO son el mismo destino`() {
        val p = pago()
        assertEquals(PagosRutas.detalleCliente(CLIENTE), DestinosDeCobranza.clienteDeUnPago(p))
        assertEquals(PagosRutas.detalleVenta(VENTA), DestinosDeCobranza.ventaDeUnPago(p))
        assertEquals(
            false,
            DestinosDeCobranza.clienteDeUnPago(p) == DestinosDeCobranza.ventaDeUnPago(p)
        )
    }

    // -----------------------------------------------------------------------
    // El resto de los puntos de entrada
    // -----------------------------------------------------------------------

    /** Punto de entrada: la fila de venta dentro de una puerta, en la lista. */
    @Test
    fun `desde una venta de la lista se abre esa VENTA`() {
        nav.navigate(PagosRutas.detalleVenta(VENTA))
        assertEquals(PagosRutas.DETALLE_VENTA, ruta())
        assertEquals(VENTA, argInt(PagosRutas.ARG_VENTA_ID))
    }

    /** Punto de entrada: el CTA "abonar $X" del dock de la venta (Task 18). */
    @Test
    fun `el dock de la venta lleva a registrar abono de ESA venta`() {
        nav.navigate(PagosRutas.registrarAbono(VENTA))
        assertEquals(PagosRutas.REGISTRAR_ABONO, ruta())
        assertEquals(VENTA, argInt(PagosRutas.ARG_VENTA_ID))
    }

    /**
     * Punto de entrada: `SaleActionsSection` ("Agregar Visita" del detalle
     * legado) y `SaleItem` ("agregar visita" del menú de la tarjeta).
     *
     * Se visita la **puerta** (`CLIENTE_ID`) con la cuenta abierta como
     * contexto (`DOCTO_CC_ACR_ID`): los dos ids viajan, y por eso se afirman los
     * dos.
     */
    @Test
    fun `desde una venta, registrar visita lleva al CLIENTE con esa venta de contexto`() {
        nav.navigate(DestinosDeCobranza.visitaDeUnaVenta(venta()))
        assertEquals(VisitasRutas.REGISTRAR, ruta())
        assertEquals(CLIENTE, argInt(VisitasRutas.ARG_CLIENTE_ID))
        assertEquals(VENTA, argInt(VisitasRutas.ARG_VENTA_ID))
    }

    /**
     * Punto de entrada: el CTA "visita" del dock del **cliente**. Ahí no hay
     * cuenta abierta, así que el argumento viaja en el centinela
     * [VisitasRutas.SIN_VENTA] y el ViewModel lo lee como "sin venta ligada".
     */
    @Test
    fun `desde el cliente, registrar visita va sin venta ligada`() {
        nav.navigate(VisitasRutas.registrar(clienteId = CLIENTE, ventaId = null))
        assertEquals(VisitasRutas.REGISTRAR, ruta())
        assertEquals(CLIENTE, argInt(VisitasRutas.ARG_CLIENTE_ID))
        assertEquals(VisitasRutas.SIN_VENTA, argInt(VisitasRutas.ARG_VENTA_ID))
    }

    /** Punto de entrada: el "⋯" de las dos pantallas de detalle. */
    @Test
    fun `el mas acciones abre el detalle legado de ESA venta, donde vive la condonacion`() {
        nav.navigate(Screen.SaleDetails.createRoute(VENTA))
        assertEquals(Screen.SaleDetails.route, ruta())
        assertEquals(VENTA.toString(), argString("saleId"))
    }

    /** Punto de entrada: la tarjeta de garantía del detalle de venta. */
    @Test
    fun `ver la garantia abre el flujo de garantias por CREDITO`() {
        nav.navigate(Screen.Guarantee.createRoute(CREDITO.toString()))
        assertEquals(Screen.Guarantee.route, ruta())
        // `DOCTO_CC_ID`, que es la llave por la que están indexadas las
        // garantías (`getGuaranteeByDoctoCcId`) — no el `EXTERNAL_ID`.
        assertEquals(CREDITO.toString(), argString("saleId"))
    }

    /** Punto de entrada: el abono registrado (Task 18 → Task 20). */
    @Test
    fun `al registrar un abono se llega a su ticket`() {
        nav.navigate(PagosRutas.ticketDePago(PAGO_ID))
        assertEquals(PagosRutas.TICKET_PAGO, ruta())
        assertEquals(PAGO_ID, argString(PagosRutas.ARG_PAGO_ID))
    }

    /** Punto de entrada: la visita registrada (Task 19 → Task 20). */
    @Test
    fun `al registrar una visita se llega a su ticket`() {
        nav.navigate(VisitasRutas.ticketDeVisita(VISITA_ID))
        assertEquals(VisitasRutas.TICKET, ruta())
        assertEquals(VISITA_ID, argString(VisitasRutas.ARG_VISITA_ID))
    }

    /** Punto de entrada: el ítem "Clientes" del cajón (reemplazó a `SalesScreen`). */
    @Test
    fun `el cajon abre la lista de clientes`() {
        nav.navigate(PagosRutas.LISTA_CLIENTES)
        assertEquals(PagosRutas.LISTA_CLIENTES, ruta())
    }

    // -----------------------------------------------------------------------
    // Ninguna ruta huérfana
    // -----------------------------------------------------------------------

    /**
     * **Nada registrado que nadie alcance.** Se enumera el grafo y se compara
     * contra el conjunto exacto de destinos que esta tarea cableó. Si mañana
     * alguien registra una pantalla y se olvida del punto de entrada, esta lista
     * se desincroniza y el test se pone rojo — que es la única forma de que
     * "ninguna ruta huérfana" sea una propiedad y no una promesa.
     */
    @Test
    fun `el grafo de cobranza registra exactamente los destinos que algo alcanza`() {
        val registradas = nav.graph
            .filterIsInstance<NavDestination>()
            .mapNotNull { it.route }
            .toSet()
        val esperadas = setOf(
            RAIZ,
            PagosRutas.LISTA_CLIENTES,
            PagosRutas.DETALLE_CLIENTE,
            PagosRutas.DETALLE_VENTA,
            PagosRutas.REGISTRAR_ABONO,
            PagosRutas.TICKET_PAGO,
            VisitasRutas.REGISTRAR,
            VisitasRutas.TICKET
        ) + legados
        assertEquals(esperadas, registradas)
    }

    /**
     * **Nada alcanzable que no esté registrado**, por el lado de las rutas que
     * esta tarea RETIRÓ. `SalesScreen` (`"sales"`) y el ticket de visita legado
     * (`"visit_ticket/{saleId}"`) ya no existen: navegar ahí no resuelve.
     *
     * Su **control positivo** es el `assertEquals` de arriba: la misma búsqueda
     * SÍ encuentra las ocho rutas vivas más las tres legadas, así que el `null`
     * de aquí abajo es una ausencia medida, no un método que no mira.
     */
    @Test
    fun `las rutas retiradas ya no resuelven en el grafo`() {
        assertNull(nav.graph.findNode("sales"))
        assertNull(nav.graph.findNode("visit_ticket/{saleId}"))
        // Control positivo, en la misma llamada: la ruta que SÍ quedó sí resuelve.
        assertEquals(
            PagosRutas.LISTA_CLIENTES,
            nav.graph.findNode(PagosRutas.LISTA_CLIENTES)?.route
        )
    }

    private fun pago(): Payment = Payment(
        ID = PAGO_ID,
        COBRADOR = "Esperanza Villalobos",
        DOCTO_CC_ACR_ID = VENTA,
        DOCTO_CC_ID = CREDITO,
        FECHA_HORA_PAGO = "2026-09-01T10:15:00Z",
        GUARDADO_EN_MICROSIP = false,
        IMPORTE = 350.0,
        LAT = 20.6736,
        LNG = -103.344,
        CLIENTE_ID = CLIENTE,
        COBRADOR_ID = 41,
        FORMA_COBRO_ID = 1,
        ZONA_CLIENTE_ID = 7,
        NOMBRE_CLIENTE = "María Guadalupe Rentería"
    )

    private fun venta(): Sale = Sale(
        DOCTO_CC_ACR_ID = VENTA,
        DOCTO_CC_ID = CREDITO,
        FOLIO = "MTY-2026-0188",
        CLIENTE_ID = CLIENTE,
        APLICADO = "N",
        COBRADOR_ID = 41,
        CLIENTE = "María Guadalupe Rentería",
        ZONA_CLIENTE_ID = 7,
        LIMITE_CREDITO = 0.0,
        NOTAS = "",
        ZONA_NOMBRE = "Tlaquepaque",
        IMPORTE_PAGO_PROMEDIO = 441.6,
        TOTAL_IMPORTE = 2650.0,
        NUM_IMPORTES = 6,
        FECHA = "2025-11-04T00:00:00Z",
        PARCIALIDAD = 350,
        ENGANCHE = 500.0,
        TIEMPO_A_CORTO_PLAZOMESES = 0,
        MONTO_A_CORTO_PLAZO = 0.0,
        VENDEDOR_1 = "Ricardo Salgado",
        VENDEDOR_2 = "",
        VENDEDOR_3 = "",
        PRECIO_TOTAL = 8400.0,
        IMPTE_REST = 5250.0,
        SALDO_REST = 5250.0,
        FECHA_ULT_PAGO = "2026-08-24T00:00:00Z",
        CALLE = "Av. Río Nilo 442",
        CIUDAD = "Guadalajara",
        ESTADO = "Jalisco",
        TELEFONO = "3312345678",
        NOMBRE_COBRADOR = "Esperanza Villalobos",
        ESTADO_COBRANZA = EstadoCobranza.PENDIENTE,
        DIA_COBRANZA = "VIERNES",
        DIA_TEMPORAL_COBRANZA = "",
        PRECIO_DE_CONTADO = 7200.0,
        AVAL_O_RESPONSABLE = "Rosalba Rentería",
        FREC_PAGO = FrecuenciaPago.SEMANAL
    )

    private companion object {
        const val RAIZ = "raiz_de_prueba"

        /** `CLIENTE_ID`. Distinto de [VENTA] y [CREDITO] a propósito. */
        const val CLIENTE = 9011

        /** `DOCTO_CC_ACR_ID` — la llave primaria de `sales`. */
        const val VENTA = 4477

        /** `DOCTO_CC_ID` — el crédito, otro espacio de id. */
        const val CREDITO = 3312

        const val PAGO_ID = "b3f1a0d2-7c44-4f1e-9a2b-5d6e7f801234"
        const val VISITA_ID = "1c9e4a55-2d38-4b70-8f61-0a2b3c4d5e6f"
    }
}
