package com.example.msp_app.core.common.busqueda

import kotlinx.coroutines.withTimeoutOrNull

/**
 * Topes de DURACIÓN TOTAL para bajar la lista de clientes (E-APP-158). Un lugar, con nombre.
 *
 * El cliente HTTP legado usa `readTimeout = 300 s`, que mide INACTIVIDAD entre bytes, no la
 * duración de la descarga: un servidor que gotea un byte cada 299 s la mantiene viva para
 * siempre y el botón "Actualizar" se quedaba girando sin límite. Estos topes miden el total.
 *
 * ## Por qué 90 s en el botón manual
 * El padrón pesa ~600 KB comprimido (gzip del Node; ~4 MB sin comprimir) = ~4.8 Mbit. En 90 s
 * eso pide un rendimiento sostenido de ~53 kbit/s: 2G EDGE real (80-120 kbit/s) lo cumple con
 * holgura (40-60 s), 3G (≥ 400 kbit/s) en 2-12 s. Quien esté por debajo de 53 kbit/s no estaba
 * usando la pantalla de todos modos: se le avisa y sigue con la lista guardada. Más de 90 s con
 * el cobrador mirando un indicador es peor que un aviso claro.
 *
 * ## Por qué 300 s en segundo plano
 * Nadie espera: el worker puede tardar más (rendimiento mínimo ~16 kbit/s) y reintenta con
 * backoff. WorkManager corta cualquier worker a los 10 min; este tope llega antes y devuelve
 * `retry()` limpio.
 *
 * Supuestos NO medidos en dispositivo: que el servidor responde en gzip (lo dijo el dueño) y
 * el rendimiento real de 2G/3G en las rutas.
 */
object TopeDeDescarga {
    const val MANUAL_MS: Long = 90_000L
    const val SEGUNDO_PLANO_MS: Long = 300_000L
}

/** La descarga pasó de su tope total. NO es una `CancellationException`: es un fallo normal. */
class TiempoAgotadoException(val topeMs: Long) :
    RuntimeException("la descarga superó el tope de $topeMs ms")

/**
 * Corre [bloque] con un tope de duración TOTAL. Si vence, cancela [bloque] (y con él la petición
 * HTTP, que Retrofit/OkHttp cancelan al cancelarse la corrutina) y lanza
 * [TiempoAgotadoException]. Una cancelación externa NO se traga: se propaga tal cual.
 */
suspend fun <T> conTopeTotal(topeMs: Long, bloque: suspend () -> T): T {
    var terminado = false
    var valor: T? = null
    withTimeoutOrNull(topeMs) {
        valor = bloque()
        terminado = true
    }
    if (!terminado) throw TiempoAgotadoException(topeMs)
    @Suppress("UNCHECKED_CAST")
    return valor as T
}
