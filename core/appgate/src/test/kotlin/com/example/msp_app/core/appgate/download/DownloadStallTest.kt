package com.example.msp_app.core.appgate.download

import java.io.File
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

private const val TIMEOUT_MS = 120_000L
private const val SIZE = 11_000_000L

/**
 * El reloj de "esto no avanza". Sin él, un trabajo encolado que nunca arranca
 * se veía exactamente igual que una descarga sana — que es lo que dejó a un
 * teléfono de campo horas en «0 de 0 MB · 0%».
 */
@OptIn(ExperimentalCoroutinesApi::class) // advanceTimeBy: tiempo virtual
class DownloadStallTest {

    @Test
    fun `un estado quieto se declara estancado al vencer el plazo`() = runTest {
        val state = MutableStateFlow<UpdateDownloadState>(UpdateDownloadState.Idle)
        val visto = mutableListOf<Boolean>()
        val job = launch { state.stalledAfter(TIMEOUT_MS).toList(visto) }

        advanceTimeBy(TIMEOUT_MS + 1)

        assertEquals(listOf(false, true), visto)
        job.cancel()
    }

    @Test
    fun `antes del plazo no se declara nada`() = runTest {
        val state = MutableStateFlow<UpdateDownloadState>(UpdateDownloadState.Idle)
        val visto = mutableListOf<Boolean>()
        val job = launch { state.stalledAfter(TIMEOUT_MS).toList(visto) }

        advanceTimeBy(TIMEOUT_MS - 1)

        assertEquals(listOf(false), visto)
        job.cancel()
    }

    @Test
    fun `cada bloque que entra reinicia el reloj`() = runTest {
        val state = MutableStateFlow<UpdateDownloadState>(UpdateDownloadState.Idle)
        val visto = mutableListOf<Boolean>()
        val job = launch { state.stalledAfter(TIMEOUT_MS).toList(visto) }

        repeat(3) { bloque ->
            advanceTimeBy(TIMEOUT_MS - 1)
            state.value = UpdateDownloadState.Running(
                DownloadProgress((bloque + 1) * 1_000_000L, SIZE)
            )
        }
        advanceTimeBy(1)

        assertFalse("una descarga viva nunca se declara estancada", visto.contains(true))
        job.cancel()
    }

    /**
     * Medido en el teléfono: el worker que se reintenta en bucle iba
     * `Idle` → `Running(0)` → `Paused(0)` → … y, contando cambios de estado,
     * el aviso tardó CUATRO minutos en salir. El avance se mide en bytes.
     */
    @Test
    fun `un bucle de reintentos que no baja nada se estanca igual, y a tiempo`() = runTest {
        val state = MutableStateFlow<UpdateDownloadState>(UpdateDownloadState.Idle)
        val visto = mutableListOf<Boolean>()
        val job = launch { state.stalledAfter(TIMEOUT_MS).toList(visto) }

        repeat(4) {
            advanceTimeBy(TIMEOUT_MS / 5)
            state.value = UpdateDownloadState.Running(DownloadProgress(0L, SIZE))
            advanceTimeBy(TIMEOUT_MS / 5)
            state.value = UpdateDownloadState.Paused(DownloadProgress(0L, SIZE))
        }

        assertEquals(
            "cero bytes en 2 min es estancado, se llame como se llame",
            listOf(false, true),
            visto
        )
        job.cancel()
    }

    @Test
    fun `un archivo ya listo no se estanca nunca`() = runTest {
        val state = MutableStateFlow<UpdateDownloadState>(
            UpdateDownloadState.Ready(File("msp.apk"))
        )
        val visto = mutableListOf<Boolean>()
        val job = launch { state.stalledAfter(TIMEOUT_MS).toList(visto) }

        advanceTimeBy(TIMEOUT_MS * 10)

        assertEquals(listOf(false), visto)
        job.cancel()
    }

    @Test
    fun `el default son dos minutos`() = runTest {
        val state = MutableStateFlow<UpdateDownloadState>(UpdateDownloadState.Idle)

        assertEquals(TIMEOUT_MS, DOWNLOAD_STALL_TIMEOUT_MS)
        assertFalse(state.stalledAfter().first())
    }
}
