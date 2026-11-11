package com.example.msp_app.core.common.busqueda

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** E-APP-157: reglas de texto de la app (español, mayúscula inicial, sin punto final). */
class TextosDeListaTest {

    private val todos = listOf(
        TextosDeLista.ACTUALIZANDO, TextosDeLista.PADRON_VACIO, TextosDeLista.SIN_RESULTADOS,
        TextosDeLista.PARECIDOS, TextosDeLista.PARECIDOS_AVISO, TextosDeLista.SEGUIR_BUSCANDO, TextosDeLista.ERROR_LENTA,
        TextosDeLista.ERROR_SIN_CONEXION, TextosDeLista.ERROR_GENERICO, TextosDeLista.ERROR_LISTA_VACIA, TextosDeLista.ERROR_LISTA_INCOMPLETA, TextosDeLista.FORZAR_ACTUALIZACION,
        TextosDeLista.mostrando(20, 45), TextosDeLista.ultimaActualizacion("14/11/2023 10:13")
    )

    @Test fun `todos arrancan con mayuscula`() = todos.forEach {
        assertTrue("«$it»", it.first { c -> c.isLetter() }.isUpperCase())
    }

    @Test fun `ninguno termina en punto`() = todos.forEach {
        assertFalse("«$it»", it.trimEnd().endsWith("."))
    }

    @Test fun `el aviso de mostrando 20 de N lleva el total`() = assertEquals(
        "Mostrando 20 de 1234 · escribe más para acotar",
        TextosDeLista.mostrando(20, 1234)
    )

    @Test fun `el padron vacio usa el texto pedido`() =
        assertEquals("La lista de clientes aún no se ha descargado", TextosDeLista.PADRON_VACIO)

    @Test fun `el mensaje de lentitud usa el texto pedido`() = assertEquals(
        "No se pudo actualizar: la conexión está lenta. Puedes seguir buscando en la lista guardada",
        TextosDeLista.ERROR_LENTA
    )

    @Test fun `ninguno dice ciclo`() = todos.forEach {
        assertFalse(
            it.lowercase().contains("ciclo")
        )
    }
}
