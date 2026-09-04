package com.example.msp_app.feature.pagos.ui

import com.example.msp_app.core.common.cobranza.domain.EstadoCuenta
import com.example.msp_app.feature.pagos.domain.OrdenDeCobranza
import com.example.msp_app.feature.pagos.domain.model.ClienteEnLista
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Buscar, filtrar y ordenar: las tres decisiones que la pantalla toma sobre la
 * ruta ya cargada.
 *
 * El caso que manda es el de **un cliente con dos ventas que rankean
 * distinto**: es donde se decide si la lista es de puertas o de ventas.
 */
class CarteraEnPantallaTest {

    private val hoyDePrueba = ListaFixtures.HOY

    private fun proyectar(
        clientes: List<ClienteEnLista> = ListaFixtures.ruta(),
        segmento: SegmentoDeCobranza = SegmentoDeCobranza.TODOS,
        query: String = "",
        hoy: LocalDate = hoyDePrueba
    ) = CarteraEnPantalla.proyectar(clientes, segmento, query, hoy)

    private fun ids(clientes: List<ClienteEnLista>) = clientes.map { it.clienteId }

    @Test
    fun `un cliente con dos ventas aparece UNA sola vez`() {
        val victoria = proyectar().clientes.filter { it.clienteId == ListaFixtures.VICTORIA }
        assertEquals(1, victoria.size)
        assertEquals(2, victoria.single().cuentas)
    }

    @Test
    fun `el cliente hereda la posicion de su venta mejor rankeada`() {
        // Victoria tiene una venta ya cobrada (mal rankeada) y otra sin un solo
        // peso abonado (bien rankeada). Manda la segunda: la puerta se visita
        // temprano aunque detrás haya también una cuenta al corriente.
        // Ricardo va antes porque su venta sin abonos es de marzo y la de
        // Victoria de agosto; Guadalupe cierra porque la suya ya recibió dinero.
        assertEquals(
            listOf(ListaFixtures.RICARDO, ListaFixtures.VICTORIA, ListaFixtures.GUADALUPE),
            ids(proyectar().clientes)
        )
    }

    /**
     * Control positivo de la regla de arriba: con estos dos clientes, heredar
     * la MEJOR venta y heredar la PEOR dan órdenes distintos. Sin este caso, el
     * test anterior podría estar pasando por casualidad.
     *
     * Norberta tiene dos cuentas: una de enero sin un peso abonado y otra que
     * ya abonó. Óscar tiene una sola, de junio, tampoco abonada.
     */
    @Test
    fun `heredar la mejor venta y heredar la peor dan ordenes distintos`() {
        val norberta = ListaFixtures.cliente(
            clienteId = 8001,
            nombre = "Norberta Ibarra Quintero",
            ventas = listOf(
                ventaSinAbonos(80011, LocalDate.of(2026, 1, 15)),
                ventaConAbonos(80012, LocalDate.of(2026, 7, 2))
            )
        )
        val oscar = ListaFixtures.cliente(
            clienteId = 8002,
            nombre = "Óscar Villalobos Peña",
            ventas = listOf(ventaSinAbonos(80021, LocalDate.of(2026, 6, 3)))
        )
        val ruta = listOf(oscar, norberta)

        // Por la MEJOR (lo implementado): Norberta encabeza, su cuenta de enero
        // no ha recibido un peso y es la más vieja de las dos que no han
        // abonado.
        assertEquals(listOf(8001, 8002), ids(proyectar(ruta).clientes))

        // Por la PEOR: la cuenta abonada de Norberta la manda al final.
        val porLaPeor = ruta.sortedWith(
            compareBy(OrdenDeCobranza.PRIMERO) { cliente: ClienteEnLista ->
                cliente.ventas.map { it.rango }.maxWith(OrdenDeCobranza.PRIMERO)
            }
        )
        assertEquals(listOf(8002, 8001), ids(porLaPeor))
    }

    private fun ventaConAbonos(ventaId: Int, fecha: LocalDate) = ListaFixtures.venta(
        ventaId = ventaId,
        folio = "V-$ventaId",
        descripcion = "Ropero 2 puertas",
        saldo = ListaFixtures.dinero("1200"),
        totalVenta = ListaFixtures.dinero("8400"),
        enganche = ListaFixtures.dinero("900"),
        fechaVenta = fecha,
        estado = ListaFixtures.estado(EstadoCuenta.SIN_TOCAR)
    )

    @Test
    fun `los clientes que empatan se desempatan por id, siempre igual`() {
        val ana = ListaFixtures.cliente(
            clienteId = 9001,
            nombre = "Ana Sofía Beltrán",
            ventas = listOf(ventaSinAbonos(90011))
        )
        val bruno = ListaFixtures.cliente(
            clienteId = 9002,
            nombre = "Bruno Cárdenas Ríos",
            ventas = listOf(ventaSinAbonos(90021))
        )
        assertEquals(listOf(9001, 9002), ids(proyectar(listOf(ana, bruno)).clientes))
        // El orden de la fuente no cambia el resultado: ese es el punto del
        // desempate estable.
        assertEquals(listOf(9001, 9002), ids(proyectar(listOf(bruno, ana)).clientes))
    }

    private fun ventaSinAbonos(ventaId: Int, fecha: LocalDate = LocalDate.of(2026, 4, 1)) =
        ListaFixtures.venta(
            ventaId = ventaId,
            folio = "V-$ventaId",
            descripcion = "Comedor 6 sillas",
            saldo = ListaFixtures.dinero("7500"),
            totalVenta = ListaFixtures.dinero("8400"),
            enganche = ListaFixtures.dinero("900"),
            fechaVenta = fecha,
            estado = ListaFixtures.estado(EstadoCuenta.SIN_TOCAR)
        )

    @Test
    fun `la busqueda apaga el orden y respeta el de la fuente`() {
        // Los tres clientes comparten "ruta 25" en la zona, así que la búsqueda
        // los trae a los tres y el orden que queda es el de la fuente.
        val buscados = proyectar(query = "Flores").clientes
        assertEquals(listOf(ListaFixtures.VICTORIA), ids(buscados))

        val todos = proyectar(query = "V-").clientes
        assertEquals(ids(ListaFixtures.ruta()), ids(todos))
        // Sin búsqueda, el mismo conjunto sale ordenado por cobranza.
        assertEquals(
            listOf(ListaFixtures.RICARDO, ListaFixtures.VICTORIA, ListaFixtures.GUADALUPE),
            ids(proyectar().clientes)
        )
    }

    @Test
    fun `el chip filtra y el orden se conserva en cada segmento`() {
        val sinVisitar = proyectar(segmento = SegmentoDeCobranza.SIN_VISITAR).clientes
        assertEquals(listOf(ListaFixtures.RICARDO, ListaFixtures.VICTORIA), ids(sinVisitar))

        val vencidos = proyectar(segmento = SegmentoDeCobranza.VENCIDOS).clientes
        assertEquals(listOf(ListaFixtures.GUADALUPE), ids(vencidos))

        val hoy = proyectar(segmento = SegmentoDeCobranza.HOY).clientes
        assertTrue(hoy.isEmpty())
    }

    @Test
    fun `el chip no recorta las ventas del cliente`() {
        // Victoria entra por su cuenta sin trabajar, pero se sigue viendo la
        // que ya cobró: el cobrador está frente a la puerta y necesita las dos.
        val victoria = proyectar(segmento = SegmentoDeCobranza.SIN_VISITAR).clientes
            .single { it.clienteId == ListaFixtures.VICTORIA }
        assertEquals(2, victoria.cuentas)
    }

    @Test
    fun `los conteos son de clientes y se calculan sobre lo buscado`() {
        val sinBuscar = proyectar().conteos
        assertEquals(3, sinBuscar[SegmentoDeCobranza.TODOS])
        assertEquals(2, sinBuscar[SegmentoDeCobranza.SIN_VISITAR])
        assertEquals(1, sinBuscar[SegmentoDeCobranza.VENCIDOS])
        assertEquals(0, sinBuscar[SegmentoDeCobranza.HOY])

        val buscando = proyectar(query = "Zepeda").conteos
        assertEquals(1, buscando[SegmentoDeCobranza.TODOS])
        assertEquals(0, buscando[SegmentoDeCobranza.VENCIDOS])
    }

    @Test
    fun `una ruta vacia no truena`() {
        val vacia = proyectar(emptyList())
        assertTrue(vacia.clientes.isEmpty())
        assertEquals(0, vacia.conteos[SegmentoDeCobranza.TODOS])
    }
}
