package com.example.msp_app.data.captura

/**
 * Las capturas que ESTE proceso está escribiendo ahora mismo (E-APP-119, D15).
 *
 * La reanudación de visitas y garantías CAPTURADA corre al volver a la app y al
 * tocar el aviso; la pantalla que capturó puede seguir viva y a media escritura
 * (esperando al cobrador, comprimiendo fotos). Reanudar esa misma captura en
 * paralelo no duplicaría nada —la escritura es idempotente por el marcador—,
 * pero sí podría encolar el envío antes de que la pantalla escriba las fotos.
 * Por eso la reanudación se salta lo que está en curso aquí.
 *
 * Vive en memoria a propósito: si el proceso muere, nadie está escribiendo y
 * todo lo CAPTURADA queda libre para reanudarse.
 */
object CapturasEnProceso {
    private val cuentas = HashMap<String, Int>()

    /** La pantalla entra siempre: el cobrador está ahí. */
    @Synchronized
    fun entrar(id: String) {
        cuentas[id] = (cuentas[id] ?: 0) + 1
    }

    /** La reanudación entra sólo si nadie más la está escribiendo. */
    @Synchronized
    fun entrarSiLibre(id: String): Boolean {
        if ((cuentas[id] ?: 0) > 0) return false
        cuentas[id] = 1
        return true
    }

    @Synchronized
    fun salir(id: String) {
        val quedan = (cuentas[id] ?: 1) - 1
        if (quedan <= 0) cuentas.remove(id) else cuentas[id] = quedan
    }
}
