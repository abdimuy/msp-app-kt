package com.example.msp_app.core.sync.cobranza

import kotlinx.coroutines.sync.Mutex

/**
 * Mutex compartido entre [CobranzaSyncManager] y [CobranzaReconciler] para
 * serializar todas las escrituras a Room de la capa cobranza.
 *
 * Invariante: en cualquier punto del tiempo, como máximo una de las dos
 * operaciones (sync incremental o reconcile de phantoms) tiene el lock.
 * Esto cierra la race condition entre el path SSE que aplica IDs recién
 * llegados del servidor y el path del reconciler que borra phantoms: sin el
 * mutex compartido, el reconciler podría borrar una venta/pago que acaba de
 * ser escrita por el sync (o viceversa), dejando el cache local inconsistente
 * durante una ventana arbitraria de tiempo.
 *
 * Se inyecta como singleton vía el patrón de providers existente:
 * [CobranzaSyncProvider] y [CobranzaReconcilerProvider] ambos llaman a
 * [CobranzaWriteMutexProvider.get] y reciben la misma instancia.
 */
class CobranzaWriteMutex {
    val mutex = Mutex()
}
