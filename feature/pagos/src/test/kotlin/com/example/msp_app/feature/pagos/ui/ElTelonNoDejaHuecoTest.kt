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
    fun `en oscuro el velo no empieza mucho antes del nombre`() =
        noEmpiezaMuchoAntes(FontSizeLevel.NORMAL, oscuro = true)

    @Test
    fun `en claro el velo no empieza mucho antes del nombre`() =
        noEmpiezaMuchoAntes(FontSizeLevel.NORMAL, oscuro = false)

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
    fun `en oscuro el mapa se apaga con una rampa, no con un corte`() =
        seApagaConRampa(FontSizeLevel.NORMAL, oscuro = true)

    @Test
    fun `en claro el mapa se apaga con una rampa, no con un corte`() =
        seApagaConRampa(FontSizeLevel.NORMAL, oscuro = false)

    /**
     * **En OSCURO el mapa se sigue distinguiendo detrás del nombre.**
     *
     * Tercer defecto del mismo viaje al aparato: *"en el que pusiste no se ve el
     * mapa de atrás, ni en el nombre ni en las notificaciones… debe sólo verse
     * un poco blur el mapa, se debe distinguir el mapa de atrás"*. El velo era
     * una pared, no un cristal.
     *
     * **Sólo en oscuro desde el 2026-09-25**, y el par que forma con
     * [`en claro el velo sí tapa el mapa detrás del nombre`] es deliberado: los
     * dos temas quieren cosas **opuestas** y la razón está en
     * `VELO_EN_CLARO`. Que esto ya no se pida en claro no es una exigencia
     * relajada — es la misma exigencia contestada al revés, y la contraria está
     * escrita al lado para que nadie "arregle" una rompiendo la otra sin verlo.
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
    fun `en oscuro el mapa se sigue distinguiendo detras del nombre`() {
        val imagen = pinta(FontSizeLevel.NORMAL, oscuro = true)
        val perfil = perfilDelMapa(imagen)
        val rastro = perfil[renglonDelNombre(imagen, perfil, oscuro = true)] / perfil.max()

        assertTrue(
            "en el renglón del nombre queda el $rastro del color del mapa: el velo volvió " +
                "a ser una pared y lo de atrás dejó de verse",
            rastro > RASTRO_TRAS_EL_NOMBRE
        )
    }

    /**
     * **Y en CLARO el velo SÍ tapa el mapa detrás del nombre — lo contrario.**
     *
     * El dueño lo pidió así el 2026-09-25 mirando el mapa real de Google: *"en
     * el modo claro se confunden las letras… en modo claro que el fondo en vez
     * de blur sea más tipo blanco"*. Sobre un mapa claro, un velo translúcido no
     * quita el ruido: deja los rótulos de las calles compitiendo con la
     * dirección, que es lo que él vio.
     *
     * Así que acá se afirma el techo, no el piso: del color del mapa tiene que
     * quedar **menos** de [RASTRO_MAXIMO_EN_CLARO] donde va el texto.
     *
     * ## Y sigue sin poder ser una pared
     *
     * El otro lado lo cobran las dos pruebas de arriba, que corren en los **dos**
     * temas: si alguien sube el velo a 1.0 para "asegurar" la legibilidad, la
     * rampa se queda sin recorrido y `el mapa se apaga con una rampa, no con un
     * corte` se pone roja en claro. Las tres juntas dejan una sola franja de
     * valores válidos.
     */
    @Test
    fun `en claro el velo si tapa el mapa detras del nombre`() {
        val imagen = pinta(FontSizeLevel.NORMAL, oscuro = false)
        val perfil = perfilDelMapa(imagen)
        val rastro = perfil[renglonDelNombre(imagen, perfil, oscuro = false)] / perfil.max()

        assertTrue(
            "en claro, en el renglón del nombre queda el $rastro del color del mapa: los " +
                "rótulos de las calles siguen compitiendo con la dirección",
            rastro < RASTRO_MAXIMO_EN_CLARO
        )
    }

    /** Lo mismo a 2.0, que es donde el telón crece y la rampa se podría perder. */
    @Test
    fun `a escala muy grande en oscuro tampoco queda hueco`() =
        noEmpiezaMuchoAntes(FontSizeLevel.MUY_GRANDE, oscuro = true)

    @Test
    fun `a escala muy grande en claro tampoco queda hueco`() =
        noEmpiezaMuchoAntes(FontSizeLevel.MUY_GRANDE, oscuro = false)

    @Test
    fun `a escala muy grande en oscuro tampoco queda canto`() =
        seApagaConRampa(FontSizeLevel.MUY_GRANDE, oscuro = true)

    @Test
    fun `a escala muy grande en claro tampoco queda canto`() =
        seApagaConRampa(FontSizeLevel.MUY_GRANDE, oscuro = false)

    // --- Las dos afirmaciones, una vez cada una ------------------------------
    //
    // Viven acá y no adentro de cada `@Test` porque cada una se tiene que correr
    // en los DOS temas y a DOS escalas, y `createAndroidComposeRule` sólo admite
    // un `setContent` por prueba: un bucle adentro del test muere con
    // "Cannot call setContent twice per test!". Así que la combinatoria se
    // reparte en métodos y el cuerpo se escribe una sola vez.

    private fun noEmpiezaMuchoAntes(nivel: FontSizeLevel, oscuro: Boolean) {
        val imagen = pinta(nivel, oscuro)
        val perfil = perfilDelMapa(imagen)
        val arranque = inicioDeLaRampa(perfil)
        val nombre = renglonDelNombre(imagen, perfil, oscuro)
        val tope = if (oscuro) ANTES_DEL_NOMBRE else ANTES_DEL_NOMBRE_EN_CLARO

        assertTrue(
            "en ${tema(oscuro)} a ${nivel.nominalScale} el velo empieza a morder el mapa en " +
                "el renglón $arranque y el nombre está en $nombre: son ${nombre - arranque} " +
                "px de franja antes del texto (el tope es $tope), y el mapa debería verse " +
                "limpio casi hasta tocarlo",
            nombre - arranque <= tope
        )
    }

    private fun seApagaConRampa(nivel: FontSizeLevel, oscuro: Boolean) {
        val imagen = pinta(nivel, oscuro)
        val perfil = perfilDelMapa(imagen)
        val salto = saltoMaximo(perfil, renglonDelNombre(imagen, perfil, oscuro))

        assertTrue(
            "en ${tema(oscuro)} a ${nivel.nominalScale} el perfil del mapa cae $salto de un " +
                "renglón al siguiente: eso es un canto, no una disolución",
            salto <= SALTO_MAXIMO
        )
    }

    private fun tema(oscuro: Boolean) = if (oscuro) "oscuro" else "claro"

    // --- Andamio -------------------------------------------------------------

    private fun pinta(nivel: FontSizeLevel, oscuro: Boolean): Bitmap {
        composeTestRule.setContent {
            CompositionLocalProvider(
                LocalDensity provides Density(DENSIDAD, nivel.nominalScale),
                LocalFontSizeLevel provides nivel
            ) {
                MspTheme(darkTheme = oscuro, animateColors = false) {
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
    /**
     * El umbral es de **doble filo, y el tema decide cuál**: en claro el texto
     * es casi negro sobre gris claro, así que es tinta lo que está **por debajo**
     * de [TINTA]; en oscuro es blanco, así que lo que hay que buscar es lo que
     * está **por encima** de [TINTA_EN_OSCURO].
     *
     * Los dos números están lejos del magenta de prueba (luminancia 165) a
     * propósito: buscar "lo oscuro" en tema oscuro daría el fondo entero en el
     * primer renglón, y buscar "lo claro" con un umbral bajo daría el mapa. El
     * control positivo de que esto mide lo que dice es que las dos pruebas de
     * rastro —una pidiendo que el mapa sobreviva, la otra que no— se apoyan en
     * este mismo renglón y salen distintas.
     */
    private fun renglonDelNombre(imagen: Bitmap, perfil: FloatArray, oscuro: Boolean): Int {
        for (y in inicioDeLaRampa(perfil) until imagen.height) {
            if (hayTinta(imagen, y, oscuro)) return y
        }
        return imagen.height - 1
    }

    private fun hayTinta(imagen: Bitmap, y: Int, oscuro: Boolean): Boolean =
        (0 until imagen.width step MUESTREO).any { x ->
            val luz = luminancia(imagen.getPixel(x, y))
            if (oscuro) luz > TINTA_EN_OSCURO else luz < TINTA
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
         * Cuánto color del mapa tiene que sobrevivir **en oscuro** en el renglón
         * del nombre.
         *
         * Un cuarto del máximo. Con la meseta del velo en 0.36 sobrevive ~0.64,
         * así que hay sitio de sobra para que la sombra del texto y el antialias
         * muerdan algo sin poner esto rojo por ruido. Con el velo opaco de antes
         * el valor era ~0.
         */
        const val RASTRO_TRAS_EL_NOMBRE = 0.25f

        /**
         * Y en claro, cuánto color del mapa **NO** puede quedar ahí.
         *
         * Es el techo gemelo del piso de arriba, y apunta al lado contrario a
         * propósito — ver las dos pruebas de rastro. Con la meseta en 0.82
         * sobrevive ~0.18; 0.25 deja el margen del antialias sin permitir que
         * alguien devuelva el velo translúcido en claro y siga verde.
         */
        const val RASTRO_MAXIMO_EN_CLARO = 0.25f

        /**
         * Cuánta franja de velo se tolera por encima del nombre.
         *
         * **90 px a densidad 2 son 45 dp, y eran 40 px (20 dp).** El tope subió
         * el 2026-09-25 porque el dueño movió la queja al lado contrario: con la
         * rampa apretada a 12 dp le pareció *"muy pronunciada y rápida"*, así
         * que el fade pasó a 48 dp con la subida en ese. Medido con ese reparto,
         * el velo empieza a morder **76 px (38 dp) antes del nombre a 1.0 y 83
         * (41.5 dp) a 2.0**; 90 deja un margen chico a propósito, para que el
         * siguiente que lo alargue tenga que venir acá a decirlo.
         *
         * Sigue cobrando el defecto original con holgura: lo reportado entonces
         * eran **~92 dp (184 px)** de rampa antes del texto, el doble de esto.
         *
         * Y no queda solo: el que impide alargar el fade sin límite es
         * [RASTRO_TRAS_EL_NOMBRE] por un lado —el velo no puede cerrar sobre el
         * mapa— y el ojo del dueño por el otro, que es quien fija este número.
         */
        const val ANTES_DEL_NOMBRE = 90

        /**
         * Lo mismo **en claro**, donde el fade es más largo a propósito.
         *
         * El velo en claro sube a 0.82 en vez de 0.36, y para que la pendiente
         * por dp no empeore el recorrido tiene que crecer con él: 80 dp de fade
         * contra 48. Ver `FADE_EN_CLARO`, que es donde está la cuenta.
         *
         * Que sean **dos números y no uno** es lo que impide el atajo cómodo:
         * subir el tope único hasta que los dos temas quepan escondería que uno
         * de los dos se alargó sin que nadie lo decidiera.
         */
        const val ANTES_DEL_NOMBRE_EN_CLARO = 150

        /**
         * Cuánto puede caer el perfil de un renglón al siguiente.
         *
         * **0.012 del croma, y eran 0.04.** El tope bajó junto con el arreglo
         * que lo hizo posible: desde que la subida del velo es una ese
         * (`rampaSuave`) en vez de una recta, la caída medida es de
         * **0.00784 = 2/255** por renglón, a 1.0 y a 2.0 — o sea **el piso de
         * cuantización de un canal de 8 bits**. Sobre este panel no existe una
         * rampa más suave que ésta.
         *
         * 0.012 son ~3/255: el margen justo para el ruido del antialias sin
         * dejar sitio a que alguien vuelva a meter una recta corta sin enterarse.
         */
        const val SALTO_MAXIMO = 0.012f

        const val TINTA = 100f

        /**
         * El umbral de tinta **en tema oscuro**, donde el texto es blanco.
         *
         * 210, y tiene que quedar por encima de la luminancia del magenta de
         * prueba (**165**): con un umbral más bajo, "el primer renglón con
         * tinta" sería el primer renglón de mapa y toda la medición se correría
         * hacia arriba sin ponerse roja.
         */
        const val TINTA_EN_OSCURO = 210f

        const val ROJO = 0.299f
        const val VERDE = 0.587f
        const val AZUL = 0.114f
    }
}
