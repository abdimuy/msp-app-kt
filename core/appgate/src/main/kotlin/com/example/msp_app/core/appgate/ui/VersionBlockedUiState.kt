package com.example.msp_app.core.appgate.ui

import com.example.msp_app.core.appgate.UpdatePackage
import com.example.msp_app.core.appgate.download.ApkVersion
import com.example.msp_app.core.appgate.download.DownloadProgress
import com.example.msp_app.core.appgate.download.NetworkStatus
import com.example.msp_app.core.appgate.download.UpdateDownloadState

/**
 * Las situaciones en las que puede encontrarse alguien bloqueado. Cada una
 * tiene a lo sumo UN botón y ese botón dice exactamente qué va a pasar al
 * tocarlo.
 *
 * Las tres últimas son estados **honestos de fracaso**: ninguna promete un
 * avance que no existe. Salieron de un teléfono de campo real que se quedó
 * horas en «Descargando · 0 de 0 MB · 0%» porque la configuración remota
 * estaba a medias.
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

    /**
     * No hay APK publicado: la oficina no llenó URL, tamaño o checksum. No es
     * culpa del cobrador y no hay botón que lo arregle — lo único útil que
     * puede hacer es avisar.
     */
    data object Unavailable : UpdateStage

    /**
     * Se dijo "descargando" y no ha entrado un byte en
     * [com.example.msp_app.core.appgate.download.DOWNLOAD_STALL_TIMEOUT_MS].
     * Deja de prometer y ofrece reintentar.
     */
    data class Stalled(val progress: DownloadProgress) : UpdateStage

    /**
     * El APK publicado trae una versión que **no alcanza el mínimo**:
     * instalarlo dejaría el teléfono igual de bloqueado. Se detecta leyendo el
     * archivo, no la promesa de la configuración.
     */
    data class Unusable(val offeredVersionName: String) : UpdateStage
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
 * 1. sin paquete publicado no hay nada que descargar, y se dice — antes que
 *    cualquier otra cosa, porque ninguna de las demás explicaciones sería
 *    cierta;
 * 2. si el archivo está listo, nada más importa — salvo que ese archivo no
 *    alcance el mínimo, en cuyo caso instalarlo no sacaría a nadie del
 *    bloqueo y hay que decirlo en vez de ofrecer "Instalar";
 * 3. sin señal se dice sin señal: explica mejor cualquier atasco que un
 *    "no avanza";
 * 4. una descarga que lleva minutos sin moverse deja de llamarse descarga;
 * 5. si está bajando de verdad, se muestra bajando;
 * 6. un corte con bytes en disco se muestra como corte, para prometer que se
 *    reanuda;
 * 7. con datos móviles se ofrece la descarga manual, con el peso a la vista;
 * 8. lo que queda es wifi sin haber empezado: baja sola, y se dice.
 *
 * (3) subió por encima de (5) respecto de la versión original: un `Running`
 * congelado sin red no es una descarga, y el estancamiento necesita que "sin
 * conexión" gane para no tapar la explicación buena con una genérica.
 */
@Suppress("ReturnCount")
internal fun resolveStage(
    download: UpdateDownloadState,
    network: NetworkStatus,
    apkComplete: Boolean,
    update: UpdatePackage?,
    belowMinimum: ApkVersion? = null,
    stalled: Boolean = false
): UpdateStage {
    if (update == null) return UpdateStage.Unavailable
    if (download is UpdateDownloadState.Ready || apkComplete) {
        return belowMinimum?.let { UpdateStage.Unusable(it.versionName) }
            ?: UpdateStage.ReadyToInstall
    }
    if (network == NetworkStatus.OFFLINE) return UpdateStage.Offline

    val progress = download.progressOr(update.sizeBytes)
    if (stalled) return UpdateStage.Stalled(progress)
    if (download is UpdateDownloadState.Running) return UpdateStage.Downloading(download.progress)
    if (download is UpdateDownloadState.Paused && download.progress.downloadedBytes > 0L) {
        return UpdateStage.Failed(download.progress)
    }
    if (network == NetworkStatus.METERED) return UpdateStage.MeteredOnly(update.sizeBytes)
    return UpdateStage.Downloading(DownloadProgress(0L, update.sizeBytes))
}

/**
 * La versión del APK descargado **solo si no alcanza** el mínimo; `null` si
 * sirve, si no hay mínimo configurado, o si no se pudo leer el archivo.
 *
 * Ese último caso se trata como "sirve" a propósito: no vamos a impedir
 * instalar por no haber podido leer un archivo que el propio instalador del
 * sistema va a validar de todos modos.
 */
internal fun belowMinimum(offered: ApkVersion?, requiredVersionCode: Int): ApkVersion? =
    offered?.takeIf { requiredVersionCode > 0 && it.versionCode < requiredVersionCode }

/** Lo bajado hasta ahora, según el estado; `0` de [sizeBytes] si no hay nada. */
private fun UpdateDownloadState.progressOr(sizeBytes: Long): DownloadProgress = when (this) {
    is UpdateDownloadState.Running -> progress
    is UpdateDownloadState.Paused -> progress
    else -> DownloadProgress(0L, sizeBytes)
}
