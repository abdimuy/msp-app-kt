package com.example.msp_app.feature.ubicacion.ui.components

import android.graphics.Bitmap
import android.graphics.Canvas
import com.example.msp_app.feature.ubicacion.ui.PaletaDelMapa
import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Pinta el panel "Propuesta" del mock con los bitmaps reales de
 * [VariantesDelMarcador] (densidad 2, como el mock renderizado a 2×) y lo deja
 * en `build/marcadores/` para compararlo lado a lado con el mock. No es un
 * golden: la afirmación que sí se cobra es que cada forma dibuja algo.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [33], application = android.app.Application::class)
class LosMarcadoresDelMockTest {

    // Pinta el panel entero del mock, marcador por marcador, para compararlo a ojo.
    @Suppress("LongMethod")
    private fun panel(p: PaletaDelMapa, nombre: String) {
        val v = VariantesDelMarcador(
            colorAro = p.mkRing,
            colorHueco = p.mkHollow,
            colorNumero = android.graphics.Color.WHITE,
            colorPildora = p.ctl,
            colorTintaPildora = p.ink,
            colorDatoPildora = p.mut,
            densidad = 2f,
            envolver = { it }
        )
        val formas = listOf(
            FormaDelMarcador(Figura.DISCO, p.mkFrec, 1),
            FormaDelMarcador(Figura.DISCO, p.mkFrec, 5),
            FormaDelMarcador(Figura.DISCO, p.mkFrec, 21),
            FormaDelMarcador(Figura.DISCO, p.mkFrec, 185),
            FormaDelMarcador(Figura.DISCO, p.mkFrec, 6, resaltado = true),
            FormaDelMarcador(
                Figura.PIN,
                p.mkCasa,
                etiqueta = EtiquetaDelMarcador(
                    "Principal",
                    "21",
                    null,
                    PosicionDeEtiqueta.DERECHA_DEL_PIN
                )
            ),
            FormaDelMarcador(Figura.HUECO, p.mkTran, 3, diametroDp = 26f),
            FormaDelMarcador(Figura.ROMBO, p.mkVis),
            FormaDelMarcador(Figura.DISCO, p.mkComp, 2, diametroDp = 26f),
            FormaDelMarcador(Figura.DISCO, p.mkAnt, 5, diametroDp = 28f),
            FormaDelMarcador(
                Figura.DISCO,
                p.mkFrec,
                6,
                diametroDp = 26f,
                etiqueta = EtiquetaDelMarcador(
                    "Último",
                    "24 sep",
                    p.mkFrec,
                    PosicionDeEtiqueta.ARRIBA
                )
            ),
            FormaDelMarcador(Figura.DISCO, p.mkAnt, 1)
        )
        val celda = 200
        val salida = Bitmap.createBitmap(celda * 6, celda * 2, Bitmap.Config.ARGB_8888)
        val lienzo = Canvas(salida)
        lienzo.drawColor(p.mapBg)
        formas.forEachIndexed { i, f ->
            val (bmp, ax, ay) = v.pintar(f)
            assertTrue("la forma $i salió vacía", bmp.width > 0)
            val cx = (i % 6) * celda + celda / 2f
            val cy = (i / 6) * celda + celda / 2f + if (f.figura == Figura.PIN) 40f else 0f
            val pintura = android.graphics.Paint().apply {
                if (i == 11) alpha = 115 else if (i == 9) alpha = 153
            }
            lienzo.drawBitmap(bmp, cx - ax * bmp.width, cy - ay * bmp.height, pintura)
        }
        val dir = File("build/marcadores").apply { mkdirs() }
        File(
            dir,
            "$nombre.png"
        ).outputStream().use { salida.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Test fun claro() = panel(PaletaDelMapa.CLARO, "marcadores_claro")

    @Test fun oscuro() = panel(PaletaDelMapa.OSCURO, "marcadores_oscuro")
}
