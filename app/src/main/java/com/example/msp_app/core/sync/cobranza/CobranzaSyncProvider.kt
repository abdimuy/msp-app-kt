package com.example.msp_app.core.sync.cobranza

import android.content.Context
import com.example.msp_app.core.network.ConnectivityMonitor
import com.example.msp_app.data.api.V2ApiProvider
import com.example.msp_app.data.api.services.cobranza.V2CobranzaApi
import com.example.msp_app.data.local.AppDatabase

/**
 * Process-wide singleton factory for the [CobranzaSyncManager]. The
 * `zonaProvider` argument is a thunk so the caller — the Compose layer that
 * owns the auth state — can read the current cobrador's zone lazily, and
 * the manager refreshes it every tick instead of binding it at construction.
 */
object CobranzaSyncProvider {

    @Volatile private var instance: CobranzaSyncManager? = null

    fun get(context: Context, zonaProvider: suspend () -> Int?): CobranzaSyncManager {
        return instance ?: synchronized(this) {
            instance ?: build(context, zonaProvider).also { instance = it }
        }
    }

    private fun build(context: Context, zonaProvider: suspend () -> Int?): CobranzaSyncManager {
        val db = AppDatabase.getInstance(context)
        return CobranzaSyncManager(
            api = V2ApiProvider.create(V2CobranzaApi::class.java),
            saleDao = db.saleDao(),
            paymentDao = db.paymentDao(),
            syncStateDao = db.cobranzaSyncStateDao(),
            connectivity = ConnectivityMonitor.getInstance(context),
            zonaProvider = zonaProvider
        )
    }
}
