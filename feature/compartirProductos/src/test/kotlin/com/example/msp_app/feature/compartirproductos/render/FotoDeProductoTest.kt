package com.example.msp_app.feature.compartirproductos.render

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import androidx.exifinterface.media.ExifInterface
import com.example.msp_app.core.testing.RobolectricTestBase
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** E-APP-184 (fotos grandes acotadas) y E-APP-185 (rotación EXIF). */
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [33], application = android.app.Application::class)
class FotoDeProductoTest : RobolectricTestBase() {

    @get:Rule val tmp = TemporaryFolder()

    private val rojo = Color.rgb(220, 20, 20)
    private val verde = Color.rgb(20, 200, 20)
    private val azul = Color.rgb(20, 20, 220)
    private val amarillo = Color.rgb(230, 230, 20)

    /** Cuatro cuadrantes: arriba-izq rojo, arriba-der verde, abajo-izq azul, abajo-der amarillo. */
    private fun jpegCuadrantes(ancho: Int, alto: Int, orientacion: Int?): File {
        val b = Bitmap.createBitmap(ancho, alto, Bitmap.Config.ARGB_8888)
        val c = Canvas(b)
        val p = Paint()
        val mx = ancho / 2f
        val my = alto / 2f
        p.color = rojo
        c.drawRect(0f, 0f, mx, my, p)
        p.color = verde
        c.drawRect(mx, 0f, ancho.toFloat(), my, p)
        p.color = azul
        c.drawRect(0f, my, mx, alto.toFloat(), p)
        p.color = amarillo
        c.drawRect(mx, my, ancho.toFloat(), alto.toFloat(), p)
        val f = tmp.newFile()
        f.outputStream().use { b.compress(Bitmap.CompressFormat.JPEG, 95, it) }
        b.recycle()
        if (orientacion != null) {
            val exif = ExifInterface(f.path)
            exif.setAttribute(ExifInterface.TAG_ORIENTATION, orientacion.toString())
            exif.saveAttributes()
        }
        return f
    }

    private fun esquinas(b: Bitmap): List<Int> {
        val m = 12
        return listOf(
            b.getPixel(m, m),
            b.getPixel(b.width - 1 - m, m),
            b.getPixel(m, b.height - 1 - m),
            b.getPixel(b.width - 1 - m, b.height - 1 - m)
        )
    }

    private fun assertColores(esperado: List<Int>, real: List<Int>, nombre: String) {
        esperado.zip(real).forEachIndexed { i, (e, r) ->
            val cerca = Math.abs(Color.red(e) - Color.red(r)) < 40 &&
                Math.abs(Color.green(e) - Color.green(r)) < 40 &&
                Math.abs(Color.blue(e) - Color.blue(r)) < 40
            assertTrue(
                "$nombre esquina $i: esperaba ${Integer.toHexString(
                    e
                )} y fue ${Integer.toHexString(r)}",
                cerca
            )
        }
    }

    // region — E-APP-185 rotación EXIF

    @Test fun `orientacion 6 convierte 800 por 400 en 400 por 800`() {
        val b = checkNotNull(FotoDeProducto.cargar(jpegCuadrantes(800, 400, 6).path))
        assertEquals(400, b.width)
        assertEquals(800, b.height)
    }

    @Test fun `cada orientacion EXIF pone los cuadrantes donde dice el estandar`() {
        // (orientacion, esquinas arriba-izq, arriba-der, abajo-izq, abajo-der, tamano rotado)
        val casos = listOf(
            Triple(1, listOf(rojo, verde, azul, amarillo), false),
            Triple(2, listOf(verde, rojo, amarillo, azul), false),
            Triple(3, listOf(amarillo, azul, verde, rojo), false),
            Triple(4, listOf(azul, amarillo, rojo, verde), false),
            Triple(5, listOf(rojo, azul, verde, amarillo), true),
            Triple(6, listOf(azul, rojo, amarillo, verde), true),
            Triple(7, listOf(amarillo, verde, azul, rojo), true),
            Triple(8, listOf(verde, amarillo, rojo, azul), true)
        )
        casos.forEach { (o, colores, gira) ->
            val b = checkNotNull(FotoDeProducto.cargar(jpegCuadrantes(800, 400, o).path))
            assertEquals("ancho orientación $o", if (gira) 400 else 800, b.width)
            assertEquals("alto orientación $o", if (gira) 800 else 400, b.height)
            assertColores(colores, esquinas(b), "orientación $o")
        }
    }

    @Test fun `sin EXIF la foto queda como esta`() {
        val b = checkNotNull(FotoDeProducto.cargar(jpegCuadrantes(800, 400, null).path))
        assertEquals(800, b.width)
        assertColores(listOf(rojo, verde, azul, amarillo), esquinas(b), "sin exif")
    }
    // endregion

    // region — E-APP-184 fotos grandes

    @Test fun `la muestra es la mayor potencia de 2 que sigue cubriendo 1080 por 1008`() {
        assertEquals(4, FotoDeProducto.muestraPara(8000, 8000)) // 2000×2000, no 4000×4000
        assertEquals(2, FotoDeProducto.muestraPara(4399, 3000)) // 2199×1500
        assertEquals(
            1,
            FotoDeProducto.muestraPara(2159, 3000)
        ) // a la mitad el ancho (1079) ya no cubre
        assertEquals(2, FotoDeProducto.muestraPara(2160, 2016))
        assertEquals(1, FotoDeProducto.muestraPara(1080, 1008))
        assertEquals(1, FotoDeProducto.muestraPara(900, 700))
        assertEquals(1, FotoDeProducto.muestraPara(10000, 1500)) // panorámica: el lado corto manda
        assertEquals(8, FotoDeProducto.muestraPara(9000, 9000))
    }

    @Test fun `la foto decodificada nunca baja de lo que el recorte necesita`() {
        listOf(
            8000 to 8000,
            4399 to 3000,
            3000 to 4399,
            6000 to 1500,
            1200 to 1100,
            5000 to 2100
        ).forEach { (w, h) ->
            val s = FotoDeProducto.muestraPara(w, h)
            val ow = w / s
            val oh = h / s
            if (s > 1) assertTrue("$w×$h → $ow×$oh", ow >= 1080 && oh >= 1008)
            // «de más» no: con una muestra el doble ya no cubriría.
            assertTrue("$w×$h demasiado grande", w / (s * 2) < 1080 || h / (s * 2) < 1008)
        }
    }

    @Test fun `una foto de 4399 por 3000 no se decodifica entera`() {
        val b = checkNotNull(FotoDeProducto.cargar(jpegCuadrantes(4399, 3000, null).path))
        assertTrue("decodificada ${b.width}×${b.height}", b.width <= 2200 && b.height <= 1500 + 1)
        // Y aun así cubre lo que la tarjeta necesita (1080×1008).
        assertTrue(b.width >= 1080 && b.height >= 1008)
    }

    @Test fun `una foto vertical grande tambien se acota y cubre`() {
        val b = checkNotNull(FotoDeProducto.cargar(jpegCuadrantes(3000, 4399, 6).path))
        // Orientación 6: queda 4399 de ancho × 3000 de alto antes de reducir.
        assertTrue(
            "decodificada ${b.width}×${b.height}",
            b.width.toLong() * b.height <= 2200L * 1500L + 1
        )
        assertTrue(b.width >= 1080 && b.height >= 1008)
    }

    @Test fun `una foto chica no se agranda ni se reduce`() {
        val b = checkNotNull(FotoDeProducto.cargar(jpegCuadrantes(900, 700, null).path))
        assertEquals(900, b.width)
        assertEquals(700, b.height)
    }

    @Test fun `rutas malas devuelven null`() {
        assertNull(FotoDeProducto.cargar(null))
        assertNull(FotoDeProducto.cargar(""))
        assertNull(FotoDeProducto.cargar(File(tmp.root, "no.jpg").path))
        assertNull(FotoDeProducto.cargar(tmp.newFile().apply { writeText("x") }.path))
        assertNotNull(FotoDeProducto.cargar(jpegCuadrantes(100, 100, null).path))
    }
    // endregion
}
