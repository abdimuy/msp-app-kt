package com.example.msp_app.feature.pagos.application

import com.example.msp_app.core.common.cobranza.domain.EstadoCuenta
import com.example.msp_app.core.common.cobranza.domain.IncidenciaCobranza
import com.example.msp_app.core.common.cobranza.domain.VentanaCobro
import com.example.msp_app.core.common.money.Money
import com.example.msp_app.core.telemetry.TelemetryEventType
import com.example.msp_app.core.testing.telemetry.RecordingTelemetry
import com.example.msp_app.feature.pagos.ui.PagosFixtures
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * El puente que faltaba: `DerivacionPeriodo.incidencias` devuelve las anomalías
 * como DATO (el dominio no puede emitir telemetría sin crear un ciclo) y este
 * caso de uso es el primero que las reenvía.
 *
 * Sin este puente, un `TIPO_VISITA` fuera del catálogo produce un
 * `VISITE_VUELVO` silencioso en producción — que es exactamente lo que la Task
 * 14 dejó anotado para esta tarea.
 */
class DerivarEstadoDelPeriodoTest {

    private val telemetria = RecordingTelemetry()
    private val derivar = DerivarEstadoDelPeriodo(telemetria)
    private val ventana = VentanaCobro(
        inicio = Instant.parse("2026-08-31T06:00:00Z"),
        fin = PagosFixtures.AHORA
    )

    private fun erroresCon(code: String) =
        telemetria.recorded.filter { it.type == TelemetryEventType.ERROR && it.name == code }

    @Test
    fun `un tipo de visita fuera de catalogo se reporta exactamente una vez`() {
        derivar(
            ventas = PagosFixtures.datosDeVentas(),
            pagos = emptyList(),
            visitas = listOf(PagosFixtures.visita("Se fue de vacaciones")),
            ventana = ventana
        )
        val emitidos = erroresCon(IncidenciaCobranza.CODE_TIPO_VISITA_FUERA_DE_CATALOGO)
        assertEquals(1, emitidos.size)
        assertEquals("1", emitidos.single().props[PagosTelemetria.PROP_OCURRENCIAS])
    }

    @Test
    fun `dos visitas fuera de catalogo siguen siendo UN evento con el conteo dentro`() {
        derivar(
            ventas = PagosFixtures.datosDeVentas(),
            pagos = emptyList(),
            visitas = listOf(
                PagosFixtures.visita("Se fue de vacaciones", fechaIso = "2026-09-01T15:00:00Z"),
                PagosFixtures.visita("Cambió de domicilio", fechaIso = "2026-09-01T16:00:00Z")
            ),
            ventana = ventana
        )
        val emitidos = erroresCon(IncidenciaCobranza.CODE_TIPO_VISITA_FUERA_DE_CATALOGO)
        assertEquals(1, emitidos.size)
        assertEquals("2", emitidos.single().props[PagosTelemetria.PROP_OCURRENCIAS])
    }

    @Test
    fun `dos sincronizaciones emiten dos veces — una por sync, no una por vida del proceso`() {
        val visitas = listOf(PagosFixtures.visita("Se fue de vacaciones"))
        derivar(PagosFixtures.datosDeVentas(), emptyList(), visitas, ventana)
        derivar(PagosFixtures.datosDeVentas(), emptyList(), visitas, ventana)
        assertEquals(2, erroresCon(IncidenciaCobranza.CODE_TIPO_VISITA_FUERA_DE_CATALOGO).size)
    }

    @Test
    fun `sin anomalias no se emite nada`() {
        derivar(
            ventas = PagosFixtures.datosDeVentas(),
            pagos = emptyList(),
            visitas = listOf(PagosFixtures.visita("No responde aunque está")),
            ventana = ventana
        )
        assertTrue(telemetria.recorded.none { it.type == TelemetryEventType.ERROR })
    }

    @Test
    fun `la visita de alcance venta sin venta ligada se reporta y no se colapsa`() {
        derivar(
            ventas = PagosFixtures.datosDeVentas(),
            pagos = emptyList(),
            visitas = listOf(PagosFixtures.visita("No responde aunque está", ventaId = null)),
            ventana = ventana
        )
        assertEquals(1, erroresCon(IncidenciaCobranza.CODE_VISITA_SIN_VENTA).size)
    }

    @Test
    fun `anti-PII — no se emite el literal ofensor ni ningun dato del cliente`() {
        derivar(
            ventas = PagosFixtures.datosDeVentas(),
            pagos = emptyList(),
            visitas = listOf(PagosFixtures.visita("Se fue de vacaciones")),
            ventana = ventana
        )
        val evento = erroresCon(IncidenciaCobranza.CODE_TIPO_VISITA_FUERA_DE_CATALOGO).single()
        val texto = evento.name + evento.props.entries.joinToString { it.key + it.value }
        assertTrue(texto, !texto.contains("vacaciones"))
        assertTrue(texto, !texto.contains("Victoria"))
        assertTrue(texto, !texto.contains("238"))
    }

    @Test
    fun `sin ventana no se inventa periodo — todo queda sin tocar y se reporta`() {
        val estados = derivar(
            ventas = PagosFixtures.datosDeVentas(),
            pagos = emptyList(),
            visitas = emptyList(),
            ventana = null
        )
        assertTrue(estados.values.all { it.estado == EstadoCuenta.SIN_TOCAR })
        assertEquals(1, erroresCon(PagosTelemetria.CODE_PERIODO_DESCONOCIDO).size)
    }

    @Test
    fun `el estado que devuelve es el del catalogo, envuelto en Money`() {
        val estados = derivar(
            ventas = PagosFixtures.datosDeVentas(),
            pagos = PagosFixtures.pagosDeLaVenta().map { it.copy(fecha = PagosFixtures.AHORA) },
            visitas = emptyList(),
            ventana = ventana
        )
        val enPromesa = estados.getValue(PagosFixtures.VENTA_EN_PROMESA)
        assertEquals(EstadoCuenta.PAGO, enPromesa.estado)
        assertEquals(Money.of(java.math.BigDecimal("2450")), enPromesa.abonoDelPeriodo)
        assertEquals(Money.of(java.math.BigDecimal("220")), enPromesa.parcialidad)
        assertNull(enPromesa.fechaPromesa)
    }
}
