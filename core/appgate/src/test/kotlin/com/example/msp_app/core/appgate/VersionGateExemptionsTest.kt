package com.example.msp_app.core.appgate

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

private const val DEVICE_A = "a1b2c3d4e5f60718"
private const val DEVICE_B = "0f1e2d3c4b5a6978"

/**
 * Las dos capas de exención que sí son código (la tercera —el proyecto de
 * Firebase— es operativa; ver KDoc de [isVersionGateExempt]).
 */
class VersionGateExemptionsTest {

    @Test
    fun `un build de depuracion nunca se bloquea`() {
        assertTrue(
            isVersionGateExempt(
                debugBuild = true,
                deviceId = DEVICE_A,
                exemptDeviceIds = emptySet()
            )
        )
    }

    @Test
    fun `un build de release con el mismo dispositivo SI entra a la compuerta`() {
        // Gemelo del anterior: cambia solo `debugBuild`.
        assertFalse(
            isVersionGateExempt(
                debugBuild = false,
                deviceId = DEVICE_A,
                exemptDeviceIds = emptySet()
            )
        )
    }

    @Test
    fun `un dispositivo en la lista queda exento`() {
        assertTrue(
            isVersionGateExempt(
                debugBuild = false,
                deviceId = DEVICE_A,
                exemptDeviceIds = setOf(DEVICE_A)
            )
        )
    }

    @Test
    fun `un dispositivo fuera de la lista no queda exento`() {
        assertFalse(
            isVersionGateExempt(
                debugBuild = false,
                deviceId = DEVICE_B,
                exemptDeviceIds = setOf(DEVICE_A)
            )
        )
    }

    @Test
    fun `un deviceId nulo nunca hace match, ni con una lista poblada`() {
        assertFalse(
            isVersionGateExempt(
                debugBuild = false,
                deviceId = null,
                exemptDeviceIds = setOf(DEVICE_A, "")
            )
        )
    }

    @Test
    fun `un deviceId en blanco nunca hace match`() {
        // Trampa real: `Settings.Secure.ANDROID_ID` puede devolver vacío en
        // algunos firmwares. Si "" estuviera en la lista, exentaría a TODOS
        // esos teléfonos de golpe.
        assertFalse(
            isVersionGateExempt(
                debugBuild = false,
                deviceId = "   ",
                exemptDeviceIds = setOf("", "   ")
            )
        )
    }

    @Test
    fun `la comparacion distingue mayusculas`() {
        assertFalse(
            isVersionGateExempt(
                debugBuild = false,
                deviceId = DEVICE_A.uppercase(),
                exemptDeviceIds = setOf(DEVICE_A)
            )
        )
    }
}
