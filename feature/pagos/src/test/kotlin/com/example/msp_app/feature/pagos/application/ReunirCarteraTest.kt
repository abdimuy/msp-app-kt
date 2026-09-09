package com.example.msp_app.feature.pagos.application

import com.example.msp_app.core.common.cobranza.domain.EstadoCuenta
import com.example.msp_app.core.common.time.AppClock
import com.example.msp_app.core.common.time.AppTime
import com.example.msp_app.core.telemetry.TelemetryEventType
import com.example.msp_app.core.testing.telemetry.RecordingTelemetry
import com.example.msp_app.core.testing.time.FakeClock
import com.example.msp_app.feature.pagos.data.fake.FakePagosPort
import com.example.msp_app.feature.pagos.data.fake.FakePeriodoDeCobroPort
import com.example.msp_app.feature.pagos.data.fake.FakeVentasPort
import com.example.msp_app.feature.pagos.data.fake.FakeVisitasPort
import com.example.msp_app.feature.pagos.ui.ListaFixtures
import com.example.msp_app.feature.pagos.ui.PagosFixtures
import java.time.Instant
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * De una lista de VENTAS a una lista de CLIENTES.
 *
 * Este es el punto donde se cierra el defecto estructural: la fuente son ventas
 * (`sales`, una fila por `DOCTO_CC_ID`) y lo que sale es una fila por puerta.
 */
class ReunirCarteraTest {

    private val clock = FakeClock(PagosFixtures.AHORA)
    private val telemetria = RecordingTelemetry(clock)
    private val ventasPort = FakeVentasPort()
    private val pagosPort = FakePagosPort()
    private val visitasPort = FakeVisitasPort()
    private val periodoPort = FakePeriodoDeCobroPort()

    private fun reunir() = ReunirCartera(
        ventasPort = ventasPort,
        pagosPort = pagosPort,
        visitasPort = visitasPort,
        resolverVentanaDeCobro = ResolverVentanaDeCobro(periodoPort, clock),
        derivarEstadoDelPeriodo = DerivarEstadoDelPeriodo(telemetria),
        telemetry = telemetria
    )

    private fun errores(code: String) = telemetria.recorded.count {
        it.type == TelemetryEventType.ERROR && it.name == code
    }

    @Test
    fun `cuatro ventas de tres clientes dan TRES filas`() = runTest {
        ventasPort.ventas = ListaFixtures.datosDeLaRuta()
        val cartera = reunir()()
        assertEquals(3, cartera.clientes.size)
        assertEquals(
            listOf(ListaFixtures.VICTORIA, ListaFixtures.RICARDO, ListaFixtures.GUADALUPE),
            cartera.clientes.map { it.clienteId }
        )
    }

    @Test
    fun `el cliente con dos ventas las lleva las dos dentro`() = runTest {
        ventasPort.ventas = ListaFixtures.datosDeLaRuta()
        val victoria = reunir()().clientes.single { it.clienteId == ListaFixtures.VICTORIA }
        assertEquals(2, victoria.cuentas)
        assertEquals(listOf("V-5021", "V-5188"), victoria.ventas.map { it.venta.folio })
        // El saldo del cliente es la suma de sus cuentas: 2100 + 5500.
        assertEquals(ListaFixtures.dinero("7600"), victoria.saldoTotal)
    }

    @Test
    fun `el texto buscable trae los folios de TODAS sus ventas`() = runTest {
        ventasPort.ventas = ListaFixtures.datosDeLaRuta()
        val victoria = reunir()().clientes.single { it.clienteId == ListaFixtures.VICTORIA }
        assertTrue(victoria.textoBuscable.contains("v-5021"))
        assertTrue(victoria.textoBuscable.contains("v-5188"))
        assertTrue(victoria.textoBuscable.contains("victoria flores olmedo"))
    }

    /**
     * `SalesScreen.kt:68` —retirada por la Task 21— concatenaba SEIS campos y
     * el sexto era `ESTADO`, la entidad federativa. Se quedó fuera en la primera
     * versión: un buscador que encuentra menos que la pantalla a la que
     * reemplazó es una regresión que el cobrador siente antes que nadie.
     */
    @Test
    fun `el texto buscable trae la entidad, como la pantalla vieja`() = runTest {
        ventasPort.ventas = ListaFixtures.datosDeLaRuta()
        val victoria = reunir()().clientes.single { it.clienteId == ListaFixtures.VICTORIA }
        assertTrue(victoria.textoBuscable.contains("puebla"))
    }

    /**
     * **El reloj se lee UNA vez.** Con dos lecturas, una carga que cruza la
     * medianoche cerraba la ventana el día N y calculaba `hoy` como N+1, así que
     * los chips "hoy"/"vencidos" contestaban sobre un día distinto al que derivó
     * los estados. El reloj de abajo avanza un minuto en cada llamada y cruza la
     * medianoche justo entre las dos.
     */
    @Test
    fun `la ventana y el hoy salen del mismo instante aunque cruce la medianoche`() = runTest {
        val relojQueCruzaLaMedianoche = object : AppClock {
            private var siguiente = Instant.parse("2026-09-02T05:59:30Z") // 23:59:30 CDMX
            override fun now(): Instant {
                val ahora = siguiente
                siguiente = siguiente.plusSeconds(60)
                return ahora
            }
        }
        ventasPort.ventas = ListaFixtures.datosDeLaRuta()
        val cartera = ReunirCartera(
            ventasPort = ventasPort,
            pagosPort = pagosPort,
            visitasPort = visitasPort,
            resolverVentanaDeCobro = ResolverVentanaDeCobro(
                periodoPort,
                relojQueCruzaLaMedianoche
            ),
            derivarEstadoDelPeriodo = DerivarEstadoDelPeriodo(telemetria),
            telemetry = telemetria
        )()
        val finDeLaVentana = pagosPort.ventanasConsultadas.single().fin
        assertEquals(AppTime.toBusinessDate(finDeLaVentana), cartera.hoy)
    }

    @Test
    fun `la ruta se lee UNA vez, no una por cliente`() = runTest {
        ventasPort.ventas = ListaFixtures.datosDeLaRuta()
        reunir()()
        assertEquals(1, ventasPort.lecturasDeTodas)
        assertEquals(1, pagosPort.ventanasConsultadas.size)
        assertEquals(1, visitasPort.ventanasConsultadas.size)
        // Y ni un solo viaje por venta ni por cliente.
        assertTrue(pagosPort.ventasConsultadas.isEmpty())
    }

    @Test
    fun `una venta con FECHA ilegible se reporta, con conteo y sin PII`() = runTest {
        ventasPort.ventas = ListaFixtures.datosDeLaRuta() + ListaFixtures.datos(
            clienteId = 9500,
            nombre = "Efraín Cordero Lagos",
            ventaId = 95001,
            folio = "V-9500",
            saldo = "1000",
            total = "5000",
            enganche = "500",
            fecha = null
        )
        reunir()()
        assertEquals(1, errores(PagosTelemetria.CODE_VENTA_SIN_FECHA_LEGIBLE))
        val evento = telemetria.recorded.single {
            it.name == PagosTelemetria.CODE_VENTA_SIN_FECHA_LEGIBLE
        }
        assertEquals("1", evento.props[PagosTelemetria.PROP_OCURRENCIAS])
        // Anti-PII: ni el folio, ni el nombre, ni el monto.
        assertTrue(evento.props.values.none { it.contains("Efraín") || it.contains("V-9500") })
    }

    @Test
    fun `sin ventas ilegibles no se emite nada`() = runTest {
        ventasPort.ventas = ListaFixtures.datosDeLaRuta()
        reunir()()
        assertEquals(0, errores(PagosTelemetria.CODE_VENTA_SIN_FECHA_LEGIBLE))
    }

    @Test
    fun `sin ventana de cobro no se inventa periodo y se reporta`() = runTest {
        periodoPort.inicio = null
        ventasPort.ventas = ListaFixtures.datosDeLaRuta()
        val cartera = reunir()()
        assertEquals(1, errores(PagosTelemetria.CODE_PERIODO_DESCONOCIDO))
        assertTrue(
            cartera.clientes
                .flatMap { it.ventas }
                .all { it.venta.estado.estado == EstadoCuenta.SIN_TOCAR }
        )
        // Y no se leen pagos ni visitas: no hay ventana que consultar.
        assertTrue(pagosPort.ventanasConsultadas.isEmpty())
        assertTrue(visitasPort.ventanasConsultadas.isEmpty())
    }

    @Test
    fun `el hoy de la cartera sale del mismo reloj que cierra la ventana`() = runTest {
        ventasPort.ventas = ListaFixtures.datosDeLaRuta()
        assertEquals(ListaFixtures.HOY, reunir()().hoy)
    }
}
