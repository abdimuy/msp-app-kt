package com.example.msp_app.feature.visitas.application

import com.example.msp_app.core.common.cobranza.domain.TipoVisitaCatalogo
import com.example.msp_app.core.common.money.Money
import com.example.msp_app.core.testing.telemetry.RecordingTelemetry
import com.example.msp_app.feature.visitas.data.fake.FakeRegistroDeVisitaPort
import com.example.msp_app.feature.visitas.data.fake.FakeUbicacionPort
import com.example.msp_app.feature.visitas.data.fake.VisitasFixtures
import com.example.msp_app.feature.visitas.domain.model.BloqueoDeLaVisita
import com.example.msp_app.feature.visitas.domain.model.CapturaDeVisita
import com.example.msp_app.feature.visitas.domain.model.ResultadoDeVisita
import com.example.msp_app.feature.visitas.domain.port.ResultadoDelRegistro
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalTime
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * El caso de uso: qué dato estructurado se arma, y **que la ubicación no pueda
 * detener el registro** (propiedad de la Task 5).
 */
class RegistrarVisitaTest {

    private val hoy = LocalDate.of(2026, 9, 1)
    private val telemetry = RecordingTelemetry()
    private val registro = FakeRegistroDeVisitaPort()

    private fun casoDeUso(ubicacion: FakeUbicacionPort = FakeUbicacionPort()) =
        RegistrarVisita(registro, ubicacion, telemetry)

    private fun promesa(
        fecha: LocalDate = hoy.plusDays(3),
        monto: Money? = Money.of(BigDecimal("220"))
    ) = CapturaDeVisita(
        resultado = ResultadoDeVisita.PROMETIO,
        etiqueta = TipoVisitaCatalogo.PIDE_REAGENDAR,
        nota = "el viernes que cobre mi esposo",
        ventaDeLaPromesa = VisitasFixtures.REFRIGERADOR,
        fechaPromesa = fecha,
        montoPrometido = monto
    )

    private suspend fun registrar(
        captura: CapturaDeVisita,
        ubicacion: FakeUbicacionPort = FakeUbicacionPort()
    ) = casoDeUso(ubicacion).invoke(
        visitaId = "visita-1",
        clienteId = VisitasFixtures.VICTORIA,
        ventaId = VisitasFixtures.REFRIGERADOR,
        captura = captura,
        hoy = hoy
    )

    // ─── la propiedad de la Task 5 ───────────────────────────────────────────

    /**
     * **La visita queda registrada aunque la ubicación falle.** Antes de la
     * Task 5 el encolado vivía dentro del servicio de ubicación; si no corría,
     * la visita nunca se enviaba.
     */
    @Test
    fun `la visita se registra aunque la ubicacion lance`() = runTest {
        val ubicacion = FakeUbicacionPort(falla = IllegalStateException("play services caido"))

        val resultado = registrar(promesa(), ubicacion)

        assertEquals(ResultadoDelRegistro.REGISTRADA, resultado)
        assertEquals(1, registro.registradas.size)
        assertNull(
            "sin ubicacion, la visita va sin coordenadas",
            registro.registradas.single().ubicacion
        )
        // El error NO se traga: se emite con su código.
        val evento = telemetry.recorded.single {
            it.name == VisitasTelemetria.CODE_UBICACION_NO_DISPONIBLE
        }
        assertEquals(VisitasTelemetria.CAUSA_EXCEPCION, evento.props[VisitasTelemetria.PROP_CAUSA])
        assertEquals("IllegalStateException", evento.props[VisitasTelemetria.PROP_EXCEPCION])
    }

    /**
     * El permiso negado contesta `null`, no lanza — y ese camino, que es el MÁS
     * común, tampoco se calla.
     */
    @Test
    fun `la visita se registra aunque la ubicacion venga vacia, y el evento se emite`() = runTest {
        val ubicacion = FakeUbicacionPort(ubicacion = null)

        val resultado = registrar(promesa(), ubicacion)

        assertEquals(ResultadoDelRegistro.REGISTRADA, resultado)
        val evento = telemetry.recorded.single {
            it.name == VisitasTelemetria.CODE_UBICACION_NO_DISPONIBLE
        }
        assertEquals(VisitasTelemetria.CAUSA_SIN_DATO, evento.props[VisitasTelemetria.PROP_CAUSA])
    }

    /**
     * Control positivo del silencio: con ubicación buena **no** se emite el
     * evento. Sin esta prueba, la de arriba no distinguiría "se emitió porque
     * falló" de "se emite siempre".
     */
    @Test
    fun `con ubicacion buena no se emite el evento de ubicacion`() = runTest {
        val resultado = registrar(promesa())

        assertEquals(ResultadoDelRegistro.REGISTRADA, resultado)
        assertEquals(18.46, requireNotNull(registro.registradas.single().ubicacion).lat, 0.0001)
        assertTrue(
            telemetry.recorded.none { it.name == VisitasTelemetria.CODE_UBICACION_NO_DISPONIBLE }
        )
    }

    // ─── el dato estructurado ────────────────────────────────────────────────

    @Test
    fun `la promesa viaja con venta, fecha y monto, y nada de eso va en la nota`() = runTest {
        registrar(promesa())

        val visita = registro.registradas.single()
        val promesa = requireNotNull(visita.promesa)
        assertEquals(VisitasFixtures.REFRIGERADOR, promesa.ventaId)
        assertEquals(hoy.plusDays(3), promesa.fecha)
        assertEquals(Money.of(BigDecimal("220")), promesa.monto)
        // La nota es texto libre y NADA MÁS: ni la fecha ni el monto adentro.
        assertEquals("el viernes que cobre mi esposo", visita.nota)
    }

    @Test
    fun `una promesa sin monto se registra con el monto en nulo, no en cero`() = runTest {
        registrar(promesa(monto = null))

        assertNull(registro.registradas.single().promesa?.monto)
    }

    @Test
    fun `la cita viaja con dia y hora como campos`() = runTest {
        val captura = CapturaDeVisita(
            resultado = ResultadoDeVisita.CITA,
            etiqueta = TipoVisitaCatalogo.PIDE_TIEMPO,
            fechaCita = hoy,
            horaCita = LocalTime.of(16, 0)
        )

        registrar(captura)

        val visita = registro.registradas.single()
        assertEquals(hoy, visita.cita?.fecha)
        assertEquals(LocalTime.of(16, 0), visita.cita?.hora)
        assertEquals(TipoVisitaCatalogo.PIDE_TIEMPO, visita.tipoVisita)
        assertNull("una cita no lleva promesa", visita.promesa)
    }

    @Test
    fun `una cita sin hora se registra con la hora en nulo`() = runTest {
        val captura = CapturaDeVisita(
            resultado = ResultadoDeVisita.CITA,
            fechaCita = hoy.plusDays(2),
            horaCita = null
        )

        registrar(captura)

        assertEquals(hoy.plusDays(2), registro.registradas.single().cita?.fecha)
        assertNull(registro.registradas.single().cita?.hora)
    }

    /**
     * Una fecha arrastrada de un desenlace anterior **no** se escribe: la
     * promesa solo existe bajo su desenlace.
     */
    @Test
    fun `una fecha arrastrada bajo otro desenlace no se escribe`() = runTest {
        val captura = CapturaDeVisita(
            resultado = ResultadoDeVisita.NO_ESTABA,
            etiqueta = TipoVisitaCatalogo.CASA_CERRADA,
            fechaPromesa = hoy.plusDays(4),
            montoPrometido = Money.of(BigDecimal("500")),
            horaCita = LocalTime.of(9, 0)
        )

        registrar(captura)

        val visita = registro.registradas.single()
        assertNull(visita.promesa)
        assertNull(visita.cita)
        assertEquals(TipoVisitaCatalogo.CASA_CERRADA, visita.tipoVisita)
    }

    /** Una etiqueta ajena al desenlace se corrige a la del grupo, no se escribe cruzada. */
    @Test
    fun `una etiqueta ajena al desenlace se corrige`() = runTest {
        val captura = CapturaDeVisita(
            resultado = ResultadoDeVisita.SE_NEGO,
            etiqueta = TipoVisitaCatalogo.SOLO_MENORES
        )

        registrar(captura)

        assertEquals(TipoVisitaCatalogo.NO_VA_A_DAR_PAGO, registro.registradas.single().tipoVisita)
    }

    // ─── el segundo cinturón ─────────────────────────────────────────────────

    /**
     * Una captura bloqueada que llega hasta aquí **no escribe nada** y se
     * reporta: es el cinturón que hace observable un hueco de la pantalla.
     */
    @Test
    fun `una promesa con fecha pasada no escribe y se reporta`() = runTest {
        val resultado = registrar(promesa(fecha = hoy.minusDays(1)))

        assertEquals(ResultadoDelRegistro.FALLO_EL_GUARDADO, resultado)
        assertTrue("nada debio escribirse", registro.registradas.isEmpty())
        val evento = telemetry.recorded.single {
            it.name == VisitasTelemetria.CODE_CAPTURA_BLOQUEADA_EN_APLICACION
        }
        assertEquals(
            BloqueoDeLaVisita.COMPROMISO_EN_EL_PASADO.name,
            evento.props[VisitasTelemetria.PROP_BLOQUEOS]
        )
    }

    /** Y con la captura bloqueada, ni siquiera se pide la ubicación. */
    @Test
    fun `una captura bloqueada ni siquiera pide la ubicacion`() = runTest {
        val ubicacion = FakeUbicacionPort()

        registrar(CapturaDeVisita(), ubicacion)

        assertEquals(0, ubicacion.vecesConsultada)
    }

    /** La recomendación viaja al puerto para que quede atada a la visita. */
    @Test
    fun `la recomendacion mostrada viaja con la visita`() = runTest {
        casoDeUso().invoke(
            visitaId = "visita-1",
            clienteId = VisitasFixtures.VICTORIA,
            ventaId = VisitasFixtures.REFRIGERADOR,
            captura = promesa(),
            hoy = hoy,
            recomendacionId = "rec-victoria-1"
        )

        assertEquals("rec-victoria-1", registro.registradas.single().recomendacionId)
    }
}
