package com.example.msp_app.feature.pagos.application

import com.example.msp_app.core.common.money.Money
import com.example.msp_app.core.testing.telemetry.RecordingTelemetry
import com.example.msp_app.core.testing.time.FakeClock
import com.example.msp_app.feature.pagos.data.fake.FakeLiquidacionPort
import com.example.msp_app.feature.pagos.data.fake.FakePagosPort
import com.example.msp_app.feature.pagos.data.fake.FakePeriodoDeCobroPort
import com.example.msp_app.feature.pagos.data.fake.FakeProductosPort
import com.example.msp_app.feature.pagos.data.fake.FakeVentasPort
import com.example.msp_app.feature.pagos.data.fake.FakeVisitasPort
import com.example.msp_app.feature.pagos.domain.model.MetodoDeCobro
import com.example.msp_app.feature.pagos.domain.model.PagoDelHistorial
import com.example.msp_app.feature.pagos.domain.model.VisitaDelCliente
import com.example.msp_app.feature.pagos.ui.PagosFixtures
import java.math.BigDecimal
import java.time.Instant
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * **`CargarBitacoraDeLaVenta` devuelve SÓLO los contactos de la venta que se
 * pidió — nunca los de otra cuenta del mismo cliente, y nunca una visita
 * registrada sin cuenta.**
 *
 * Es la mitad de dominio del cambio "la bitácora es por venta, no por
 * cliente" (decisión del dueño, tras quitar "últimos contactos" del detalle
 * de cliente). La otra mitad —que la pantalla se titule con el producto y no
 * con el folio— la cubre `BitacoraViewModelTest`.
 *
 * El control positivo es la propia venta pedida: si el filtro fuera un error
 * que vaciara TODO, la aserción de ausencia de la otra cuenta pasaría por la
 * razón equivocada. Por eso cada test también comprueba que SÍ queda algo de
 * la cuenta correcta.
 */
class CargarBitacoraDeLaVentaTest {

    private val clock = FakeClock(PagosFixtures.AHORA)
    private val telemetria = RecordingTelemetry(clock)

    private val ventasPort = FakeVentasPort()
    private val pagosPort = FakePagosPort()
    private val visitasPort = FakeVisitasPort()
    private val productosPort = FakeProductosPort()

    private val cargar = CargarBitacoraDeLaVenta(
        ventasPort = ventasPort,
        productosPort = productosPort,
        reunirCobranzaDelCliente = ReunirCobranzaDelCliente(
            ventasPort = ventasPort,
            pagosPort = pagosPort,
            visitasPort = visitasPort,
            liquidacionPort = FakeLiquidacionPort(),
            resolverVentanaDeCobro = ResolverVentanaDeCobro(FakePeriodoDeCobroPort(), clock),
            derivarEstadoDelPeriodo = DerivarEstadoDelPeriodo(telemetria)
        ),
        clock = clock
    )

    private fun sembrar() {
        ventasPort.ventas = listOf(
            PagosFixtures.datosDeVenta(
                ventaId = VENTA_PEDIDA,
                folio = "Y00001786",
                descripcion = "Recámara y base",
                cifras = PagosFixtures.Cifras(dinero("8400"), dinero("4000"), dinero("400"), dinero("4400"))
            ),
            PagosFixtures.datosDeVenta(
                ventaId = OTRA_VENTA,
                folio = "Y00002103",
                descripcion = "Bocina",
                cifras = PagosFixtures.Cifras(dinero("1200"), dinero("1100"), dinero("100"), dinero("100"))
            )
        )
        pagosPort.pagos = listOf(
            pago(VENTA_PEDIDA, "COB-PROPIO", "2026-08-20T12:00:00Z"),
            pago(OTRA_VENTA, "COB-AJENO", "2026-08-21T12:00:00Z")
        )
        visitasPort.visitas = listOf(
            visita("visita-propia", VENTA_PEDIDA, "2026-08-22T16:00:00Z"),
            visita("visita-ajena", OTRA_VENTA, "2026-08-23T16:00:00Z"),
            // Una visita sin cuenta — el cobrador la registró sin elegir venta.
            // NO entra a la bitácora de NINGUNA cuenta.
            visita("visita-sin-cuenta", null, "2026-08-24T16:00:00Z")
        )
    }

    @Test
    fun `no trae el contacto de la otra cuenta del mismo cliente`() = runTest {
        sembrar()

        val bitacora = checkNotNull(cargar(VENTA_PEDIDA))

        assertTrue(
            "trajo el pago de la OTRA cuenta",
            bitacora.contactos.none { it.id == "COB-AJENO" }
        )
        assertTrue(
            "trajo la visita de la OTRA cuenta",
            bitacora.contactos.none { it.id == "visita-ajena" }
        )
    }

    @Test
    fun `no trae una visita registrada sin cuenta`() = runTest {
        sembrar()

        val bitacora = checkNotNull(cargar(VENTA_PEDIDA))

        assertTrue(
            "una visita sin ventaId apareció en la bitácora de una cuenta que no eligió",
            bitacora.contactos.none { it.id == "visita-sin-cuenta" }
        )
    }

    /**
     * **Control positivo de los dos de arriba.** Sin esto, un filtro roto que
     * vaciara la lista entera pasaría las dos ausencias por la razón
     * equivocada: "no está" es distinto de "no hay nada".
     */
    @Test
    fun `control positivo - si trae los contactos de la cuenta pedida`() = runTest {
        sembrar()

        val bitacora = checkNotNull(cargar(VENTA_PEDIDA))

        assertEquals(
            "la cuenta pedida trae un pago y una visita: la lista no puede venir vacía " +
                "ni traer sólo uno de los dos",
            setOf("COB-PROPIO", "visita-propia"),
            bitacora.contactos.map { it.id }.toSet()
        )
    }

    @Test
    fun `una venta que el telefono no tiene devuelve null`() = runTest {
        sembrar()

        assertNull(cargar(999_999))
    }

    private fun pago(ventaId: Int, id: String, fechaIso: String) = PagoDelHistorial(
        pagoId = id,
        ventaId = ventaId,
        fecha = Instant.parse(fechaIso),
        importe = dinero("400"),
        formaCobroId = MetodoDeCobro.EFECTIVO.formaCobroId,
        metodo = MetodoDeCobro.EFECTIVO,
        nota = null
    )

    private fun visita(id: String, ventaId: Int?, fechaIso: String) = VisitaDelCliente(
        visitaId = id,
        clienteId = PagosFixtures.CLIENTE_ID,
        ventaId = ventaId,
        fecha = Instant.parse(fechaIso),
        tipoVisita = "No estaba",
        nota = null,
        fechaPromesa = null
    )

    private fun dinero(pesos: String): Money = Money.of(BigDecimal(pesos))

    private companion object {
        const val VENTA_PEDIDA = 12_845_224
        const val OTRA_VENTA = 14_431_255
    }
}
