package com.example.msp_app.core.sync.visitas

import com.example.msp_app.core.visitas.envio.TelemetriaDeVisitas
import java.util.concurrent.ConcurrentHashMap

/** Dónde cerró una corrida de la bajada: el par `(cursor, afterId)` que quedó guardado. */
data class PosicionDeCierre(val cursor: String?, val afterId: String?)

/**
 * Detecta la bajada de visitas atorada (plan visitas P2, Tarea 18; spec §12;
 * E-APP-110): varias corridas seguidas que CIERRAN en la misma posición
 * mientras siguen aplicando filas. Es la firma del defecto D1 de pagos
 * (`COBRANZA-SYNC.md` §11): el sync baja la misma página una y otra vez.
 *
 * ## Cierre contra cierre, entre corridas
 *
 * La posición anterior vive en la memoria de este detector, no en
 * `visitas_sync_state`: una telemetría que lee la fuente rota hereda su
 * ceguera. Comparar arranque contra cierre dentro de la misma corrida da
 * falso negativo (con D1 cada corrida arrancaba sin posición y cerraba en el
 * mismo lugar: siempre "avanzó"). Lo cobra `cursorStalledComparaCierreContraCierre`.
 *
 * Cerrar en el mismo lugar SIN aplicar filas es salud (la agenda está al día),
 * no atasco: la alerta exige `aplicadas > 0`.
 *
 * La memoria se pierde al reiniciar el proceso: la primera corrida de un
 * proceso nunca está atorada respecto de nada.
 */
class DetectorDeCursorAtorado(private val umbral: Int = UMBRAL) {

    private val ultimoCierre = ConcurrentHashMap<Int, PosicionDeCierre>()
    private val racha = ConcurrentHashMap<Int, Int>()

    /**
     * Registra el cierre de una corrida de [zona]. Devuelve la racha de
     * corridas seguidas sin avanzar si la bajada está atorada (racha ≥
     * [umbral] aplicando filas), o `null` si no.
     */
    fun registrar(zona: Int, cierre: PosicionDeCierre, aplicadas: Int): Int? {
        val anterior = ultimoCierre.put(zona, cierre)
        val avanzo = anterior == null || anterior != cierre
        val n = if (avanzo) {
            racha.remove(zona)
            0
        } else {
            racha.merge(zona, 1, Int::plus) ?: 1
        }
        return n.takeIf { aplicadas > 0 && it >= umbral }
    }

    companion object {
        /** Tres corridas seguidas, como `CobranzaSyncTelemetry.STALL_THRESHOLD`. */
        const val UMBRAL = 3
    }
}

/**
 * Pasa el resultado de una corrida al [detector] y, si está atorada, emite
 * `visitas_sync.cursor_stalled` con la racha (un conteo, por la lista blanca de
 * [TelemetriaDeVisitas]). Solo [ResultadoSync.Ok] trae posición; lo demás no
 * cuenta. Nunca lanza: `TelemetriaDeVisitas.emitir` traga todo.
 */
fun reportarBajada(detector: DetectorDeCursorAtorado, zona: Int, resultado: ResultadoSync) {
    val ok = resultado as? ResultadoSync.Ok ?: return
    val racha = detector.registrar(zona, PosicionDeCierre(ok.cursor, ok.afterId), ok.aplicadas)
        ?: return
    TelemetriaDeVisitas.emitir(
        TelemetriaDeVisitas.EVENTO_SYNC,
        mapOf(TelemetriaDeVisitas.SYNC_CURSOR_STALLED to racha)
    )
}
