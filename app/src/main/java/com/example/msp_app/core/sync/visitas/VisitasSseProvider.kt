package com.example.msp_app.core.sync.visitas

import android.content.Context
import androidx.annotation.VisibleForTesting
import com.example.msp_app.core.common.sync.pendingwork.domain.ports.SyncErrorReporter
import com.example.msp_app.core.common.sync.pendingwork.domain.usecases.HandleVisitsPushEventUseCase
import com.example.msp_app.core.network.BearerAuthInterceptor
import com.example.msp_app.core.sync.cobranza.CobranzaSyncProvider
import com.example.msp_app.core.sync.pendingwork.data.visits.VisitsReconcileTriggerProvider
import com.example.msp_app.core.sync.pendingwork.di.VisitsReconcileEntryPoint
import com.example.msp_app.data.api.FirebaseAuthTokenProvider
import com.example.msp_app.data.api.V2ApiProvider
import dagger.hilt.android.EntryPointAccessors
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CoroutineScope
import okhttp3.OkHttpClient

/**
 * Process-wide singleton factory for [VisitasSseSubscriber].
 *
 * Mirrors [com.example.msp_app.core.sync.cobranza.CobranzaSseProvider]: a plain
 * `object`, not a Hilt `@Singleton`. The subscriber is started from a lifecycle
 * observer rather than injected, so a Hilt binding would serve no consumer —
 * and it holds no `@Provides`, so `NetworkKillSwitchGuardTest`'s sweep has
 * nothing here to find.
 *
 * The OkHttp client is built for streaming: `readTimeout(0)` because an SSE
 * connection is long-lived and the 60s default would cut it before the
 * server's first 25s keep-alive ping.
 *
 * ## The trigger is re-resolved, never captured
 *
 * [VisitsReconcileTriggerProvider.get] is called on every event rather than
 * once at build time. That is what keeps push funnelling into the SAME
 * process-wide [com.example.msp_app.core.common.sync.pendingwork.domain
 * .usecases.TriggerVisitsReconciliationUseCase] the other three triggers use —
 * which is the only reason Task 11's `Mutex.tryLock()` debounces push against
 * them at all.
 */
object VisitasSseProvider {

    @Volatile
    private var instance: VisitasSseSubscriber? = null

    fun get(context: Context, scope: CoroutineScope): VisitasSseSubscriber {
        return instance ?: synchronized(this) {
            instance ?: build(context, scope).also { instance = it }
        }
    }

    /**
     * Clears the cached instance so the NEXT [get] rebuilds. Tests only — see
     * [VisitsReconcileTriggerProvider.reset] for the same reasoning about
     * Robolectric's per-method context.
     */
    @VisibleForTesting
    fun reset() {
        synchronized(this) {
            instance = null
        }
    }

    private fun build(context: Context, scope: CoroutineScope): VisitasSseSubscriber {
        val appContext = context.applicationContext
        val client = OkHttpClient.Builder()
            .addInterceptor(BearerAuthInterceptor(FirebaseAuthTokenProvider()))
            .connectTimeout(CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            // readTimeout(0) es crítico: SSE necesita la conexión abierta
            // indefinidamente. Con el default de 60s el stream se cortaría
            // antes del primer ping del servidor (cada 25s).
            .readTimeout(0, TimeUnit.MILLISECONDS)
            .pingInterval(PING_INTERVAL_SECONDS, TimeUnit.SECONDS)
            .build()

        val entryPoint by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
            EntryPointAccessors.fromApplication(appContext, VisitsReconcileEntryPoint::class.java)
        }

        return VisitasSseSubscriber(
            okHttpClient = client,
            baseUrl = V2ApiProvider.v2BaseUrl,
            userContextFlow = CobranzaSyncProvider.userContextFlow,
            handler = HandleVisitsPushEventUseCase(
                triggerReconciliation = {
                    VisitsReconcileTriggerProvider.get(appContext).execute()
                },
                errorReporter = object : SyncErrorReporter {
                    override fun report(code: String, message: String, props: Map<String, String>) {
                        entryPoint.syncErrorReporter().report(code, message, props)
                    }
                }
            ),
            coroutineScope = scope
        )
    }

    private const val CONNECT_TIMEOUT_SECONDS = 60L
    private const val PING_INTERVAL_SECONDS = 30L
}
