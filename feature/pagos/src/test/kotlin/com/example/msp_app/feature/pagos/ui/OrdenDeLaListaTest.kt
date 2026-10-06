package com.example.msp_app.feature.pagos.ui

import com.example.msp_app.core.common.cobranza.domain.EstadoCuenta
import com.example.msp_app.feature.pagos.domain.OrdenDeLaLista
import com.example.msp_app.feature.pagos.domain.model.VentaEnLista
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Las formas de ordenar la lista que el cobrador elige en la hoja "Ordenar por"
 * (decisión del dueño del 2026-10-05). "Cobranza" es la de siempre y la cubre
 * [CarteraEnPantallaTest]; aquí se prueba que cada orden nuevo pone primero a
 * quien dice, y que ninguno la cambia cuando no se eligió.
 *
 * La ruta de prueba está armada para que **cada orden dé un primero distinto**:
 * si dos órdenes devolvieran lo mismo, una de sus pruebas pasaría por casualidad.
 */
class OrdenDeLaListaTest {

    private val hoy = LocalDate.of(2026, 10, 5)

    // Amalia: venta nueva (sin abonos, de ayer), poco saldo, nunca ha pagado.
    private val amalia = ListaFixtures.cliente(
        clienteId = 101,
        nombre = "Amalia Zárate Ponce",
        ventas = listOf(
            venta(1011, LocalDate.of(2026, 10, 4), saldo = "2700", total = "3000", enganche = "300")
        ),
        ultimoPago = null
    )

    // Benito: muchos atrasos, venta vieja, pagó hace poco.
    private val benito = ListaFixtures.cliente(
        clienteId = 102,
        nombre = "Benito Ávila Cruz",
        ventas = listOf(
            venta(
                1021,
                LocalDate.of(2025, 3, 10),
                saldo = "1500",
                total = "9000",
                enganche = "500",
                atrasos = 31
            )
        ),
        ultimoPago = LocalDate.of(2026, 10, 1)
    )

    // Carmen: el saldo más alto, pagó hace un mes.
    private val carmen = ListaFixtures.cliente(
        clienteId = 103,
        nombre = "Carmen Bautista Ruiz",
        ventas = listOf(
            venta(
                1031,
                LocalDate.of(2026, 6, 2),
                saldo = "15800",
                total = "18000",
                enganche = "1000",
                atrasos = 2
            )
        ),
        ultimoPago = LocalDate.of(2026, 9, 3)
    )

    // Delfino: lo más viejo sin pagar entre los que ya pagaron alguna vez.
    private val delfino = ListaFixtures.cliente(
        clienteId = 104,
        nombre = "Delfino Méndez Ortiz",
        ventas = listOf(
            venta(
                1041,
                LocalDate.of(2025, 11, 20),
                saldo = "4100",
                total = "7000",
                enganche = "500",
                atrasos = 9
            )
        ),
        ultimoPago = LocalDate.of(2026, 2, 19)
    )

    private val ruta = listOf(carmen, delfino, benito, amalia)

    private fun ordenados(
        orden: OrdenDeLaLista,
        distancias: Map<Int, Double> = emptyMap(),
        query: String = ""
    ) = CarteraEnPantalla.proyectar(
        clientes = ruta,
        segmento = SegmentoDeCobranza.SIN_VISITAR,
        query = query,
        hoy = hoy,
        orden = orden,
        distanciasPorVenta = distancias
    ).clientes.map { it.nombre.substringBefore(' ') }

    @Test
    fun `cobranza sigue siendo el orden por defecto y no cambia`() {
        val porDefecto = CarteraEnPantalla.proyectar(ruta, SegmentoDeCobranza.SIN_VISITAR, "", hoy)
            .clientes.map { it.nombre.substringBefore(' ') }
        assertEquals(porDefecto, ordenados(OrdenDeLaLista.COBRANZA))
        // La única sin abonos va primero; luego las más viejas.
        assertEquals(listOf("Amalia", "Benito", "Delfino", "Carmen"), porDefecto)
    }

    @Test
    fun `ventas nuevas pone primero la vendida mas recientemente`() {
        assertEquals(
            listOf("Amalia", "Carmen", "Delfino", "Benito"),
            ordenados(OrdenDeLaLista.VENTAS_NUEVAS)
        )
    }

    @Test
    fun `mas atrasos pone primero al que debe mas semanas`() {
        assertEquals(
            listOf("Benito", "Delfino", "Carmen", "Amalia"),
            ordenados(OrdenDeLaLista.MAS_ATRASOS)
        )
    }

    @Test
    fun `sin pagar hace mas pone primero al que nunca ha pagado y luego al pago mas viejo`() {
        assertEquals(
            listOf("Amalia", "Delfino", "Carmen", "Benito"),
            ordenados(OrdenDeLaLista.SIN_PAGAR_HACE_MAS)
        )
    }

    @Test
    fun `mayor saldo pone primero al que mas debe`() {
        assertEquals(
            listOf("Carmen", "Delfino", "Amalia", "Benito"),
            ordenados(OrdenDeLaLista.MAYOR_SALDO)
        )
    }

    @Test
    fun `nombre ordena de la A a la Z sin importar acentos`() {
        // "Ávila" no es apellido aquí: se ordena por el nombre completo tal como se lee.
        assertEquals(
            listOf("Amalia", "Benito", "Carmen", "Delfino"),
            ordenados(OrdenDeLaLista.NOMBRE)
        )
    }

    @Test
    fun `mas cerca usa la venta mas cercana de cada cliente y deja al final a los que no tienen ubicacion`() {
        val distancias = mapOf(1031 to 850.0, 1041 to 40.0, 1021 to 3_200.0)
        assertEquals(
            listOf("Delfino", "Carmen", "Benito", "Amalia"),
            ordenados(OrdenDeLaLista.MAS_CERCA, distancias)
        )
    }

    @Test
    fun `mas cerca sin ninguna ubicacion conserva el orden de cobranza`() {
        assertEquals(ordenados(OrdenDeLaLista.COBRANZA), ordenados(OrdenDeLaLista.MAS_CERCA))
    }

    @Test
    fun `buscando por nombre ningun orden se aplica`() {
        // Los cuatro comparten la zona "ruta 25"; buscando, queda el orden de la fuente.
        val fuente = ruta.map { it.nombre.substringBefore(' ') }
        OrdenDeLaLista.entries.forEach { orden ->
            assertEquals(orden.name, fuente, ordenados(orden, query = "Hidalgo"))
        }
    }

    @Test
    fun `los empates se resuelven por id para que la lista no salte entre cargas`() {
        val gemelo = ListaFixtures.cliente(
            clienteId = 99,
            nombre = "Amalia Zárate Ponce",
            ventas = listOf(
                venta(
                    991,
                    LocalDate.of(2026, 10, 4),
                    saldo = "2700",
                    total = "3000",
                    enganche = "300"
                )
            ),
            ultimoPago = null
        )
        val dos = CarteraEnPantalla.proyectar(
            listOf(amalia, gemelo),
            SegmentoDeCobranza.SIN_VISITAR,
            "",
            hoy,
            OrdenDeLaLista.NOMBRE,
            emptyMap()
        ).clientes.map { it.clienteId }
        assertEquals(listOf(99, 101), dos)
    }

    @Test
    fun `una venta sin abonos es nueva y una con abonos no`() {
        assertTrue(amalia.ventas.single().esNueva)
        // Control: la de Benito ya recibió abonos.
        assertFalse(benito.ventas.single().esNueva)
    }

    @Test
    fun `una venta vieja sin abonos tambien es nueva`() {
        // Decisión del dueño del 2026-10-05: sin límite de días.
        val vieja =
            venta(5001, LocalDate.of(2025, 1, 14), saldo = "4500", total = "5000", enganche = "500")
        assertTrue(vieja.esNueva)
    }

    private fun venta(
        ventaId: Int,
        fecha: LocalDate,
        saldo: String,
        total: String,
        enganche: String,
        atrasos: Int = 0
    ): VentaEnLista = ListaFixtures.venta(
        ventaId = ventaId,
        folio = "V-$ventaId",
        descripcion = "Lavadora Easy 15 kg",
        saldo = ListaFixtures.dinero(saldo),
        totalVenta = ListaFixtures.dinero(total),
        enganche = ListaFixtures.dinero(enganche),
        fechaVenta = fecha,
        estado = ListaFixtures.estado(EstadoCuenta.SIN_TOCAR),
        atrasos = atrasos
    )
}
