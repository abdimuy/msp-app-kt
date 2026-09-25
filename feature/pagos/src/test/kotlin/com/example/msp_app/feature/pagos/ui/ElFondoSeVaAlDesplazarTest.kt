package com.example.msp_app.feature.pagos.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.setValue
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
import com.example.msp_app.feature.pagos.ui.components.RECORRIDO_DEL_FONDO
import kotlin.math.abs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * **El fondo se va con el desplazamiento — medido en píxeles, no en intención.**
 *
 * ## Por qué esto se fotografía y no se afirma con `getBoundsInRoot`
 *
 * Porque **nada de lo que hace esta animación mueve la caja de layout**. El
 * paralaje, el encogimiento, el desenfoque y el apagado viven todos dentro de un
 * bloque `graphicsLayer`, que es dibujo: la geometría del nodo es idéntica a
 * cero y a mitad del recorrido. Una prueba que midiera bordes daría **verde con
 * la animación entera borrada** — que es exactamente la clase de verde que este
 * repo existe para no producir.
 *
 * Lo que sí cambia son los píxeles. Así que se capturan tres estados del mismo
 * fondo y se comparan entre ellos. Cada aserción de abajo se pone **roja** si se
 * revierte la pieza que le toca, y eso se comprobó revirtiéndolas a propósito
 * una por una:
 *
 * | Si se borra… | Se pone rojo |
 * |---|---|
 * | el apagado (`alpha`) | `al final del recorrido el fondo ya no esta` **y** `con movimiento reducido…` |
 * | el paralaje | `a mitad del recorrido el fondo subio` |
 * | el respaldo de movimiento reducido | `con movimiento reducido no se desliza…` |
 * | todo el bloque | los tres |
 *
 * ## Y el `@GraphicsMode(NATIVE)` no es opcional
 *
 * Sin él, Robolectric no rasteriza y **no hay píxeles que leer**: las capturas
 * saldrían vacías y todas las comparaciones darían lo mismo. Está medido aparte,
 * en `ElRenderEffectSeAplicaDeVerdadTest`, que además confirma que este
 * renderizador sí aplica `RenderEffect` — o sea que el desenfoque de esta
 * animación queda guardado por los goldens y no sólo por la buena fe.
 *
 * ## Lo que NO se prueba acá
 *
 * El mapa de verdad: vive en `:app` y necesita red y GL. Lo que se fotografía es
 * la capa que este módulo dibuja —la seña de la puerta—, que es la que recibe
 * exactamente el mismo tratamiento.
 */
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(
    sdk = [33],
    qualifiers = "w360dp-h800dp-xhdpi",
    application = android.app.Application::class
)
class ElFondoSeVaAlDesplazarTest : RobolectricTestBase() {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    /** Cuánto se ha desplazado el contenido, como estado: se mueve entre capturas. */
    private val avance = mutableFloatStateOf(0f)

    @Test
    fun `sin desplazar, el fondo se ve entero`() {
        monta(sinMovimiento = false)
        val quieto = pinta(0f)

        assertTrue(
            "el fondo no pintó un solo píxel de tinta sin desplazar: no hay nada que medir " +
                "y las comparaciones de abajo no probarían nada",
            tinta(quieto) > 0
        )
    }

    /**
     * **Al final del recorrido el fondo está apagado del todo.**
     *
     * No "casi": cero tinta. Si quedara tinta, el fondo seguiría asomando detrás
     * del encabezado compacto que entra justo ahí, y las dos capas se leerían
     * una encima de la otra.
     *
     * **Se monta con el movimiento reducido a propósito**, y no es un descuido:
     * con el paralaje puesto el fondo ya salió de su propia caja al final del
     * recorrido, así que la tinta daría cero **aunque nadie lo hubiera
     * apagado**. Apagando el deslizamiento, lo único que puede hacer
     * desaparecer la tinta es el `alpha` — que es lo que este test existe para
     * cobrar. Se comprobó: borrando el apagado, esto se pone rojo.
     */
    @Test
    fun `al final del recorrido el fondo ya no esta`() {
        monta(sinMovimiento = true)
        val ido = pinta(1f)

        assertEquals(
            "al terminar el recorrido el fondo todavía pinta tinta: no se apagó",
            0,
            tinta(ido)
        )
    }

    /**
     * **A mitad del recorrido el fondo SUBIÓ** — eso es el paralaje.
     *
     * Se mide el renglón más bajo con tinta. Va a la mitad y no al final a
     * propósito: al final el fondo está apagado y no hay nada que localizar, así
     * que un test del paralaje puesto ahí no distinguiría "se deslizó" de "se
     * apagó".
     *
     * El umbral es holgado —[SUBIDA_MINIMA] px— para no atarse al dp exacto: lo
     * que se afirma es que **se movió hacia arriba**, no cuánto. La fracción
     * exacta (0.42 de la velocidad del contenido) vive en una constante con su
     * KDoc; lo que una prueba tiene que impedir es que desaparezca.
     */
    @Test
    fun `a mitad del recorrido el fondo subio`() {
        monta(sinMovimiento = false)
        val quieto = pinta(0f)
        val medio = pinta(MITAD)

        val antes = ultimoRenglonConTinta(quieto)
        val despues = ultimoRenglonConTinta(medio)
        assertTrue(
            "la tinta arrancaba en el renglón $antes y a mitad del recorrido arranca en " +
                "$despues: el fondo no se deslizó",
            antes - despues >= SUBIDA_MINIMA
        )
    }

    /**
     * **Con movimiento reducido no se desliza**, y el fondo sigue apagándose.
     *
     * Las dos mitades importan. Que no se deslice es el respaldo que pide el
     * principio 13 del brief —la señal la combina `rememberMspReducedMotion`, que
     * mira el ajuste del sistema **y** la casilla de Configuración—. Que siga
     * apagándose es lo que impide "arreglarlo" congelando el fondo: sin el
     * apagado, el mapa se quedaría debajo del encabezado compacto para siempre.
     *
     * Un crossfade es exactamente lo que el design system permite en este caso
     * (*crossfade o instantáneo*), así que no se apaga el apagado.
     */
    @Test
    fun `con movimiento reducido no se desliza, pero si se apaga`() {
        monta(sinMovimiento = true)
        val quieto = pinta(0f)
        val medio = pinta(0.5f)

        // Con **tolerancia de un par de píxeles**, y no por flojera: con
        // movimiento reducido lo único que cambia es el alpha, y a media
        // opacidad la última fila del degradado del telón cae por debajo del
        // umbral de tinta. Medido: 554 quieto contra 553 a media carrera. Lo
        // que este test tiene que distinguir es un deslizamiento real —a mitad
        // del recorrido son ~96 px—, y [ROCE] deja pasar el antialias sin
        // dejar pasar eso.
        val corrimiento = abs(ultimoRenglonConTinta(quieto) - ultimoRenglonConTinta(medio))
        assertTrue(
            "con movimiento reducido el fondo se corrió $corrimiento px: eso es justo lo " +
                "que el ajuste apaga",
            corrimiento <= ROCE
        )
        assertTrue(
            "con movimiento reducido el fondo dejó de apagarse: entonces se queda debajo " +
                "del encabezado compacto que entra encima",
            tinta(medio) < tinta(quieto)
        )
    }

    // --- Andamio -------------------------------------------------------------

    /**
     * Pinta el fondo solo, sobre blanco, y devuelve la imagen.
     *
     * Sin mapa: la ranura se deja vacía a propósito, porque el mapa de verdad
     * necesita red y GL. Lo que queda es la seña de la puerta, que recibe el
     * mismo tratamiento y **sí** se puede fotografiar.
     */
    private fun monta(sinMovimiento: Boolean) {
        composeTestRule.setContent {
            CompositionLocalProvider(LocalFontSizeLevel provides FontSizeLevel.NORMAL) {
                Lienzo {
                    FondoDeLaPuerta(
                        ubicacion = PUNTO,
                        calle = CALLE,
                        nombre = NOMBRE,
                        direccion = CALLE,
                        // Los dos se leen del MISMO estado, igual que en la
                        // pantalla: el paralaje es lineal con el desplazamiento
                        // y lo demás sigue la curva.
                        avance = { avance.floatValue },
                        desplazamientoPx = { avance.floatValue * RECORRIDO_DEL_FONDO.value * DENSIDAD },
                        sinMovimiento = sinMovimiento,
                        insetDeArriba = 0.dp
                    )
                }
            }
        }
    }

    /**
     * Rasteriza la pantalla tal como está, con el desplazamiento puesto en
     * [cuanto].
     *
     * **Se dibuja la vista a un `Bitmap` a mano y no con `captureToImage()` ni
     * con Roborazzi**, y las dos exclusiones están medidas:
     * `captureToImage()` truena bajo Robolectric (`ComposeTimeoutException` en
     * `forceRedraw`), y `captureRoboImage` **sólo escribe el archivo en modo
     * grabación** — bajo `testDebugUnitTest` no deja nada en disco y lo que se
     * leería es `null`. Dibujar la vista funciona en las dos tareas, que es lo
     * que hace que esta prueba corra en la compuerta y no sólo cuando alguien
     * regraba goldens.
     */
    private fun pinta(cuanto: Float): Bitmap {
        avance.floatValue = cuanto
        composeTestRule.waitForIdle()
        // `android.R.id.content` y no el `decorView`: el decor trae la franja
        // de la barra de estado y el fondo del tema legado de la actividad, que
        // no son blancos y contarían como tinta en todas las capturas por igual.
        val vista: View = composeTestRule.activity.findViewById(android.R.id.content)
        val mapa = Bitmap.createBitmap(vista.width, vista.height, Bitmap.Config.ARGB_8888)
        vista.draw(Canvas(mapa))
        return mapa
    }

    @Composable
    private fun Lienzo(contenido: @Composable () -> Unit) {
        val density = LocalDensity.current
        CompositionLocalProvider(LocalDensity provides Density(DENSIDAD, density.fontScale)) {
            MspTheme(darkTheme = false, animateColors = false) {
                // Lienzo blanco a pantalla completa: lo que no sea blanco es
                // tinta del fondo, y así el conteo no depende de dónde caiga el
                // recorte de la vista.
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.White)
                ) {
                    contenido()
                }
            }
        }
    }

    /**
     * Cuántos píxeles tienen tinta — o sea que NO son el blanco del lienzo.
     *
     * El umbral no es cero: el antialias de un texto deja píxeles a un paso del
     * blanco, y contarlos haría que "apagado del todo" nunca fuera cierto. Con
     * [UMBRAL] se cuenta lo que un ojo ve como tinta.
     */
    private fun tinta(imagen: Bitmap): Int {
        var cuantos = 0
        for (y in 0 until imagen.height) {
            for (x in 0 until imagen.width) {
                if (esTinta(imagen.getPixel(x, y))) cuantos += 1
            }
        }
        return cuantos
    }

    /**
     * El renglón más BAJO con tinta, o `-1` si no hay ninguna.
     *
     * El más bajo y no el más alto, y la diferencia importa: el fondo se desliza
     * **hacia arriba**, así que su borde superior sale de pantalla y se recorta
     * —el renglón más alto con tinta se queda pegado en 0 y deja de medir nada—.
     * El borde de abajo viaja entero y es el que dice cuánto se movió.
     */
    private fun ultimoRenglonConTinta(imagen: Bitmap): Int {
        for (y in imagen.height - 1 downTo 0) {
            for (x in 0 until imagen.width) {
                if (esTinta(imagen.getPixel(x, y))) return y
            }
        }
        return -1
    }

    private fun esTinta(argb: Int): Boolean {
        val r = (argb shr 16) and 0xFF
        val g = (argb shr 8) and 0xFF
        val b = argb and 0xFF
        return r < UMBRAL || g < UMBRAL || b < UMBRAL
    }

    private companion object {
        val ANCHO = 360.dp
        val ALTO = 260.dp

        /** La densidad del lienzo, fija para que los renglones sean comparables. */
        const val DENSIDAD = 2f

        /**
         * Cuánto tiene que haber subido la tinta a mitad del recorrido.
         *
         * 12 px a densidad 2 son 6 dp. El paralaje real a mitad de 230 dp es
         * `0.42 × 115 = 48.3 dp`, así que hay margen de sobra: el umbral está
         * puesto para que un cambio de constante razonable no ponga rojo el
         * test, y para que **borrar** el paralaje sí lo ponga.
         */
        const val SUBIDA_MINIMA = 12

        /**
         * A qué altura del recorrido se mide el paralaje: **un cuarto**.
         *
         * A la mitad el fondo ya salió casi entero de su propia caja y lo que
         * queda es un sobrante recortado; a un cuarto todavía hay bloque que
         * localizar, que es lo que hace que la medición signifique algo.
         */
        const val MITAD = 0.25f

        /** Por debajo de esto un canal cuenta como tinta y no como lienzo. */
        const val UMBRAL = 200

        /**
         * Cuántos píxeles de corrimiento se perdonan por el antialias del borde.
         *
         * Cuatro. El paralaje real que este archivo vigila mueve ~96 px a mitad
         * del recorrido, así que el margen no puede esconderlo.
         */
        const val ROCE = 4

        const val CALLE = "C. Hidalgo 214"
        const val NOMBRE = "Victoria Flores Olmedo"
        val PUNTO = UbicacionDelCobro(lat = 18.4609, lng = -97.3926)
    }
}
