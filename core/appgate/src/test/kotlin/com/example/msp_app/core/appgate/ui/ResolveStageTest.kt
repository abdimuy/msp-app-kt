package com.example.msp_app.core.appgate.ui

import com.example.msp_app.core.appgate.download.DownloadProgress
import com.example.msp_app.core.appgate.download.NetworkStatus
import com.example.msp_app.core.appgate.download.UpdateDownloadState
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

private const val SIZE = 11_000_000L

/** El orden de las ramas de [resolveStage] ES la regla; esto lo fija. */
class ResolveStageTest {

    private fun stage(
        download: UpdateDownloadState = UpdateDownloadState.Idle,
        network: NetworkStatus = NetworkStatus.UNMETERED,
        apkComplete: Boolean = false
    ) = resolveStage(download, network, apkComplete, SIZE)

    @Test
    fun `si el archivo ya esta, gana sobre todo lo demas`() {
        assertEquals(
            UpdateStage.ReadyToInstall,
            stage(
                download = UpdateDownloadState.Ready(File("msp.apk")),
                network = NetworkStatus.OFFLINE
            )
        )
    }

    @Test
    fun `un archivo completo en disco cuenta como listo aunque el proceso se haya reiniciado`() {
        assertEquals(
            UpdateStage.ReadyToInstall,
            stage(apkComplete = true, network = NetworkStatus.OFFLINE)
        )
    }

    @Test
    fun `mientras baja se muestra bajando`() {
        val progress = DownloadProgress(4_200_000L, SIZE)

        assertEquals(
            UpdateStage.Downloading(progress),
            stage(download = UpdateDownloadState.Running(progress))
        )
    }

    @Test
    fun `sin señal se dice sin señal`() {
        assertEquals(UpdateStage.Offline, stage(network = NetworkStatus.OFFLINE))
    }

    @Test
    fun `un corte con bytes en disco se muestra como corte`() {
        val progress = DownloadProgress(6_100_000L, SIZE)

        assertEquals(
            UpdateStage.Failed(progress),
            stage(download = UpdateDownloadState.Paused(progress))
        )
    }

    @Test
    fun `un corte sin un solo byte bajado no promete una reanudacion que no existe`() {
        val stage = stage(download = UpdateDownloadState.Paused(DownloadProgress(0L, SIZE)))

        assertTrue(
            "con 0 bytes no hay nada desde donde continuar",
            stage is UpdateStage.Downloading
        )
    }

    @Test
    fun `con datos moviles se ofrece la descarga manual, con el peso`() {
        assertEquals(UpdateStage.MeteredOnly(SIZE), stage(network = NetworkStatus.METERED))
    }

    @Test
    fun `en wifi y sin haber empezado, baja sola`() {
        assertEquals(UpdateStage.Downloading(DownloadProgress(0L, SIZE)), stage())
    }
}
