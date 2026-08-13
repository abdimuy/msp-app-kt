package com.example.msp_app.core.utils

import com.example.msp_app.core.common.location.SaleDistance
import com.example.msp_app.core.database.entities.PaymentLocation
import com.example.msp_app.data.models.payment.PaymentLocationsGroup
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * La venta sin centroide no es un caso raro: DBSCAN corre con `minPts = 3`, asi
 * que toda venta con uno o dos pagos georreferenciados —o con pagos dispersos—
 * llega aca sin ubicacion, sin importar la calidad de las coordenadas que mande
 * el servidor.
 */
class SortGroupsByClosestCentroidTest {

    private val posicionActual = Coord(lat = 19.4326, lng = -99.1332)

    private fun grupo(saleId: Int, vararg coords: Coord) = PaymentLocationsGroup(
        saleId = saleId,
        locations = coords.map {
            PaymentLocation(DOCTO_CC_ACR_ID = saleId, LAT = it.lat, LNG = it.lng)
        }
    )

    @Test
    fun `una venta sin centroide queda sin ubicacion`() {
        val resultado = sortGroupsByClosestCentroid(listOf(grupo(saleId = 7)), posicionActual)

        assertEquals(listOf(SaleProximity(saleId = 7, distance = SaleDistance.Unknown)), resultado)
    }

    @Test
    fun `una venta con centroide obtiene una distancia real`() {
        val resultado = sortGroupsByClosestCentroid(
            listOf(grupo(saleId = 7, posicionActual)),
            posicionActual
        )

        val distancia = resultado.single().distance
        assertTrue(
            "se esperaba una distancia conocida: $distancia",
            distancia is SaleDistance.Known
        )
        assertEquals(0.0, (distancia as SaleDistance.Known).meters, TOLERANCIA_METROS)
    }

    @Test
    fun `las ventas se ordenan de la mas cercana a la mas lejana`() {
        val lejos = Coord(lat = posicionActual.lat + GRADO_LEJANO, lng = posicionActual.lng)
        val cerca = Coord(lat = posicionActual.lat + GRADO_CERCANO, lng = posicionActual.lng)

        val resultado = sortGroupsByClosestCentroid(
            listOf(grupo(saleId = 1, lejos), grupo(saleId = 2, cerca)),
            posicionActual
        )

        assertEquals(listOf(2, 1), resultado.map { it.saleId })
    }

    @Test
    fun `toma el centroide mas cercano de la venta`() {
        val lejos = Coord(lat = posicionActual.lat + GRADO_LEJANO, lng = posicionActual.lng)
        val cerca = Coord(lat = posicionActual.lat + GRADO_CERCANO, lng = posicionActual.lng)

        val conAmbos = sortGroupsByClosestCentroid(
            listOf(grupo(saleId = 1, lejos, cerca)),
            posicionActual
        )
        val soloCerca = sortGroupsByClosestCentroid(
            listOf(grupo(saleId = 1, cerca)),
            posicionActual
        )

        assertEquals(soloCerca.single().distance, conAmbos.single().distance)
    }

    @Test
    fun `las ventas sin ubicacion se ordenan al final`() {
        val lejos = Coord(lat = posicionActual.lat + GRADO_LEJANO, lng = posicionActual.lng)
        val cerca = Coord(lat = posicionActual.lat + GRADO_CERCANO, lng = posicionActual.lng)

        val resultado = sortGroupsByClosestCentroid(
            listOf(
                grupo(saleId = 10),
                grupo(saleId = 11, lejos),
                grupo(saleId = 12),
                grupo(saleId = 13, cerca)
            ),
            posicionActual
        )

        assertEquals(listOf(13, 11, 10, 12), resultado.map { it.saleId })
        assertEquals(
            listOf(SaleDistance.Unknown, SaleDistance.Unknown),
            resultado.takeLast(2).map { it.distance }
        )
    }

    @Test
    fun `ninguna venta produce una distancia impresentable`() {
        val resultado = sortGroupsByClosestCentroid(
            listOf(grupo(saleId = 1), grupo(saleId = 2, posicionActual)),
            posicionActual
        )

        resultado.forEach { proximidad ->
            val metros = proximidad.distance.metersOrNull
            assertTrue(
                "distancia fuera de rango en ${proximidad.saleId}: $metros",
                metros == null || (metros.isFinite() && metros <= SaleDistance.MAX_PLAUSIBLE_METERS)
            )
        }
    }

    @Test
    fun `sin ventas devuelve una lista vacia`() {
        assertEquals(
            emptyList<SaleProximity>(),
            sortGroupsByClosestCentroid(emptyList(), posicionActual)
        )
    }

    private companion object {
        const val TOLERANCIA_METROS = 0.01
        const val GRADO_CERCANO = 0.001
        const val GRADO_LEJANO = 0.05
    }
}
