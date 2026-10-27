package com.example.msp_app.feature.pagos.ui

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * E-APP-070 (Review Focus 5): el scroll guardado por una versión con cuatro
 * chips trae nombres que ya no existen (`VOLVER_A_VISITAR`, `SIN_VISITAR`,
 * `YA_NO_ESTA_SEMANA`). Restaurarlo no debe tronar ni inventar una posición.
 */
class ScrollPorSegmentoEstadoViejoTest {

    private fun restaurar(plano: List<Any>): ScrollPorSegmento =
        checkNotNull(ScrollPorSegmento.Saver.restore(plano))

    @Test
    fun `los nombres de los cuatro chips viejos se descartan sin tronar`() {
        val restaurado = restaurar(
            listOf(
                "SIN_VISITAR", 3, 10,
                "VOLVER_A_VISITAR", 4, 0,
                "YA_NO_ESTA_SEMANA", 5, 7,
                "PAGADOS", 2, 1
            )
        )

        // PAGADOS sobrevive con su posición: control positivo de que el
        // restaurador SÍ leyó el plano y no devolvió todo vacío.
        val pagados = restaurado.de(SegmentoDeCobranza.PAGADOS)
        assertEquals(2, pagados.firstVisibleItemIndex)
        assertEquals(1, pagados.firstVisibleItemScrollOffset)

        // Los nombres viejos no se mudaron a una columna nueva: las dos que
        // existen y no estaban guardadas arrancan en el tope.
        assertEquals(0, restaurado.de(SegmentoDeCobranza.POR_VISITAR).firstVisibleItemIndex)
        assertEquals(0, restaurado.de(SegmentoDeCobranza.NO_PAGA).firstVisibleItemIndex)
    }

    @Test
    fun `un plano con tipos equivocados se descarta`() {
        val restaurado = restaurar(listOf("NO_PAGA", "4", 0))
        assertEquals(0, restaurado.de(SegmentoDeCobranza.NO_PAGA).firstVisibleItemIndex)
    }
}
