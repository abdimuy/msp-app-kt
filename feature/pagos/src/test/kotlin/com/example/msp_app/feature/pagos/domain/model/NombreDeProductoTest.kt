package com.example.msp_app.feature.pagos.domain.model

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * **La única regla que convierte `ARTICULO` en texto de usuario** (`task-2-brief.md`).
 *
 * Microsip guarda el nombre del producto en MAYÚSCULAS. Lo que aquí se prueba
 * es que la conversión es SENTENCIA —una mayúscula inicial y el resto en
 * minúsculas— y no "Title Case" por palabra, que capitalizaría también "King"
 * y "Chocolate" como si fueran nombres propios.
 */
class NombreDeProductoTest {

    /** El nombre exacto del caso real que originó la tarea. */
    @Test
    fun `una frase completa en mayusculas baja a sentencia`() {
        assertEquals(
            "Recamara cantaro king size chocolate",
            NombreDeProducto.normaliza("RECAMARA CANTARO KING SIZE CHOCOLATE")
        )
    }

    /** El otro nombre del caso real, con comillas dentro del literal. */
    @Test
    fun `las comillas dentro del nombre no se tocan`() {
        assertEquals(
            "Bocina profesional 8'' audiobahn",
            NombreDeProducto.normaliza("BOCINA PROFESIONAL 8'' AUDIOBAHN")
        )
    }

    /** Espacios sueltos al borde no deberían quedar en el texto de usuario. */
    @Test
    fun `el espacio sobrante al borde se recorta`() {
        assertEquals(
            "Base de cama matrimonial",
            NombreDeProducto.normaliza("  BASE DE CAMA MATRIMONIAL  ")
        )
    }

    /** Control positivo: un nombre de una sola palabra no queda distinto. */
    @Test
    fun `una sola palabra tambien baja a sentencia`() {
        assertEquals("Refrigerador", NombreDeProducto.normaliza("REFRIGERADOR"))
    }
}
