package com.example.msp_app.core.common.visitas.domain

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * E-APP-122 · Bordes de `derivarEstadoTarea` que el juego compartido 057–076
 * no fija (el mutante `isAfter` -> `!isBefore` del pago sobrevivía a los 20
 * casos): el instante EXACTO de la asignación no atiende, ni para un pago ni
 * para una visita (decisión del dueño, espejo de `primeraAtencion` en Go).
 */
class TareasTest {

    private val zona = ZoneId.of("America/Mexico_City")
    private val hoy = LocalDate.parse("2026-10-19")
    private val asignada = Instant.parse("2026-10-14T16:00:00Z")
    private val cuenta = CuentaCompromiso(ventaId = 100, clienteId = 10)
    private val tarea = TareaCompromiso(
        id = "t-1",
        ventaId = 100,
        fecha = LocalDate.parse("2026-10-20"),
        hora = null,
        createdAt = asignada,
        canceladaAt = null
    )

    private fun pago(instante: Instant) = PagoCompromiso(
        ventaId = 100,
        instante = instante,
        fecha = instante.atZone(zona).toLocalDate(),
        rechazado = false,
        id = "p-1",
        cobradorId = 7
    )

    private fun visita(instante: Instant) = VisitaCompromiso(
        id = "v-1",
        clienteId = 10,
        ventaId = 100,
        tipoVisita = "Pago",
        instante = instante,
        promesa = null,
        cita = null,
        cobradorId = 7
    )

    private fun estado(
        visitas: List<VisitaCompromiso> = emptyList(),
        pagos: List<PagoCompromiso> = emptyList()
    ) = derivarEstadoTarea(tarea, visitas, pagos, cuenta, hoy, zona)

    @Test
    fun `un pago en el instante exacto de la asignacion no la atiende`() {
        assertEquals(EstadoTarea.PENDIENTE, estado(pagos = listOf(pago(asignada))).estado)
    }

    @Test
    fun `un pago un segundo despues de la asignacion la atiende`() {
        val r = estado(pagos = listOf(pago(asignada.plusSeconds(1))))
        assertEquals(EstadoTarea.ATENDIDA, r.estado)
        assertEquals(TipoAtencion.PAGO, r.atendida?.tipo)
        assertEquals(7, r.atendida?.por)
    }

    @Test
    fun `una visita en el instante exacto de la asignacion no la atiende`() {
        assertEquals(EstadoTarea.PENDIENTE, estado(visitas = listOf(visita(asignada))).estado)
    }

    @Test
    fun `una visita un segundo despues la atiende y informa a su cobrador`() {
        val r = estado(visitas = listOf(visita(asignada.plusSeconds(1))))
        assertEquals(EstadoTarea.ATENDIDA, r.estado)
        assertEquals(TipoAtencion.VISITA, r.atendida?.tipo)
        assertEquals(7, r.atendida?.por)
    }

    @Test
    fun `cancelada gana aunque haya atencion y no informa quien atendio`() {
        val r = derivarEstadoTarea(
            tarea.copy(canceladaAt = asignada.plusSeconds(60)),
            emptyList(),
            listOf(pago(asignada.plusSeconds(1))),
            cuenta,
            hoy,
            zona
        )
        assertEquals(EstadoDeTarea(EstadoTarea.CANCELADA), r)
    }
}
