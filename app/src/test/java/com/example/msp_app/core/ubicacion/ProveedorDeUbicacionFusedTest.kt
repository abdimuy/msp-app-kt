package com.example.msp_app.core.ubicacion

import android.location.Location
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * E-APP-149 (H1): `Location.isMock` existe desde API 31 y minSdk es 24. En Android 7 a 11 llamarlo lanza
 * NoSuchMethodError; la lectura debe usar `isFromMockProvider` por debajo de 31 y nunca tumbar la app.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = android.app.Application::class)
class ProveedorDeUbicacionFusedTest {

    private fun ubicacion() = Location("gps").apply {
        latitude = 20.5888
        longitude = -100.3899
        accuracy = 8f
        time = 1_000L
        elapsedRealtimeNanos = 5_000_000_000L
    }

    @Test
    fun `E-APP-149 H1 convertir un Location a fix en Android 9 no lanza NoSuchMethodError`() {
        val fix = ubicacion().aFix()
        assertFalse(fix.esMock)
        assertEquals(8.0, fix.precisionM, 0.0)
    }

    @Test
    fun `E-APP-149 H1 antes de API 31 se lee isFromMockProvider y no isMock`() {
        var llamoIsMock = false
        val r = leerSimulada(
            sdkInt = 30,
            desdeApi31 = {
                llamoIsMock = true
                throw NoSuchMethodError("isMock")
            },
            antesDeApi31 = { true }
        )
        assertTrue(r)
        assertFalse(llamoIsMock)
    }

    @Test
    fun `E-APP-149 H1 desde API 31 se lee isMock`() {
        assertTrue(leerSimulada(31, desdeApi31 = { true }, antesDeApi31 = { false }))
        assertFalse(leerSimulada(34, desdeApi31 = { false }, antesDeApi31 = { true }))
    }

    @Test
    fun `E-APP-149 H1 si la lectura falla por cualquier causa no fatal se trata como simulada`() {
        assertTrue(
            leerSimulada(30, desdeApi31 = {
                false
            }, antesDeApi31 = { throw NoSuchMethodError("x") })
        )
        assertTrue(leerSimulada(33, desdeApi31 = { error("x") }, antesDeApi31 = { false }))
    }

    @Test(expected = OutOfMemoryError::class)
    fun `E-APP-149 H1 un error fatal de la maquina virtual no se traga`() {
        leerSimulada(30, desdeApi31 = { false }, antesDeApi31 = { throw OutOfMemoryError() })
    }
}
