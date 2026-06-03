package com.example.msp_app.core.sync.cobranza

import com.example.msp_app.data.api.FirebaseBearerInterceptor
import com.example.msp_app.data.api.V2ApiProvider
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CoroutineScope
import okhttp3.OkHttpClient

/**
 * Process-wide singleton factory para [CobranzaSseSubscriber].
 *
 * Construye el [OkHttpClient] específico para SSE:
 *  - readTimeout(0) — los streams SSE son long-lived; el timeout default de
 *    60s cerraría la conexión antes de recibir el primer ping del servidor.
 *  - [FirebaseBearerInterceptor] — misma lógica de auth que V2BaseApi; cada
 *    instancia tiene su propia caché de token aunque Firebase hace caching
 *    interno de `getIdToken(false)`, así que no hay costo real.
 *
 * Patrón espejo de [CobranzaReconcilerProvider]. El [CobranzaSyncManager]
 * se pasa en el primer `get` para que el subscriber pueda disparar
 * `syncNow()` sin necesitar un Context ni referiéndose a otro singleton.
 */
object CobranzaSseProvider {

    @Volatile private var instance: CobranzaSseSubscriber? = null

    /**
     * Devuelve el suscriptor (perezoso). La primera llamada construye el
     * singleton con el [manager] y el [scope] dados. Llamadas posteriores
     * devuelven el mismo objeto.
     */
    fun get(manager: CobranzaSyncManager, scope: CoroutineScope): CobranzaSseSubscriber {
        return instance ?: synchronized(this) {
            instance ?: build(manager, scope).also { instance = it }
        }
    }

    private fun build(manager: CobranzaSyncManager, scope: CoroutineScope): CobranzaSseSubscriber {
        val client = OkHttpClient.Builder()
            .addInterceptor(FirebaseBearerInterceptor())
            .connectTimeout(60, TimeUnit.SECONDS)
            // readTimeout(0) es crítico: SSE necesita la conexión abierta
            // indefinidamente. Con el default de 60s el stream se cortaría
            // antes del primer ping del servidor (cada 25s).
            .readTimeout(0, TimeUnit.MILLISECONDS)
            .pingInterval(30, TimeUnit.SECONDS)
            .build()

        return CobranzaSseSubscriber(
            okHttpClient = client,
            baseUrl = V2ApiProvider.BASE_URL,
            userContextFlow = CobranzaSyncProvider.userContextFlow,
            onEvent = { manager.syncNow() },
            coroutineScope = scope
        )
    }
}
