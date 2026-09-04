package com.example.msp_app.feature.pagos.application

import com.example.msp_app.core.common.money.Money
import com.example.msp_app.core.telemetry.TelemetryEventType
import com.example.msp_app.core.testing.telemetry.RecordingTelemetry
import com.example.msp_app.core.testing.time.FakeClock
import com.example.msp_app.feature.pagos.data.fake.FakeRegistroDeAbonoPort
import com.example.msp_app.feature.pagos.domain.model.MetodoDeCobro
import com.example.msp_app.feature.pagos.domain.port.ResultadoDelAbono
import com.example.msp_app.feature.pagos.ui.AbonoFixtures
import com.example.msp_app.feature.pagos.ui.PagosFixtures
import java.math.BigDecimal
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * El **segundo cinturón** del bloqueo duro, ejercitado directamente.
 *
 * Desde el ViewModel este camino es inalcanzable —la pantalla ya bloquea—, así
 * que nada lo probaba. Y un cinturón que nadie prueba es un cinturón que un
 * refactor puede quitar sin que se ponga rojo nada: exactamente lo que este
 * caso de uso existe para evitar. Aquí se construye a mano y se le pasa un
 * monto prohibido.
 */
class RegistrarAbonoTest {

    private val clock = FakeClock(PagosFixtures.AHORA)
    private val telemetria = RecordingTelemetry(clock)
    private val port = FakeRegistroDeAbonoPort()

    private val registrarAbono = RegistrarAbono(port, telemetria)

    private val venta = AbonoFixtures.detalle()

    @Test
    fun `un sobrepago no llega al puerto, y no se traga`() = runTest {
        val resultado = registrar(dinero("1451"))

        assertEquals(ResultadoDelAbono.BLOQUEADO_POR_SEGURIDAD, resultado)
        assertTrue("el escritor ni se enteró", port.registrados.isEmpty())
        val error = telemetria.recorded.single {
            it.type == TelemetryEventType.ERROR &&
                it.name == PagosTelemetria.CODE_ABONO_BLOQUEADO_EN_APLICACION
        }
        assertEquals("EXCEDE_EL_SALDO", error.props[PagosTelemetria.PROP_BLOQUEOS])
        // Anti-PII: viajan los NOMBRES de los bloqueos, nunca el monto ni el saldo.
        assertFalse(error.props.values.any { it.contains("1451") || it.contains("1450") })
    }

    @Test
    fun `un monto en cero tampoco llega al puerto`() = runTest {
        assertEquals(ResultadoDelAbono.BLOQUEADO_POR_SEGURIDAD, registrar(Money.ZERO))
        assertTrue(port.registrados.isEmpty())
        assertEquals(
            "NO_ES_POSITIVO",
            telemetria.recorded
                .single { it.name == PagosTelemetria.CODE_ABONO_BLOQUEADO_EN_APLICACION }
                .props[PagosTelemetria.PROP_BLOQUEOS]
        )
    }

    @Test
    fun `una venta sin saldo no admite abonos`() = runTest {
        val saldada = venta.copy(saldo = Money.ZERO)
        val resultado = registrarAbono(
            abonoId = ABONO_ID,
            venta = saldada,
            importe = dinero("100"),
            metodo = MetodoDeCobro.EFECTIVO
        )
        assertEquals(ResultadoDelAbono.BLOQUEADO_POR_SEGURIDAD, resultado)
        assertTrue(port.registrados.isEmpty())
    }

    // --- Control positivo: el cinturón no bloquea lo que sí se puede ---------

    @Test
    fun `el saldo exacto pasa el cinturon y llega al puerto`() = runTest {
        assertEquals(ResultadoDelAbono.REGISTRADO, registrar(AbonoFixtures.SALDO))
        assertEquals(1, port.registrados.size)
        assertEquals(AbonoFixtures.SALDO, port.registrados.single().importe)
        assertEquals(ABONO_ID, port.registrados.single().abonoId)
        assertEquals(AbonoFixtures.VENTA_ID, port.registrados.single().ventaId)
        assertTrue(
            "sin bloqueo no hay evento",
            telemetria.recorded.none {
                it.name == PagosTelemetria.CODE_ABONO_BLOQUEADO_EN_APLICACION
            }
        )
    }

    @Test
    fun `el resultado del puerto viaja tal cual`() = runTest {
        port.resultado = ResultadoDelAbono.SIN_COBRADOR
        assertEquals(ResultadoDelAbono.SIN_COBRADOR, registrar(dinero("220")))
    }

    private suspend fun registrar(importe: Money) = registrarAbono(
        abonoId = ABONO_ID,
        venta = venta,
        importe = importe,
        metodo = MetodoDeCobro.EFECTIVO
    )

    private fun dinero(pesos: String): Money = Money.of(BigDecimal(pesos))

    private companion object {
        const val ABONO_ID = "abono-de-prueba-0001"
    }
}
