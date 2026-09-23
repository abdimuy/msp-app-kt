package com.example.msp_app.feature.visitas.application

import com.example.msp_app.core.common.cobranza.domain.TipoVisitaCatalogo
import com.example.msp_app.core.common.money.Money
import com.example.msp_app.core.testing.telemetry.RecordingTelemetry
import com.example.msp_app.feature.visitas.data.fake.FakeRegistroDeVisitaPort
import com.example.msp_app.feature.visitas.data.fake.FakeUbicacionPort
import com.example.msp_app.feature.visitas.data.fake.VisitasFixtures
import com.example.msp_app.feature.visitas.domain.model.BloqueoDeLaVisita
import com.example.msp_app.feature.visitas.domain.model.CapturaDeVisita
import com.example.msp_app.feature.visitas.domain.model.ComprobanteDeVisita
import com.example.msp_app.feature.visitas.domain.model.ResultadoDeVisita
import com.example.msp_app.feature.visitas.domain.port.ResultadoDelRegistro
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalTime
import java.util.UUID
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
        cuentas = setOf(VisitasFixtures.REFRIGERADOR),
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
            etiqueta = TipoVisitaCatalogo.SOLO_MENORES,
            cuentas = setOf(VisitasFixtures.REFRIGERADOR)
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

    // ─── una captura, N visitas ──────────────────────────────────────────────

    private fun seNego(cuentas: Set<Int>) = CapturaDeVisita(
        resultado = ResultadoDeVisita.SE_NEGO,
        etiqueta = TipoVisitaCatalogo.NO_VA_A_DAR_PAGO,
        nota = "dice que hasta que le arreglen el refri",
        cuentas = cuentas
    )

    /**
     * **Dos cuentas marcadas escriben DOS visitas**, cada una atada a su cuenta.
     * Antes había que registrar dos visitas a mano —dos capturas, dos notas— para
     * dejar constancia de una frase que el cliente dijo una sola vez.
     */
    @Test
    fun `se negó en las dos cuentas escribe dos visitas, una por cuenta`() = runTest {
        registrar(seNego(setOf(VisitasFixtures.SALA, VisitasFixtures.REFRIGERADOR)))

        assertEquals(2, registro.registradas.size)
        assertEquals(
            listOf(VisitasFixtures.SALA, VisitasFixtures.REFRIGERADOR),
            registro.registradas.map { it.ventaId }
        )
        // La nota y la etiqueta son las MISMAS: es un solo hecho contado en dos
        // filas, no dos hechos distintos.
        assertEquals(1, registro.registradas.map { it.nota }.toSet().size)
        assertEquals(1, registro.registradas.map { it.tipoVisita }.toSet().size)
    }

    /**
     * Control positivo del reparto: con UNA cuenta marcada se escribe UNA visita.
     * Sin esto, la prueba de arriba no distinguiría "escribe una por cuenta" de
     * "escribe dos siempre".
     */
    @Test
    fun `una sola cuenta marcada escribe una sola visita`() = runTest {
        registrar(seNego(setOf(VisitasFixtures.REFRIGERADOR)))

        assertEquals(1, registro.registradas.size)
        assertEquals(VisitasFixtures.REFRIGERADOR, registro.registradas.single().ventaId)
    }

    /** Y un desenlace de toda la puerta sigue escribiendo UNA fila, con la semilla. */
    @Test
    fun `un desenlace de toda la puerta escribe una sola visita con la semilla`() = runTest {
        registrar(
            CapturaDeVisita(
                resultado = ResultadoDeVisita.NO_ESTABA,
                etiqueta = TipoVisitaCatalogo.CASA_CERRADA
            )
        )

        assertEquals(1, registro.registradas.size)
        assertEquals("visita-1", registro.registradas.single().visitaId)
    }

    /**
     * **Los ids son deterministas.** Dos corridas de la MISMA semilla con las
     * MISMAS cuentas producen exactamente los mismos ids — que es la condición
     * para que el reintento reescriba las filas en vez de duplicarlas.
     */
    @Test
    fun `la misma semilla y las mismas cuentas producen los mismos ids dos veces`() = runTest {
        val cuentas = setOf(VisitasFixtures.SALA, VisitasFixtures.REFRIGERADOR)

        registrar(seNego(cuentas))
        val primera = registro.registradas.map { it.visitaId }
        registro.registradas.clear()
        registrar(seNego(cuentas))
        val segunda = registro.registradas.map { it.visitaId }

        assertEquals(2, primera.size)
        assertEquals(primera, segunda)
        assertEquals("dos cuentas, dos ids distintos", 2, primera.toSet().size)
    }

    /**
     * Y cada id es un **UUID canónico**. No es cosmética: el servidor hace
     * `uuid.Parse` sobre el id antes de mirar nada más, así que un id con forma
     * libre viajaría bien por Room y rebotaría con 422 en la subida — la visita
     * se quedaría reintentando en el teléfono para siempre.
     */
    @Test
    fun `cada id derivado es un UUID que el servidor puede parsear`() = runTest {
        registrar(seNego(setOf(VisitasFixtures.SALA, VisitasFixtures.REFRIGERADOR)))

        registro.registradas.forEach { visita ->
            assertEquals(
                "el id derivado no es un UUID canonico: ${visita.visitaId}",
                visita.visitaId,
                UUID.fromString(visita.visitaId).toString()
            )
        }
    }

    /**
     * **Las fotos y la recomendación cuelgan del ancla, y de nadie más.**
     * `VisitImageEntity.ID` es llave primaria: la misma foto en dos visitas se
     * colapsaría a la última y dejaría a la otra sin evidencia.
     */
    @Test
    fun `las fotos y la recomendacion van solo en la visita ancla`() = runTest {
        casoDeUso().invoke(
            visitaId = "visita-1",
            clienteId = VisitasFixtures.VICTORIA,
            ventaId = null,
            captura = seNego(setOf(VisitasFixtures.SALA, VisitasFixtures.REFRIGERADOR)),
            hoy = hoy,
            recomendacionId = "rec-victoria-1",
            comprobantes = listOf(ComprobanteDeVisita("IMG-1", "/files/IMG-1.jpg", "image/jpeg"))
        )

        // El ancla es la cuenta más baja: SALA.
        val ancla = registro.registradas.single { it.ventaId == VisitasFixtures.SALA }
        val otra = registro.registradas.single { it.ventaId == VisitasFixtures.REFRIGERADOR }
        assertEquals(1, ancla.comprobantes.size)
        assertEquals("rec-victoria-1", ancla.recomendacionId)
        assertTrue("la segunda cuenta no se lleva la foto", otra.comprobantes.isEmpty())
        assertNull("la recomendacion se liga una sola vez", otra.recomendacionId)
    }

    /**
     * Un desenlace de cuenta **sin una sola cuenta marcada** no escribe nada y se
     * reporta: sin venta, una visita de alcance VENTA no toca ninguna fila de
     * `sales` y el trabajo de campo se perdería en silencio.
     */
    @Test
    fun `se negó sin cuentas marcadas no escribe y se reporta`() = runTest {
        val resultado = registrar(seNego(emptySet()))

        assertEquals(ResultadoDelRegistro.FALLO_EL_GUARDADO, resultado)
        assertTrue("nada debio escribirse", registro.registradas.isEmpty())
        val evento = telemetry.recorded.single {
            it.name == VisitasTelemetria.CODE_CAPTURA_BLOQUEADA_EN_APLICACION
        }
        assertEquals(
            BloqueoDeLaVisita.SIN_CUENTAS.name,
            evento.props[VisitasTelemetria.PROP_BLOQUEOS]
        )
    }

    /**
     * **La ubicación se pide UNA vez para las N filas.** Son el mismo hecho, en
     * el mismo instante y en la misma puerta: pedirla por cuenta daría N
     * coordenadas de un cobrador que no se movió y N eventos por un solo permiso
     * negado.
     */
    @Test
    fun `la ubicacion se pide una sola vez aunque se escriban dos visitas`() = runTest {
        val ubicacion = FakeUbicacionPort()

        registrar(seNego(setOf(VisitasFixtures.SALA, VisitasFixtures.REFRIGERADOR)), ubicacion)

        assertEquals(2, registro.registradas.size)
        assertEquals(1, ubicacion.vecesConsultada)
    }

    /**
     * Una cuenta que falla **no tira a las demás**, y el desenlace a medias no se
     * calla: nadie podría reconstruir después "se guardaron 2 de 3".
     */
    @Test
    fun `si una cuenta falla se intentan las demas y el parcial se reporta`() = runTest {
        val registroParcial = FakeRegistroDeVisitaPort()
        registroParcial.fallaEn = 1
        RegistrarVisita(registroParcial, FakeUbicacionPort(), telemetry).invoke(
            visitaId = "visita-1",
            clienteId = VisitasFixtures.VICTORIA,
            ventaId = null,
            captura = seNego(setOf(VisitasFixtures.SALA, VisitasFixtures.REFRIGERADOR)),
            hoy = hoy
        ).let { assertEquals(ResultadoDelRegistro.FALLO_EL_GUARDADO, it) }

        assertEquals("las dos cuentas se intentaron", 2, registroParcial.registradas.size)
        val evento = telemetry.recorded.single {
            it.name == VisitasTelemetria.CODE_VISITA_PARCIAL_POR_CUENTA
        }
        assertEquals("1", evento.props[VisitasTelemetria.PROP_OCURRENCIAS])
        assertEquals("2", evento.props[VisitasTelemetria.PROP_CUENTAS])
    }

    /**
     * Control positivo del silencio: cuando las N cuentas se escriben, el evento
     * de parcial **no** se emite.
     */
    @Test
    fun `sin fallos no se emite el evento de parcial`() = runTest {
        registrar(seNego(setOf(VisitasFixtures.SALA, VisitasFixtures.REFRIGERADOR)))

        assertTrue(
            telemetry.recorded.none {
                it.name == VisitasTelemetria.CODE_VISITA_PARCIAL_POR_CUENTA
            }
        )
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
