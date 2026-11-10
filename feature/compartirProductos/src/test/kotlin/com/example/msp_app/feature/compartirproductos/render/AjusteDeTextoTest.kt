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

class RecorteDeFotoTest {

    @Test fun `foto del mismo aspecto no se recorta`() {
        val r = RecorteDeFoto.cubrir(1080, 1008, 1080, 1008)
        assertEquals(RecorteDeFoto(0, 0, 1080, 1008), r)
    }

    @Test fun `foto horizontal pierde costados y se centra`() {
        val r = RecorteDeFoto.cubrir(2000, 1000, 1080, 1008)
        // Ancho visible = 1000 * 1080 / 1008 = 1071; sobran 929 repartidos al centro.
        assertEquals(1071, r.ancho)
        assertEquals(1000, r.alto)
        assertEquals(0, r.arriba)
        assertEquals(464, r.izquierda)
    }

    @Test fun `foto vertical pierde arriba y abajo con foco al 60 por ciento`() {
        val r = RecorteDeFoto.cubrir(1000, 2000, 1080, 1008)
        // Alto visible = 1000 * 1008 / 1080 = 933; sobran 1067, el 60 % arriba.
        assertEquals(933, r.alto)
        assertEquals(1000, r.ancho)
        assertEquals(640, r.arriba)
        assertEquals(0, r.izquierda)
    }

    @Test fun `el recorte siempre queda dentro de la foto`() {
        listOf(
            1 to 1,
            10 to 10000,
            10000 to 10,
            4000 to 3000,
            3000 to 4000,
            1080 to 1920,
            1920 to 1080
        ).forEach { (w, h) ->
            listOf(1008 to 1080, 1128 to 1080).forEach { (dh, dw) ->
                val r = RecorteDeFoto.cubrir(w, h, dw, dh)
                assertEquals(
                    "$w×$h",
                    true,
                    r.izquierda >= 0 && r.arriba >= 0 && r.derecha <= w && r.abajo <= h
                )
                assertEquals("$w×$h", true, r.ancho >= 1 && r.alto >= 1)
            }
        }
    }

    @Test fun `el recorte conserva la proporcion del destino`() {
        val r = RecorteDeFoto.cubrir(4000, 3000, 1080, 1008)
        assertEquals(1080.0 / 1008.0, r.ancho.toDouble() / r.alto, 0.01)
    }

    @Test fun `medidas invalidas se rechazan`() {
        assertThrows(IllegalArgumentException::class.java) { RecorteDeFoto.cubrir(0, 10, 10, 10) }
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
