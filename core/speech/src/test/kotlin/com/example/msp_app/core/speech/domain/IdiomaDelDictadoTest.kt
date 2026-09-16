package com.example.msp_app.core.speech.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Qué español pedirle al reconocedor.
 *
 * El caso que manda es el primero: es **el teléfono del dueño**, medido, y el que
 * demuestra que pedir `es-MX` a ciegas dejaba el dictado muerto.
 */
class IdiomaDelDictadoTest {

    /**
     * **El SM-A256E, medido en vidrio.** `installedOnDeviceLanguages = [es-US]`, y
     * pedir `es-MX` contesta `ERROR_LANGUAGE_NOT_SUPPORTED` (12).
     *
     * **Control de reversión:** devolver `PREFERIDO` a secas pone este test en ROJO.
     */
    @Test
    fun `el telefono del dueno trae es-US y es lo que hay que pedirle`() {
        assertEquals("es-US", IdiomaDelDictado.de(listOf("es-US")))
    }

    /** Con `es-MX` instalado se pide `es-MX`: es el español de donde se cobra. */
    @Test
    fun `con es-MX instalado se pide es-MX`() {
        assertEquals("es-MX", IdiomaDelDictado.de(listOf("en-US", "es-MX", "es-US")))
    }

    /** Entre `es-US` y `es-ES` gana el latinoamericano, que está más cerca. */
    @Test
    fun `entre es-US y es-ES gana el latinoamericano`() {
        assertEquals("es-US", IdiomaDelDictado.de(listOf("es-ES", "es-US")))
    }

    /**
     * Sin `es-MX` ni `es-US`, se toma el que haya — y **siempre el mismo**. Un
     * orden no determinista cambiaría la calidad del dictado entre dos arranques
     * sin que nadie tocara nada.
     */
    @Test
    fun `sin los dos preferidos elige determinista, no el primero que llego`() {
        val unOrden = IdiomaDelDictado.de(listOf("es-PE", "es-AR", "es-CO"))
        val otroOrden = IdiomaDelDictado.de(listOf("es-CO", "es-PE", "es-AR"))
        assertEquals("es-AR", unOrden)
        assertEquals("las mismas instaladas no pueden dar dos idiomas", unOrden, otroOrden)
    }

    /**
     * Sin español instalado se pide [IdiomaDelDictado.PREFERIDO] igual: no hay
     * dictado que salvar, y pedir el idioma correcto deja que el motor diga la
     * verdad en vez de fallar por un idioma ajeno que confundiría el diagnóstico.
     */
    @Test
    fun `sin espanol instalado se pide el preferido y el motor dira la verdad`() {
        assertEquals("es-MX", IdiomaDelDictado.de(listOf("en-US", "pt-BR")))
        assertEquals("es-MX", IdiomaDelDictado.de(emptyList()))
    }

    /**
     * Las etiquetas llegan con formas distintas según el motor, y lo que se
     * devuelve es **la del motor**, no una normalizada: si el aparato dice
     * `es_US`, se le pide `es_US`. Pedirle una versión "bonita" que no reconoce
     * sería el mismo defecto de `es-MX` otra vez.
     */
    @Test
    fun `devuelve la etiqueta tal como la reporto el motor`() {
        assertEquals("es_US", IdiomaDelDictado.de(listOf("es_US")))
        assertEquals("ES-US", IdiomaDelDictado.de(listOf("ES-US")))
        assertTrue(IdiomaDelDictado.esEspanol("spa"))
        assertTrue(IdiomaDelDictado.esEspanol("  es-MX  "))
    }

    /** `es-419` es el español de Latinoamérica: es español, y cuenta. */
    @Test
    fun `es-419 es espanol de latinoamerica`() {
        assertTrue(IdiomaDelDictado.esEspanol("es-419"))
        assertEquals("es-419", IdiomaDelDictado.de(listOf("et-EE", "es-419")))
    }

    /**
     * **Control positivo del filtro.** Un `startsWith("es")` crudo diría que el
     * estonio es español; si este test no estuviera, el de arriba pasaría igual
     * con un filtro roto.
     */
    @Test
    fun `el estonio no es espanol`() {
        assertFalse(IdiomaDelDictado.esEspanol("et-EE"))
        assertFalse(IdiomaDelDictado.esEspanol("est"))
        assertEquals("es-MX", IdiomaDelDictado.de(listOf("et-EE", "en-US")))
    }
}
