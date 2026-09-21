package com.example.msp_app.feature.ventacorreccion.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Forma de las seis cadenas de [TextosCorreccion] (plan "Corregir una venta
 * antes de que suba", corrección 1 del orquestador): 2 a 4 palabras, arranca
 * con mayúscula, sin punto final, nunca la palabra "ciclo"
 * (`feedback_ui_nunca_decir_ciclo`) — como PRUEBA, no como comentario que
 * nadie vuelve a leer. [TextosCorreccion.NO_SE_PUDO_GUARDAR] se agregó en la
 * ronda 1 de arreglo de Task 3.
 */
class TextosCorreccionTest {

    private val todasLasCadenas = listOf(
        "CORREGIR_VENTA" to TextosCorreccion.CORREGIR_VENTA,
        "SE_ESTA_ENVIANDO" to TextosCorreccion.SE_ESTA_ENVIANDO,
        "YA_SE_ENVIO" to TextosCorreccion.YA_SE_ENVIO,
        "LA_REVISA_LA_OFICINA" to TextosCorreccion.LA_REVISA_LA_OFICINA,
        "CORRECCION_GUARDADA" to TextosCorreccion.CORRECCION_GUARDADA,
        "NO_SE_PUDO_GUARDAR" to TextosCorreccion.NO_SE_PUDO_GUARDAR
    )

    @Test
    fun `hay exactamente seis cadenas`() {
        assertEquals(6, todasLasCadenas.size)
    }

    @Test
    fun `cada cadena tiene entre 2 y 4 palabras`() {
        todasLasCadenas.forEach { (nombre, texto) ->
            val palabras = texto.trim().split(Regex("\\s+"))
            assertTrue(
                "$nombre ('$texto') tiene ${palabras.size} palabras, se esperaban 2-4",
                palabras.size in 2..4
            )
        }
    }

    @Test
    fun `cada cadena arranca con mayuscula`() {
        todasLasCadenas.forEach { (nombre, texto) ->
            val primeraLetra = texto.first()
            assertTrue(
                "$nombre ('$texto') no arranca con mayúscula",
                primeraLetra.isUpperCase()
            )
        }
    }

    @Test
    fun `ninguna cadena termina en punto`() {
        todasLasCadenas.forEach { (nombre, texto) ->
            assertFalse("$nombre ('$texto') termina en punto", texto.endsWith("."))
        }
    }

    @Test
    fun `ninguna cadena contiene la palabra ciclo`() {
        todasLasCadenas.forEach { (nombre, texto) ->
            assertFalse(
                "$nombre ('$texto') contiene 'ciclo' — la UI dice 'semana', nunca 'ciclo'",
                texto.contains("ciclo", ignoreCase = true)
            )
        }
    }

    // ── Cadenas exactas (evita una paráfrasis "casi igual" que rompa goldens
    // de Task 5 o textos que el dueño ya aprobó) ─────────────────────────

    @Test
    fun `cadenas exactas del plan`() {
        assertEquals("Corregir venta", TextosCorreccion.CORREGIR_VENTA)
        assertEquals("Se está enviando", TextosCorreccion.SE_ESTA_ENVIANDO)
        assertEquals("Ya se envió", TextosCorreccion.YA_SE_ENVIO)
        assertEquals("La revisa la oficina", TextosCorreccion.LA_REVISA_LA_OFICINA)
        assertEquals("Corrección guardada", TextosCorreccion.CORRECCION_GUARDADA)
        assertEquals("No se pudo guardar", TextosCorreccion.NO_SE_PUDO_GUARDAR)
    }
}
