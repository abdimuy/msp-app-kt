package com.example.msp_app.core.common.text

import org.junit.Assert.assertEquals
import org.junit.Test

class TruncateTest {
    @Test
    fun `string mas corto que el limite se retorna sin cambios`() {
        assertEquals("Juan", "Juan".ellipsize(10))
    }

    @Test
    fun `string mas largo que el limite se recorta y agrega elipsis`() {
        assertEquals("Juan Perez…", "Juan Perez Gonzalez".ellipsize(11))
    }

    @Test
    fun `string igual al limite se retorna sin cambios`() {
        assertEquals("Juan", "Juan".ellipsize(4))
    }

    @Test
    fun `limite en cero retorna string vacio`() {
        assertEquals("", "Juan Perez".ellipsize(0))
    }

    @Test
    fun `limite negativo retorna string vacio`() {
        assertEquals("", "Juan Perez".ellipsize(-5))
    }

    @Test
    fun `limite de uno retorna solo la elipsis`() {
        assertEquals("…", "Juan Perez".ellipsize(1))
    }
}
