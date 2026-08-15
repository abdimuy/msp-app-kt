package com.example.msp_app.core.appgate.ui

import com.example.msp_app.core.appgate.download.DownloadProgress
import com.example.msp_app.core.appgate.download.NetworkStatus
import com.example.msp_app.core.appgate.download.UpdateDownloadState

/**
 * Las cinco situaciones en las que puede encontrarse alguien bloqueado, tal
 * como las fija el mockup aprobado. Cada una tiene UN botón y ese botón dice
 * exactamente qué va a pasar al tocarlo.
 */
sealed interface UpdateStage {
    /** El archivo ya está en el teléfono. El caso que queremos que sea el normal. */
    data object ReadyToInstall : UpdateStage

    /** Bajando por wifi, sola. Progreso en megas, nunca una rueda. */
    data class Downloading(val progress: DownloadProgress) : UpdateStage

    /** Hay red pero la pagan sus datos: la automática espera al wifi. */
    data class MeteredOnly(val sizeBytes: Long) : UpdateStage

    /** Sin señal. No se puede maquillar y no se intenta. */
    data object Offline : UpdateStage

    /** Se cortó a medias. Lo bajado sigue ahí. */
    data class Failed(val progress: DownloadProgress) : UpdateStage
}

/** Todo lo que la pantalla de bloqueo pinta. */
data class VersionBlockedUiState(
    val installedVersionName: String = "",
    val requiredVersionName: String = "",
    val deadlineLabel: String = "",
    val stage: UpdateStage = UpdateStage.Offline
)

/**
 * De "qué está pasando" a "qué se ve". El orden de las ramas ES la regla:
 *
 * 1. si el archivo está listo, nada más importa — es la salida buena;
 * 2. si está bajando, se muestra bajando aunque la red acabe de cambiar;
 * 3. sin señal se dice sin señal, antes que cualquier otra explicación;
 * 4. un corte con bytes en disco se muestra como corte, para prometer que se
 *    reanuda;
 * 5. con datos móviles se ofrece la descarga manual, con el peso a la vista;
 * 6. lo que queda es wifi sin haber empezado: baja sola, y se dice.
 */
internal fun resolveStage(
    download: UpdateDownloadState,
    network: NetworkStatus,
    apkComplete: Boolean,
    sizeBytes: Long
): UpdateStage = when {
    download is UpdateDownloadState.Ready || apkComplete -> UpdateStage.ReadyToInstall
    download is UpdateDownloadState.Running -> UpdateStage.Downloading(download.progress)
    network == NetworkStatus.OFFLINE -> UpdateStage.Offline
    download is UpdateDownloadState.Paused && download.progress.downloadedBytes > 0L ->
        UpdateStage.Failed(download.progress)

    network == NetworkStatus.METERED -> UpdateStage.MeteredOnly(sizeBytes)
    else -> UpdateStage.Downloading(DownloadProgress(0L, sizeBytes))
}
