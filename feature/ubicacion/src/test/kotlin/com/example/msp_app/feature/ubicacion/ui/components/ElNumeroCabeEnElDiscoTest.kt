package com.example.msp_app.feature.ubicacion.ui.components

import android.graphics.Bitmap
import android.graphics.Color
import kotlin.math.hypot
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * **El número cabe en el disco, a la densidad del SM-A256E.**
 *
 * ## El defecto que guarda
 *
 * `pintar` calculaba `textSize = max(minimo, radio) * densidad * proporcion`
 * con `radio` **ya en píxeles**: la densidad entraba dos veces. En el aparato
 * (densidad 3) el número salía de ~105 px en un disco de 78 px. Revertir el
 * arreglo pone esta prueba en rojo.
 *
 * ## Cómo mide
 *
 * `@GraphicsMode(NATIVE)` para que `Canvas` dibuje de verdad. El aro se pinta
 * **verde** y el número **rojo** para que la tinta del número sea separable del
 * aro blanco del producto. Se cuentan los píxeles "rojos" (R alto, G y B
 * bajos): su alto tiene que ser ≤ 45 % del diámetro y todos tienen que caer
 * dentro del disco. **Control positivo:** que haya tinta — sin él, un número
 * que no se dibuja pasaría por bueno.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [33], application = android.app.Application::class)
class ElNumeroCabeEnElDiscoTest {

    private val densidad = 3f

    private fun variantes() = VariantesDelMarcador(
        colorAro = Color.GREEN,
        colorHueco = Color.WHITE,
        colorNumero = Color.RED,
        colorPildora = Color.WHITE,
        colorTintaPildora = Color.BLACK,
        colorDatoPildora = Color.GRAY,
        densidad = densidad,
        envolver = { it }
    )

    private fun esTinta(c: Int) =
        Color.red(c) > 160 && Color.green(c) < 90 && Color.blue(c) < 90 && Color.alpha(c) > 200

    private fun revisa(conteo: Int) {
        val forma = FormaDelMarcador(Figura.DISCO, Color.BLUE, conteo)
        val (bmp: Bitmap, ax, ay) = variantes().pintar(forma)
        val cx = ax * bmp.width
        val cy = ay * bmp.height
        val diametroPx = forma.diametroDp * densidad
        var arriba = Int.MAX_VALUE
        var abajo = Int.MIN_VALUE
        var tinta = 0
        var fuera = 0
        for (y in 0 until bmp.height) for (x in 0 until bmp.width) {
            if (!esTinta(bmp.getPixel(x, y))) continue
            tinta++
            arriba = minOf(arriba, y)
            abajo = maxOf(abajo, y)
            if (hypot(x + 0.5f - cx, y + 0.5f - cy) > diametroPx / 2) fuera++
        }
        assertTrue("control positivo: el número $conteo no dejó tinta", tinta > 20)
        val alto = abajo - arriba + 1
        assertTrue(
            "el número $conteo mide $alto px en un disco de $diametroPx px (tope 45 %)",
            alto <= diametroPx * 0.45f
        )
        assertTrue("el número $conteo se sale del disco: $fuera px fuera", fuera == 0)
    }

    @Test fun `un cobro`() = revisa(1)

    @Test fun `seis cobros`() = revisa(6)

    @Test fun `veintiuno en el tope de 38 dp`() = revisa(21)

    @Test fun `ciento ochenta y cinco cabe`() = revisa(185)
}
