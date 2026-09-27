package com.example.msp_app.feature.ubicacion.ui.components

import android.graphics.Bitmap
import android.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * **El costo crece con las FORMAS, no con los marcadores.**
 *
 * ## Qué afirmación fija, y por qué no basta con leer el código
 *
 * `MarkerComposable` infla un `ComposeView` **por cada marcador**: lo cuelga del
 * `MapView`, lo mide, lo posiciona y lo dibuja a un `Canvas`, sincrónico y en el
 * hilo principal. Con 40 puntos —medido el 2026-09-24, el 44 % de los clientes
 * tiene 21 o más— son 40 inflados de vista para dibujar 40 cosas casi iguales.
 *
 * El caché de variantes existe para que sean **ocho, pase lo que pase con el
 * número de puntos**. Eso es una afirmación **estructural**, y una afirmación
 * estructural se comprueba **contando, no cronometrando**: un cronómetro bajo
 * Robolectric no dice nada de un teléfono, pero *"cuántos bitmaps se dibujaron"*
 * es el mismo número en los dos.
 *
 * `@GraphicsMode(NATIVE)` porque sin gráficos nativos `Bitmap` y `Canvas` de
 * Robolectric no hacen trabajo real y la prueba mediría un simulacro — ver
 * `E-INF-003` en `msp-api/docs/evidencia/infra.md`.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [33], application = android.app.Application::class)
class ElCacheDibujaPorFormaTest {

    /**
     * El envoltorio de prueba: en vez de un `BitmapDescriptor` —que exige Play
     * Services— devuelve la **medida** del bitmap.
     *
     * No debilita nada: el bitmap se dibuja de verdad con `Canvas` nativo antes
     * de llegar aquí, y lo que la prueba cuenta es **cuántas veces se dibujó**.
     * Lo único que se sustituye es el envoltorio del SDK de Google, que no
     * participa de la afirmación. De paso, exigir `width > 0` cierra la puerta a
     * que un bitmap vacío pase por bueno.
     */
    private fun medidaDe(bitmap: Bitmap): String {
        require(bitmap.width > 0 && bitmap.height > 0) { "el bitmap salió vacío" }
        return "bmp-${bitmap.width}x${bitmap.height}"
    }

    private fun variantes(aro: Int = Color.WHITE) = VariantesDelMarcador(
        colorAro = aro,
        colorHueco = Color.WHITE,
        colorNumero = Color.WHITE,
        colorPildora = Color.WHITE,
        colorTintaPildora = Color.BLACK,
        colorDatoPildora = Color.GRAY,
        densidad = 2f,
        envolver = ::medidaDe
    )

    @Test
    fun `cuarenta marcadores de una sola forma dibujan UN bitmap`() {
        val v = variantes()
        val forma = FormaDelMarcador(Figura.DISCO, Color.BLUE, conteo = 1)
        val primero = v.de(forma)
        repeat(39) { v.de(forma) }
        assertEquals("se dibujó más de una vez la misma forma", 1, v.dibujados)
        assertSame("el caché devolvió otra entrada", primero, v.de(forma))
    }

    @Test
    fun `un cliente cargado no pasa de ocho bitmaps`() {
        // El peor caso realista: 40 mediciones repartidas entre las formas que
        // la pantalla sabe pintar (disco/aro × resaltado/no × 1 o 2 cobros).
        val v = variantes()
        val formas = listOf(Figura.DISCO, Figura.HUECO).flatMap { figura ->
            listOf(true, false).flatMap { resaltado ->
                listOf(
                    1,
                    2
                ).map { conteo ->
                    FormaDelMarcador(
                        figura,
                        Color.BLUE,
                        conteo,
                        resaltado = resaltado
                    )
                }
            }
        }
        repeat(40) { i -> v.de(formas[i % formas.size]) }
        assertEquals("el catálogo de formas creció sin querer", 8, formas.size)
        assertEquals("se dibujó un bitmap por marcador, no por forma", 8, v.dibujados)
        assertTrue(
            "40 marcadores costaron más de 8 dibujos: el caché no está cortando",
            v.dibujados < 40
        )
    }

    @Test
    fun `cambiar de tema obliga a un cache nuevo`() {
        // La paleta es de construcción, así que un tema distinto es OTRO caché.
        // Es lo que impide que al pasar de claro a oscuro queden los bitmaps del
        // tema anterior — el `remember` de `recordarVariantes` lleva la paleta.
        val claro = variantes()
        val oscuro = variantes(aro = Color.BLACK)
        val forma = FormaDelMarcador(Figura.DISCO, Color.BLUE, conteo = 3)
        claro.de(forma)
        oscuro.de(forma)
        assertEquals(1, claro.dibujados)
        assertEquals(1, oscuro.dibujados)
    }
}
