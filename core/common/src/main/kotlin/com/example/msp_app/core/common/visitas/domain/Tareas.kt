package com.example.msp_app.core.common.visitas.domain

import com.example.msp_app.core.common.cobranza.domain.VisitScope
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/*
 * Estado derivado de una tarea de oficina en el teléfono (plan visitas P3,
 * Tarea 11; spec §6.3; I5, I9; E-APP-122).
 *
 * Es la MISMA regla que `DerivarEstadoTarea` de msp-api
 * (`internal/visitas/domain/derivar_tareas.go`), punto por punto, y las dos se
 * amarran con los casos 057–076 de `testdata/compromisos/` (copia byte a byte
 * en `src/test/resources/compromisos/`, mismo VERSION). El estado NO se guarda
 * en ninguna tabla: `tarea_zona` solo trae el hecho de la cancelación.
 *
 * Función pura: "hoy" y la zona del negocio se inyectan. En este orden, el
 * primero que aplique gana:
 *
 *  1. cancelada: la tarea trae `canceladaAt`. Gana aunque haya atención y no
 *     se informa quién atendió.
 *  2. atendida: hay un pago a la venta de la tarea (no rechazado; incluye
 *     condonación y la captura local pendiente) o una visita APLICABLE a la
 *     venta (alcance CLIENTE del cliente de la venta, o alcance VENTA con esa
 *     venta) con instante ESTRICTAMENTE posterior a `createdAt`. Un pago sin
 *     hora atiende si su fecha civil >= la de `createdAt` en la zona. Se
 *     informa el PRIMERO: menor instante; empate, menor id en orden de BYTES
 *     UTF-8 ([compararIdsPorBytes]). Un pago sin hora se ordena al inicio de
 *     su día civil.
 *  3. no_atendida: hoy − fecha > [DIAS_PARA_NO_ATENDIDA] (desde la fecha de la
 *     tarea, no desde la asignación).
 *  4. pendiente.
 *
 * La hora de la tarea no cambia el estado: solo decide la hora del aviso.
 */

/** Estado derivado de una tarea. [codigo] es el del servidor. */
enum class EstadoTarea(val codigo: String) {
    CANCELADA("cancelada"),
    ATENDIDA("atendida"),
    NO_ATENDIDA("no_atendida"),
    PENDIENTE("pendiente")
}

/** Qué atendió la tarea. */
enum class TipoAtencion(val codigo: String) {
    PAGO("pago"),
    VISITA("visita")
}

/** Hasta hoy − fecha <= 14 la tarea sigue pendiente; después es no_atendida. */
const val DIAS_PARA_NO_ATENDIDA: Long = 14

/**
 * El primer pago o visita que atendió la tarea. [instante] nulo = pago de
 * oficina sin hora; [fecha] es su día civil en la zona del negocio.
 */
data class AtendidaPor(
    val tipo: TipoAtencion,
    val id: String,
    val por: Int?,
    val instante: Instant?,
    val fecha: LocalDate
)

/** El resultado: [atendida] va solo con [EstadoTarea.ATENDIDA]. */
data class EstadoDeTarea(val estado: EstadoTarea, val atendida: AtendidaPor? = null)

/** Lo que la derivación necesita de una tarea. [createdAt] y [canceladaAt] son UTC. */
data class TareaCompromiso(
    val id: String,
    val ventaId: Int,
    val fecha: LocalDate,
    val hora: LocalTime?,
    val createdAt: Instant,
    val canceladaAt: Instant?
)

/**
 * El estado de [t]. [cuenta] aporta el CLIENTE de la venta de la tarea (para
 * el alcance CLIENTE); la venta es siempre la de [t]. Visitas y pagos de otras
 * ventas u otros clientes se ignoran: se puede pasar el historial completo.
 */
fun derivarEstadoTarea(
    t: TareaCompromiso,
    visitas: List<VisitaCompromiso>,
    pagos: List<PagoCompromiso>,
    cuenta: CuentaCompromiso,
    hoy: LocalDate,
    zona: ZoneId
): EstadoDeTarea {
    if (t.canceladaAt != null) return EstadoDeTarea(EstadoTarea.CANCELADA)
    val aplicables = visitas.filter { v ->
        when (alcanceDe(v.tipoVisita, v.cita != null)) {
            VisitScope.CLIENTE -> v.clienteId == cuenta.clienteId
            else -> v.ventaId == t.ventaId
        }
    }
    primeraAtencion(t, aplicables, pagos, zona)?.let {
        return EstadoDeTarea(EstadoTarea.ATENDIDA, it)
    }
    if (ChronoUnit.DAYS.between(t.fecha, hoy) > DIAS_PARA_NO_ATENDIDA) {
        return EstadoDeTarea(EstadoTarea.NO_ATENDIDA)
    }
    return EstadoDeTarea(EstadoTarea.PENDIENTE)
}

/** Punto 2. [aplicables] ya viene filtrada por alcance. */
// Lectura lineal de las reglas del contrato v2.2: partirla separaría el desempate de la comparación.
@Suppress("CyclomaticComplexMethod")
private fun primeraAtencion(
    t: TareaCompromiso,
    aplicables: List<VisitaCompromiso>,
    pagos: List<PagoCompromiso>,
    zona: ZoneId
): AtendidaPor? {
    var mejor: AtendidaPor? = null
    var claveMejor: Instant = Instant.MAX

    @Suppress("ComplexCondition")
    fun considerar(clave: Instant, a: AtendidaPor) {
        val actual = mejor
        if (actual == null || clave.isBefore(claveMejor) ||
            (clave == claveMejor && compararIdsPorBytes(a.id, actual.id) < 0)
        ) {
            mejor = a
            claveMejor = clave
        }
    }
    val diaAsignacion = t.createdAt.atZone(zona).toLocalDate()
    for (p in pagos) {
        if (p.ventaId != t.ventaId || p.rechazado) continue
        val instante = p.instante
        if (instante != null) {
            if (instante.isAfter(t.createdAt)) {
                considerar(
                    instante,
                    AtendidaPor(TipoAtencion.PAGO, p.id.orEmpty(), p.cobradorId, instante, p.fecha)
                )
            }
        } else if (!p.fecha.isBefore(diaAsignacion)) {
            considerar(
                p.fecha.atStartOfDay(zona).toInstant(),
                AtendidaPor(TipoAtencion.PAGO, p.id.orEmpty(), p.cobradorId, null, p.fecha)
            )
        }
    }
    for (v in aplicables) {
        if (v.instante.isAfter(t.createdAt)) {
            considerar(
                v.instante,
                AtendidaPor(
                    TipoAtencion.VISITA,
                    v.id,
                    v.cobradorId,
                    v.instante,
                    v.instante.atZone(zona).toLocalDate()
                )
            )
        }
    }
    return mejor
}
