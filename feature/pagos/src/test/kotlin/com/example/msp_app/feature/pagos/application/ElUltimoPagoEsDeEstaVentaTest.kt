package com.example.msp_app.feature.pagos.application

import com.example.msp_app.core.common.money.Money
import com.example.msp_app.core.testing.telemetry.RecordingTelemetry
import com.example.msp_app.core.testing.time.FakeClock
import com.example.msp_app.feature.pagos.data.fake.FakeGarantiasPort
import com.example.msp_app.feature.pagos.data.fake.FakeLiquidacionPort
import com.example.msp_app.feature.pagos.data.fake.FakePagosPort
import com.example.msp_app.feature.pagos.data.fake.FakePeriodoDeCobroPort
import com.example.msp_app.feature.pagos.data.fake.FakeProductosPort
import com.example.msp_app.feature.pagos.data.fake.FakeVentasPort
import com.example.msp_app.feature.pagos.data.fake.FakeVisitasPort
import com.example.msp_app.feature.pagos.domain.model.MetodoDeCobro
import com.example.msp_app.feature.pagos.domain.model.PagoDelHistorial
import com.example.msp_app.feature.pagos.ui.PagosFixtures
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * **"Último pago" es el último abono de ESTA venta**, no la última visita del
 * cliente ni el último abono de otra de sus cuentas (mock
 * `detalle-de-venta-final.html`, "Decidido" punto 3).
 *
 * El caso que lo prueba siembra, MÁS RECIENTES que el abono propio, un abono de
 * la otra venta del cliente y una visita a esta misma venta: si la carga tomara
 * cualquiera de los dos, la fecha saldría otra.
 */
class ElUltimoPagoEsDeEstaVentaTest {

    private val clock = FakeClock(PagosFixtures.AHORA)
    private val ventasPort = FakeVentasPort().apply { ventas = PagosFixtures.datosDeVentas() }
    private val pagosPort = FakePagosPort()
    private val visitasPort = FakeVisitasPort()

    private val cargar = CargarDetalleVenta(
        ventasPort = ventasPort,
        garantiasPort = FakeGarantiasPort(),
        productosPort = FakeProductosPort(),
        reunirCobranzaDelCliente = ReunirCobranzaDelCliente(
            ventasPort = ventasPort,
            pagosPort = pagosPort,
            visitasPort = visitasPort,
            liquidacionPort = FakeLiquidacionPort(),
            resolverVentanaDeCobro = ResolverVentanaDeCobro(FakePeriodoDeCobroPort(), clock),
            derivarEstadoDelPeriodo = DerivarEstadoDelPeriodo(RecordingTelemetry(clock))
        ),
        clock = clock
    )

    @Test
    fun `el ultimo pago es el de esta venta, no el de otra cuenta ni una visita`() = runBlocking {
        pagosPort.pagos = listOf(
            pago("propio-viejo", "2026-08-03T17:00:00Z", PagosFixtures.VENTA_EN_PROMESA),
            pago("propio", "2026-08-31T17:00:00Z", PagosFixtures.VENTA_EN_PROMESA),
            // Más reciente que el propio, pero de la OTRA cuenta del cliente.
            pago("ajeno", "2026-09-05T17:00:00Z", PagosFixtures.VENTA_PAGADA)
        )
        // Y una visita a ESTA venta, también más reciente que el abono propio.
        visitasPort.visitas = listOf(PagosFixtures.visita("No estaba", fechaIso = "2026-09-06T16:00:00Z"))

        val detalle = checkNotNull(cargar(PagosFixtures.VENTA_EN_PROMESA))

        assertEquals(LocalDate.of(2026, 8, 31), detalle.ultimoPago)
    }

    @Test
    fun `control positivo - la otra venta ve SU ultimo pago`() = runBlocking {
        pagosPort.pagos = listOf(
            pago("propio", "2026-08-31T17:00:00Z", PagosFixtures.VENTA_EN_PROMESA),
            pago("ajeno", "2026-09-05T17:00:00Z", PagosFixtures.VENTA_PAGADA)
        )

        val detalle = checkNotNull(cargar(PagosFixtures.VENTA_PAGADA))

        assertEquals(LocalDate.of(2026, 9, 5), detalle.ultimoPago)
    }

    @Test
    fun `sin abonos de esta venta no hay ultimo pago que pintar`() = runBlocking {
        pagosPort.pagos = listOf(pago("ajeno", "2026-09-05T17:00:00Z", PagosFixtures.VENTA_PAGADA))
        visitasPort.visitas = listOf(PagosFixtures.visita("No estaba", fechaIso = "2026-09-06T16:00:00Z"))

        val detalle = checkNotNull(cargar(PagosFixtures.VENTA_EN_PROMESA))

        assertNull(detalle.ultimoPago)
    }

    private fun pago(id: String, fechaIso: String, ventaId: Int) = PagoDelHistorial(
        pagoId = id,
        ventaId = ventaId,
        fecha = Instant.parse(fechaIso),
        importe = Money.of(BigDecimal("220")),
        formaCobroId = MetodoDeCobro.EFECTIVO.formaCobroId,
        metodo = MetodoDeCobro.EFECTIVO,
        nota = null
    )
}
