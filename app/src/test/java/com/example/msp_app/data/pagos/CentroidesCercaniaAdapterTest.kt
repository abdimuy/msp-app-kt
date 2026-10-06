package com.example.msp_app.data.pagos

import android.Manifest
import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.example.msp_app.core.database.entities.PaymentEntity
import com.example.msp_app.core.testing.RoomTestBase
import com.example.msp_app.core.testing.telemetry.RecordingTelemetry
import com.example.msp_app.core.testing.time.FakeClock
import com.example.msp_app.core.utils.Coord
import com.example.msp_app.core.utils.CurrentLocationReader
import com.example.msp_app.data.local.datasource.payment.PaymentsLocalDataSource
import java.time.Instant
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.robolectric.Shadows

/**
 * [CentroidesCercaniaAdapter] contra una base Room en memoria: la MISMA cuenta
 * que `PaymentsViewModel.getCentroidsBySale()` hace para "VENTAS CERCANAS" en
 * Inicio ([com.example.msp_app.core.utils.centroidesPorVenta]), pero por venta
 * en vez de colapsada a tarjetas de cliente.
 *
 * `minPts = 3`, pero medido contra el DBSCAN real
 * (`org.apache.commons.math3...DBSCANClusterer`) hacen falta CUATRO pagos
 * agrupados para que produzca un centroide, no tres: ese `minPts` cuenta los
 * vecinos DENTRO de `eps` sin contar al punto mismo, así que un punto
 * necesita otros tres iguales o cercanos —cuatro en total— para calificar
 * como núcleo. [SortGroupsByClosestCentroidTest] no lo mide porque llama a
 * `sortGroupsByClosestCentroid` directo, sobre un `PaymentLocationsGroup` ya
 * armado a mano, sin pasar por `computeCentroids`/DBSCAN.
 */
class CentroidesCercaniaAdapterTest : RoomTestBase() {

    private val clock = FakeClock(Instant.parse("2026-10-05T12:00:00Z"))
    private val telemetria = RecordingTelemetry(clock)

    private val aqui = Coord(lat = 19.4326, lng = -99.1332)
    private val lejos = Coord(lat = aqui.lat + GRADO_LEJANO, lng = aqui.lng)

    private fun paymentStore() = PaymentsLocalDataSource(db.paymentDao(), db.saleDao())

    private fun adaptador(posicion: suspend () -> Coord?) = CentroidesCercaniaAdapter(
        paymentStore = paymentStore(),
        telemetry = telemetria,
        posicionActual = posicion
    )

    private suspend fun sembrarPagos(saleId: Int, coord: Coord, cuantos: Int) {
        val store = paymentStore()
        repeat(cuantos) { indice ->
            store.savePayment(
                PaymentEntity(
                    ID = "pago-$saleId-$indice",
                    COBRADOR = "Rosa Elena Martinez Vazquez",
                    DOCTO_CC_ACR_ID = saleId,
                    DOCTO_CC_ID = saleId + 1,
                    FECHA_HORA_PAGO = "2026-10-01T12:00:00Z",
                    GUARDADO_EN_MICROSIP = true,
                    IMPORTE = 300.0,
                    LAT = coord.lat,
                    LNG = coord.lng,
                    CLIENTE_ID = 4821,
                    COBRADOR_ID = 7,
                    FORMA_COBRO_ID = 157,
                    ZONA_CLIENTE_ID = 21,
                    NOMBRE_CLIENTE = "Guadalupe Hernandez Soto"
                )
            )
        }
    }

    @Test
    fun `con posicion y centroides calcula metros y descarta la venta sin centroide`() = runTest {
        sembrarPagos(saleId = CERCA, coord = aqui, cuantos = PAGOS_PARA_CENTROIDE)
        sembrarPagos(saleId = DE_LEJOS, coord = lejos, cuantos = PAGOS_PARA_CENTROIDE)
        // Un solo pago: muy por debajo de lo que DBSCAN necesita, no produce centroide.
        sembrarPagos(saleId = SIN_CENTROIDE, coord = aqui, cuantos = 1)

        val distancias = adaptador { aqui }.distanciasPorVenta()

        // Control positivo: si el descubrimiento devolviera vacío, las dos
        // aserciones de abajo pasarían sin haber medido nada.
        assertTrue("no se calculó ninguna distancia: $distancias", distancias.isNotEmpty())

        assertTrue(
            "la venta sin centroide no debe aparecer: $distancias",
            SIN_CENTROIDE !in distancias
        )
        assertEquals(0.0, distancias.getValue(CERCA), TOLERANCIA_METROS)
        assertTrue(
            "la venta cercana debe medir menos metros que la de lejos",
            distancias.getValue(CERCA) < distancias.getValue(DE_LEJOS)
        )
    }

    @Test
    fun `sin posicion devuelve un mapa vacio aunque haya centroides`() = runTest {
        sembrarPagos(saleId = CERCA, coord = aqui, cuantos = PAGOS_PARA_CENTROIDE)

        val distancias = adaptador { null }.distanciasPorVenta()

        assertEquals(emptyMap<Int, Double>(), distancias)
    }

    /**
     * El camino real, de punta a punta: el lector de ubicación de producción
     * ([CurrentLocationReader]), con el permiso negado — mismo fixture que
     * [com.example.msp_app.core.utils.CurrentLocationReaderTest] usa para medir
     * que `current()` contesta `null` en vez de lanzar. Aquí se mide la otra
     * mitad: que el adaptador, alimentado con ese `null` real, degrada a mapa
     * vacío en vez de propagar una excepción.
     */
    @Test
    fun `sin permiso el lector real resuelve null y el adaptador degrada a mapa vacio`() = runTest {
        val context: Application = ApplicationProvider.getApplicationContext()
        Shadows.shadowOf(context).denyPermissions(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )
        sembrarPagos(saleId = CERCA, coord = aqui, cuantos = PAGOS_PARA_CENTROIDE)

        val distancias = adaptador { CurrentLocationReader(context).current() }.distanciasPorVenta()

        assertEquals(emptyMap<Int, Double>(), distancias)
    }

    private companion object {
        const val CERCA = 101
        const val DE_LEJOS = 102
        const val SIN_CENTROIDE = 103
        const val TOLERANCIA_METROS = 1.0
        const val GRADO_LEJANO = 0.05

        /**
         * MEDIDO contra el DBSCAN real (no deducido del `minPts = 3` de la
         * firma): con 3 pagos idénticos `computeCentroids` devuelve 0
         * centroides; con 4, devuelve 1. Ver el KDoc de la clase.
         */
        const val PAGOS_PARA_CENTROIDE = 4
    }
}
