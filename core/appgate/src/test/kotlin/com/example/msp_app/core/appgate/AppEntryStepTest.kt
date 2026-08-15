package com.example.msp_app.core.appgate

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Quién gana en el arranque.
 *
 * El defecto que fija esta prueba se veía como "a veces sale la huella
 * primero, a veces el bloqueo": `MainActivity.isAuthenticated` sobrevive a la
 * recreación de la `Activity`, así que el orden dependía de si el proceso
 * venía vivo. Acá deja de depender de eso.
 */
class AppEntryStepTest {

    @Test
    fun `el bloqueo por version gana a la huella`() {
        assertEquals(
            AppEntryStep.VERSION_BLOCKED,
            resolveAppEntryStep(
                verdict = VersionVerdict.BLOCKED,
                authenticated = false,
                gateWaitElapsed = false
            )
        )
    }

    @Test
    fun `el bloqueo tambien gana con el proceso ya autenticado`() {
        assertEquals(
            AppEntryStep.VERSION_BLOCKED,
            resolveAppEntryStep(
                verdict = VersionVerdict.BLOCKED,
                authenticated = true,
                gateWaitElapsed = true
            )
        )
    }

    @Test
    fun `sin veredicto todavia no se pide huella`() {
        assertEquals(
            AppEntryStep.WAITING_FOR_GATE,
            resolveAppEntryStep(
                verdict = null,
                authenticated = false,
                gateWaitElapsed = false
            )
        )
    }

    @Test
    fun `si el veredicto no llega a tiempo se sigue adelante`() {
        assertEquals(
            AppEntryStep.AUTHENTICATE,
            resolveAppEntryStep(
                verdict = null,
                authenticated = false,
                gateWaitElapsed = true
            )
        )
    }

    @Test
    fun `con la compuerta abierta toca la huella`() {
        assertEquals(
            AppEntryStep.AUTHENTICATE,
            resolveAppEntryStep(
                verdict = VersionVerdict.ALLOWED,
                authenticated = false,
                gateWaitElapsed = false
            )
        )
    }

    @Test
    fun `ya autenticado y sin bloqueo, la app corre sin esperar nada`() {
        assertEquals(
            AppEntryStep.RUN,
            resolveAppEntryStep(
                verdict = null,
                authenticated = true,
                gateWaitElapsed = false
            )
        )
    }
}
