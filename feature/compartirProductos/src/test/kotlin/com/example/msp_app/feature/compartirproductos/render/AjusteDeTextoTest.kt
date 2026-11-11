package com.example.msp_app.feature.compartirproductos.render

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class AjusteDeTextoTest {

    @Test fun `si cabe al maximo se queda en 110`() {
        assertEquals(110f, AjusteDeTexto.tamanoDeTitulo { 1 }, 0f)
        assertEquals(110f, AjusteDeTexto.tamanoDeTitulo { 2 }, 0f)
    }

    @Test fun `baja de dos en dos hasta el primer tamano que cabe`() {
        // A 110 px pide 4 renglones; cada 2 px menos pierde un cuarto de renglon: cabe a 100.
        val pedidos = mutableListOf<Float>()
        val t = AjusteDeTexto.tamanoDeTitulo { tam ->
            pedidos += tam
            if (tam > 100f) 3 else 2
        }
        assertEquals(100f, t, 0f)
        assertEquals(110f, pedidos.first(), 0f)
        assertEquals(100f, pedidos.last(), 0f)
        assertEquals(6, pedidos.size) // 110, 108, 106, 104, 102, 100
    }

    @Test fun `el minimo es 60 aunque nunca quepa`() {
        assertEquals(60f, AjusteDeTexto.tamanoDeTitulo { 9 }, 0f)
    }

    @Test fun `no pide tamanos por debajo del minimo ni por encima del maximo`() {
        val vistos = mutableListOf<Float>()
        AjusteDeTexto.tamanoDeTitulo { tam ->
            vistos += tam
            9
        }
        assertEquals(110f, vistos.max(), 0f)
        assertEquals(true, vistos.min() > 60f) // el minimo se devuelve sin medirlo
    }

    @Test fun `cabe justo en el minimo`() {
        assertEquals(60f, AjusteDeTexto.tamanoDeTitulo { tam -> if (tam > 60f) 3 else 2 }, 0f)
    }

    @Test fun `respeta el maximo de renglones pedido`() {
        assertEquals(110f, AjusteDeTexto.tamanoDeTitulo(maxLineas = 1) { 1 }, 0f)
        assertEquals(60f, AjusteDeTexto.tamanoDeTitulo(maxLineas = 1) { 2 }, 0f)
    }

    @Test fun `rango invalido se rechaza`() {
        assertThrows(IllegalArgumentException::class.java) {
            AjusteDeTexto.tamanoDeTitulo(maximo = 50f, minimo = 60f) { 1 }
        }
        assertThrows(
            IllegalArgumentException::class.java
        ) { AjusteDeTexto.tamanoDeTitulo(paso = 0f) { 1 } }
    }
}

class PalabraEnCursivaTest {
    @Test fun `la ultima palabra significativa va en cursiva`() {
        val t = "Sofá Valencia terciopelo"
        assertEquals("terciopelo", t.substring(PalabraEnCursiva.rango(t)!!))
    }

    @Test fun `una sola palabra no lleva cursiva`() {
        assertEquals(null, PalabraEnCursiva.rango("Colchón"))
    }

    @Test fun `palabras cortas de relleno o con cifras no llevan cursiva`() {
        assertEquals(null, PalabraEnCursiva.rango("Sala para"))
        assertEquals(null, PalabraEnCursiva.rango("Sala 3 Piezas 6C"))
        assertEquals(null, PalabraEnCursiva.rango("Cama Rey"))
    }

    @Test fun `la puntuacion final no entra en la cursiva`() {
        val t = "Sala Valencia grande,"
        assertEquals("grande", t.substring(PalabraEnCursiva.rango(t)!!))
    }
}
