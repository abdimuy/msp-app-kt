package com.example.msp_app.feature.pagos.application

import com.example.msp_app.core.common.money.Money
import com.example.msp_app.core.testing.telemetry.RecordingTelemetry
import com.example.msp_app.core.testing.time.FakeClock
import com.example.msp_app.feature.pagos.data.fake.FakeFichaPort
import com.example.msp_app.feature.pagos.data.fake.FakeGarantiasPort
import com.example.msp_app.feature.pagos.data.fake.FakeLiquidacionPort
import com.example.msp_app.feature.pagos.data.fake.FakePagosPort
import com.example.msp_app.feature.pagos.data.fake.FakePeriodoDeCobroPort
import com.example.msp_app.feature.pagos.data.fake.FakeProductosPort
import com.example.msp_app.feature.pagos.data.fake.FakeVentasPort
import com.example.msp_app.feature.pagos.data.fake.FakeVisitasPort
import com.example.msp_app.feature.pagos.domain.model.MetodoDeCobro
import com.example.msp_app.feature.pagos.domain.model.PagoDelHistorial
import com.example.msp_app.feature.pagos.domain.model.ProductoDeVenta
import com.example.msp_app.feature.pagos.ui.PagosFixtures
import java.math.BigDecimal
import java.time.Instant
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * **El dato entra por la mezcla, y las tres pantallas lo tienen** — la
 * condición del `task-2-brief.md`: [CargarDetalleCliente],
 * [CargarBitacoraDelCliente] y [CargarDetalleVenta] pasan por
 * [com.example.msp_app.feature.pagos.domain.BitacoraDelCliente.de], así que la
 * cuenta tiene que llegar a las tres o el defecto reaparece en dos de tres
 * pantallas y ninguna prueba lo vería.
 *
 * Es el caso real completo: dos cuentas del mismo cliente, cobradas con un
 * minuto de diferencia, que hoy se ven como un cobro repetido porque la fila
 * no dice a cuál cuenta se abonó.
 */
class CuentaEnLasTresPantallasTest {

    private val clock = FakeClock(PagosFixtures.AHORA)
    private val telemetria = RecordingTelemetry(clock)

    private val ventasPort = FakeVentasPort()
    private val pagosPort = FakePagosPort()
    private val visitasPort = FakeVisitasPort()
    private val liquidacionPort = FakeLiquidacionPort()
    private val periodoPort = FakePeriodoDeCobroPort()
    private val productosPort = FakeProductosPort()

    private val reunirCobranzaDelCliente = ReunirCobranzaDelCliente(
        ventasPort = ventasPort,
        pagosPort = pagosPort,
        visitasPort = visitasPort,
        liquidacionPort = liquidacionPort,
        resolverVentanaDeCobro = ResolverVentanaDeCobro(periodoPort, clock),
        derivarEstadoDelPeriodo = DerivarEstadoDelPeriodo(telemetria)
    )

    private fun sembrarElCasoReal() {
        ventasPort.ventas = listOf(
            PagosFixtures.datosDeVenta(
                ventaId = VENTA_RECAMARA,
                folio = "Y00001786",
                descripcion = "Recámara y base",
                cifras = PagosFixtures.Cifras(dinero("8400"), dinero("4000"), dinero("400"), dinero("4400"))
            ),
            PagosFixtures.datosDeVenta(
                ventaId = VENTA_BOCINA,
                folio = "Y00002103",
                descripcion = "Bocina",
                cifras = PagosFixtures.Cifras(dinero("1200"), dinero("1100"), dinero("100"), dinero("100"))
            )
        )
        pagosPort.pagos = listOf(
            pago(VENTA_RECAMARA, "COB-RECAMARA", "2026-09-11T12:00:00Z", "400"),
            pago(VENTA_BOCINA, "COB-BOCINA", "2026-09-11T12:01:00Z", "100")
        )
        // La cuenta de la recámara trae DOS renglones: la recámara primero,
        // la base de cama después — el orden real del folio Y00001786.
        productosPort.porFolio = mapOf(
            "Y00001786" to listOf(
                ProductoDeVenta("RECAMARA CANTARO KING SIZE CHOCOLATE", dinero("300")),
                ProductoDeVenta("BASE DE CAMA MATRIMONIAL", dinero("100"))
            ),
            "Y00002103" to listOf(ProductoDeVenta("BOCINA PROFESIONAL 8'' AUDIOBAHN", dinero("100")))
        )
    }

    private fun pago(ventaId: Int, id: String, fechaIso: String, pesos: String) = PagoDelHistorial(
        pagoId = id,
        ventaId = ventaId,
        fecha = Instant.parse(fechaIso),
        importe = dinero(pesos),
        formaCobroId = MetodoDeCobro.EFECTIVO.formaCobroId,
        metodo = MetodoDeCobro.EFECTIVO,
        nota = null
    )

    private fun dinero(pesos: String): Money = Money.of(BigDecimal(pesos))

    @Test
    fun `CargarDetalleCliente pinta las dos cuentas con nombres distintos`() = runTest {
        sembrarElCasoReal()
        val detalle = checkNotNull(
            CargarDetalleCliente(
                reunirCobranzaDelCliente = reunirCobranzaDelCliente,
                fichaPort = FakeFichaPort(),
                productosPort = productosPort,
                clock = clock
            )(PagosFixtures.CLIENTE_ID)
        )
        val porVenta = detalle.contactos.associateBy { it.ventaId }

        assertEquals(
            "Recamara cantaro king size chocolate",
            porVenta.getValue(VENTA_RECAMARA).cuenta
        )
        assertEquals("Bocina profesional 8'' audiobahn", porVenta.getValue(VENTA_BOCINA).cuenta)
    }

    @Test
    fun `CargarBitacoraDelCliente pinta las dos cuentas con nombres distintos`() = runTest {
        sembrarElCasoReal()
        val bitacora = checkNotNull(
            CargarBitacoraDelCliente(
                reunirCobranzaDelCliente = reunirCobranzaDelCliente,
                productosPort = productosPort
            )(PagosFixtures.CLIENTE_ID)
        )
        val porVenta = bitacora.contactos.associateBy { it.ventaId }

        assertEquals(
            "Recamara cantaro king size chocolate",
            porVenta.getValue(VENTA_RECAMARA).cuenta
        )
        assertEquals("Bocina profesional 8'' audiobahn", porVenta.getValue(VENTA_BOCINA).cuenta)
    }

    /**
     * Vista desde la cuenta de la bocina, el contacto de la RECÁMARA —de la
     * OTRA cuenta del mismo cliente— también trae su nombre: `cuentas` se
     * arma con TODAS las ventas del cliente, no solo con la que se está
     * viendo.
     */
    @Test
    fun `CargarDetalleVenta pinta la cuenta de la venta que se ve y la de la otra`() = runTest {
        sembrarElCasoReal()
        val detalle = checkNotNull(
            CargarDetalleVenta(
                ventasPort = ventasPort,
                garantiasPort = FakeGarantiasPort(),
                productosPort = productosPort,
                reunirCobranzaDelCliente = reunirCobranzaDelCliente,
                clock = clock
            )(VENTA_BOCINA)
        )
        val porVenta = detalle.contactos.associateBy { it.ventaId }

        assertEquals("Bocina profesional 8'' audiobahn", porVenta.getValue(VENTA_BOCINA).cuenta)
        assertEquals(
            "Recamara cantaro king size chocolate",
            porVenta.getValue(VENTA_RECAMARA).cuenta
        )
    }

    private companion object {
        const val VENTA_RECAMARA = 12_845_224
        const val VENTA_BOCINA = 14_431_255
    }
}
