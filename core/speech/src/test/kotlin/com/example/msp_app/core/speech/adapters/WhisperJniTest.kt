package com.example.msp_app.core.speech.adapters

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * **El `.so` que no está: que su ausencia no tumbe nada.**
 *
 * ## Qué prueba este archivo y qué NO
 *
 * Prueba que `System.loadLibrary` fallando deja el motor en "ausente" en vez de
 * propagar un `UnsatisfiedLinkError`, que es un `Error` y no una `Exception` —
 * o sea que un `catch (e: Exception)` lo dejaría pasar y tumbaría la app al
 * construir el grafo de Hilt. Eso sí se puede medir en la JVM, y es el defecto
 * que de verdad importa acá.
 *
 * **NO prueba** que el JNI funcione: no hay `.so` en este repo y la JVM no
 * podría cargar uno de Android aunque lo hubiera. No se escribe un test que
 * finja lo contrario.
 *
 * La carga se inyecta por constructor —no se toca `System.loadLibrary` de
 * verdad— porque el fallo que interesa es el de **este código**, no el del
 * cargador de clases.
 */
class WhisperJniTest {

    @Test
    fun `sin la libreria el motor se declara ausente y no lanza`() {
        val motor = WhisperJni { throw UnsatisfiedLinkError("no such library") }

        assertFalse("un .so ausente no puede tumbar el grafo", motor.cargada)
    }

    /** `SecurityException` (un cargador restringido) tiene el mismo desenlace. */
    @Test
    fun `una carga prohibida tambien deja el motor ausente`() {
        val motor = WhisperJni { throw SecurityException("prohibido") }

        assertFalse(motor.cargada)
    }

    /**
     * **Control positivo.** Si la costura no pudiera decir "sí" nunca, los dos
     * tests de arriba pasarían sin probar nada — serían una ausencia
     * inverificable, que es exactamente lo que la regla del repo prohíbe.
     */
    @Test
    fun `con una carga que funciona el motor se declara cargado`() {
        val cargadas = mutableListOf<String>()
        val motor = WhisperJni { cargadas += it }

        assertTrue("el control positivo no encontraria nada", motor.cargada)
        assertEquals(listOf("whisper_msp"), cargadas)
    }

    /** Transcribir sin librería devuelve `failure`, no lanza. */
    @Test
    fun `transcribir sin libreria falla sin lanzar`() {
        val motor = WhisperJni { throw UnsatisfiedLinkError("no such library") }

        val resultado = motor.transcribir("/modelo.bin", "/audio.wav")

        assertTrue(resultado.isFailure)
    }
}
