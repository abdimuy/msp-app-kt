package com.example.msp_app.features.home.components.homenearbyclientssection

import com.example.msp_app.core.common.location.SaleDistance
import com.example.msp_app.core.database.entities.PaymentLocation
import com.example.msp_app.core.utils.Coord
import com.example.msp_app.data.models.payment.PaymentLocationsGroup
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * **La decisión de producto de esta lista, medida.**
 *
 * La maquinaria de cercanía razona en **ventas** y la pantalla habla de
 * **clientes**; [nearbyClientsFrom] es donde se colapsa lo uno en lo otro, con
 * la regla "la venta más cercana manda". Estos tests fijan esa regla y los tres
 * caminos por los que la lista sale vacía — que es lo que hace que la sección no
 * se pinte en vez de dejar un hueco.
 *
 * El orden por cercanía en sí **no** se prueba acá: ya lo prueba
 * `SortGroupsByClosestCentroidTest`, sobre la misma función que esta usa.
 */
class NearbyClientsFromTest {

    private val posicionActual = Coord(lat = 19.4326, lng = -99.1332)

    // -----------------------------------------------------------------------
    // La decisión de producto: una puerta, una fila
    // -----------------------------------------------------------------------

    @Test
    fun `un cliente con dos ventas aparece una sola vez`() {
        val clientes = nearbyClientsFrom(
            position = posicionActual,
            centroidsBySale = listOf(
                grupo(cargo = 101, coord = lejos()),
                grupo(cargo = 102, coord = cerca())
            ),
            sales = listOf(
                venta(cargo = 101, clienteId = 88),
                venta(cargo = 102, clienteId = 88)
            )
        )

        assertEquals(1, clientes.size)
        assertEquals(88, clientes.single().clientId)
    }

    @Test
    fun `la fila queda a la distancia de la venta mas cercana del cliente`() {
        val soloLaCercana = nearbyClientsFrom(
            position = posicionActual,
            centroidsBySale = listOf(grupo(cargo = 102, coord = cerca())),
            sales = listOf(venta(cargo = 102, clienteId = 88))
        ).single().distance

        val lasDos = nearbyClientsFrom(
            position = posicionActual,
            centroidsBySale = listOf(
                grupo(cargo = 101, coord = lejos()),
                grupo(cargo = 102, coord = cerca())
            ),
            sales = listOf(
                venta(cargo = 101, clienteId = 88),
                venta(cargo = 102, clienteId = 88)
            )
        ).single().distance

        assertEquals(soloLaCercana, lasDos)
    }

    @Test
    fun `la fila dice cuantas cuentas colapso`() {
        val clientes = nearbyClientsFrom(
            position = posicionActual,
            centroidsBySale = listOf(
                grupo(cargo = 101, coord = lejos()),
                grupo(cargo = 102, coord = cerca())
            ),
            sales = listOf(
                venta(cargo = 101, clienteId = 88),
                venta(cargo = 102, clienteId = 88)
            )
        )

        assertEquals(2, clientes.single().accounts)
    }

    /**
     * Control negativo del colapso: dos clientes distintos NO se juntan. Sin
     * esto, un `groupBy` por una constante haría pasar los tests de arriba.
     */
    @Test
    fun `dos clientes distintos siguen siendo dos filas`() {
        val clientes = nearbyClientsFrom(
            position = posicionActual,
            centroidsBySale = listOf(
                grupo(cargo = 101, coord = lejos()),
                grupo(cargo = 102, coord = cerca())
            ),
            sales = listOf(
                venta(cargo = 101, clienteId = 88),
                venta(cargo = 102, clienteId = 99)
            )
        )

        assertEquals(listOf(99, 88), clientes.map { it.clientId })
    }

    @Test
    fun `el nombre y la direccion salen de la venta mas cercana`() {
        val clientes = nearbyClientsFrom(
            position = posicionActual,
            centroidsBySale = listOf(grupo(cargo = 102, coord = cerca())),
            sales = listOf(
                venta(
                    cargo = 102,
                    clienteId = 88,
                    cliente = "María Fernanda Villalobos Treviño",
                    calle = "Av. Francisco I. Madero 1204",
                    ciudad = "Cuauhtémoc"
                )
            )
        )

        val fila = clientes.single()
        assertEquals("María Fernanda Villalobos Treviño", fila.name)
        assertEquals("Av. Francisco I. Madero 1204, Cuauhtémoc", fila.address)
    }

    @Test
    fun `una direccion a medias no arrastra separadores sueltos`() {
        val clientes = nearbyClientsFrom(
            position = posicionActual,
            centroidsBySale = listOf(grupo(cargo = 102, coord = cerca())),
            sales = listOf(
                venta(cargo = 102, clienteId = 88, calle = "  ", ciudad = "Parral")
            )
        )

        assertEquals("Parral", clientes.single().address)
    }

    // -----------------------------------------------------------------------
    // Los tres caminos a la lista vacía
    // -----------------------------------------------------------------------

    /**
     * El cobrador que dijo que no al permiso, y el teléfono sin fix: los dos
     * llegan acá como una posición nula. `CurrentLocationReader.current()`
     * devuelve exactamente eso en los dos casos.
     */
    @Test
    fun `sin ubicacion no hay lista que ordenar`() {
        val clientes = nearbyClientsFrom(
            position = null,
            centroidsBySale = listOf(grupo(cargo = 102, coord = cerca())),
            sales = listOf(venta(cargo = 102, clienteId = 88))
        )

        assertEquals(emptyList<NearbyClient>(), clientes)
    }

    /**
     * Control positivo del test de arriba: con los MISMOS insumos y una posición
     * real, la lista sí trae la fila. Sin esto, un fixture mal armado dejaría
     * pasar "vacía" por el motivo equivocado.
     */
    @Test
    fun `con los mismos datos y una posicion real la lista si trae la fila`() {
        val clientes = nearbyClientsFrom(
            position = posicionActual,
            centroidsBySale = listOf(grupo(cargo = 102, coord = cerca())),
            sales = listOf(venta(cargo = 102, clienteId = 88))
        )

        assertEquals(listOf(88), clientes.map { it.clientId })
    }

    @Test
    fun `sin centroides la lista sale vacia`() {
        val clientes = nearbyClientsFrom(
            position = posicionActual,
            centroidsBySale = emptyList(),
            sales = listOf(venta(cargo = 102, clienteId = 88))
        )

        assertEquals(emptyList<NearbyClient>(), clientes)
    }

    @Test
    fun `un centroide sin su fila en sales no entra a la lista`() {
        val clientes = nearbyClientsFrom(
            position = posicionActual,
            centroidsBySale = listOf(grupo(cargo = 777, coord = cerca())),
            sales = listOf(venta(cargo = 102, clienteId = 88))
        )

        assertEquals(emptyList<NearbyClient>(), clientes)
    }

    // -----------------------------------------------------------------------
    // El resto del contrato
    // -----------------------------------------------------------------------

    @Test
    fun `la lista nunca pasa del limite`() {
        val cargos = (1..(NEARBY_CLIENTS_LIMIT + 5))
        val clientes = nearbyClientsFrom(
            position = posicionActual,
            centroidsBySale = cargos.map { grupo(cargo = it, coord = cerca()) },
            sales = cargos.map { venta(cargo = it, clienteId = it * 10) }
        )

        assertEquals(NEARBY_CLIENTS_LIMIT, clientes.size)
    }

    @Test
    fun `las puertas sin ubicacion quedan al final`() {
        val clientes = nearbyClientsFrom(
            position = posicionActual,
            centroidsBySale = listOf(
                PaymentLocationsGroup(saleId = 101, locations = emptyList()),
                grupo(cargo = 102, coord = cerca())
            ),
            sales = listOf(
                venta(cargo = 101, clienteId = 88),
                venta(cargo = 102, clienteId = 99)
            )
        )

        assertEquals(listOf(99, 88), clientes.map { it.clientId })
        assertEquals(SaleDistance.Unknown, clientes.last().distance)
        assertTrue(clientes.first().distance is SaleDistance.Known)
    }

    // -----------------------------------------------------------------------
    // Fixtures
    // -----------------------------------------------------------------------

    private fun cerca() = Coord(lat = posicionActual.lat + GRADO_CERCANO, lng = posicionActual.lng)

    private fun lejos() = Coord(lat = posicionActual.lat + GRADO_LEJANO, lng = posicionActual.lng)

    private fun grupo(cargo: Int, coord: Coord) = PaymentLocationsGroup(
        saleId = cargo,
        locations = listOf(
            PaymentLocation(DOCTO_CC_ACR_ID = cargo, LAT = coord.lat, LNG = coord.lng)
        )
    )

    private companion object {
        const val GRADO_CERCANO = 0.001
        const val GRADO_LEJANO = 0.05
    }
}
