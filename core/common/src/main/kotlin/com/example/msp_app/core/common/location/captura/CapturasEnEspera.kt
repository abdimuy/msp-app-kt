package com.example.msp_app.core.common.location.captura

/** Una captura guardada que sigue sin ubicación y espera un fix (E-APP-146). */
data class CapturaEnEspera(val id: String, val tipo: String, val guardadoNanos: Long)

/** Una captura cuya ubicación ya se puede escribir: la decisión de la regla y el fix que ganó. */
data class CapturaUbicada(
    val captura: CapturaEnEspera,
    val decision: DecisionDeUbicacion.Aceptada
)

/**
 * Las capturas (abono, condonación, visita) que se guardaron SIN ubicación y esperan un fix
 * (E-APP-146, K5; decisión del dueño 2026-10-30). Pura: ni Android, ni Room, ni reloj propio.
 *
 * Cada vez que el tomador recibe un fix, [resolver] aplica la MISMA regla de aceptación
 * ([AceptacionDeUbicacion.decidir]: edad, precisión, mock y quietud) a cada captura contra su
 * instante de guardado y devuelve las que ya tienen ubicación, que SALEN de la lista: una captura
 * se ubica una sola vez. Se descartan, sin escribir nada, las que:
 * - ya pasaron la ventana de la regla (`maxEdadSeg`) desde su guardado, o
 * - ya tienen movimiento (la regla DESPUÉS exige quietud y "más fixes solo convierten una
 *   aceptación en rechazo": ese rechazo no se revierte).
 *
 * Vive en memoria a propósito: la lista solo decide a quién se le escribe un fix tardío. Quien
 * hace esperar al envío es el plazo persistido en Room (`plazo_de_envio`), que sobrevive a la
 * muerte del proceso; sin esta lista (proceso reiniciado) el envío sale sin ubicación al vencer
 * su plazo, nunca se pierde ni se duplica.
 */
class CapturasEnEspera(
    private val config: () -> ConfigDeAceptacion = { ConfigDeAceptacion.POR_DEFECTO },
    private val capacidad: Int = CAPACIDAD
) {
    private val esperando = LinkedHashMap<String, CapturaEnEspera>()

    init {
        require(capacidad > 0) { "capacidad debe ser positiva: $capacidad" }
    }

    /** Anota una captura recién guardada sin ubicación. Anotar el mismo id otra vez no la cambia. */
    @Synchronized
    fun registrar(id: String, tipo: String, guardadoNanos: Long) {
        if (id in esperando) return
        if (esperando.size >= capacidad) esperando.remove(esperando.keys.first())
        esperando[id] = CapturaEnEspera(id, tipo, guardadoNanos)
    }

    @Synchronized
    fun pendientes(): List<CapturaEnEspera> = esperando.values.toList()

    /** Quita una captura (p. ej. si su guardado terminó mal). */
    @Synchronized
    fun olvidar(id: String) {
        esperando.remove(id)
    }

    /**
     * Resuelve lo que se pueda con los [fixes] recibidos hasta [ahoraNanos] (reloj monótono).
     * Devuelve las capturas ubicadas, en el orden en que se guardaron.
     */
    @Synchronized
    fun resolver(ahoraNanos: Long, fixes: List<FixDeUbicacion>): List<CapturaUbicada> {
        val cfg = config()
        val ventanaNanos = cfg.maxEdadSeg * NANOS_POR_SEG
        val hasta = fixes.filter { it.elapsedRealtimeNanos <= ahoraNanos }
        val ubicadas = mutableListOf<CapturaUbicada>()
        val iterador = esperando.values.iterator()
        while (iterador.hasNext()) {
            val captura = iterador.next()
            if (ahoraNanos - captura.guardadoNanos > ventanaNanos) {
                iterador.remove()
                continue
            }
            when (val decision = AceptacionDeUbicacion.decidir(captura.guardadoNanos, hasta, cfg)) {
                is DecisionDeUbicacion.Aceptada -> {
                    iterador.remove()
                    ubicadas += CapturaUbicada(captura, decision)
                }
                is DecisionDeUbicacion.Rechazar ->
                    if (decision.motivo == MotivoDeRechazo.CON_MOVIMIENTO) iterador.remove()
            }
        }
        return ubicadas
    }

    companion object {
        /** Cuántas capturas esperan a la vez como máximo (la más vieja se suelta primero). */
        const val CAPACIDAD = 64
        private const val NANOS_POR_SEG = 1_000_000_000L
    }
}
