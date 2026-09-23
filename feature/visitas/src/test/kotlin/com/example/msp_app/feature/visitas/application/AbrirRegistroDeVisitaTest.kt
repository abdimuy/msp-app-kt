package com.example.msp_app.feature.visitas.application

import com.example.msp_app.core.testing.telemetry.RecordingTelemetry
import com.example.msp_app.feature.visitas.data.fake.FakeContextoDeVisitaPort
import com.example.msp_app.feature.visitas.data.fake.FakeRecomendacionesPort
import com.example.msp_app.feature.visitas.data.fake.VisitasFixtures
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** La apertura: el cliente manda, la recomendación acompaña y nunca tumba nada. */
class AbrirRegistroDeVisitaTest {

    private val telemetry = RecordingTelemetry()

    @Test
    fun `trae el cliente y la recomendacion vigente`() = runTest {
        val recomendaciones = FakeRecomendacionesPort(VisitasFixtures.recomendacion())
        val abrir = AbrirRegistroDeVisita(FakeContextoDeVisitaPort(), recomendaciones, telemetry)

        val apertura = abrir(VisitasFixtures.VICTORIA)

        assertEquals("Victoria Flores Olmedo", apertura.contexto?.nombre)
        assertEquals("rec-victoria-1", apertura.recomendacion?.recomendacionId)
    }

    @Test
    fun `sin cuentas del cliente el contexto viene vacio`() = runTest {
        val contexto = FakeContextoDeVisitaPort(contexto = null)
        val abrir = AbrirRegistroDeVisita(contexto, FakeRecomendacionesPort(), telemetry)

        assertNull(abrir(VisitasFixtures.VICTORIA).contexto)
    }

    /**
     * Si la recomendación falla, la pantalla sigue **y el error no se traga**.
     * El trabajo de campo pesa más que el acompañamiento.
     */
    @Test
    fun `un fallo de la recomendacion no tumba la pantalla y se reporta`() = runTest {
        val recomendaciones = FakeRecomendacionesPort(falla = IllegalStateException("room caido"))
        val abrir = AbrirRegistroDeVisita(FakeContextoDeVisitaPort(), recomendaciones, telemetry)

        val apertura = abrir(VisitasFixtures.VICTORIA)

        assertNotNull("el cliente tiene que llegar igual", apertura.contexto)
        assertNull(apertura.recomendacion)
        val evento = telemetry.recorded.single {
            it.name == VisitasTelemetria.CODE_RECOMENDACION_FALLO
        }
        assertEquals("IllegalStateException", evento.props[VisitasTelemetria.PROP_EXCEPCION])
    }

    /**
     * Control positivo del silencio: en el camino sano **no** se emite el
     * evento. Sin esto, la prueba de arriba no distinguiría "se emitió porque
     * falló" de "se emite siempre".
     */
    @Test
    fun `sin fallo no se emite el evento de recomendacion`() = runTest {
        val abrir = AbrirRegistroDeVisita(
            FakeContextoDeVisitaPort(),
            FakeRecomendacionesPort(VisitasFixtures.recomendacion()),
            telemetry
        )

        abrir(VisitasFixtures.VICTORIA)

        assertTrue(
            telemetry.recorded.none { it.name == VisitasTelemetria.CODE_RECOMENDACION_FALLO }
        )
    }
}
