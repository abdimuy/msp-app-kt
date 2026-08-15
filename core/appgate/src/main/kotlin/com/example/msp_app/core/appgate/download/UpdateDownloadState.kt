package com.example.msp_app.core.appgate.download

import android.content.Context
import com.example.msp_app.core.appgate.UpdatePackage
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** En qué va la descarga del APK, desde el punto de vista de la pantalla. */
sealed interface UpdateDownloadState {
    /** Nada en curso. No dice si hay archivo en disco — eso lo sabe [UpdateFileLocator]. */
    data object Idle : UpdateDownloadState

    /** Bajando. [progress] se refresca conforme entra cada bloque. */
    data class Running(val progress: DownloadProgress) : UpdateDownloadState

    /**
     * Se cortó a medias y lo bajado sigue en disco. El reintento continúa
     * desde ahí — decirlo es lo que hace que la gente reintente en vez de
     * rendirse.
     */
    data class Paused(val progress: DownloadProgress) : UpdateDownloadState

    /** Descargado y verificado. Un toque y cinco segundos. */
    data class Ready(val file: File) : UpdateDownloadState
}

/**
 * El estado de la descarga, compartido entre el worker que baja el archivo y
 * la pantalla que lo mira.
 *
 * En memoria a propósito: si el proceso muere, el estado se reconstruye de lo
 * único que sobrevive de verdad —el archivo en disco— vía [UpdateFileLocator].
 * Persistir un "iba en 6.1 MB" que puede contradecir al archivo real sería
 * peor que no tenerlo.
 */
@Singleton
class UpdateDownloadStateHolder @Inject constructor() {
    private val _state = MutableStateFlow<UpdateDownloadState>(UpdateDownloadState.Idle)
    val state: StateFlow<UpdateDownloadState> = _state.asStateFlow()

    fun update(state: UpdateDownloadState) {
        _state.value = state
    }
}

/**
 * Dónde vive el APK descargado.
 *
 * El nombre lleva el SHA-256 del paquete: cuando la oficina publica una
 * versión nueva, el archivo destino cambia solo y el parcial viejo deja de
 * estorbar (lo limpia [UpdateFileLocator.clearOthers]) — sin lógica de
 * invalidación que mantener.
 */
class UpdateFileLocator(private val context: Context) {

    fun fileFor(update: UpdatePackage): File =
        File(File(context.filesDir, UPDATES_DIR), "${update.sha256}.apk")

    /** Cuánto hay bajado ya de [update]. `0` si no hay nada. */
    fun downloadedBytes(update: UpdatePackage): Long =
        fileFor(update).takeIf { it.isFile }?.length() ?: 0L

    /** `true` si el archivo está completo según el tamaño anunciado. */
    fun isComplete(update: UpdatePackage): Boolean =
        update.sizeBytes > 0L && downloadedBytes(update) >= update.sizeBytes

    /** Borra descargas de paquetes que ya no son el vigente. */
    fun clearOthers(current: UpdatePackage) {
        val keep = fileFor(current).name
        File(context.filesDir, UPDATES_DIR)
            .listFiles()
            ?.filter { it.name != keep }
            ?.forEach { it.delete() }
    }

    private companion object {
        const val UPDATES_DIR = "updates"
    }
}
