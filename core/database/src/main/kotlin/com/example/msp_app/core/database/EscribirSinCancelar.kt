package com.example.msp_app.core.database

import androidx.room.RoomDatabase
import androidx.room.withTransaction
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext

/**
 * UNA transacción que la salida de la pantalla no puede cortar (E-APP-084).
 *
 * - `NonCancellable`: el `viewModelScope` se cancela al ir hacia atrás; sin esto
 *   la escritura de dinero podía quedar a medias o sin encolar.
 * - Marca `terminado` (patrón E-APP-078, 9163baac): `withTransaction` de Room
 *   2.6.1 corre en un `runBlocking` hijo y puede reanudar al llamador después de
 *   que el job se canceló, con el bloque YA commiteado; `withContext` lanzaría
 *   la cancelación como si nada se hubiera escrito. Si el bloque terminó, se
 *   devuelve su resultado; si no, se relanza la MISMA instancia.
 *
 * El llamador NO debe leer una `CancellationException` como "no se guardó".
 */
suspend fun <T> RoomDatabase.escribirSinCancelar(bloque: suspend () -> T): T {
    val caja = Caja<T>()
    try {
        return withContext(NonCancellable) {
            val resultado = withTransaction { bloque() }
            caja.valor = resultado
            caja.terminado = true
            resultado
        }
    } catch (tardia: CancellationException) {
        if (caja.terminado) {
            @Suppress("UNCHECKED_CAST")
            return caja.valor as T
        }
        throw tardia
    }
}

/**
 * Lo mismo sin transacción: para lo que va DESPUÉS del commit (fotos, encolado,
 * ubicación). Un bloque que terminó cuenta como hecho aunque la cancelación
 * llegue tarde.
 */
suspend fun <T> sinCancelar(bloque: suspend () -> T): T {
    val caja = Caja<T>()
    try {
        return withContext(NonCancellable) {
            val resultado = bloque()
            caja.valor = resultado
            caja.terminado = true
            resultado
        }
    } catch (tardia: CancellationException) {
        if (caja.terminado) {
            @Suppress("UNCHECKED_CAST")
            return caja.valor as T
        }
        throw tardia
    }
}

private class Caja<T> {
    var terminado: Boolean = false
    var valor: T? = null
}
