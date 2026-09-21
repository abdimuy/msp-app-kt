package com.example.msp_app.feature.ventacorreccion.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Forma de las siete cadenas de [TextosCorreccion] (plan "Corregir una venta
 * antes de que suba", corrección 1 del orquestador): 2 a 4 palabras, arranca
 * con mayúscula, sin punto final, nunca la palabra "ciclo"
 * (`feedback_ui_nunca_decir_ciclo`) — como PRUEBA, no como comentario que
 * nadie vuelve a leer. [TextosCorreccion.NO_SE_PUDO_GUARDAR] se agregó en la
 * ronda 1 de arreglo de Task 3; [TextosCorreccion.GUARDAR_CORRECCION] en la
 * ronda de arreglo 1 de Task 5.
 *
 * [todasLasCadenas] se arma por REFLEXIÓN sobre los campos `String` de
 * [TextosCorreccion] — antes era una lista a mano: agregar una cadena nueva
 * a [TextosCorreccion] sin acordarse de sumarla aquí dejaba la cuenta y las
 * reglas de forma corriendo sobre menos cadenas de las reales, en verde, sin
 * que nadie lo notara (exactamente el defecto que motivó este cambio en la
 * ronda de arreglo 1 de Task 5). `cadenas exactas del plan` sigue siendo un
 * mapa A MANO a propósito — esa prueba existe justo para pescar una
 * paráfrasis "casi igual"; lo que la reflexión cierra es "¿me olvidé de
 * traer la cadena nueva a la prueba?", no "¿el texto es el correcto?" — y lo
 * cierra de verdad: si alguien agrega un campo a [TextosCorreccion] sin
 * tocar el mapa de abajo, los dos mapas dejan de tener el mismo tamaño y
 * `cadenas exactas del plan` también se pone rojo.
 */
class TextosCorreccionTest {

    private val todasLasCadenas: List<Pair<String, String>> =
        TextosCorreccion::class.java.declaredFields
            .filter { it.type == String::class.java }
            .onEach { it.isAccessible = true }
            .map { it.name to (it.get(null) as String) }

    @Test
    fun `hay exactamente siete cadenas`() {
        assertEquals(7, todasLasCadenas.size)
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
        val esperadas = mapOf(
            "CORREGIR_VENTA" to "Corregir venta",
            "SE_ESTA_ENVIANDO" to "Se está enviando",
            "YA_SE_ENVIO" to "Ya se envió",
            "LA_REVISA_LA_OFICINA" to "La revisa la oficina",
            "CORRECCION_GUARDADA" to "Corrección guardada",
            "NO_SE_PUDO_GUARDAR" to "No se pudo guardar",
            "GUARDAR_CORRECCION" to "Guardar corrección"
        )
        assertEquals(esperadas, todasLasCadenas.toMap())
    }
}
