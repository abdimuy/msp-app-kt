package com.example.msp_app.core.appgate.download

import androidx.work.NetworkType
import com.example.msp_app.core.appgate.UpdatePackage
import com.example.msp_app.core.testing.RobolectricTestBase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

private val PAQUETE = UpdatePackage(
    url = "https://example.invalid/msp-app-2.17.0.apk",
    sizeBytes = 11_000_000L,
    sha256 = "9f86d081884c7d659a2feaa0c55ad015a3bf4f1b2b0b822cd15d6c15b0f00a08"
)

/**
 * La política de red, que es la mitad del trato: **automática solo por wifi,
 * manual en cualquier red**. Es una constante de una línea, y justamente por
 * eso conviene que tenga candado: invertirla gastaría los datos de la flota
 * sin que nadie se entere hasta el recibo.
 */
class UpdateDownloadSchedulerTest : RobolectricTestBase() {

    @Test
    fun `la descarga automatica exige una red sin costo`() {
        assertEquals(
            NetworkType.UNMETERED,
            updateDownloadConstraints(automatic = true).requiredNetworkType
        )
    }

    @Test
    fun `la descarga manual se conforma con cualquier red`() {
        assertEquals(
            NetworkType.CONNECTED,
            updateDownloadConstraints(automatic = false).requiredNetworkType
        )
    }

    @Test
    fun `el paquete viaja entero al worker`() {
        val recuperado = PAQUETE.toInputData().toUpdatePackage()

        assertEquals(PAQUETE, recuperado)
    }

    @Test
    fun `unos datos de entrada sin checksum no producen paquete`() {
        val sinChecksum = PAQUETE.copy(sha256 = "").toInputData()

        // El worker responde `failure` con esto: no hay forma de verificar la
        // descarga, así que no tiene sentido intentarla.
        assertNull(sinChecksum.toUpdatePackage())
    }
}
