package com.example.msp_app.core.appgate.download

import android.content.Context
import com.example.msp_app.core.appgate.UpdatePackage
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

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
class UpdateFileLocator(
    private val context: Context,
    private val versionReader: ApkVersionReader,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) {

    fun fileFor(update: UpdatePackage): File =
        File(File(context.filesDir, UPDATES_DIR), "${update.sha256}.apk")

    /**
     * Qué versión trae el archivo que hay en disco — la del APK, no la que
     * promete la configuración remota. `null` si no hay archivo o no se puede
     * leer (típicamente, una descarga a medias).
     */
    fun versionOf(update: UpdatePackage): ApkVersion? = versionReader.read(fileFor(update))

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

    /**
     * Borra los APK que ya no le sirven a nadie: los que traen una versión
     * **igual o anterior** a la instalada.
     *
     * Es el barrido de después de instalar. No se puede borrar al lanzar el
     * `Intent` de instalación —es asíncrono, la app puede morir a medias y el
     * instalador se quedaría sin archivo—, así que se hace al revés: la señal
     * de que la instalación funcionó es que el `versionCode` instalado alcanzó
     * al del archivo, y eso se comprueba en el siguiente arranque. Sobrevive a
     * que el proceso muera en cualquier punto porque no recuerda nada: mira el
     * disco y la versión que corre.
     *
     * Lo que **no** borra, a propósito:
     * - un APK más nuevo que el instalado — es justamente la descarga
     *   adelantada por wifi que hace que el bloqueo llegue con el archivo ya
     *   puesto;
     * - un archivo cuya versión no se puede leer — típicamente una descarga a
     *   medias, que se reanuda en vez de rebajarse entera.
     *
     * @return cuántos archivos se borraron (para el log; nadie decide con eso).
     */
    suspend fun clearObsolete(installedVersionCode: Int): Int = withContext(ioDispatcher) {
        sweepObsoleteApks(
            File(context.filesDir, UPDATES_DIR),
            installedVersionCode,
            versionReader
        )
    }

    private companion object {
        const val UPDATES_DIR = "updates"
    }
}

/**
 * El barrido, sin Android de por medio para poder probarlo con un directorio
 * temporal y un lector de mentira.
 */
internal fun sweepObsoleteApks(
    dir: File,
    installedVersionCode: Int,
    reader: ApkVersionReader
): Int = dir.listFiles()
    .orEmpty()
    .filter { file ->
        val version = reader.read(file) ?: return@filter false
        version.versionCode <= installedVersionCode
    }
    .count { it.delete() }
