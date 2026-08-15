package com.example.msp_app.core.appgate.download

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map

/**
 * Cuánto puede la pantalla decir "descargando" sin que entre un solo byte.
 *
 * Dos minutos, no diez: el número no es un timeout de red (de eso se encarga
 * OkHttp y el reintento con backoff de WorkManager) sino el límite de lo que
 * una persona aguanta mirando una barra quieta antes de concluir que el
 * teléfono se trabó. Pasado eso, más vale ofrecerle un botón que seguir
 * prometiendo.
 */
const val DOWNLOAD_STALL_TIMEOUT_MS: Long = 120_000L

/**
 * `true` cuando la descarga lleva [timeoutMs] sin que entre un solo byte.
 *
 * El avance se mide en **bytes bajados**, no en cambios de estado. La
 * diferencia se vio en el teléfono: un trabajo que se reintenta en bucle
 * (`Idle` → `Running(0)` → `Paused(0)` → …) cambiaba de estado cada tanto y
 * reiniciaba el reloj, así que el aviso tardó cuatro minutos en aparecer en
 * vez de dos. Mirando los bytes, un bucle que no baja nada es exactamente tan
 * estancado como un `Idle` quieto.
 *
 * `ApkDownloader` reporta el progreso en cada bloque de 64 KB, así que
 * cualquier descarga viva reinicia el reloj muchas veces por segundo.
 *
 * [UpdateDownloadState.Ready] no vence nunca: ahí ya no hay nada que esperar.
 */
@OptIn(ExperimentalCoroutinesApi::class)
fun Flow<UpdateDownloadState>.stalledAfter(
    timeoutMs: Long = DOWNLOAD_STALL_TIMEOUT_MS
): Flow<Boolean> = map { state -> state.stallKey() }
    .distinctUntilChanged()
    .flatMapLatest { key ->
        flow {
            emit(false)
            if (key == READY_KEY) return@flow
            delay(timeoutMs)
            emit(true)
        }
    }
    .distinctUntilChanged()

/** Marca de "ya no hay nada que esperar"; ningún avance real puede valer esto. */
private const val READY_KEY = -1L

/** Lo único que cuenta como avance: bytes en disco. */
private fun UpdateDownloadState.stallKey(): Long = when (this) {
    is UpdateDownloadState.Running -> progress.downloadedBytes
    is UpdateDownloadState.Paused -> progress.downloadedBytes
    UpdateDownloadState.Idle -> 0L
    is UpdateDownloadState.Ready -> READY_KEY
}
