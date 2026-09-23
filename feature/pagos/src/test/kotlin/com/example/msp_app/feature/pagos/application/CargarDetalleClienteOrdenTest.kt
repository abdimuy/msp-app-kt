package com.example.msp_app.feature.pagos.application

import com.example.msp_app.core.common.money.Money
import com.example.msp_app.core.testing.telemetry.RecordingTelemetry
import com.example.msp_app.core.testing.time.FakeClock
import com.example.msp_app.feature.pagos.data.fake.FakeFichaPort
import com.example.msp_app.feature.pagos.data.fake.FakeLiquidacionPort
import com.example.msp_app.feature.pagos.data.fake.FakePagosPort
import com.example.msp_app.feature.pagos.data.fake.FakePeriodoDeCobroPort
import com.example.msp_app.feature.pagos.data.fake.FakeProductosPort
import com.example.msp_app.feature.pagos.data.fake.FakeVentasPort
import com.example.msp_app.feature.pagos.data.fake.FakeVisitasPort
import com.example.msp_app.feature.pagos.domain.model.DatosDeVenta
import com.example.msp_app.feature.pagos.domain.model.FichaDelCliente
import com.example.msp_app.feature.pagos.ui.PagosFixtures
import java.math.BigDecimal
import java.time.Instant
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * **El orden de "sus ventas" es determinista, y con él la cuenta que se cobra.**
 *
 * ## El defecto que esto cierra
 *
 * `DetalleClienteScreen.cuentaQueEncabeza` toma
 * `state.detalle?.ventas?.firstOrNull()?.ventaId` y ese id es el argumento de
 * `pagos/abono/{ventaId}` — o sea **a qué cuenta se le abona**. Esa lista venía
 * de `SaleDao.getByClientId`, que agrupa por `sales.DOCTO_CC_ID` **sin `ORDER
 * BY`**, y nada entre el DAO y la UI la ordenaba. Con dos cuentas, cuál cobraba
 * el cobrador dependía del orden en que el motor decidiera emitir las filas.
 *
 * Es el mismo peligro que este plan ya arregló dos veces, y los dos KDoc nombran
 * esa misma consulta: `RegistroDeVisitaAdapter` (`minByOrNull { it.
 * DOCTO_CC_ACR_ID }`) y `CarteraEnPantalla` (`.thenBy { it.clienteId }`).
 *
 * ## Cómo se prueba
 *
 * El patrón que estableció la Task 17 y repitió la 19: **la misma lista en dos
 * órdenes de origen distintos**, exigiendo la misma cuenta y el mismo orden
 * pintado. Un fake que devuelve exactamente lo que se le da es lo que hace
 * posible simular al motor emitiendo al revés.
 *
 * **Control de reversión (verificado, ver `task-21-fix-1-report.md`):** quitar
 * el `sortedWith(ORDEN_DE_SUS_VENTAS)` de [CargarDetalleCliente] pone en ROJO
 * `las mismas ventas en dos ordenes de origen dan el mismo orden pintado`.
 */
class CargarDetalleClienteOrdenTest {

    private val clock = FakeClock(PagosFixtures.AHORA)
    private val telemetria = RecordingTelemetry(clock)

    private val ventasPort = FakeVentasPort()
    private val pagosPort = FakePagosPort()
    private val visitasPort = FakeVisitasPort()
    private val liquidacionPort = FakeLiquidacionPort()
    private val periodoPort = FakePeriodoDeCobroPort()
    private val fichaPort = FakeFichaPort()

    private fun cargar() = CargarDetalleCliente(
        fichaPort = fichaPort,
        productosPort = FakeProductosPort(),
        clock = clock,
        reunirCobranzaDelCliente = ReunirCobranzaDelCliente(
            ventasPort = ventasPort,
            pagosPort = pagosPort,
            visitasPort = visitasPort,
            liquidacionPort = liquidacionPort,
            resolverVentanaDeCobro = ResolverVentanaDeCobro(periodoPort, clock),
            derivarEstadoDelPeriodo = DerivarEstadoDelPeriodo(telemetria)
        )
    )

    private suspend fun ventasPintadas(deOrigen: List<DatosDeVenta>): List<Int> {
        ventasPort.ventas = deOrigen
        val detalle = checkNotNull(cargar()(PagosFixtures.CLIENTE_ID)) {
            "el fixture siempre trae ventas: sin ellas no hay detalle que ordenar"
        }
        return detalle.ventas.map { it.ventaId }
    }

    // --- El patrón de las Tasks 17 y 19: dos órdenes de origen ---------------

    @Test
    fun `las mismas ventas en dos ordenes de origen dan el mismo orden pintado`() = runTest {
        val enOrden = PagosFixtures.datosDeVentas()
        val alReves = enOrden.reversed()

        assertEquals(
            "el motor puede emitir al revés; la pantalla no puede cambiar por eso",
            ventasPintadas(enOrden),
            ventasPintadas(alReves)
        )
    }

    @Test
    fun `y la cuenta que encabeza es la misma en los dos ordenes`() = runTest {
        val enOrden = PagosFixtures.datosDeVentas()
        assertEquals(
            ventasPintadas(enOrden).first(),
            ventasPintadas(enOrden.reversed()).first()
        )
    }

    /**
     * **El desempate es TOTAL.** Estas cuatro cuentas empatan de verdad en las
     * DOS claves de `OrdenDeCobranza.PRIMERO` —mismo instante de venta, todas
     * sin un peso abonado (`saldo == totalVenta - enganche`)—, así que sin el
     * `.thenBy { it.ventaId }` el orden lo decidiría el azar de la consulta.
     *
     * Se prueban las cuatro permutaciones que importan (identidad, invertida, y
     * dos rotaciones) contra el orden esperado escrito a mano: el
     * `DOCTO_CC_ACR_ID` ascendente.
     */
    @Test
    fun `con empate total en las dos claves, manda el DOCTO_CC_ACR_ID ascendente`() = runTest {
        val empatadas = listOf(90_004, 90_001, 90_003, 90_002).map { sinAbonos(it) }
        val esperado = listOf(90_001, 90_002, 90_003, 90_004)

        assertEquals(esperado, ventasPintadas(empatadas))
        assertEquals(esperado, ventasPintadas(empatadas.reversed()))
        assertEquals(esperado, ventasPintadas(empatadas.drop(1) + empatadas.first()))
        assertEquals(esperado, ventasPintadas(empatadas.sortedByDescending { it.ventaId }))
    }

    /**
     * **Control positivo del test de arriba.** El mismo montaje, con las mismas
     * cuatro cuentas, SÍ cambia de orden cuando una de ellas rankea distinto por
     * la primera clave: la venta sin un peso abonado sube al frente aunque su id
     * sea el más alto. O sea que el desempate por id no está aplastando el
     * criterio de cobranza — solo actúa cuando ese criterio empata.
     */
    @Test
    fun `control positivo, cuando NO hay empate manda el orden de cobranza`() = runTest {
        val conAbonos = listOf(90_001, 90_002, 90_003).map { yaAbono(it) }
        val virgen = sinAbonos(90_009)

        assertEquals(
            listOf(90_009, 90_001, 90_002, 90_003),
            ventasPintadas(conAbonos + virgen)
        )
    }

    /**
     * El representante del cliente —nombre, teléfono, dirección, nota de la
     * venta— sale de la MISMA primera venta que el dock cobra. Sin el orden, dos
     * cuentas del mismo cliente con notas distintas pintaban una u otra según la
     * corrida.
     *
     * La **ficha** (Task 24) NO entra aquí y ese es justo el punto: es del
     * CLIENTE, no de una venta, así que no depende de este orden en absoluto.
     */
    @Test
    fun `el representante del cliente sale de la primera venta ya ordenada`() = runTest {
        val alta = sinAbonos(90_009).copy(notas = "nota de la cuenta alta")
        val baja = sinAbonos(90_001).copy(notas = "nota de la cuenta baja")

        ventasPort.ventas = listOf(alta, baja)
        val primero = checkNotNull(cargar()(PagosFixtures.CLIENTE_ID))
        ventasPort.ventas = listOf(baja, alta)
        val segundo = checkNotNull(cargar()(PagosFixtures.CLIENTE_ID))

        assertEquals("nota de la cuenta baja", primero.notaDeLaVenta)
        assertEquals(primero.notaDeLaVenta, segundo.notaDeLaVenta)
        assertEquals(90_001, primero.ventas.first().ventaId)
    }

    /**
     * La ficha se lee UNA vez por carga, con el `CLIENTE_ID` —nunca con un
     * `DOCTO_CC_ACR_ID`—, y es la misma sin importar cómo salgan ordenadas las
     * ventas. Este plan ya lleva siete defectos de "un id donde iba el otro".
     */
    @Test
    fun `la ficha es del CLIENTE y no cambia con el orden de sus ventas`() = runTest {
        fichaPort.fichas[PagosFixtures.CLIENTE_ID] = FichaDelCliente(nota = "atiende la suegra")
        val alta = sinAbonos(90_009)
        val baja = sinAbonos(90_001)

        ventasPort.ventas = listOf(alta, baja)
        val primero = checkNotNull(cargar()(PagosFixtures.CLIENTE_ID))
        ventasPort.ventas = listOf(baja, alta)
        val segundo = checkNotNull(cargar()(PagosFixtures.CLIENTE_ID))

        assertEquals("atiende la suegra", primero.ficha?.nota)
        assertEquals(primero.ficha, segundo.ficha)
    }

    /** Un fallo de la ficha no puede dejar al cobrador sin saldo ni sin ventas. */
    @Test
    fun `si la ficha no se puede leer el detalle igual se arma`() = runTest {
        fichaPort.seLee = false
        ventasPort.ventas = listOf(sinAbonos(90_001))
        val detalle = checkNotNull(cargar()(PagosFixtures.CLIENTE_ID))
        assertNull("no se pudo leer, no es que no haya", detalle.ficha)
        assertEquals(1, detalle.ventas.size)
    }

    // --- Plomería ------------------------------------------------------------

    private fun dinero(pesos: String): Money = Money.of(BigDecimal(pesos))

    /**
     * Una venta que **no ha recibido un peso**: `saldo == totalVenta - enganche`,
     * que es el predicado exacto de `RangoDeCobranza.sinAbonos`. Todas comparten
     * [MISMO_INSTANTE], así que empatan también en la segunda clave.
     */
    private fun sinAbonos(ventaId: Int): DatosDeVenta = PagosFixtures.datosDeVenta(
        ventaId = ventaId,
        folio = "V-$ventaId",
        descripcion = "Sala 3 piezas",
        cifras = PagosFixtures.Cifras(
            total = dinero("8400"),
            restante = dinero("7500"),
            cuota = dinero("350"),
            cubierto = Money.ZERO
        )
    ).copy(instanteDeVenta = MISMO_INSTANTE)

    /** La misma venta pero con dinero adentro: ya no rankea como virgen. */
    private fun yaAbono(ventaId: Int): DatosDeVenta =
        sinAbonos(ventaId).copy(saldo = dinero("2100"))

    private companion object {
        /** El mismo instante para todas: fuerza el empate en la segunda clave. */
        val MISMO_INSTANTE: Instant = Instant.parse("2026-05-04T18:00:00Z")
    }
}
