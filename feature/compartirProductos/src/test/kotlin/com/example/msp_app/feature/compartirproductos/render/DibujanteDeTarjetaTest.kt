package com.example.msp_app.feature.compartirproductos.render

import android.graphics.Bitmap
import android.graphics.Color
import com.example.msp_app.feature.compartirproductos.domain.SeleccionDePrecios
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.robolectric.RuntimeEnvironment

/**
 * E-APP-181. Pruebas del dibujo con texto real (Skia nativo): tamaño, zona segura, que nada
 * se salga del área, el recorte de la foto y que la escala de fuente del sistema
 * no cambie nada. Los PNG de referencia salen de `TarjetasDeReferenciaTest`.
 */
class DibujanteDeTarjetaTest : RenderBase() {

    /** El último renglón visible: Android rellena lo recortado con U+FEFF (invisible), que aquí no cuenta. */
    private fun ultimaLinea(layout: android.text.StaticLayout): String {
        val i = layout.lineCount - 1
        return layout.text.subSequence(
            layout.getLineStart(i),
            layout.getLineEnd(i)
        ).toString().replace("\uFEFF", "").trimEnd()
    }

    private val margen = Medidas.MARGEN_LATERAL
    private val derechaDelTexto = (Medidas.ANCHO - Medidas.MARGEN_LATERAL).toFloat()
    private val limiteAbajo = (Medidas.ALTO - Medidas.ZONA_SEGURA_ABAJO).toFloat()

    @Test fun `las fuentes empaquetadas cargan y no se cae al sistema`() {
        assertTrue(
            "Instrument Serif y Geist deben salir de assets/fonts",
            dibujante.fuentesCompletas
        )
    }

    @Test fun `la imagen mide 1080 por 1920`() {
        val b = dibujante.dibujar(tarjeta(), fotoConFranjas(1600, 1000, vertical = false))
        assertEquals(1080, b.width)
        assertEquals(1920, b.height)
    }

    @Test fun `el fondo de la tarjeta es f4f1ec`() {
        val b = dibujante.dibujar(tarjeta(), null)
        assertEquals(Color.parseColor("#f4f1ec"), b.getPixel(3, Medidas.ALTO - 3))
        assertEquals(Color.parseColor("#f4f1ec"), b.getPixel(Medidas.ANCHO - 3, Medidas.ALTO - 3))
    }

    // region — zona segura y límites

    private fun verificarLimites(
        etiqueta: String,
        t: com.example.msp_app.feature.compartirproductos.domain.TarjetaDeProducto,
        foto: Bitmap?
    ) {
        val d = dibujante.disposicion(t, foto != null)
        d.cajas.forEach { caja ->
            assertTrue(
                "$etiqueta/${caja.nombre} se sale por la izquierda",
                caja.izquierda >= margen - 0.5f
            )
            assertTrue(
                "$etiqueta/${caja.nombre} se sale por la derecha: ${caja.derecha}",
                caja.derecha <= derechaDelTexto + 0.5f
            )
            assertTrue(
                "$etiqueta/${caja.nombre} invade la foto: ${caja.arriba} < ${d.fotoAlto}",
                caja.arriba >= d.fotoAlto
            )
            assertTrue(
                "$etiqueta/${caja.nombre} baja a ${caja.abajo}, pasa de $limiteAbajo",
                caja.abajo <= limiteAbajo + 0.5f
            )
        }
        assertTrue("$etiqueta: foto mínima", d.fotoAlto >= Medidas.FOTO_ALTO_MINIMO)
        assertTrue("$etiqueta: todo cupo con la foto en su mínimo", d.cabe)
        assertTrue("$etiqueta: título ≤ 2 renglones", d.titulo.lineCount <= 2)
        assertTrue("$etiqueta: detalle ≤ 3 renglones", (d.detalle?.lineCount ?: 0) <= 3)
        assertTrue("$etiqueta: tamaño del título", d.tituloTamano in 60f..110f)
        // Las cajas no se encimen entre sí (de arriba abajo).
        val ordenadas = d.cajas.sortedBy { it.arriba }
        ordenadas.zipWithNext().forEach { (a, b) ->
            assertTrue(
                "$etiqueta: ${a.nombre} (${a.abajo}) pisa a ${b.nombre} (${b.arriba})",
                a.abajo <= b.arriba + 0.5f
            )
        }
    }

    @Test fun `ningun texto se sale del area con nombres cortos largos y extremos`() {
        val nombres = listOf(
            "SALA",
            "SALA VALENCIA TERCIOPELO",
            "RECAMARA TOSCANA MATRIMONIAL C/CABECERA TAPIZADA, BURO DOBLE Y COMODA DE 6 CAJONES",
            "COMEDOR ROBLE NATURAL 6 SILLAS C/CRISTAL TEMPLADO, BASE METALICA, TAPIZADO EN VINIL NEGRO, ACABADO MATE ANTIDERRAPANTE",
            // El peor caso real: título de dos renglones, detalle de tres y las tres filas de precios.
            "COMEDOR ROBLE NATURAL 6 SILLAS C/CRISTAL TEMPLADO, BASE METALICA, TAPIZADO EN VINIL NEGRO, " +
                "ACABADO MATE ANTIDERRAPANTE, ENVIO E INSTALACION SIN COSTO EN LA ZONA METROPOLITANA DE LA CIUDAD",
            "RECAMARA TOSCANA MATRIMONIAL ROBLE NATURAL, " + "TERCIOPELO VERDE OSCURO CON COSTURAS DORADAS ".repeat(8),
            "A".repeat(300),
            "PALABRA ".repeat(40),
            ("X".repeat(150) + ", " + "Y".repeat(150)),
            "SOFA-CAMA " + "SUPERCALIFRAGILISTICO".repeat(8),
            "ÑANDÚ ÁRBOL ÓPTIMO ÜBER ÉPICO ÍNTEGRO ".repeat(8),
            ""
        )
        nombres.forEachIndexed { i, nombre ->
            val t = tarjeta(articulo(nombre = nombre))
            verificarLimites("nombre#$i", t, fotoConFranjas(1600, 1000, vertical = false))
            verificarLimites("nombre#$i/sin foto", t, null)
        }
    }

    @Test fun `ninguna combinacion de precios se sale del area ni invade la zona segura`() {
        val casillas = listOf(true, false)
        for (c in casillas) for (s in casillas) for (cr in casillas) {
            val t = tarjeta(
                articulo(
                    nombre = "RECAMARA TOSCANA MATRIMONIAL C/CABECERA TAPIZADA, BURO DOBLE Y COMODA DE 6 CAJONES Y ESPEJO",
                    contado = "99999999",
                    corto = "1250000",
                    credito = "1250000"
                ),
                SeleccionDePrecios(c, s, cr)
            )
            verificarLimites("precios $c/$s/$cr", t, fotoConFranjas(1000, 1600, vertical = true))
        }
    }

    @Test fun `con una sola fila de precios el monto grande cabe`() {
        val t = tarjeta(articulo(contado = "1250000"), SeleccionDePrecios(true, false, false))
        verificarLimites("solo contado", t, null)
        val d = dibujante.disposicion(t, false)
        val fila = d.filas.single()
        val ancho = fila.montoPaint.measureText(fila.precio.texto) + fila.etiquetaPaint.measureText(fila.precio.fila.etiqueta)
        assertTrue(ancho < Medidas.ANCHO - 2 * margen)
    }

    @Test fun `ningun pixel fuera de los margenes en la zona de texto ni dentro de la zona segura inferior`() {
        val t =
            tarjeta(
                articulo(
                    nombre = "RECAMARA TOSCANA MATRIMONIAL C/CABECERA TAPIZADA, BURO DOBLE Y COMODA DE 6 CAJONES"
                )
            )
        val b = dibujante.dibujar(t, fotoConFranjas(1600, 1000, vertical = false))
        val d = dibujante.disposicion(t, true)
        val fondo = Color.parseColor("#f4f1ec")
        // Laterales en toda la zona de texto: a 40 px del borde no hay nada (el margen es 67; los
        // 27 px de diferencia dejan sitio a lo que una letra sobresale de su caja).
        val holgura = 40
        for (y in d.fotoAlto until Medidas.ALTO) {
            for (x in 0 until holgura) assertEquals("x=$x y=$y", fondo, b.getPixel(x, y))
            for (x in Medidas.ANCHO - holgura until Medidas.ANCHO) assertEquals(
                "x=$x y=$y",
                fondo,
                b.getPixel(x, y)
            )
        }
        // Zona segura inferior: ni un pixel dibujado.
        for (y in Medidas.ALTO - Medidas.ZONA_SEGURA_ABAJO + 1 until Medidas.ALTO step 3) {
            for (x in 0 until Medidas.ANCHO step 3) assertEquals(
                "x=$x y=$y",
                fondo,
                b.getPixel(x, y)
            )
        }
    }

    @Test fun `sin foto la zona segura superior queda vacia y la marca esta debajo del seis por ciento`() {
        val t = tarjeta()
        val b = dibujante.dibujar(t, null)
        val sinFoto = Color.parseColor("#e8e3da")
        for (y in 0 until Medidas.ZONA_SEGURA_ARRIBA step 2) {
            for (x in 0 until Medidas.ANCHO step 3) assertEquals(
                "x=$x y=$y",
                sinFoto,
                b.getPixel(x, y)
            )
        }
        assertTrue(Medidas.LOGO_ARRIBA >= Medidas.ZONA_SEGURA_ARRIBA)
        // Y la marca sí se ve donde debe: hay tinta en el logo.
        var hayTinta = false
        for (y in Medidas.LOGO_ARRIBA until Medidas.LOGO_ARRIBA + Medidas.LOGO_LADO) {
            for (x in Medidas.MARGEN_LATERAL until Medidas.MARGEN_LATERAL + Medidas.LOGO_LADO) {
                if (b.getPixel(x, y) != sinFoto) hayTinta = true
            }
        }
        assertTrue("el logo debe dibujarse", hayTinta)
    }
    // endregion

    // region — título y detalle

    @Test fun `un titulo corto va a 110 y uno larguisimo baja al minimo con puntos suspensivos`() {
        val corto = dibujante.disposicion(tarjeta(articulo(nombre = "SALA")), false)
        assertEquals(110f, corto.tituloTamano, 0f)
        assertEquals(1, corto.titulo.lineCount)
        val extremo = dibujante.disposicion(
            tarjeta(articulo(nombre = "PALABRA ".repeat(40))),
            false
        )
        assertEquals(60f, extremo.tituloTamano, 0f)
        assertEquals(2, extremo.titulo.lineCount)
        assertTrue(
            "el último renglón debe terminar en …: «${ultimaLinea(extremo.titulo)}»",
            ultimaLinea(extremo.titulo).endsWith("…")
        )
    }

    @Test fun `un titulo mediano reduce el tamano pero cabe en dos renglones sin recortarse`() {
        val d = dibujante.disposicion(
            tarjeta(articulo(nombre = "RECAMARA TOSCANA MATRIMONIAL ROBLE NATURAL")),
            false
        )
        assertTrue("tamaño ${d.tituloTamano}", d.tituloTamano < 110f && d.tituloTamano > 60f)
        assertEquals(2, d.titulo.lineCount)
        assertFalse(d.titulo.text.toString().contains("…"))
    }

    @Test fun `una palabra de 300 letras sin espacios se corta en dos renglones con puntos suspensivos y no se sale`() {
        val d = dibujante.disposicion(tarjeta(articulo(nombre = "A".repeat(300))), false)
        assertEquals(2, d.titulo.lineCount)
        for (i in 0 until d.titulo.lineCount) assertTrue(
            d.titulo.getLineRight(i) <= Medidas.ANCHO - 2 * margen + 0.5f
        )
    }

    @Test fun `un detalle de seis renglones se corta en tres y termina en puntos suspensivos`() {
        val nombre = "SALA, " + "TERCIOPELO VERDE OSCURO CON COSTURAS DORADAS ".repeat(8)
        val d = dibujante.disposicion(tarjeta(articulo(nombre = nombre)), true)
        val detalle = checkNotNull(d.detalle)
        assertEquals(3, detalle.lineCount)
        assertTrue("«${ultimaLinea(detalle)}»", ultimaLinea(detalle).endsWith("…"))
    }

    @Test fun `un detalle corto no se recorta`() {
        val d = dibujante.disposicion(tarjeta(articulo(nombre = "SALA C/PUFF")), true)
        assertFalse(checkNotNull(d.detalle).text.toString().contains("…"))
    }

    @Test fun `sin separador no hay detalle`() {
        assertEquals(
            null,
            dibujante.disposicion(tarjeta(articulo(nombre = "SALA VALENCIA")), true).detalle
        )
    }

    @Test fun `la categoria se omite cuando no hay dato`() {
        assertEquals(
            null,
            dibujante.disposicion(tarjeta(articulo(categoria = null)), true).categoria
        )
        assertEquals(
            "SALAS",
            dibujante.disposicion(
                tarjeta(articulo(categoria = "salas")),
                true
            ).categoria?.text.toString()
        )
    }
    // endregion

    // region — precios dibujados

    @Test fun `las filas salen en orden contado corto credito y solo las marcadas`() {
        val todas = dibujante.disposicion(tarjeta(), true)
        assertEquals(
            listOf("Contado", "Corto plazo", "Crédito"),
            todas.filas.map {
                it.precio.fila.etiqueta
            }
        )
        assertEquals(listOf("$8,990", "$10,450", "$13,200"), todas.filas.map { it.precio.texto })
        assertTrue(todas.bloqueDePrecios!!.conCaja)
        val dos = dibujante.disposicion(
            tarjeta(seleccion = SeleccionDePrecios(true, false, true)),
            true
        )
        assertEquals(listOf("Contado", "Crédito"), dos.filas.map { it.precio.fila.etiqueta })
        assertTrue(dos.bloqueDePrecios!!.conCaja)
    }

    @Test fun `una sola fila va sin caja blanca como en el diseno de nombre largo`() {
        val d = dibujante.disposicion(
            tarjeta(seleccion = SeleccionDePrecios(true, false, false)),
            true
        )
        assertEquals(1, d.filas.size)
        assertFalse(d.bloqueDePrecios!!.conCaja)
    }

    @Test fun `sin precios no hay bloque y la tarjeta conserva titulo detalle y pie`() {
        val t =
            tarjeta(
                articulo(nombre = "SALA VALENCIA C/PUFF"),
                SeleccionDePrecios(false, false, false)
            )
        val d = dibujante.disposicion(t, true)
        assertEquals(null, d.bloqueDePrecios)
        assertTrue(d.filas.isEmpty())
        verificarLimites("sin precios", t, null)
        assertEquals("Precios vigentes al 10 nov 2026", d.pie.text.toString())
    }

    @Test fun `la caja blanca se dibuja en blanco y los montos quedan alineados a la derecha`() {
        val t = tarjeta()
        val b = dibujante.dibujar(t, null)
        val d = dibujante.disposicion(t, false)
        val bloque = d.bloqueDePrecios!!
        // Dentro de la caja, a la izquierda de las etiquetas (que empiezan 43 px adentro), todo es blanco.
        assertTrue(
            cerca(
                Color.WHITE,
                b.getPixel(Medidas.MARGEN_LATERAL + 20, bloque.arriba + bloque.alto / 2),
                2
            )
        )
        // Fuera de la caja, a la izquierda, sigue el fondo.
        assertTrue(
            cerca(
                Color.parseColor("#f4f1ec"),
                b.getPixel(Medidas.MARGEN_LATERAL - 10, bloque.arriba + bloque.alto / 2),
                2
            )
        )
        // Alineación a la derecha: en cada fila, lo último que se pinta termina a la misma altura
        // del borde interior de la caja (der - 43 = 970), con ±12 px de aire propio de la letra.
        val derechaInterior = Medidas.ANCHO - Medidas.MARGEN_LATERAL - Medidas.BLOQUE_RELLENO_LATERAL
        d.filas.forEach { fila ->
            val ultimoX = ultimoPixelNoBlanco(b, fila.arriba, fila.arriba + fila.alto)
            assertTrue(
                "${fila.precio.fila}: termina en $ultimoX, esperaba ~$derechaInterior",
                ultimoX in (derechaInterior - 12)..(derechaInterior + 2)
            )
        }
    }

    /** La columna más a la derecha (dentro de la caja) con tinta, entre las filas [desde] y [hasta]. */
    private fun ultimoPixelNoBlanco(b: Bitmap, desde: Int, hasta: Int): Int {
        var ultimoX = 0
        for (y in desde until hasta) {
            val ultimoEnFila = (Medidas.ANCHO / 2 until Medidas.ANCHO - Medidas.MARGEN_LATERAL - 2).lastOrNull { x ->
                !cerca(
                    Color.WHITE,
                    b.getPixel(x, y),
                    40
                )
            }
            if (ultimoEnFila != null) ultimoX = maxOf(ultimoX, ultimoEnFila)
        }
        return ultimoX
    }

    @Test fun `las cifras son tabulares los digitos de un monto miden lo mismo`() {
        val d = dibujante.disposicion(tarjeta(), false)
        val p = d.filas.first().montoPaint
        val anchos = "0123456789".map { p.measureText(it.toString()) }
        assertEquals("Geist trae cifras tabulares", 1, anchos.map { Math.round(it) }.toSet().size)
    }
    // endregion

    // region — foto

    @Test fun `foto horizontal se recorta por los costados y llena el area sin deformarse`() {
        val foto = fotoConFranjas(2000, 1000, vertical = false)
        val b = dibujante.dibujar(tarjeta(), foto)
        val d = dibujante.disposicion(tarjeta(), true)
        val y = d.fotoAlto / 2
        // Recorte centrado: el 0..10 % de la foto (franja 0) queda fuera; el borde izquierdo es la franja 2 (464/2000 = 23 %).
        assertTrue(cerca(colorDeFranja(2), b.getPixel(2, y), 8))
        assertTrue(cerca(colorDeFranja(7), b.getPixel(Medidas.ANCHO - 3, y), 8))
        // El centro del recorte es el centro de la foto: frontera entre franja 4 y 5.
        assertTrue(cerca(colorDeFranja(4), b.getPixel(Medidas.ANCHO / 2 - 6, y), 8))
        assertTrue(cerca(colorDeFranja(5), b.getPixel(Medidas.ANCHO / 2 + 6, y), 8))
    }

    @Test fun `foto vertical se recorta arriba y abajo con foco al sesenta por ciento`() {
        val foto = fotoConFranjas(1000, 2000, vertical = true)
        val b = dibujante.dibujar(tarjeta(), foto)
        val d = dibujante.disposicion(tarjeta(), true)
        // Recorte: filas 640..1573 de la foto → franja 3 al inicio (640/2000 = 32 %) y franja 7 al final (78 %).
        assertTrue(cerca(colorDeFranja(3), b.getPixel(Medidas.ANCHO / 2, 160), 8))
        assertTrue(cerca(colorDeFranja(7), b.getPixel(Medidas.ANCHO / 2, d.fotoAlto - 4), 8))
        // La franja 0 (techo de la foto) no aparece en ningún renglón.
        for (y in 0 until d.fotoAlto step 7) assertFalse(
            cerca(colorDeFranja(0), b.getPixel(Medidas.ANCHO - 5, y), 3)
        )
    }

    @Test fun `la foto llena todo el ancho y el alto de su area`() {
        val foto = fotoConFranjas(1200, 1200, vertical = false)
        val b = dibujante.dibujar(tarjeta(), foto)
        val d = dibujante.disposicion(tarjeta(), true)
        val fondo = Color.parseColor("#f4f1ec")
        for (x in listOf(0, 540, 1079)) {
            // Evita la banda de la marca (esquina superior izquierda) leyendo abajo.
            assertNotEquals(fondo, b.getPixel(x, d.fotoAlto - 2))
            assertNotEquals(fondo, b.getPixel(x, d.fotoAlto - 40))
        }
    }

    @Test fun `el area de la foto con foto mide 52 por ciento y sin foto crece`() {
        val corta = tarjeta(articulo(nombre = "SALA"), SeleccionDePrecios(true, false, false))
        assertEquals(1008, dibujante.disposicion(corta, true).fotoAlto)
        assertEquals(1128, dibujante.disposicion(corta, false).fotoAlto)
        // Con las tres filas el texto pide más sitio y la foto cede, sin pasar de su máximo.
        assertTrue(dibujante.disposicion(tarjeta(), false).fotoAlto in 1008..1128)
    }

    @Test fun `si el texto crece la foto cede pero no baja del cuarenta por ciento`() {
        val chica = dibujante.disposicion(tarjeta(articulo(nombre = "SALA")), true).fotoAlto
        val grande = dibujante.disposicion(
            tarjeta(
                articulo(
                    nombre = "RECAMARA, " + "TERCIOPELO VERDE OSCURO CON COSTURAS DORADAS ".repeat(8),
                    categoria = "RECAMARAS"
                )
            ),
            true
        ).fotoAlto
        assertTrue(grande <= chica)
        assertTrue(grande >= 768)
    }

    @Test fun `sin foto el area es beige con monograma MSP en cursiva y sin texto adicional`() {
        val b = dibujante.dibujar(tarjeta(), null)
        val d = dibujante.disposicion(tarjeta(), false)
        assertTrue(cerca(Color.parseColor("#e8e3da"), b.getPixel(10, d.fotoAlto - 10), 2))
        // Hay pixeles del color del monograma #cfc7ba y están en la mitad central.
        var cuenta = 0
        var minX = Medidas.ANCHO
        var maxX = 0
        for (y in 300 until d.fotoAlto - 300 step 2) for (x in 0 until Medidas.ANCHO step 2) {
            if (cerca(Color.parseColor("#cfc7ba"), b.getPixel(x, y), 4)) {
                cuenta++
                minX = minOf(minX, x)
                maxX = maxOf(maxX, x)
            }
        }
        assertTrue("monograma presente ($cuenta px)", cuenta > 500)
        assertTrue(
            "monograma centrado: $minX..$maxX",
            Math.abs((minX + maxX) / 2 - Medidas.ANCHO / 2) < 60
        )
    }
    // endregion

    // region — determinismo, tipografía del sistema y tamaño

    @Test fun `la misma tarjeta dibujada dos veces es identica pixel por pixel`() {
        val t = tarjeta(articulo(nombre = "RECAMARA TOSCANA C/CABECERA TAPIZADA"))
        val a = dibujante.dibujar(t, fotoConFranjas(1600, 1000, vertical = false))
        val b = dibujante.dibujar(t, fotoConFranjas(1600, 1000, vertical = false))
        assertTrue(a.sameAs(b))
    }

    @Test fun `la escala de fuente y la densidad del sistema no cambian la tarjeta`() {
        val t =
            tarjeta(
                articulo(nombre = "RECAMARA TOSCANA MATRIMONIAL C/CABECERA TAPIZADA, BURO DOBLE")
            )
        val normal = dibujante.dibujar(t, null)
        RuntimeEnvironment.setFontScale(2.0f)
        RuntimeEnvironment.setQualifiers("+xxxhdpi")
        val otro = DibujanteDeTarjeta(contexto).dibujar(t, null)
        assertTrue(
            "la tarjeta es una imagen fija: no depende de fontScale ni de densidad",
            normal.sameAs(otro)
        )
    }

    @Test fun `el JPEG es calidad 88 y pesa lo razonable`() {
        val real = jpeg(dibujante.dibujar(tarjeta(), fotoConFranjas(1600, 1000, vertical = false)))
        assertTrue("JPEG válido (FFD8)", real[0] == 0xFF.toByte() && real[1] == 0xD8.toByte())
        assertTrue("pesa ${real.size}", real.size in 20_000..600_000)
        // Peor caso: foto de ruido puro. Aun así no pasa de un tamaño que WhatsApp reciba bien.
        val ruido = jpeg(dibujante.dibujar(tarjeta(), fotoConRuido(1080, 1008)))
        assertTrue("con ruido pesa ${ruido.size}", ruido.size < 1_500_000)
    }

    @Test fun `el JPEG se puede decodificar de vuelta a 1080 por 1920`() {
        val bytes = jpeg(dibujante.dibujar(tarjeta(), null))
        val de = android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        assertEquals(1080, de.width)
        assertEquals(1920, de.height)
    }
    // endregion
}
