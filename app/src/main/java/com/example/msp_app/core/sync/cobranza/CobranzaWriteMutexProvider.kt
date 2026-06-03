package com.example.msp_app.core.sync.cobranza

/**
 * Singleton factory para [CobranzaWriteMutex].
 *
 * Tanto [CobranzaSyncProvider] como [CobranzaReconcilerProvider] deben llamar
 * a [get] para obtener la misma instancia — esto garantiza que el mutex sea
 * realmente compartido entre ambas piezas.
 */
object CobranzaWriteMutexProvider {

    @Volatile private var instance: CobranzaWriteMutex? = null

    fun get(): CobranzaWriteMutex = instance ?: synchronized(this) {
        instance ?: CobranzaWriteMutex().also { instance = it }
    }
}
