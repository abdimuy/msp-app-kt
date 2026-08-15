package com.example.msp_app.core.appgate.ui

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.msp_app.core.appgate.AppVersionGate
import com.example.msp_app.core.appgate.UpdatePackage
import com.example.msp_app.core.appgate.VersionGateStatus
import com.example.msp_app.core.appgate.VersionVerdict
import com.example.msp_app.core.appgate.download.ApkInstaller
import com.example.msp_app.core.appgate.download.NetworkStatus
import com.example.msp_app.core.appgate.download.NetworkStatusProvider
import com.example.msp_app.core.appgate.download.UpdateDownloadScheduler
import com.example.msp_app.core.appgate.download.UpdateDownloadState
import com.example.msp_app.core.appgate.download.UpdateDownloadStateHolder
import com.example.msp_app.core.appgate.download.UpdateFileLocator
import com.example.msp_app.core.appgate.download.stalledAfter
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

private const val TAG = "VersionGate"

/**
 * Punto de entrada de la compuerta para la capa de navegación.
 *
 * Hace cuatro cosas y nada más: expone el veredicto ([verdict]) para que
 * `AppNavigation` mande a la pantalla sin salida, arma el estado de esa
 * pantalla ([blockedState]), **encola la descarga automática por wifi** en
 * cuanto la configuración remota anuncia un APK que todavía no está en disco,
 * y **borra el APK ya instalado** cuando la compuerta vuelve a permitir.
 *
 * La tercera parte es el corazón del trato: el archivo se baja **antes**, por
 * wifi y sin que nadie lo pida, para que cuando el bloqueo llegue ya esté en
 * el teléfono. Bloquear más fuerte no cambia el incentivo; abaratar la
 * actualización sí.
 */
@HiltViewModel
class VersionGateViewModel @Inject constructor(
    private val gate: AppVersionGate,
    private val downloadState: UpdateDownloadStateHolder,
    networkStatus: NetworkStatusProvider,
    private val locator: UpdateFileLocator,
    private val scheduler: UpdateDownloadScheduler,
    private val installer: ApkInstaller
) : ViewModel() {

    private val status: StateFlow<VersionGateStatus?> = gate.status.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
        initialValue = null
    )

    /**
     * `null` mientras no se ha leído la caché. La navegación NO debe bloquear
     * con `null`: no saber todavía no es lo mismo que saber que hay que
     * bloquear.
     */
    val verdict: StateFlow<VersionVerdict?> = status
        .map { it?.verdict }
        .distinctUntilChanged()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
            initialValue = null
        )

    val blockedState: StateFlow<VersionBlockedUiState> = combine(
        status,
        downloadState.state,
        networkStatus.observe(),
        // Reloj de "esto no avanza": sin él, un trabajo encolado que nunca
        // arranca se ve igual que una descarga sana.
        downloadState.state.stalledAfter()
    ) { gateStatus, download, network, stalled ->
        toUiState(gateStatus, download, network, stalled)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
        initialValue = VersionBlockedUiState()
    )

    init {
        // Escucha remota → caché. Vive lo que viva el ViewModel; la caché es
        // la que sobrevive al proceso.
        viewModelScope.launch { gate.syncRemote() }
        viewModelScope.launch {
            status.collect { gateStatus ->
                if (gateStatus == null) return@collect
                cleanUpInstalledApk(gateStatus)
                val update = gateStatus.updatePackage ?: return@collect
                // `Ready` es descarga terminada y verificada: reencolar ahí
                // volvería a bajar 11 MB por nada.
                if (downloadState.state.value is UpdateDownloadState.Ready) return@collect
                if (!locator.isComplete(update)) scheduler.enqueueAutomatic(update)
            }
        }
    }

    /**
     * Descarga a petición explícita: **cualquier red**, incluidos datos
     * móviles. El botón que llama aquí siempre dice el peso — que decida
     * sabiendo.
     */
    fun downloadNow() {
        val update = status.value?.updatePackage ?: return
        scheduler.enqueueManual(update)
    }

    /** Abre el instalador del sistema con el APK ya verificado. */
    fun install() {
        val ready = downloadState.state.value as? UpdateDownloadState.Ready
        val file = ready?.file ?: status.value?.updatePackage?.let { locator.fileFor(it) } ?: return
        installer.install(file)
    }

    /**
     * Barrido de después de instalar.
     *
     * Solo con la compuerta **abierta**: mientras el bloqueo sigue puesto, un
     * APK "viejo" en disco es la evidencia de que lo publicado no alcanza —
     * borrarlo ahí dispararía una descarga nueva del mismo archivo inservible,
     * en bucle. Con la compuerta abierta ya no hay nada que probar y el
     * archivo solo ocupa 11 MB del teléfono para siempre (`filesDir` no lo
     * recupera Android nunca).
     */
    private suspend fun cleanUpInstalledApk(gateStatus: VersionGateStatus) {
        if (gateStatus.blocked) return
        val removed = locator.clearObsolete(gateStatus.installedVersionCode)
        if (removed > 0) Log.i(TAG, "se borraron $removed APK ya instalados")
    }

    private fun toUiState(
        gateStatus: VersionGateStatus?,
        download: UpdateDownloadState,
        network: NetworkStatus,
        stalled: Boolean
    ): VersionBlockedUiState {
        val update: UpdatePackage? = gateStatus?.updatePackage
        val apkComplete = update != null && locator.isComplete(update)
        // El APK solo se lee cuando está entero: un parcial no se puede
        // parsear, y hacerlo en cada bloque de 64 KB sería E/S por nada.
        val offered = if (update != null && (apkComplete || download is UpdateDownloadState.Ready)) {
            locator.versionOf(update)
        } else {
            null
        }
        return VersionBlockedUiState(
            installedVersionName = gateStatus?.installedVersionName.orEmpty(),
            requiredVersionName = gateStatus?.requiredVersionName.orEmpty(),
            deadlineLabel = gateStatus?.deadlineLabel.orEmpty(),
            stage = resolveStage(
                download = download,
                network = network,
                apkComplete = apkComplete,
                update = update,
                belowMinimum = belowMinimum(offered, gateStatus?.requiredVersionCode ?: 0),
                stalled = stalled
            )
        )
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
