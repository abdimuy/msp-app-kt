package com.example.msp_app.feature.pagos.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.example.msp_app.core.designsystem.theme.FontSizeLevel
import com.example.msp_app.core.designsystem.theme.LocalFontSizeLevel
import com.example.msp_app.core.designsystem.theme.MspTheme
import com.example.msp_app.core.testing.RobolectricTestBase
import com.example.msp_app.feature.pagos.domain.model.UbicacionDelCobro
import com.example.msp_app.feature.pagos.ui.components.FondoDeLaPuerta
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * **Entre el mapa y el nombre no puede quedar una banda muerta.**
 *
 * ## El defecto que esto cobra, con los dp que lo reportaron
 *
 * El dueño lo vio en el aparato con la versión que sí pinta teselas: *"el blur
 * del nombre y la dirección está demasiado arriba, debe estar más abajo pegado
 * al nombre"*. Medido sobre su captura (1080×2340, 3 px/dp): el mapa empezaba a
 * apagarse a ~130 dp, quedaba **completamente negro a ~200** y el nombre no
 * empezaba hasta ~222. Esos ~22 dp no eran ni mapa ni telón — eran **un hueco**,
 * y un hueco se lee como una pantalla mal armada.
 *
 * Nada lo cobraba. Los tests del telón miraban textos y bordes, y **la caja de
 * layout del telón no cambia** cuando el degradado arranca antes o después: lo
 * que cambia son los píxeles. Por eso esto se fotografía.
 *
 * ## Cómo se mide, y por qué con un mapa de color plano
 *
 * En la ranura del suelo entra un rectángulo de un color que no existe en el
 * tema. Eso permite preguntarle a la imagen dos cosas sin ambigüedad:
 *
 *  - **hasta qué renglón se ve el mapa** — el último con rastro de ese color, o
 *    sea donde el degradado terminó de cerrar;
 *  - **dónde empieza el nombre** — el primer renglón con tinta por debajo.
 *
 * Si entre los dos hay más de [HUECO_MAXIMO] px, hay banda muerta.
 *
 * ## Y la otra mitad: que tampoco haya canto
 *
 * Pegar el degradado al texto arregla el hueco y abre el defecto contrario —una
 * línea donde el desenfoque empieza—. Así que se afirma también que el color
 * del mapa **se apaga de forma gradual**: se cuentan los renglones en los que
 * está a medias, y tienen que ser varios. Con un corte seco serían cero o uno.
 *
 * **Las dos aserciones juntas son el test.** Cualquiera sola se puede satisfacer
 * rompiendo la otra.
 */
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(
    sdk = [33],
    qualifiers = "w360dp-h800dp-xhdpi",
    application = android.app.Application::class
)
class ElTelonNoDejaHuecoTest : RobolectricTestBase() {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun `el velo no empieza mucho antes del nombre`() {
        val imagen = pinta(FontSizeLevel.NORMAL)
        val perfil = perfilDelMapa(imagen)
        val arranque = inicioDeLaRampa(perfil)
        val nombre = renglonDelNombre(imagen, perfil)

        assertTrue(
            "el velo empieza a morder el mapa en el renglón $arranque y el nombre está en " +
                "$nombre: son ${nombre - arranque} px de franja antes del texto, y el mapa " +
                "debería verse limpio casi hasta tocarlo",
            nombre - arranque <= ANTES_DEL_NOMBRE
        )
    }

    /**
     * **Y el mapa se apaga con una rampa, no con un corte.**
     *
     * Es la mitad que impide "arreglar" el hueco pegando el degradado al texto:
     * eso dejaría una línea visible donde el velo empieza, que es el canto que
     * el telón existe para no tener.
     *
     * Se mide el **salto más brusco** del perfil, no cuántos renglones están a
     * medias: desde que el velo es translúcido la meseta también está a medias,
     * y contarlos daba alto con canto y sin él.
     */
    @Test
    fun `el mapa se apaga con una rampa, no con un corte`() {
        val imagen = pinta(FontSizeLevel.NORMAL)
        val perfil = perfilDelMapa(imagen)
        val salto = saltoMaximo(perfil, renglonDelNombre(imagen, perfil))

        assertTrue(
            "el perfil del mapa cae $salto de un renglón al siguiente: eso es un canto, " +
                "no una disolución",
            salto <= SALTO_MAXIMO
        )
    }

    /**
     * **El mapa se sigue distinguiendo DETRÁS del nombre.**
     *
     * Tercer defecto del mismo viaje al aparato: *"en el que pusiste no se ve el
     * mapa de atrás, ni en el nombre ni en las notificaciones… debe sólo verse
     * un poco blur el mapa, se debe distinguir el mapa de atrás"*. El velo era
     * una pared, no un cristal.
     *
     * ## Lo que NO prueba, y hay que decirlo
     *
     * Que las **calles** se reconozcan. Eso depende del radio del desenfoque y
     * acá el mapa es un color plano: un plano desenfocado sigue siendo el mismo
     * plano. Lo que esto cierra es la mitad medible —que el velo dejó de
     * tapar— y la otra mitad se juzga en el aparato, que es donde el mapa
     * pinta. El golden de este módulo **no puede enseñarlo**: excluye el mapa a
     * propósito para ser determinista.
     */
    @Test
    fun `el mapa se sigue distinguiendo detras del nombre`() {
        val imagen = pinta(FontSizeLevel.NORMAL)
        val perfil = perfilDelMapa(imagen)
        val rastro = perfil[renglonDelNombre(imagen, perfil)] / perfil.max()

        assertTrue(
            "en el renglón del nombre queda el $rastro del color del mapa: el velo volvió " +
                "a ser una pared y lo de atrás dejó de verse",
            rastro > RASTRO_TRAS_EL_NOMBRE
        )
    }

    /** Lo mismo a 2.0, que es donde el telón crece y la rampa se podría perder. */
    @Test
    fun `a escala muy grande tampoco queda hueco ni canto`() {
        val imagen = pinta(FontSizeLevel.MUY_GRANDE)
        val perfil = perfilDelMapa(imagen)
        val nombre = renglonDelNombre(imagen, perfil)

        assertTrue(
            "a 2.0 el velo empieza ${nombre - inicioDeLaRampa(perfil)} px antes del nombre",
            nombre - inicioDeLaRampa(perfil) <= ANTES_DEL_NOMBRE
        )
        assertTrue(
            "a 2.0 el perfil cae ${saltoMaximo(perfil, nombre)} de golpe",
            saltoMaximo(perfil, nombre) <= SALTO_MAXIMO
        )
    }

    // --- Andamio -------------------------------------------------------------

    private fun pinta(nivel: FontSizeLevel): Bitmap {
        composeTestRule.setContent {
            CompositionLocalProvider(
                LocalDensity provides Density(DENSIDAD, nivel.nominalScale),
                LocalFontSizeLevel provides nivel
            ) {
                MspTheme(darkTheme = false, animateColors = false) {
                    Box(modifier = Modifier.fillMaxSize().background(MspTheme.colors.background)) {
                        FondoDeLaPuerta(
                            ubicacion = PUNTO,
                            calle = CALLE,
                            nombre = NOMBRE,
                            direccion = CALLE,
                            avance = { 0f },
                            desplazamientoPx = { 0f },
                            sinMovimiento = true,
                            insetDeArriba = 0.dp,
                            // El "mapa": un color plano que no existe en el
                            // tema, para poder preguntarle a la imagen hasta
                            // dónde se ve sin confundirlo con nada más.
                            suelo = { Box(Modifier.fillMaxSize().background(MAPA)) }
                        )
                    }
                }
            }
        }
        composeTestRule.waitForIdle()
        val vista: View = composeTestRule.activity.findViewById(android.R.id.content)
        val mapa = Bitmap.createBitmap(vista.width, vista.height, Bitmap.Config.ARGB_8888)
        vista.draw(Canvas(mapa))
        return mapa
    }

    /**
     * **El perfil del mapa: cuánto de su color sobrevive en cada renglón.**
     *
     * Se mide por **croma** —lo que distingue al magenta del fondo claro y de
     * la tinta— y se toma el MÁXIMO del renglón, no el promedio: así el texto
     * escrito encima no arrastra la medición hacia abajo. Lo que se quiere
     * saber es cuánto velo hay, no cuántas letras.
     */
    private fun perfilDelMapa(imagen: Bitmap): FloatArray = FloatArray(imagen.height) { y ->
        var mayor = 0f
        for (x in 0 until imagen.width step MUESTREO) {
            val px = imagen.getPixel(x, y)
            val r = (px shr 16) and 0xFF
            val g = (px shr 8) and 0xFF
            val b = px and 0xFF
            val cuanto = ((r + b) / 2f - g) / MAXIMO
            if (cuanto > mayor) mayor = cuanto
        }
        mayor
    }

    /**
     * El renglón donde el velo **empieza** a morder el mapa.
     *
     * Relativo al máximo que el mapa alcanza en esta imagen y no a un absoluto:
     * el croma del color depende del color, y atar el test a un número fijo lo
     * rompería el día que alguien cambie el magenta de prueba.
     */
    private fun inicioDeLaRampa(perfil: FloatArray): Int {
        val entero = perfil.max() * ENTERO
        return perfil.indices.firstOrNull { perfil[it] < entero } ?: perfil.lastIndex
    }

    /**
     * El salto más brusco del perfil, de un renglón al siguiente.
     *
     * **Esto es lo que detecta un canto.** Una disolución reparte la caída
     * entre decenas de renglones; un corte la concentra en uno. Contar
     * "renglones a medias" no servía: desde que el velo es translúcido, la
     * meseta también está a medias y el conteo sale alto con canto y sin él.
     */
    private fun saltoMaximo(perfil: FloatArray, hasta: Int): Float {
        var mayor = 0f
        for (y in 0 until hasta) {
            val caida = perfil[y] - perfil[y + 1]
            if (caida > mayor) mayor = caida
        }
        return mayor
    }

    /**
     * El renglón donde está el nombre: el primer texto por debajo de donde la
     * rampa ya empezó.
     *
     * Se busca **desde el arranque de la rampa** y no desde arriba porque la
     * textura tipográfica del fondo también es tinta, y es lo primero que hay.
     */
    private fun renglonDelNombre(imagen: Bitmap, perfil: FloatArray): Int {
        for (y in inicioDeLaRampa(perfil) until imagen.height) {
            for (x in 0 until imagen.width step MUESTREO) {
                if (luminancia(imagen.getPixel(x, y)) < TINTA) return y
            }
        }
        return imagen.height - 1
    }

    private fun luminancia(px: Int): Float {
        val r = (px shr 16) and 0xFF
        val g = (px shr 8) and 0xFF
        val b = px and 0xFF
        return ROJO * r + VERDE * g + AZUL * b
    }

    private companion object {
        const val DENSIDAD = 2f
        const val MUESTREO = 2
        const val MAXIMO = 255f

        /**
         * El "mapa": magenta claro.
         *
         * Claro y no puro a propósito: el texto se detecta por **luminancia** y
         * el mapa por **croma**, así que los dos umbrales tienen que quedar
         * lejos. Un magenta saturado tiene luminancia 105 y se confundiría con
         * la tinta del nombre.
         */
        val MAPA = Color(0xFFFF66FF)

        val PUNTO = UbicacionDelCobro(lat = 18.4609, lng = -97.3926)
        const val NOMBRE = "Victoria Flores Olmedo"
        const val CALLE = "C. Miguel Hidalgo y Costilla 214 Col. Emiliano Zapata, Tehuacán"

        /** Por encima de esta fracción del máximo el mapa está sin tocar. */
        const val ENTERO = 0.9f

        /**
         * Cuánto color del mapa tiene que sobrevivir en el renglón del nombre.
         *
         * Un cuarto del máximo. Con la meseta del velo en 0.36 sobrevive ~0.64,
         * así que hay sitio de sobra para que la sombra del texto y el antialias
         * muerdan algo sin poner esto rojo por ruido. Con el velo opaco de antes
         * el valor era ~0.
         */
        const val RASTRO_TRAS_EL_NOMBRE = 0.25f

        /**
         * Cuánta franja de velo se tolera por encima del nombre.
         *
         * 40 px a densidad 2 son 20 dp. Lo reportado eran ~92 dp de rampa antes
         * del texto: esto se habría puesto rojo con holgura.
         */
        const val ANTES_DEL_NOMBRE = 40

        /**
         * Cuánto puede caer el perfil de un renglón al siguiente.
         *
         * 0.04 del croma. Una rampa de 12 dp reparte la caída en 24 renglones,
         * o sea ~0.025 cada uno; un corte la haría de golpe.
         */
        const val SALTO_MAXIMO = 0.04f

        const val TINTA = 100f

        const val ROJO = 0.299f
        const val VERDE = 0.587f
        const val AZUL = 0.114f
    }
}
