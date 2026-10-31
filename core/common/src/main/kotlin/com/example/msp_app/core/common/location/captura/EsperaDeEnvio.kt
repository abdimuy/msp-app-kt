package com.example.msp_app.core.common.location.captura

import com.example.msp_app.core.common.time.AppClock
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay

/** Por que [EsperaDeEnvio.esperar] dejo de esperar. */
enum class SalidaDeLaEspera {
    /** No habia nada que esperar: sin plazo valido, o plazo vencido o fuera de rango. */
    SIN_ESPERA,

    /** La captura ya tiene ubicacion (al empezar o durante la espera). */
    HAY_UBICACION,

    /** La toma informo que no va a llegar nada (permiso negado o GPS apagado). */
    NO_VA_A_LLEGAR,

    /** Se agoto el tiempo que quedaba. */
    VENCIO,

    /** No se pudo leer Room: no se espera mas; el envio sale con lo que haya. */
    LECTURA_FALLIDA
}

/**
 * La retencion del envio de una captura hasta tener ubicacion o hasta 30 s desde que se guardo
 * (E-APP-147; decision del dueno 2026-10-30; condiciones K3 y K4 de la compuerta ANTES c2).
 * Pura: el reloj de pared, el monotono y la pausa se inyectan.
 *
 * ## Que decide cada reloj (K4)
 * - El plazo persistido es de PARED (`guardadoEpochMs + plazoMs`). Solo decide si hay algo que
 *   esperar al empezar: `restante = plazo - ahora`. Si `restante <= 0` o `restante > 30 s`, NO se
 *   espera: un reloj atrasado 1 h no retiene 1 h, y uno adelantado no retiene nunca.
 * - Con `restante` valido, el limite de ESTA corrida se fija con el reloj MONOTONO
 *   (`elapsedRealtime`) y no vuelve a mirar la pared: si la pared salta durante la espera, la
 *   espera no cambia. Nunca se espera mas de 30 s, ni por corrida ni sumando reanudaciones
 *   mientras la pared no se mueva (la reanudacion empieza de cero con un `restante` menor).
 *
 * ## Que garantiza (K3)
 * Solo LEE (a traves de [hayUbicacion]) y duerme con [pausa], que es `delay`: suspendible, y
 * propaga `CancellationException`. Cada vuelta relee, asi que ve la ubicacion que escribio el
 * tomador (lat y lng juntos, en un solo UPDATE) en cuanto esta en Room.
 */
class EsperaDeEnvio(
    private val reloj: AppClock,
    private val monotono: RelojMonotono,
    private val sondeoMs: Long = SONDEO_POR_DEFECTO_MS,
    private val pausa: suspend (ms: Long) -> Unit = { delay(it) }
) {
    init {
        require(sondeoMs > 0) { "sondeoMs debe ser positivo: $sondeoMs" }
    }

    /**
     * @param guardadoEpochMs hora de pared del guardado (de `plazo_de_envio`).
     * @param plazoMs cuanto puede esperar desde el guardado (se topa en [PLAZO_MAXIMO_MS]).
     * @param hayUbicacion `true` si la captura ya tiene ubicacion (el par lat/lng no es 0,0), o si
     *   ya no hay nada que esperar (la fila desaparecio). Lee Room en cada llamada.
     * @param noVaALlegar `true` si la toma dijo que no va a haber ubicacion. Se consulta antes y en
     *   cada vuelta; no suspende.
     */
    suspend fun esperar(
        guardadoEpochMs: Long,
        plazoMs: Long,
        hayUbicacion: suspend () -> Boolean,
        noVaALlegar: () -> Boolean = { false }
    ): SalidaDeLaEspera {
        val restanteMs = restanteAEsperarMs(guardadoEpochMs, plazoMs)
            ?: return SalidaDeLaEspera.SIN_ESPERA
        val limiteNanos = monotono.elapsedNanos() + restanteMs * NANOS_POR_MS
        while (true) {
            salidaPorEstado(hayUbicacion, noVaALlegar)?.let { return it }
            val quedaNanos = limiteNanos - monotono.elapsedNanos()
            if (quedaNanos <= 0) return SalidaDeLaEspera.VENCIO
            val quedaMs = (quedaNanos + NANOS_POR_MS - 1) / NANOS_POR_MS
            pausa(minOf(sondeoMs, quedaMs))
        }
    }

    /** Lo que queda por esperar segun la pared, o `null` si no hay nada que esperar (K4). */
    private fun restanteAEsperarMs(guardadoEpochMs: Long, plazoMs: Long): Long? {
        val tope = minOf(plazoMs, PLAZO_MAXIMO_MS)
        if (tope <= 0) return null
        val restanteMs = guardadoEpochMs + plazoMs - reloj.now().toEpochMilli()
        return restanteMs.takeIf { it in 1..tope }
    }

    /** La salida que se sigue de lo que dicen Room y la toma, o `null` si hay que seguir esperando. */
    @Suppress("TooGenericExceptionCaught")
    private suspend fun salidaPorEstado(
        hayUbicacion: suspend () -> Boolean,
        noVaALlegar: () -> Boolean
    ): SalidaDeLaEspera? {
        val hay = try {
            hayUbicacion()
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            return SalidaDeLaEspera.LECTURA_FALLIDA
        }
        return when {
            hay -> SalidaDeLaEspera.HAY_UBICACION
            noVaALlegar() -> SalidaDeLaEspera.NO_VA_A_LLEGAR
            else -> null
        }
    }

    companion object {
        /** Tope absoluto de la retencion: 30 s, decision del dueno. */
        const val PLAZO_MAXIMO_MS = 30_000L

        /** Cada cuanto se relee Room mientras se espera. */
        const val SONDEO_POR_DEFECTO_MS = 250L
        private const val NANOS_POR_MS = 1_000_000L
    }
}
