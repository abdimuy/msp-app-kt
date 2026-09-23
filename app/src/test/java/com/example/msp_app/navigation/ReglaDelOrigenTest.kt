package com.example.msp_app.navigation

import androidx.navigation.NavDestination
import androidx.navigation.compose.ComposeNavigator
import androidx.navigation.compose.composable
import androidx.navigation.createGraph
import androidx.navigation.testing.TestNavHostController
import androidx.test.core.app.ApplicationProvider
import com.example.msp_app.core.common.location.SaleDistance
import com.example.msp_app.core.database.dao.sale.EstadoCobranza
import com.example.msp_app.core.speech.ui.DictadoRutas
import com.example.msp_app.data.models.payment.Payment
import com.example.msp_app.data.models.sale.FrecuenciaPago
import com.example.msp_app.data.models.sale.Sale
import com.example.msp_app.data.models.sale.SaleWithProducts
import com.example.msp_app.feature.pagos.ui.PagosRutas
import com.example.msp_app.feature.visitas.ui.VisitasRutas
import com.example.msp_app.features.home.components.homenearbyclientssection.NearbyClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
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

    /**
     * Punto de entrada: la lista de **clientes cercanos** de la pantalla
     * principal. Estuvo fuera desde el `d76d8f69` y vuelve en esta rama; cuando
     * existía llevaba al **detalle de venta**, porque la maquinaria de cercanía
     * razona en ventas. Ahora la fila ya viene colapsada por cliente y entra por
     * la misma puerta que la lista de cobranza: la regla del origen no tiene
     * excepciones.
     */
    @Test
    fun `desde la lista de cercanos se entra al CLIENTE`() {
        nav.navigate(DestinosDeCobranza.clienteCercano(clienteCercano()))
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

    /**
     * Punto de entrada: "ver los N contactos" del detalle de cliente — lo único
     * que el "⋯" retirado sí llevaba y que ahora tiene destino propio.
     *
     * Viaja el `clienteId` porque la bitácora es del **domicilio**: mandar la
     * lista ya armada la congelaría en lo que se leyó al abrir el detalle.
     */
    @Test
    fun `desde el cliente, ver los contactos abre SU bitacora`() {
        nav.navigate(PagosRutas.bitacora(CLIENTE))
        assertEquals(PagosRutas.BITACORA, ruta())
        assertEquals(CLIENTE, argInt(PagosRutas.ARG_CLIENTE_ID))
    }

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
     * Punto de entrada: `SaleActionsSection` ("Agregar Pago" del detalle legado)
     * y `SaleItem` ("agregar pago" del menú de la tarjeta) — los dos llamadores
     * que tenían el `NewPaymentDialog` y que la ronda 1 de arreglo enrutó al
     * destino de la Task 18.
     *
     * Se abona a la **cuenta**: el argumento es el `DOCTO_CC_ACR_ID`, que es lo
     * que `pagos/abono/{ventaId}` lee y lo que `SaleDao.getById` filtra.
     */
    @Test
    fun `desde una venta, agregar pago lleva a registrar abono de ESA cuenta`() {
        nav.navigate(DestinosDeCobranza.abonoDeUnaVenta(venta()))
        assertEquals(PagosRutas.REGISTRAR_ABONO, ruta())
        assertEquals(VENTA, argInt(PagosRutas.ARG_VENTA_ID))
    }

    /**
     * **El control que nombra el defecto de la familia del identificador.** El
     * id que viaja al abono NO es el del crédito ni el del cliente, y los tres
     * números son distintos de verdad en el fixture: si dos coincidieran, la
     * afirmación de arriba no probaría nada.
     */
    @Test
    fun `el abono NO viaja con el credito ni con el cliente`() {
        val ruta = DestinosDeCobranza.abonoDeUnaVenta(venta())
        assertEquals(PagosRutas.registrarAbono(VENTA), ruta)
        assertNotEquals(PagosRutas.registrarAbono(CREDITO), ruta)
        assertNotEquals(PagosRutas.registrarAbono(CLIENTE), ruta)
        assertNotEquals(VENTA, CREDITO)
        assertNotEquals(VENTA, CLIENTE)
    }

    /**
     * Las dos sobrecargas —`Sale` del detalle legado y `SaleWithProducts` de la
     * tarjeta de lista— tienen que producir **la misma ruta** para la misma
     * cuenta. Son dos llamadores de la misma regla y divergir sería el defecto
     * que [DestinosDeCobranza] existe para impedir.
     */
    @Test
    fun `las dos sobrecargas del abono producen la misma ruta`() {
        assertEquals(
            DestinosDeCobranza.abonoDeUnaVenta(venta()),
            DestinosDeCobranza.abonoDeUnaVenta(ventaConProductos())
        )
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

    /**
     * Punto de entrada: "Ver los N abonos" del detalle de venta. El "⋯" que
     * abría la MISMA ruta se quitó (el dueño no lo quiere ver más); esta
     * puerta sigue abierta porque nadie pidió cerrarla.
     */
    @Test
    fun `ver los abonos abre el detalle legado de ESA venta, donde vive la condonacion`() {
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

    /**
     * Punto de entrada: la acción "Condonar" del detalle de venta NUEVO
     * (`:feature:pagos`). Hasta hoy la única puerta era el detalle legado, con
     * "Ver los N abonos"; el dueño la quiso también aquí, sin depender de esa
     * puerta ni de que haya oferta de liquidación vigente.
     */
    @Test
    fun `condonar lleva a la condonacion de ESA venta`() {
        nav.navigate(Screen.Forgiveness.createRoute(VENTA))
        assertEquals(Screen.Forgiveness.route, ruta())
        // El `DOCTO_CC_ACR_ID`, el mismo espacio que `Screen.SaleDetails` — no
        // el `DOCTO_CC_ID` del crédito ni el `CLIENTE_ID`.
        assertEquals(VENTA.toString(), argString("saleId"))
    }

    /**
     * `PagosRutas.TICKET_PAGO` sigue registrado y resolviendo, pero hoy está
     * ESTACIONADO: ningún punto de entrada de producción navega aquí. El
     * abono registrado y la hoja del último cobro vuelven al ticket LEGADO
     * (`Screen.PaymentTicket`), por decisión del dueño — ver
     * `desde el abono registrado se llega a SU ticket legado` más abajo. Este
     * test sólo prueba que la ruta del módulo no quedó huérfana mientras
     * espera el día que se reencienda.
     */
    @Test
    fun `al registrar un abono se llega a su ticket`() {
        nav.navigate(PagosRutas.ticketDePago(PAGO_ID))
        assertEquals(PagosRutas.TICKET_PAGO, ruta())
        assertEquals(PAGO_ID, argString(PagosRutas.ARG_PAGO_ID))
    }

    /**
     * **El punto de entrada real, hoy.** El abono registrado y la hoja del
     * último cobro (Task 18/Task 20 y la bitácora) llevan al ticket LEGADO,
     * no al del módulo: el papel migrado perdía el teléfono y el WhatsApp
     * del negocio, el teléfono del agente, la fecha de la venta, los
     * productos, el precio a meses, el de contado, el enganche, los tres
     * vendedores y el estado en la dirección, así que el dueño decidió
     * volver al viejo.
     */
    @Test
    fun `desde el abono registrado se llega a SU ticket legado`() {
        nav.navigate(Screen.PaymentTicket.createRoute(PAGO_ID))
        assertEquals(Screen.PaymentTicket.route, ruta())
        assertEquals(PAGO_ID, argString("paymentId"))
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

    /**
     * Punto de entrada: el renglón "Dictado por voz" de la sección Descargas de
     * Configuración. **Hasta este cambio no existía ninguno**: la pantalla
     * estaba escrita, probada y con goldens, y sin una sola cadena que llevara a
     * ella.
     */
    @Test
    fun `desde configuracion se llega a la descarga del dictado`() {
        nav.navigate(DictadoRutas.DESCARGA)
        assertEquals(DictadoRutas.DESCARGA, ruta())
    }

    /**
     * **El mapa grande tiene punto de entrada, y es el cuadro de la puerta.**
     *
     * El cuadro del detalle de cliente es `liteMode`: un bitmap estático sin
     * zoom. Tocarlo abre esta pantalla, que es la que sí lo tiene. No compite
     * con "cómo llegar": aquélla sale de la app a navegar por un `geo:`, ésta
     * enseña la puerta dentro de la app antes de arrancar.
     *
     * Sin este test la ruta quedaría registrada y huérfana, que es exactamente
     * el defecto que la descarga del dictado tuvo durante toda una tarea.
     */
    @Test
    fun `desde el cuadro del detalle se llega al mapa de la puerta`() {
        nav.navigate(RutasDelMapa.ubicacion(LAT_DE_PRUEBA, LNG_DE_PRUEBA, DIRECCION_DE_PRUEBA))
        assertEquals(RutasDelMapa.UBICACION, ruta())
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
            PagosRutas.BITACORA,
            PagosRutas.DETALLE_VENTA,
            PagosRutas.REGISTRAR_ABONO,
            PagosRutas.TICKET_PAGO,
            VisitasRutas.REGISTRAR,
            VisitasRutas.TICKET,
            DictadoRutas.DESCARGA,
            RutasDelMapa.UBICACION,
            Screen.Forgiveness.route
        ) + legados
        assertEquals(esperadas, registradas)
    }

    /**
     * **Nada alcanzable que no esté registrado**, por el lado de las rutas que
     * esta tarea RETIRÓ. `SalesScreen` (`"sales"`) y el ticket de visita legado
     * (`"visit_ticket/{saleId}"`) ya no existen: navegar ahí no resuelve.
     *
     * Su **control positivo** es el `assertEquals` de arriba: la misma búsqueda
     * SÍ encuentra las diez rutas vivas más las tres legadas, así que el `null`
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

    /** Una fila de la lista de clientes cercanos de la pantalla principal. */
    private fun clienteCercano(): NearbyClient = NearbyClient(
        clientId = CLIENTE,
        name = "María Guadalupe Rentería",
        address = "Av. Juárez 1188, Delicias",
        accounts = 2,
        distance = SaleDistance.of(850.0)
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

    /** La MISMA cuenta, en la forma que traen las tarjetas de las listas. */
    private fun ventaConProductos(): SaleWithProducts = venta().let { v ->
        SaleWithProducts(
            DOCTO_CC_ACR_ID = v.DOCTO_CC_ACR_ID,
            DOCTO_CC_ID = v.DOCTO_CC_ID,
            FOLIO = v.FOLIO,
            CLIENTE_ID = v.CLIENTE_ID,
            APLICADO = v.APLICADO,
            COBRADOR_ID = v.COBRADOR_ID,
            CLIENTE = v.CLIENTE,
            ZONA_CLIENTE_ID = v.ZONA_CLIENTE_ID,
            LIMITE_CREDITO = v.LIMITE_CREDITO,
            NOTAS = v.NOTAS,
            ZONA_NOMBRE = v.ZONA_NOMBRE,
            IMPORTE_PAGO_PROMEDIO = v.IMPORTE_PAGO_PROMEDIO,
            TOTAL_IMPORTE = v.TOTAL_IMPORTE,
            NUM_IMPORTES = v.NUM_IMPORTES,
            FECHA = v.FECHA,
            PARCIALIDAD = v.PARCIALIDAD,
            ENGANCHE = v.ENGANCHE,
            TIEMPO_A_CORTO_PLAZOMESES = v.TIEMPO_A_CORTO_PLAZOMESES,
            MONTO_A_CORTO_PLAZO = v.MONTO_A_CORTO_PLAZO,
            VENDEDOR_1 = v.VENDEDOR_1,
            VENDEDOR_2 = v.VENDEDOR_2,
            VENDEDOR_3 = v.VENDEDOR_3,
            PRECIO_TOTAL = v.PRECIO_TOTAL,
            IMPTE_REST = v.IMPTE_REST,
            SALDO_REST = v.SALDO_REST,
            FECHA_ULT_PAGO = v.FECHA_ULT_PAGO,
            CALLE = v.CALLE,
            CIUDAD = v.CIUDAD,
            ESTADO = v.ESTADO,
            TELEFONO = v.TELEFONO,
            NOMBRE_COBRADOR = v.NOMBRE_COBRADOR,
            ESTADO_COBRANZA = v.ESTADO_COBRANZA,
            DIA_COBRANZA = v.DIA_COBRANZA,
            DIA_TEMPORAL_COBRANZA = v.DIA_TEMPORAL_COBRANZA,
            PRECIO_DE_CONTADO = v.PRECIO_DE_CONTADO,
            AVAL_O_RESPONSABLE = v.AVAL_O_RESPONSABLE,
            FREC_PAGO = v.FREC_PAGO ?: FrecuenciaPago.SEMANAL,
            PRODUCTOS = "Refrigerador Mabe 14'"
        )
    }

    private companion object {
        const val RAIZ = "raiz_de_prueba"

        /** Una coordenada cualquiera: lo que se prueba es la ruta, no el punto. */
        const val LAT_DE_PRUEBA = 18.4609

        /** Idem. */
        const val LNG_DE_PRUEBA = -97.3926

        /** Con coma y acento a propósito: la ruta la codifica con `Uri.encode`. */
        const val DIRECCION_DE_PRUEBA = "C. Hidalgo 214, Centro"

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
