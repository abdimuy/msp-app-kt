package com.example.msp_app.feature.pagos.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import com.example.msp_app.core.designsystem.theme.MspTheme
import com.example.msp_app.core.testing.RobolectricTestBase
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * **El botón de menú (hamburguesa) tiene que verse en tema oscuro — medido en
 * píxeles, no en intención.**
 *
 * El defecto, encontrado revisando los goldens: `pagos_lista_dark_1_0.png` y
 * `pagos_barra_compacta_dark_1_0.png` traían un hueco donde debía estar el
 * ícono. `Icon(Icons.Default.Menu, …)` sin `tint` cae al `LocalContentColor`
 * por defecto (`Color.Black`, el default global de Compose cuando nada en la
 * composición lo sobreescribe), y `IconButton` no lo corrige — a diferencia de
 * `OutlinedTextField`, que sí resuelve el color de sus íconos contra
 * `colorScheme`. Negro sobre el `background` #000000 de `mspDarkColors()` es
 * invisible.
 *
 * **Por qué se fotografía y no se afirma con `getBoundsInRoot`**: la geometría
 * del nodo (`BOTON_MENU_TAG`, 50×50dp) es idéntica con o sin `tint` — lo único
 * que cambia es el color con el que se pintan sus píxeles, y eso sólo se ve
 * dibujando la vista. Mismo criterio que `ElFondoSeVaAlDesplazarTest`.
 *
 * **Se dibuja la vista a un `Bitmap` a mano y no con `captureToImage()`**:
 * documentado (y medido) en `ElFondoSeVaAlDesplazarTest` —
 * `captureToImage()` truena bajo Robolectric con `ComposeTimeoutException` en
 * `forceRedraw`—, así que se reusa el mismo mecanismo que sí corre ahí.
 *
 * **Control de rojo**: quitar el `tint = MspTheme.colors.onSurface` de
 * `BotonDeMenu` pone esta prueba en rojo — medido, no supuesto.
 */
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(
    sdk = [33],
    qualifiers = "w360dp-h800dp-xhdpi",
    application = android.app.Application::class
)
class ElBotonDeMenuSeVeEnOscuroTest : RobolectricTestBase() {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun `el boton de menu pinta pixeles con contraste contra el fondo negro`() {
        composeTestRule.setContent {
            MspTheme(darkTheme = true, animateColors = false) {
                ListaDeClientesContent(
                    state = ListaDeClientesUiState(cargando = false, temaOscuro = true),
                    onBuscar = {},
                    onElegirSegmento = {},
                    onAbrirCliente = {},
                    onReintentar = {},
                    onAlternarTema = {},
                    onAlternarPrivacidad = {},
                    onElegirOrden = {},
                    onQuitarOrden = {},
                    onAbrirMenu = {}
                )
            }
        }
        composeTestRule.waitForIdle()

        val limites = composeTestRule.onNodeWithTag(
            BOTON_MENU_TAG
        ).fetchSemanticsNode().boundsInRoot
        val vista: View = composeTestRule.activity.findViewById(android.R.id.content)
        val completa = Bitmap.createBitmap(vista.width, vista.height, Bitmap.Config.ARGB_8888)
        vista.draw(Canvas(completa))

        val recorte = Bitmap.createBitmap(
            completa,
            limites.left.toInt().coerceIn(0, completa.width - 1),
            limites.top.toInt().coerceIn(0, completa.height - 1),
            limites.width.toInt().coerceAtMost(
                completa.width - limites.left.toInt().coerceAtLeast(0)
            ),
            limites.height.toInt().coerceAtMost(
                completa.height - limites.top.toInt().coerceAtLeast(0)
            )
        )

        assertTrue(
            "el botón de menú no pintó un solo píxel con contraste contra el negro: " +
                "en tema oscuro es invisible",
            pixelesConContraste(recorte) > 0
        )
    }

    /** Cuántos píxeles del recorte NO son (casi) negros — el ícono, si se pintó con algún tinte. */
    private fun pixelesConContraste(imagen: Bitmap): Int {
        var cuantos = 0
        for (y in 0 until imagen.height) {
            for (x in 0 until imagen.width) {
                val argb = imagen.getPixel(x, y)
                val r = (argb shr 16) and 0xFF
                val g = (argb shr 8) and 0xFF
                val b = argb and 0xFF
                if (r > UMBRAL_DE_NEGRO || g > UMBRAL_DE_NEGRO || b > UMBRAL_DE_NEGRO) cuantos += 1
            }
        }
        return cuantos
    }

    private companion object {
        /** Por encima de esto un canal ya no cuenta como "negro" — deja margen al antialias. */
        const val UMBRAL_DE_NEGRO = 20
    }
}
