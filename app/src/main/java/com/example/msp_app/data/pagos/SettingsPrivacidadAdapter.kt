package com.example.msp_app.data.pagos

import com.example.msp_app.core.navegacion.domain.port.PrivacidadDelMenuPort
import com.example.msp_app.core.settings.SettingsRepository
import com.example.msp_app.feature.pagos.domain.port.PrivacidadPort
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn

/**
 * [PrivacidadPort] (`:feature:pagos`) **y** [PrivacidadDelMenuPort]
 * (`:core:navegacion`, Task 8, ruling R2 del controlador, 2026-10-06) sobre
 * [SettingsRepository] — el adaptador vive en `:app` porque es el único
 * módulo que ve los dos puertos y la preferencia a la vez.
 *
 * **Un solo singleton para los dos puertos, a propósito.** A diferencia del
 * tema (ver [ThemeControllerTemaDeLaAppAdapter]), aquí SÍ importa que sea la
 * MISMA instancia: el estado real vive en [cache], no en `SettingsRepository`
 * (que solo expone un `Flow` frío). Si la lista de clientes y el menú
 * recibieran cada uno su propia instancia, cada una abriría su PROPIO
 * `CoroutineScope`/`StateFlow` sobre el mismo `Flow` — dos cachés que podrían
 * quedar unos milisegundos desincronizados entre sí, en vez de una sola
 * verdad. `@Singleton` en la clase más `fun providePrivacidadDelMenuPort(a:
 * SettingsPrivacidadAdapter): PrivacidadDelMenuPort = a` en `MenuLateralModule`
 * (igual que ya hace `PagosPortsModule.providePrivacidadPort`) le entregan a
 * Dagger la misma garantía: cualquier punto de inyección que pida
 * [SettingsPrivacidadAdapter] —directo o a través de cualquiera de los dos
 * puertos— recibe el mismo objeto.
 *
 * ## Por qué `stateIn(Eagerly)` y no leer el `Flow` cada vez
 *
 * [ocultosAhora] tiene que contestar **sin suspender**, y DataStore no ofrece
 * lectura síncrona. Se resuelve cacheando: un `StateFlow` singleton, arrancado
 * `Eagerly`, que ya tiene el valor real mucho antes de que alguien abra la
 * lista. El `false` inicial solo se vería si la pantalla se abriera en el mismo
 * milisegundo que el proceso, y el default de la preferencia es `false` de
 * todos modos.
 */
@Singleton
class SettingsPrivacidadAdapter @Inject constructor(
    private val settings: SettingsRepository
) : PrivacidadPort, PrivacidadDelMenuPort {

    // Scope propio del singleton, como `RemoteLogger` y `RemoteDbDebugger`: no
    // hay un scope de aplicación inyectable en el grafo y este vive lo que la app.
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val cache = settings.privacyMasked.stateIn(
        scope = scope,
        started = SharingStarted.Eagerly,
        initialValue = false
    )

    override val ocultos: Flow<Boolean> = cache

    override fun ocultosAhora(): Boolean = cache.value

    override suspend fun alternar() {
        settings.setPrivacyMasked(!settings.privacyMasked.first())
    }
}
