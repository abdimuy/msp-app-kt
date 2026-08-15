package com.example.msp_app.core.appgate.ui

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
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Punto de entrada de la compuerta para la capa de navegación.
 *
 * Hace tres cosas y nada más: expone el veredicto ([verdict]) para que
 * `AppNavigation` mande a la pantalla sin salida, arma el estado de esa
 * pantalla ([blockedState]) y **encola la descarga automática por wifi** en
 * cuanto la configuración remota anuncia un APK que todavía no está en disco.
 *
 * Esa última parte es el corazón del trato: el archivo se baja **antes**, por
 * wifi y sin que nadie lo pida, para que cuando el bloqueo llegue ya esté en
 * el teléfono. Bloquear más fuerte no cambia el incentivo; abaratar la
 * actualización sí.
 */
@HiltViewModel
class VersionGateViewModel @Inject constructor(
    private val gate: AppVersionGate,
    private val downloadState: UpdateDownloadStateHolder,
    private val networkStatus: NetworkStatusProvider,
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
        networkStatus.observe()
    ) { gateStatus, download, network ->
        toUiState(gateStatus, download, network)
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
                val update = gateStatus?.updatePackage ?: return@collect
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

    private fun toUiState(
        gateStatus: VersionGateStatus?,
        download: UpdateDownloadState,
        network: NetworkStatus
    ): VersionBlockedUiState {
        val update: UpdatePackage? = gateStatus?.updatePackage
        return VersionBlockedUiState(
            installedVersionName = gateStatus?.installedVersionName.orEmpty(),
            requiredVersionName = gateStatus?.requiredVersionName.orEmpty(),
            deadlineLabel = gateStatus?.deadlineLabel.orEmpty(),
            stage = resolveStage(
                download = download,
                network = network,
                apkComplete = update != null && locator.isComplete(update),
                sizeBytes = update?.sizeBytes ?: 0L
            )
        )
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
