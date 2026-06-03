package com.example.msp_app.core.sync.cobranza

import android.content.Context
import com.example.msp_app.core.network.ConnectivityMonitor
import com.example.msp_app.data.api.V2ApiProvider
import com.example.msp_app.data.api.services.cobranza.V2CobranzaApi
import com.example.msp_app.data.local.AppDatabase

/**
 * Process-wide singleton factory for [CobranzaReconciler].
 *
 * Reuses the [CobranzaSyncProvider.userContextFlow] so the reconciler
 * always sees the same user context as the sync manager — no duplicated
 * state, no race between two parallel flows.
 */
object CobranzaReconcilerProvider {

    @Volatile private var instance: CobranzaReconciler? = null

    /**
     * Returns the reconciler (lazy); the first `get` constructs it.
     * Subsequent calls return the same instance — to update the user
     * context, call [CobranzaSyncProvider.setContext].
     */
    fun get(context: Context): CobranzaReconciler {
        return instance ?: synchronized(this) {
            instance ?: build(context).also { instance = it }
        }
    }

    private fun build(context: Context): CobranzaReconciler {
        val db = AppDatabase.getInstance(context)
        return CobranzaReconciler(
            api = V2ApiProvider.create(V2CobranzaApi::class.java),
            saleDao = db.saleDao(),
            paymentDao = db.paymentDao(),
            connectivity = ConnectivityMonitor.getInstance(context),
            userContextFlow = CobranzaSyncProvider.userContextFlow,
            cobranzaWriteMutex = CobranzaWriteMutexProvider.get()
        )
    }
}
