package com.example.msp_app.feature.ubicacion.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/**
 * **El lugar tocado queda en el centro de lo que se ve**, no de la pantalla.
 *
 * Los números son los medidos en el SM-A256E (1080 × 2340 px) el 2026-09-27:
 * la hoja empezaba en y ≈ 820 px, los controles tapan hasta ≈ 250 px y la punta
 * del pin quedó en y ≈ 810 px, pegada a la hoja.
 */
class AreaVisibleTest {

    private val area =
        AreaVisible(anchoPx = 1080f, altoPx = 2340f, arribaPx = 250f, hojaPx = 2340f - 820f)

    @Test
    fun `el centro es el de la franja entre los controles y la hoja`() {
        assertEquals(535f, area.centroY, 0.5f)
        assertEquals(540f, area.centroX, 0.5f)
    }

    @Test
    fun `el desplazamiento lleva el pin del borde de la hoja al centro visible`() {
        val (dx, dy) = area.desplazamiento(540f, 810f)
        // scrollBy(dx, dy) mueve el contenido -dy: el pin queda en 810 - dy.
        assertEquals(0f, dx, 0.5f)
        assertEquals(535f, 810f - dy, 0.5f)
    }

    /** Control positivo: la hoja sí cuenta. Sin hoja el centro sería otro. */
    @Test
    fun `sin hoja el centro cambia, asi que la hoja se esta descontando`() {
        val sinHoja = area.copy(hojaPx = 0f)
        assertNotEquals(area.centroY, sinHoja.centroY, 1f)
        assertEquals(250f + (2340f - 250f) / 2f, sinHoja.centroY, 0.5f)
    }
}
