package com.example.msp_app.core.sync.cobranza

import android.content.Context
import com.example.msp_app.core.network.ConnectivityMonitor
import com.example.msp_app.data.api.V2ApiProvider
import com.example.msp_app.data.api.services.cobranza.V2CobranzaApi
import com.example.msp_app.data.local.AppDatabase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Process-wide singleton factory for the [CobranzaSyncManager].
 *
 * Mantiene un [MutableStateFlow] del contexto del cobrador (zona +
 * FECHA_CARGA_INICIAL) para que la capa Compose pueda **actualizarlo** —
 * no solo leerlo perezosamente. Esto importa porque el manager se
 * construye una sola vez por proceso: si la lambda capturara los valores
 * por closure, los cambios de zona o de FECHA_CARGA_INICIAL nunca
 * llegarían al sync. Con el flow, cada tick lee el valor más reciente.
 */
object CobranzaSyncProvider {

    private val currentContext = MutableStateFlow<UserContext?>(null)

    /**
     * Read-only view of the active user context. Shared with
     * [CobranzaReconcilerProvider] so both components observe the same
     * zone / FECHA_CARGA_INICIAL without duplicating state.
     */
    val userContextFlow = currentContext.asStateFlow()

    @Volatile private var instance: CobranzaSyncManager? = null

    /**
     * Devuelve el manager (perezoso); el primer `get` lo construye con
     * una referencia al StateFlow del contexto. El manager observa el
     * flow para disparar `syncNow` cuando aparece o cambia el contexto,
     * y también lee `.value` en cada tick. Los `get` posteriores
     * devuelven el mismo manager — para actualizar zona/ventana hay que
     * llamar [setContext].
     */
    fun get(context: Context): CobranzaSyncManager {
        return instance ?: synchronized(this) {
            instance ?: build(context).also { instance = it }
        }
    }

    /**
     * Empuja un nuevo contexto del cobrador. El siguiente tick del manager
     * (o `syncNow()` invocado a mano) lo recoge.
     */
    fun setContext(userContext: UserContext?) {
        currentContext.value = userContext
    }

    private fun build(context: Context): CobranzaSyncManager {
        val db = AppDatabase.getInstance(context)
        return CobranzaSyncManager(
            api = V2ApiProvider.create(V2CobranzaApi::class.java),
            db = db,
            saleDao = db.saleDao(),
            paymentDao = db.paymentDao(),
            syncStateDao = db.cobranzaSyncStateDao(),
            connectivity = ConnectivityMonitor.getInstance(context),
            userContextFlow = currentContext.asStateFlow()
        )
    }
}
