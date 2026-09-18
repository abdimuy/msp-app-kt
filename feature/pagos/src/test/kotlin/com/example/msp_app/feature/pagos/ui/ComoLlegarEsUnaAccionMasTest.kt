package com.example.msp_app.feature.pagos.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.dp
import com.example.msp_app.core.designsystem.theme.FontSizeLevel
import com.example.msp_app.core.designsystem.theme.LocalFontSizeLevel
import com.example.msp_app.core.designsystem.theme.MspTheme
import com.example.msp_app.core.testing.RobolectricTestBase
import com.example.msp_app.feature.pagos.domain.model.UbicacionDelCobro
import com.example.msp_app.feature.pagos.ui.components.ACCION_DE_CONTACTO_TAG
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.robolectric.annotation.Config

/**
 * **"Cómo llegar" es una acción permanente, y es la única.**
 *
 * ## El comportamiento que esto fija
 *
 * Hasta este cambio la acción era condicional: vivía dentro del cuadro de mapa
 * cuando había un cobro con coordenadas, y bajaba a la fila de acciones sólo
 * cuando no lo había. Eran **dos caminos** al mismo intent, y cuál se pintaba
 * dependía de si alguien había cobrado ahí con el GPS prendido.
 *
 * El cuadro de mapa se fue con `:core:mapas` —su renderizador ocupaba 47.9 MB de
 * `.so` en cuatro ABIs del APK y viajaba aunque nadie bajara las teselas—, así
 * que ahora la acción es una sola y está siempre. Estos tests son lo que impide
 * que vuelva a depender del dato: **con coordenada y sin ella, la fila tiene las
 * mismas tres acciones y una sola "Cómo llegar"**.
 *
 * Eran cuatro hasta que las **Notas** se fueron al dock, donde se ven sin
 * desplazar y pueden llevar distintivo. Lo que este archivo mide no cambió.
 *
 * Lo que la coordenada sigue decidiendo —y que no se toca acá— es el `geo:` que
 * arma `IntentAccionesExternasAdapter`: con `ultimoCobroAqui` abre la app de
 * mapas en el punto donde se cobró, y sin él en la dirección escrita.
 *
 * ## El toque, en las tres escalas
 *
 * Con la cuarta acción, un cuarto del ancho partía "whatsapp" a mitad de palabra
 * (`whatsap` + una `p` a 1.5), así que la implementación acomoda **dos por
 * renglón** a las escalas grandes — dos fijo, no `size / 2`, que con tres daría
 * una por renglón y un renglón más de alto. Cambiar el reparto cambia el alto
 * de cada celda, y lo
 * que se mide acá es que **ninguna caiga por debajo de los 50 dp** del repo. Que
 * la etiqueta entre entera lo miran los goldens `pagos_cliente_*`, que un assert
 * no puede ver.
 */
@Config(qualifiers = "w360dp-h800dp-xhdpi")
class ComoLlegarEsUnaAccionMasTest : RobolectricTestBase() {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun `con coordenada medida hay tres acciones y una sola como llegar`() {
        monta(conCoordenada = true)

        assertEquals(ACCIONES, cuantasAcciones())
        assertEquals(1, composeTestRule.onAllNodesWithText(COMO_LLEGAR).fetchSemanticsNodes().size)
    }

    /**
     * **El caso que antes cambiaba la pantalla.** Sin coordenada el cuadro de
     * mapa no se pintaba y "cómo llegar" era la cuarta de la fila; con ella
     * desaparecía de la fila y subía al cuadro. Ahora las dos son la misma
     * pantalla.
     */
    @Test
    fun `sin coordenada hay exactamente las mismas tres acciones`() {
        monta(conCoordenada = false)

        assertEquals(ACCIONES, cuantasAcciones())
        assertEquals(1, composeTestRule.onAllNodesWithText(COMO_LLEGAR).fetchSemanticsNodes().size)
    }

    @Test
    fun `tocar como llegar dispara la accion`() {
        var pedido = 0
        monta(conCoordenada = true, onComoLlegar = { pedido += 1 })

        composeTestRule.onNodeWithText(COMO_LLEGAR).performScrollTo().performClick()

        assertEquals(1, pedido)
    }

    /**
     * Control positivo del conteo: las tres no sólo existen en el árbol, se
     * ven. Un `fetchSemanticsNodes` de tres nodos apilados fuera de la
     * pantalla daría el mismo número.
     */
    @Test
    fun `las tres acciones se ven sin desplazar`() {
        monta(conCoordenada = true)

        listOf("Llamar", "WhatsApp", COMO_LLEGAR).forEach {
            composeTestRule.onNodeWithText(it).assertIsDisplayed()
        }
    }

    @Test
    fun `las acciones conservan sus 50dp tocables a escala normal`() =
        elToqueAguanta(FontSizeLevel.NORMAL)

    @Test
    fun `las acciones conservan sus 50dp tocables a escala grande`() =
        elToqueAguanta(FontSizeLevel.GRANDE)

    @Test
    fun `las acciones conservan sus 50dp tocables a escala muy grande`() =
        elToqueAguanta(FontSizeLevel.MUY_GRANDE)

    // -----------------------------------------------------------------------

    private fun elToqueAguanta(nivel: FontSizeLevel) {
        monta(conCoordenada = true, nivel = nivel)

        val altos = composeTestRule.onAllNodesWithTag(ACCION_DE_CONTACTO_TAG)
            .fetchSemanticsNodes()
            .map { with(composeTestRule.density) { it.boundsInRoot.height.toDp() } }
        // Control positivo: sin esto, un selector que no encontrara ninguna
        // acción dejaría la lista vacía y el filtro de abajo pasaría en verde
        // sin haber medido nada.
        assertEquals("no se midió ninguna acción: no probaría nada", ACCIONES, altos.size)
        assertEquals(
            "a escala $nivel hay acciones por debajo de los $TOQUE_MINIMO que el repo exige: " +
                "el cobrador toca y el tap no entra. Subí la implementación, no bajes el mínimo",
            emptyList<androidx.compose.ui.unit.Dp>(),
            altos.filter { it < TOQUE_MINIMO }
        )
    }

    private fun cuantasAcciones(): Int =
        composeTestRule.onAllNodesWithTag(ACCION_DE_CONTACTO_TAG).fetchSemanticsNodes().size

    private fun monta(
        conCoordenada: Boolean,
        nivel: FontSizeLevel = FontSizeLevel.NORMAL,
        onComoLlegar: () -> Unit = {}
    ) {
        composeTestRule.setContent {
            Detalle(conCoordenada = conCoordenada, nivel = nivel, onComoLlegar = onComoLlegar)
        }
    }

    @Composable
    private fun Detalle(conCoordenada: Boolean, nivel: FontSizeLevel, onComoLlegar: () -> Unit) {
        CompositionLocalProvider(LocalFontSizeLevel provides nivel) {
            MspTheme(animateColors = false) {
                DetalleClienteContent(
                    state = DetalleClienteUiState(
                        cargando = false,
                        detalle = PagosFixtures.detalleCliente().copy(
                            ultimoCobroAqui = if (conCoordenada) COORDENADA else null
                        )
                    ),
                    onAtras = {},
                    onAbrirVenta = {},
                    onRegistrarAbono = {},
                    onRegistrarVisita = {},
                    onVerContactos = {},
                    onAlternarTema = {},
                    onAlternarPrivacidad = {},
                    contacto = AccionesDeContacto(onComoLlegar = onComoLlegar)
                )
            }
        }
    }

    private companion object {
        const val ACCIONES = 3
        const val COMO_LLEGAR = "Cómo llegar"

        /** El mínimo tocable del repo, más estricto que los 48 de Material. */
        val TOQUE_MINIMO = 50.dp
        val COORDENADA = UbicacionDelCobro(lat = 18.4609, lng = -97.3926)
    }
}
