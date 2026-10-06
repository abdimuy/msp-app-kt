package com.example.msp_app.core.designsystem.component

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.InfiniteRepeatableSpec
import androidx.compose.animation.core.StartOffset
import androidx.compose.animation.core.StartOffsetType
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes

/**
 * Timing compartido del pulso "venta nueva" ([MspInsigniaNueva] / [MspPuntoNuevo]) —
 * decisión del dueño del 2026-10-05, mock "Decidido · v3"
 * (`https://claude.ai/artifact/BhdPihYNWezfoZr1ggi2Zk`). El CSS del mock es la fuente
 * de verdad del movimiento (regla de este repo: nunca un KDoc, siempre el brief) y
 * estas constantes son su traducción 1:1 a Compose — no se redondean ni se
 * "mejoran" los valores.
 *
 * Ambos componentes comparten EXACTAMENTE este timing (mismo periodo, mismo
 * retraso del segundo anillo/halo, mismo easing) porque el CSS del mock define un
 * solo `@keyframes pulse` que los dos usan — divergir el timing entre la insignia y
 * el punto rompería la lectura "laten juntos" que pidió el dueño.
 */
internal const val MSP_PULSE_PERIOD_MS = 2400

/** `animation-delay` del segundo anillo/halo en el CSS del mock. */
internal const val MSP_PULSE_RING_DELAY_MS = 350

/**
 * 70% de [MSP_PULSE_PERIOD_MS] (1,680ms): el keyframe `70%` del CSS, donde el
 * anillo/halo ya llegó a su opacidad 0 y a su contorno/escala máximos. Del 70% al
 * 100% el CSS no mueve nada (`70% {...} 100% {...}` son el mismo valor) — es el
 * hueco de silencio antes de que el loop reinicie, el mismo hueco que hace que el
 * segundo anillo con 0.35s de retraso nunca se vea "pisando" al primero.
 */
internal const val MSP_PULSE_GROWTH_END_MS = 1680

/** 15% de [MSP_PULSE_PERIOD_MS] (360ms): pico de la respiración del punto interior. */
internal const val MSP_PULSE_BREATHE_PEAK_MS = 360

/** 35% de [MSP_PULSE_PERIOD_MS] (840ms): la respiración ya volvió a escala 1. */
internal const val MSP_PULSE_BREATHE_SETTLE_MS = 840

/** Escala pico del punto blanco interior de [MspInsigniaNueva] al 15% del periodo. */
internal const val MSP_PULSE_BREATHE_PEAK_SCALE = 1.35f

/**
 * `cubic-bezier(.22,.61,.36,1)` del CSS del mock — easing del crecimiento del
 * anillo/halo (0% → 70%). Es el ÚNICO lugar donde se declara: ambos componentes lo
 * importan de aquí, nunca copian los 4 números sueltos.
 */
internal val MspPulseRingEasing = CubicBezierEasing(0.22f, 0.61f, 0.36f, 1f)

/**
 * `ease-in-out` del CSS del mock (keyword estándar, equivalente a
 * `cubic-bezier(.42,0,.58,1)`) — easing de la respiración del punto interior.
 */
internal val MspPulseBreatheEasing = CubicBezierEasing(0.42f, 0f, 0.58f, 1f)

/**
 * Progreso 0→1 de UN anillo/halo: crece de 0 a 1 entre 0ms y [MSP_PULSE_GROWTH_END_MS]
 * con [MspPulseRingEasing] (el `0% → 70%` del CSS), luego se queda en 1 hasta
 * [MSP_PULSE_PERIOD_MS] (el hueco `70% → 100%`), y repite para siempre.
 *
 * [delayMs] traduce el `animation-delay` del segundo anillo/halo: al ser un
 * `initialStartOffset`, SOLO retrasa el primer arranque — el periodo en régimen
 * permanente sigue siendo [MSP_PULSE_PERIOD_MS] exacto, así que el desfase de fase
 * entre los dos anillos se mantiene constante para siempre (igual que
 * `animation-delay` en CSS sobre una animación `infinite`).
 */
internal fun mspPulseRingSpec(delayMs: Int = 0): InfiniteRepeatableSpec<Float> = infiniteRepeatable(
    animation = keyframes {
        durationMillis = MSP_PULSE_PERIOD_MS
        0f at 0
        1f at MSP_PULSE_GROWTH_END_MS using MspPulseRingEasing
        1f at MSP_PULSE_PERIOD_MS
    },
    initialStartOffset = StartOffset(delayMs, StartOffsetType.Delay)
)

/**
 * Escala 1→[MSP_PULSE_BREATHE_PEAK_SCALE]→1 del punto blanco interior de
 * [MspInsigniaNueva]: sube en el primer 15% del periodo, vuelve a 1 al 35%, y se
 * queda en 1 el resto — 1:1 con las tres paradas del CSS (`0%,100%`/`15%`/`35%`).
 */
internal fun mspPulseBreatheSpec(): InfiniteRepeatableSpec<Float> = infiniteRepeatable(
    animation = keyframes {
        durationMillis = MSP_PULSE_PERIOD_MS
        1f at 0
        MSP_PULSE_BREATHE_PEAK_SCALE at MSP_PULSE_BREATHE_PEAK_MS using MspPulseBreatheEasing
        1f at MSP_PULSE_BREATHE_SETTLE_MS using MspPulseBreatheEasing
        1f at MSP_PULSE_PERIOD_MS
    }
)
