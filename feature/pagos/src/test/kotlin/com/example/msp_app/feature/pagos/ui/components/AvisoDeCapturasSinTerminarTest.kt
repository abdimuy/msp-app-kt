package com.example.msp_app.feature.pagos.ui.components

import com.example.msp_app.feature.pagos.domain.port.CapturasSinTerminar
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * E-APP-118 (decisión 4 del dueño 2026-10-20): "Un envío no coincide con la
 * oficina" se muestra también para pagos y condonaciones, no sólo para ventas.
 * Textos aprobados, con mayúscula inicial y sin punto final.
 */
class AvisoDeCapturasSinTerminarTest {

    @Test
    fun `E-APP-118 con un pago que no coincide el aviso lo dice con el texto aprobado`() {
        val lineas =
            lineasDelAviso(CapturasSinTerminar(cuantas = 1, ventaId = 77188, noCoinciden = 1))

        assertEquals(
            listOf("1 captura sin terminar", "Un envío no coincide con la oficina"),
            lineas
        )
    }

    @Test
    fun `E-APP-118 control positivo, sin envios que no coinciden no aparece el texto`() {
        assertEquals(
            listOf("2 capturas sin terminar"),
            lineasDelAviso(CapturasSinTerminar(cuantas = 2, ventaId = null))
        )
    }

    @Test
    fun `sin capturas el aviso no pinta nada`() {
        assertEquals(emptyList<String>(), lineasDelAviso(CapturasSinTerminar.NINGUNA))
    }
}
