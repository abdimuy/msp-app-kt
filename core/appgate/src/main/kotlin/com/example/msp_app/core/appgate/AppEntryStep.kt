package com.example.msp_app.core.appgate

/** Qué le toca a la raíz de composición mostrar en el arranque. */
enum class AppEntryStep {
    /** Todavía no se sabe si la app puede correr. Ni huella ni navegación. */
    WAITING_FOR_GATE,

    /** Pantalla de bloqueo por versión, sin salida. */
    VERSION_BLOCKED,

    /** Toca el prompt biométrico. */
    AUTHENTICATE,

    /** La app corre normal. */
    RUN
}

/**
 * Cuánto se espera al veredicto antes de dejar pasar.
 *
 * La caché en disco responde en milisegundos; este techo existe solo para que
 * un `DataStore` ilegible no deje la app mirando una pantalla vacía. Pasado el
 * plazo se sigue adelante y el watcher de `AppNavigation` mandará al bloqueo
 * si el veredicto llega tarde — degradar a "como antes" es preferible a un
 * arranque colgado.
 */
const val GATE_WAIT_TIMEOUT_MS: Long = 3_000L

/**
 * **El bloqueo por versión gana a la huella.** No tiene sentido autenticarse
 * en una app que no se puede usar: el prompt biométrico es la llave de una
 * puerta que está tapiada, y verlo primero sugiere que el problema es el
 * usuario cuando el problema es la versión.
 *
 * Antes el orden dependía del azar: `MainActivity.isAuthenticated` es un
 * `companion object` que sobrevive a la recreación de la `Activity`, así que
 * un arranque en frío pedía huella y luego bloqueaba, mientras que uno tibio
 * (proceso vivo) saltaba directo al bloqueo. Misma app, dos comportamientos.
 * Esta función lo vuelve determinista.
 *
 * | `verdict` | `authenticated` | `gateWaitElapsed` | Paso |
 * |---|---|---|---|
 * | `BLOCKED` | cualquiera | cualquiera | VERSION_BLOCKED |
 * | ≠ `BLOCKED` | `true` | cualquiera | RUN |
 * | `null` | `false` | `false` | WAITING_FOR_GATE |
 * | `null` | `false` | `true` | AUTHENTICATE |
 * | `ALLOWED` | `false` | cualquiera | AUTHENTICATE |
 *
 * @param verdict veredicto vigente; `null` = todavía no se leyó la caché.
 * @param authenticated ya pasó el prompt biométrico en este proceso.
 * @param gateWaitElapsed venció [GATE_WAIT_TIMEOUT_MS] sin veredicto.
 */
fun resolveAppEntryStep(
    verdict: VersionVerdict?,
    authenticated: Boolean,
    gateWaitElapsed: Boolean
): AppEntryStep = when {
    verdict == VersionVerdict.BLOCKED -> AppEntryStep.VERSION_BLOCKED
    // Ya está dentro: no se le vuelve a poner una espera encima por un
    // veredicto que todavía no llega — el watcher de `AppNavigation` sigue
    // vigilando y lo sacará si hace falta.
    authenticated -> AppEntryStep.RUN
    verdict == null && !gateWaitElapsed -> AppEntryStep.WAITING_FOR_GATE
    else -> AppEntryStep.AUTHENTICATE
}
