package com.example.msp_app.core.appgate.ui

import com.example.msp_app.core.appgate.UpdatePackage
import com.example.msp_app.core.appgate.download.ApkVersion
import com.example.msp_app.core.appgate.download.DownloadProgress
import com.example.msp_app.core.appgate.download.NetworkStatus
import com.example.msp_app.core.appgate.download.UpdateDownloadState
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

private const val SIZE = 11_000_000L
private const val REQUIRED_CODE = 57

private val PAQUETE = UpdatePackage(
    url = "https://example.invalid/msp-app-2.17.0.apk",
    sizeBytes = SIZE,
    sha256 = "9f86d081884c7d659a2feaa0c55ad015a3bf4f1b2b0b822cd15d6c15b0f00a08"
)

/** El orden de las ramas de [resolveStage] ES la regla; esto lo fija. */
class ResolveStageTest {

    @Suppress("LongParameterList")
    private fun stage(
        download: UpdateDownloadState = UpdateDownloadState.Idle,
        network: NetworkStatus = NetworkStatus.UNMETERED,
        apkComplete: Boolean = false,
        update: UpdatePackage? = PAQUETE,
        offeredVersion: ApkVersion? = null,
        requiredVersionCode: Int = REQUIRED_CODE,
        stalled: Boolean = false
    ) = resolveStage(
        download = download,
        network = network,
        apkComplete = apkComplete,
        update = update,
        // Se pasa por `belowMinimum` a propósito: así el test ejercita también
        // la comparación, que es donde vive la regla del defecto 2.
        belowMinimum = belowMinimum(offeredVersion, requiredVersionCode),
        stalled = stalled
    )

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

    // ─── defecto 1: configuración a medias no es una descarga ─────────────────

    @Test
    fun `sin paquete publicado se dice que no hay actualizacion, no que esta bajando`() {
        assertEquals(UpdateStage.Unavailable, stage(update = null))
    }

    @Test
    fun `sin paquete publicado ni la red ni el disco cambian nada`() {
        assertEquals(UpdateStage.Unavailable, stage(update = null, network = NetworkStatus.METERED))
        assertEquals(UpdateStage.Unavailable, stage(update = null, apkComplete = true))
    }

    // ─── defecto 1 (segunda mitad): una descarga que no avanza ────────────────

    @Test
    fun `una descarga estancada deja de llamarse descarga`() {
        assertEquals(
            UpdateStage.Stalled(DownloadProgress(0L, SIZE)),
            stage(stalled = true)
        )
    }

    @Test
    fun `estancada conserva lo que ya se habia bajado`() {
        val progress = DownloadProgress(4_200_000L, SIZE)

        assertEquals(
            UpdateStage.Stalled(progress),
            stage(download = UpdateDownloadState.Running(progress), stalled = true)
        )
    }

    @Test
    fun `sin conexion explica mejor que un estancamiento generico`() {
        assertEquals(
            UpdateStage.Offline,
            stage(network = NetworkStatus.OFFLINE, stalled = true)
        )
    }

    // ─── defecto 2: el APK ofrecido tiene que alcanzar el mínimo ──────────────

    @Test
    fun `un APK por debajo del minimo no se ofrece para instalar`() {
        assertEquals(
            UpdateStage.Unusable("2.16.0"),
            stage(
                download = UpdateDownloadState.Ready(File("msp.apk")),
                offeredVersion = ApkVersion(versionCode = 56, versionName = "2.16.0")
            )
        )
    }

    @Test
    fun `un APK que si alcanza el minimo se instala`() {
        assertEquals(
            UpdateStage.ReadyToInstall,
            stage(
                apkComplete = true,
                offeredVersion = ApkVersion(versionCode = 57, versionName = "2.17.0")
            )
        )
    }

    @Test
    fun `un APK ilegible no se descarta - lo validara el instalador del sistema`() {
        assertEquals(
            UpdateStage.ReadyToInstall,
            stage(apkComplete = true, offeredVersion = null)
        )
    }

    @Test
    fun `sin minimo configurado no hay nada contra que comparar`() {
        assertEquals(
            UpdateStage.ReadyToInstall,
            stage(
                apkComplete = true,
                offeredVersion = ApkVersion(versionCode = 1, versionName = "0.0.1"),
                requiredVersionCode = 0
            )
        )
    }
}
