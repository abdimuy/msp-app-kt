package com.example.msp_app.core.sync.cobranza

import android.content.Context
import android.util.Log
import com.example.msp_app.core.database.AppDatabase
import com.example.msp_app.core.network.ConnectivityMonitor
import com.example.msp_app.core.telemetry.Telemetry
import com.example.msp_app.data.api.V2ApiProvider
import com.example.msp_app.data.api.services.cobranza.V2CobranzaApi
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
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
            productDao = db.productDao(),
            syncStateDao = db.cobranzaSyncStateDao(),
            connectivity = ConnectivityMonitor.getInstance(context),
            userContextFlow = currentContext.asStateFlow(),
            cobranzaWriteMutex = CobranzaWriteMutexProvider.get(),
            telemetry = telemetryOf(context)
        )
    }

    /**
     * Puente al grafo de Hilt desde este `object` (que no es inyectable):
     * `EntryPointAccessors` es la vía oficial para leer un binding de
     * `SingletonComponent` fuera de un componente inyectado.
     *
     * El `runCatching` no es decorativo: si el grafo de Hilt no está listo
     * (p. ej. un `Context` que no viene de la Application, o un arranque en
     * pruebas sin `@HiltAndroidApp`), la alternativa es que la construcción del
     * manager reviente y el cobrador se quede SIN SYNC por culpa de la
     * telemetría. Se degrada a [NoOpTelemetry] y el sync sigue — es la misma
     * regla que gobierna cada emisión (ver [CobranzaSyncTelemetry]).
     */
    private fun telemetryOf(context: Context): Telemetry = runCatching {
        EntryPointAccessors
            .fromApplication(context.applicationContext, TelemetryEntryPoint::class.java)
            .telemetry()
    }.getOrElse { e ->
        Log.w(TAG, "telemetria no disponible, el sync corre sin ella: ${e.message}")
        NoOpTelemetry
    }

    private const val TAG = "CobranzaSyncProvider"

    /** Acceso al binding de [Telemetry] que publica `TelemetryModule`. */
    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface TelemetryEntryPoint {
        fun telemetry(): Telemetry
    }
}
