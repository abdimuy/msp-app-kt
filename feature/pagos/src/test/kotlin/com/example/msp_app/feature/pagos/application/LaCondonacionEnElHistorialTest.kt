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
import com.example.msp_app.feature.pagos.domain.FiltroDeContactos
import com.example.msp_app.feature.pagos.domain.GruposDeContactos
import com.example.msp_app.feature.pagos.domain.ToqueDelContacto
import com.example.msp_app.feature.pagos.domain.model.CondonacionDelHistorial
import com.example.msp_app.feature.pagos.domain.model.MetodoDeCobro
import com.example.msp_app.feature.pagos.domain.model.PagoDelHistorial
import com.example.msp_app.feature.pagos.domain.model.TipoDeContacto
import com.example.msp_app.feature.pagos.ui.PagosFixtures
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * **La condonación aparece en el historial de la venta, y en NINGÚN número.**
 *
 * El dueño: *"también debe aparecer en el historial de la venta"*. Hasta el
 * 2026-09-27 no aparecía en "lo que ha pasado" ni en la bitácora, porque las dos
 * salen de [PagoDelHistorial], que filtra `FORMAS_COBRO_COBRANZA` (157, 158,
 * 52569). La venta bajaba de saldo sin ningún renglón que lo explicara — y el
 * cobrador volvía a condonar (E-APP-029).
 *
 * Lo que se cobra aquí es que la condonación **entra a la línea de tiempo** y
 * **no toca nada de lo que ese conjunto protege**: el ritmo, el riel, el último
 * pago, el estado del periodo, el subtotal cobrado del mes, la pastilla
 * "Cobros" y el ticket desde la fila.
 *
 * ## El control positivo de cada ausencia
 *
 * "La condonación no cambió el último pago" es una ausencia. Para que valga,
 * [`control positivo - un abono si cambia ultimo pago, ritmo y estado`] mete un
 * **abono** con la misma fecha y el mismo monto que la condonación y comprueba
 * que ESE sí mueve los tres números. Sin él, un `ultimoPago` que nunca cambiara
 * por nada pasaría la prueba por la razón equivocada.
 */
class LaCondonacionEnElHistorialTest {

    private val clock = FakeClock(PagosFixtures.AHORA)
    private val telemetria = RecordingTelemetry(clock)

    private val ventasPort = FakeVentasPort()
    private val pagosPort = FakePagosPort()
    private val visitasPort = FakeVisitasPort()
    private val productosPort = FakeProductosPort()

    private val reunir = ReunirCobranzaDelCliente(
        ventasPort = ventasPort,
        pagosPort = pagosPort,
        visitasPort = visitasPort,
        liquidacionPort = FakeLiquidacionPort(),
        resolverVentanaDeCobro = ResolverVentanaDeCobro(FakePeriodoDeCobroPort(), clock),
        derivarEstadoDelPeriodo = DerivarEstadoDelPeriodo(telemetria)
    )

    private val cargarDetalle = CargarDetalleVenta(
        ventasPort = ventasPort,
        garantiasPort = FakeGarantiasPort(),
        productosPort = productosPort,
        reunirCobranzaDelCliente = reunir,
        clock = clock
    )

    private val cargarBitacora = CargarBitacoraDeLaVenta(
        ventasPort = ventasPort,
        productosPort = productosPort,
        reunirCobranzaDelCliente = reunir,
        clock = clock
    )

    private fun sembrarVenta() {
        ventasPort.ventas = listOf(
            PagosFixtures.datosDeVenta(
                ventaId = VENTA,
                folio = "Y00001786",
                descripcion = "Recámara y base",
                cifras = PagosFixtures.Cifras(
                    dinero("8400"),
                    dinero("4000"),
                    dinero("400"),
                    dinero("4400")
                )
            )
        )
        pagosPort.pagos = listOf(abono("ABONO-VIEJO", "2026-08-20T12:00:00Z"))
    }

    @Test
    fun `la condonacion aparece en lo que ha pasado del detalle de venta`() = runTest {
        sembrarVenta()
        pagosPort.condonaciones = listOf(condonacion("COND-1", HOY_A_MEDIODIA, aplicada = true))

        val detalle = checkNotNull(cargarDetalle(VENTA))
        val renglon = detalle.contactos.singleOrNull { it.id == "COND-1" }

        assertTrue(
            "la condonación no llegó a lo que ha pasado: ${detalle.contactos}",
            renglon != null
        )
        renglon!!
        assertEquals(TipoDeContacto.CONDONACION, renglon.tipo)
        assertEquals("Condonación", renglon.etiqueta)
        assertEquals(dinero("2300"), renglon.importe)
        assertEquals(PagosFixtures.COBRADOR, renglon.cobrador)
        assertEquals(Instant.parse(HOY_A_MEDIODIA), renglon.fecha)
        assertEquals(VENTA, renglon.ventaId)
        assertNull("condonar no es una forma de pago", renglon.metodo)
        assertTrue(renglon.aplicado)
    }

    @Test
    fun `la condonacion aparece en la bitacora de la venta`() = runTest {
        sembrarVenta()
        pagosPort.condonaciones = listOf(condonacion("COND-1", HOY_A_MEDIODIA, aplicada = true))

        val bitacora = checkNotNull(cargarBitacora(VENTA))

        assertEquals(
            "la bitácora tiene que traer el abono Y la condonación",
            setOf("ABONO-VIEJO", "COND-1"),
            bitacora.contactos.map { it.id }.toSet()
        )
        assertEquals(
            "Condonación",
            bitacora.contactos.single { it.id == "COND-1" }.etiqueta
        )
    }

    @Test
    fun `la condonacion no suma al cobrado del mes`() = runTest {
        sembrarVenta()
        pagosPort.pagos = listOf(abono("ABONO-SEP", "2026-09-01T12:00:00Z"))
        pagosPort.condonaciones = listOf(condonacion("COND-1", HOY_A_MEDIODIA, aplicada = true))

        val bitacora = checkNotNull(cargarBitacora(VENTA))
        val septiembre = GruposDeContactos.porMes(bitacora.contactos).single()

        assertEquals("el mes trae el abono y la condonación", 2, septiembre.contactos.size)
        assertEquals(
            "el cobrado del mes es sólo el abono de $400, no $400 + $2,300",
            dinero("400"),
            septiembre.cobrado
        )
    }

    @Test
    fun `la condonacion sola no pinta un cobrado de mes`() = runTest {
        sembrarVenta()
        pagosPort.pagos = emptyList()
        pagosPort.condonaciones = listOf(condonacion("COND-1", HOY_A_MEDIODIA, aplicada = true))

        val bitacora = checkNotNull(cargarBitacora(VENTA))

        assertNull(
            "un mes sólo con una condonación no cobró nada: el subtotal va en null",
            GruposDeContactos.porMes(bitacora.contactos).single().cobrado
        )
    }

    @Test
    fun `la condonacion cuenta en Todos y no en Cobros ni en Visitas`() = runTest {
        sembrarVenta()
        pagosPort.condonaciones = listOf(condonacion("COND-1", HOY_A_MEDIODIA, aplicada = true))

        val contactos = checkNotNull(cargarBitacora(VENTA)).contactos
        val renglon = contactos.single { it.id == "COND-1" }

        assertTrue(FiltroDeContactos.TODOS.deja(renglon))
        assertFalse("perdonar deuda no es un cobro", FiltroDeContactos.COBROS.deja(renglon))
        assertFalse(FiltroDeContactos.VISITAS.deja(renglon))
        assertFalse(FiltroDeContactos.PROMESAS.deja(renglon))
        val conteos = FiltroDeContactos.conteos(contactos)
        assertEquals(2, conteos[FiltroDeContactos.TODOS])
        assertEquals("sólo el abono", 1, conteos[FiltroDeContactos.COBROS])
    }

    /**
     * Cargada de verdad (puerto → mezcla → detalle), la condonación de hoy que
     * es el último movimiento de dinero de su cuenta ofrece SU ticket, igual que
     * un cobro (pedido del dueño, 2026-09-28). Sin punto medido: directo.
     */
    @Test
    fun `la condonacion de hoy, ultima de su cuenta, ofrece su ticket desde la fila`() = runTest {
        sembrarVenta()
        pagosPort.condonaciones = listOf(condonacion("COND-1", HOY_A_MEDIODIA, aplicada = true))

        val detalle = checkNotNull(cargarDetalle(VENTA))
        val renglon = detalle.contactos.single { it.id == "COND-1" }

        assertEquals(
            ToqueDelContacto.TICKET,
            ToqueDelContacto.de(renglon, detalle.contactos, detalle.hoy)
        )
    }

    /**
     * La condonación no mueve el ritmo, el riel, el total de pagos, el último
     * pago ni el estado del periodo: se comparan contra la MISMA venta sin
     * condonación.
     */
    @Test
    fun `la condonacion no cambia ritmo, ultimo pago ni estado del periodo`() = runTest {
        sembrarVenta()
        val sin = checkNotNull(cargarDetalle(VENTA))

        pagosPort.condonaciones = listOf(condonacion("COND-1", HOY_A_MEDIODIA, aplicada = true))
        val con = checkNotNull(cargarDetalle(VENTA))

        assertEquals(sin.historial, con.historial)
        assertEquals(sin.ultimoPago, con.ultimoPago)
        assertEquals(sin.estado, con.estado)
        assertEquals(1, con.historial.totalPagos)
    }

    /** Control positivo del de arriba: un ABONO igual sí mueve los tres. */
    @Test
    fun `control positivo - un abono si cambia ultimo pago, ritmo y estado`() = runTest {
        sembrarVenta()
        val sin = checkNotNull(cargarDetalle(VENTA))

        pagosPort.pagos = pagosPort.pagos + abono("ABONO-HOY", HOY_A_MEDIODIA, pesos = "2300")
        val con = checkNotNull(cargarDetalle(VENTA))

        assertNotEquals(sin.historial, con.historial)
        assertEquals(LocalDate.parse("2026-09-01"), con.ultimoPago)
        assertNotEquals(sin.ultimoPago, con.ultimoPago)
        assertNotEquals(sin.estado, con.estado)
    }

    /**
     * **Una condonación que el servidor rechazó no se pinta como aplicada.** Se
     * enseña —se intentó, y la oficina la tiene resguardada— con su etiqueta
     * propia y apagada.
     */
    @Test
    fun `una condonacion rechazada se ve como no aplicada`() = runTest {
        sembrarVenta()
        pagosPort.condonaciones = listOf(
            condonacion("COND-OK", "2026-09-01T15:00:00Z", aplicada = true),
            condonacion("COND-RECHAZADA", HOY_A_MEDIODIA, aplicada = false)
        )

        val contactos = checkNotNull(cargarBitacora(VENTA)).contactos
        val rechazada = contactos.single { it.id == "COND-RECHAZADA" }
        val aplicada = contactos.single { it.id == "COND-OK" }

        assertEquals("Condonación no aplicada", rechazada.etiqueta)
        assertFalse(rechazada.aplicado)
        // Control positivo: la aplicada del mismo lote sí dice "Condonación".
        assertEquals("Condonación", aplicada.etiqueta)
        assertTrue(aplicada.aplicado)
    }

    private fun abono(id: String, fechaIso: String, pesos: String = "400") = PagoDelHistorial(
        pagoId = id,
        ventaId = VENTA,
        fecha = Instant.parse(fechaIso),
        importe = dinero(pesos),
        formaCobroId = MetodoDeCobro.EFECTIVO.formaCobroId,
        metodo = MetodoDeCobro.EFECTIVO,
        nota = null,
        cobrador = PagosFixtures.COBRADOR
    )

    private fun condonacion(id: String, fechaIso: String, aplicada: Boolean) =
        CondonacionDelHistorial(
            condonacionId = id,
            ventaId = VENTA,
            fecha = Instant.parse(fechaIso),
            importe = dinero("2300"),
            cobrador = PagosFixtures.COBRADOR,
            aplicada = aplicada
        )

    private fun dinero(pesos: String): Money = Money.of(BigDecimal(pesos))

    private companion object {
        const val VENTA = 12_845_224

        /** El mismo día que `PagosFixtures.AHORA` (2026-09-01), a media mañana. */
        const val HOY_A_MEDIODIA = "2026-09-01T17:00:00Z"
    }
}
