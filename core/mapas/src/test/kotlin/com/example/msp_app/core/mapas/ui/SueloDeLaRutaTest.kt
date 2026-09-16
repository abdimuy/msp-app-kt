package com.example.msp_app.core.mapas.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.height
import androidx.compose.ui.unit.width
import com.example.msp_app.core.designsystem.theme.MspTheme
import com.example.msp_app.core.mapas.domain.MapaDeLaRuta
import com.example.msp_app.core.mapas.domain.PuntoDelMapa
import com.example.msp_app.core.mapas.fake.mapaDePrueba
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * **Las tres cosas que pueden estar mal en el suelo del mapa**, medidas del lado
 * determinista de la costura.
 *
 * El lienzo real necesita GL y en Robolectric no existe, así que se inyecta uno
 * de mentira por el slot [SueloDeLaRuta] — que es exactamente para lo que el
 * slot está. Lo que queda probado es lo que puede fallar por una decisión
 * humana: que sin extracto se dibuje algo, que con extracto falte la atribución,
 * y que el bloque cambie de tamaño entre los dos estados.
 *
 * Lo que NO queda probado acá son los píxeles de las calles. Ningún test de JVM
 * podría verlos, y fingir que sí los ve es peor que decirlo.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(
    sdk = [33],
    qualifiers = "w360dp-h800dp-xhdpi",
    application = android.app.Application::class
)
class SueloDeLaRutaTest {

    @get:Rule
    val compose = createComposeRule()

    private val punto = PuntoDelMapa(lat = 18.4609, lng = -97.3926)

    /** `testTag` del lienzo de mentira que sustituye a MapLibre. */
    private val lienzoFalsoTag = "lienzo_falso"

    /**
     * El lienzo de mentira que sustituye a MapLibre. Ignora sus dos parámetros a
     * propósito: lo que se mide acá es la geometría del suelo, no lo que el
     * motor haría con ellos.
     */
    @Composable
    @Suppress("UNUSED_PARAMETER")
    private fun LienzoFalso(mapa: MapaDeLaRuta, donde: PuntoDelMapa?) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .testTag(lienzoFalsoTag)
                .background(Color(0xFF335577))
        )
    }

    @Composable
    private fun bloque(mapa: MapaDeLaRuta?) {
        MspTheme(darkTheme = false, animateColors = false) {
            Box(modifier = Modifier.fillMaxWidth().height(ALTO_DEL_BLOQUE)) {
                SueloDeLaRuta(
                    mapa = mapa,
                    punto = punto,
                    lienzo = { m, d -> LienzoFalso(m, d) }
                )
            }
        }
    }

    /**
     * **Sin extracto no se dibuja un mapa, y no se dibuja nada que se le
     * parezca.** El suelo liso es la degradación correcta: una retícula o unas
     * calles de relleno se leerían como la traza real de la colonia.
     */
    @Test
    fun `sin extracto se pinta el suelo liso y NINGUN lienzo`() {
        compose.setContent { bloque(mapa = null) }

        compose.onNodeWithTag(SUELO_LISO_TAG).assertIsDisplayed()
        compose.onNodeWithTag(LIENZO_TAG).assertDoesNotExist()
        compose.onNodeWithTag(lienzoFalsoTag).assertDoesNotExist()
    }

    /**
     * **Sin mapa no hay atribución.** No es un olvido: donde no se muestran
     * datos de OpenStreetMap no hay nada que atribuir, y un crédito sobre un
     * rectángulo liso sería ruido que además enseñaría a ignorarlo.
     */
    @Test
    fun `sin extracto tampoco hay atribucion`() {
        compose.setContent { bloque(mapa = null) }

        compose.onNodeWithTag(ATRIBUCION_TAG).assertDoesNotExist()
    }

    /**
     * **Con mapa, la atribución SIEMPRE.** ODbL lo exige y es la condición bajo
     * la cual este módulo puede existir sin llave, sin servidor y sin proveedor.
     * Está pegada al dibujo —no a quien lo llama— justamente para que no se
     * pueda olvidar en una pantalla.
     */
    @Test
    fun `con extracto se pinta el lienzo Y la atribucion de OpenStreetMap`() {
        compose.setContent { bloque(mapa = mapaDePrueba()) }

        compose.onNodeWithTag(lienzoFalsoTag).assertIsDisplayed()
        compose.onNodeWithTag(ATRIBUCION_TAG).assertIsDisplayed()
        compose.onNodeWithTag(SUELO_LISO_TAG).assertDoesNotExist()
    }

    /**
     * **El pin lo pinta el mapa, y en el centro exacto.**
     *
     * El centro de la caja del lienzo es el objetivo de la cámara (gestos
     * apagados, cámara centrada en el punto). Que el pin caiga ahí es lo que
     * hace cierto el "es aquí". El golden del bloque completo midió la versión
     * anterior —el pin lo pintaba la feature en su banda— con **21 dp de
     * error**, que a zoom 17 son ~24 metros.
     */
    @Test
    fun `con mapa y punto, el pin va al centro exacto del lienzo`() {
        compose.setContent { bloque(mapa = mapaDePrueba()) }

        val lienzo = compose.onNodeWithTag(LIENZO_TAG).getUnclippedBoundsInRoot()
        val pin = compose.onNodeWithTag(PIN_DEL_MAPA_TAG).getUnclippedBoundsInRoot()

        assertEquals(
            lienzo.top + (lienzo.height - pin.height) / 2,
            pin.top
        )
        assertEquals(
            lienzo.left + (lienzo.width - pin.width) / 2,
            pin.left
        )
    }

    /**
     * **Sin punto medido no hay pin, ni siquiera con mapa.** Un pin en el centro
     * de un mapa sin coordenada diría "es aquí" sobre lo que la cámara haya
     * dejado en el medio, que es cualquier cosa.
     */
    @Test
    fun `con mapa y SIN punto no hay pin`() {
        compose.setContent {
            MspTheme(darkTheme = false, animateColors = false) {
                Box(modifier = Modifier.fillMaxWidth().height(ALTO_DEL_BLOQUE)) {
                    SueloDeLaRuta(
                        mapa = mapaDePrueba(),
                        punto = null,
                        lienzo = { m, d -> LienzoFalso(m, d) }
                    )
                }
            }
        }

        compose.onNodeWithTag(PIN_DEL_MAPA_TAG).assertDoesNotExist()
        // Control positivo: el lienzo SÍ está, así que la ausencia del pin no es
        // que no se haya pintado nada.
        compose.onNodeWithTag(lienzoFalsoTag).assertIsDisplayed()
        compose.onNodeWithTag(ATRIBUCION_TAG).assertIsDisplayed()
    }

    /**
     * **Nada salta** (principio 12): el bloque mide lo mismo con mapa y sin él,
     * así que el detalle del cliente no se reacomoda el día que el extracto
     * termina de bajar mientras la pantalla está abierta.
     */
    @Test
    fun `el bloque mide lo mismo con mapa y sin mapa`() {
        // La MISMA composición, cambiando el estado: dos composiciones distintas
        // podrían diferir por otra razón y el test estaría midiendo otra cosa.
        val mapa = mutableStateOf<MapaDeLaRuta?>(null)
        compose.setContent { bloque(mapa = mapa.value) }

        val sinMapa = compose.onNodeWithTag(SUELO_LISO_TAG).getUnclippedBoundsInRoot().height

        mapa.value = mapaDePrueba()
        compose.waitForIdle()
        val conMapa = compose.onNodeWithTag(LIENZO_TAG).getUnclippedBoundsInRoot().height

        assertEquals(ALTO_DEL_BLOQUE, sinMapa)
        assertEquals(ALTO_DEL_BLOQUE, conMapa)
    }

    private companion object {
        /** Los 130 dp del bloque de mapa del detalle de cliente. */
        val ALTO_DEL_BLOQUE = 130.dp
    }
}
