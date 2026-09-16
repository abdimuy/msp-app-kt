package com.example.msp_app.feature.configuracion.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.dp
import com.example.msp_app.core.designsystem.theme.FontSizeLevel
import com.example.msp_app.core.designsystem.theme.MspTheme
import com.example.msp_app.core.testing.RobolectricTestBase
import com.example.msp_app.feature.configuracion.domain.port.AppThemeMode
import com.example.msp_app.feature.configuracion.ui.components.FONT_SIZE_OPTION_TAG_PREFIX
import com.example.msp_app.feature.configuracion.ui.components.MINI_REPORT_PREVIEW_TAG
import com.example.msp_app.feature.configuracion.ui.components.PRIVACY_MASKED_TOGGLE_TAG
import com.example.msp_app.feature.configuracion.ui.components.REDUCE_MOTION_TOGGLE_TAG
import com.example.msp_app.feature.configuracion.ui.components.tagDeLaDescarga
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * Compose-test (no golden) de [ConfiguracionContent]: las dos secciones + el header renderizan,
 * cada control informa el evento correcto al caller (mismo patrón "stateless, spy vía lambda"
 * que [com.example.msp_app.core.designsystem.component.SegmentChipsTest]), y el preview en vivo
 * reacciona al tocar cada tarjeta de tamaño (se re-monta con el `level` nuevo sin crashear, sin
 * perder ninguna de sus filas). La cobertura de que efectivamente calcula el `Density` correcto
 * por nivel vive en [com.example.msp_app.feature.configuracion.ui.components.MiniReportPreviewTest]
 * (JVM plano, sin Robolectric) — ver su KDoc para por qué una aserción de tamaño de layout en
 * píxeles sería frágil aquí (modo gráfico no-nativo de [RobolectricTestBase]).
 */
class ConfiguracionScreenTest : RobolectricTestBase() {

    @get:Rule
    val composeTestRule = createComposeRule()

    private fun setContent(
        state: ConfiguracionUiState = ConfiguracionUiState(),
        onBack: () -> Unit = {},
        onSelectFontSize: (FontSizeLevel) -> Unit = {},
        onSelectThemeMode: (AppThemeMode) -> Unit = {},
        onPrivacyMaskedChanged: (Boolean) -> Unit = {},
        onReduceMotionChanged: (Boolean) -> Unit = {}
    ) {
        composeTestRule.setContent {
            MspTheme(animateColors = false) {
                ConfiguracionContent(
                    state = state,
                    onBack = onBack,
                    onSelectFontSize = onSelectFontSize,
                    onSelectThemeMode = onSelectThemeMode,
                    onPrivacyMaskedChanged = onPrivacyMaskedChanged,
                    onReduceMotionChanged = onReduceMotionChanged,
                    onAbrirDescarga = {}
                )
            }
        }
    }

    /**
     * El montaje de la sección "Descargas". Va aparte de [setContent] y no como
     * un séptimo parámetro suyo: siete lambdas sueltas es exactamente el umbral
     * de `LongParameterList` que detekt aplica a este módulo, y una llamada de
     * siete lambdas tampoco se lee.
     */
    private fun setDescargas(
        descargas: List<FilaDeDescarga>,
        onAbrirDescarga: (DescargaOpcional) -> Unit = {}
    ) {
        composeTestRule.setContent {
            MspTheme(animateColors = false) {
                ConfiguracionContent(
                    state = ConfiguracionUiState(descargas = descargas),
                    onBack = {},
                    onSelectFontSize = {},
                    onSelectThemeMode = {},
                    onPrivacyMaskedChanged = {},
                    onReduceMotionChanged = {},
                    onAbrirDescarga = onAbrirDescarga
                )
            }
        }
    }

    @Test
    fun `renderiza el header y las dos secciones`() {
        setContent()

        composeTestRule.onNodeWithText("Configuración").assertExists()
        composeTestRule.onNodeWithText("Tamaño de letra").assertExists()
        composeTestRule.onNodeWithText("Apariencia").assertExists()
        composeTestRule.onNodeWithText("Así se verá").assertExists()
        composeTestRule.onNodeWithTag(MINI_REPORT_PREVIEW_TAG).assertExists()
    }

    @Test
    fun `tocar el boton de regreso llama a onBack`() {
        var backCalled = false
        setContent(onBack = { backCalled = true })

        composeTestRule.onNodeWithTag(CONFIGURACION_BACK_BUTTON_TAG).performClick()

        assertTrue(backCalled)
    }

    @Test
    fun `tocar una tarjeta de tamano informa ese nivel via onSelectFontSize`() {
        var selected: FontSizeLevel? = null
        setContent(onSelectFontSize = { selected = it })

        composeTestRule.onNodeWithTag(
            "$FONT_SIZE_OPTION_TAG_PREFIX${FontSizeLevel.GRANDE.name}"
        ).performClick()

        assertEquals(FontSizeLevel.GRANDE, selected)
    }

    @Test
    fun `tocar Automatico en el segmento de tema informa AppThemeMode SYSTEM`() {
        var selectedMode: AppThemeMode? = null
        setContent(
            state = ConfiguracionUiState(themeMode = AppThemeMode.LIGHT),
            onSelectThemeMode = { selectedMode = it }
        )

        composeTestRule.onNodeWithText("Automático").performScrollTo().performClick()

        assertEquals(AppThemeMode.SYSTEM, selectedMode)
    }

    @Test
    fun `tocar el toggle de ocultar cifras informa el nuevo valor`() {
        var masked: Boolean? = null
        setContent(
            state = ConfiguracionUiState(privacyMasked = false),
            onPrivacyMaskedChanged = { masked = it }
        )

        composeTestRule.onNodeWithTag(PRIVACY_MASKED_TOGGLE_TAG).performScrollTo().performClick()

        assertEquals(true, masked)
    }

    @Test
    fun `tocar el toggle de reduce-motion informa el nuevo valor`() {
        var enabled: Boolean? = null
        setContent(
            state = ConfiguracionUiState(reduceMotion = false),
            onReduceMotionChanged = { enabled = it }
        )

        composeTestRule.onNodeWithTag(REDUCE_MOTION_TOGGLE_TAG).performScrollTo().performClick()

        assertEquals(true, enabled)
    }

    @Test
    fun `tocar cada tarjeta de tamano re-monta el preview con ese nivel sin crashear`() {
        // El estado lo hoistea el propio test (como lo haría `ConfiguracionViewModel` real vía
        // el `Flow` de `SettingsRepository`) — cada tap en una tarjeta cambia `level`, lo que
        // recompone `FontSizeSection` -> `MiniReportPreview` con el nivel nuevo. Cubre la
        // reactividad de punta a punta (tap -> estado -> preview) sin depender de medir píxeles.
        var level by mutableStateOf(FontSizeLevel.NORMAL)
        composeTestRule.setContent {
            MspTheme(animateColors = false) {
                ConfiguracionContent(
                    state = ConfiguracionUiState(fontSizeLevel = level),
                    onBack = {},
                    onSelectFontSize = { level = it },
                    onSelectThemeMode = {},
                    onPrivacyMaskedChanged = {},
                    onReduceMotionChanged = {},
                    onAbrirDescarga = {}
                )
            }
        }

        FontSizeLevel.entries.forEach { target ->
            composeTestRule.onNodeWithTag(
                "$FONT_SIZE_OPTION_TAG_PREFIX${target.name}"
            ).performClick()

            assertEquals(target, level)
            composeTestRule.onNodeWithTag(MINI_REPORT_PREVIEW_TAG).assertExists()
        }
    }

    // -----------------------------------------------------------------------
    // La sección "Descargas" — la puerta que las dos pantallas no tenían
    // -----------------------------------------------------------------------

    /**
     * **La sección no se pinta cuando no hay renglones.** Un encabezado
     * "Descargas" sobre cero filas se lee como algo que no cargó, que es
     * exactamente el defecto que los goldens de esta rama ya destaparon una vez.
     */
    @Test
    fun `sin renglones la seccion de descargas no existe`() {
        setDescargas(emptyList())

        composeTestRule.onNodeWithText("Descargas").assertDoesNotExist()
    }

    @Test
    fun `cada renglon dice que es, que gana y cuanto ocupa`() {
        setDescargas(LAS_DOS)

        composeTestRule.onNodeWithText("Descargas").performScrollTo().assertExists()
        composeTestRule.onNodeWithText("Dictado por voz").performScrollTo().assertExists()
        composeTestRule.onNodeWithText("Dicta la nota sin señal").performScrollTo().assertExists()
        composeTestRule.onNodeWithText("Ocupa 43.5 MB").performScrollTo().assertExists()
        composeTestRule.onNodeWithText("Mapa de la ruta").performScrollTo().assertExists()
        composeTestRule.onNodeWithText("Ve las calles sin señal").performScrollTo().assertExists()
    }

    /**
     * **El estado real de hoy.** El extracto no está publicado en ningún
     * servidor, así que el renglón del mapa no puede anunciar un peso: si
     * apareciera un "Ocupa … MB" ahí, sería un número inventado.
     */
    @Test
    fun `sin origen el renglon del mapa lo dice y no anuncia megas`() {
        setDescargas(LAS_DOS)

        composeTestRule.onNodeWithText("Todavía no se puede").performScrollTo().assertExists()
        composeTestRule.onNodeWithText("Ocupa 25.5 MB").assertDoesNotExist()
    }

    @Test
    fun `tocar un renglon informa CUAL descarga se abrió`() {
        var abierta: DescargaOpcional? = null
        setDescargas(LAS_DOS, onAbrirDescarga = { abierta = it })

        composeTestRule.onNodeWithTag(tagDeLaDescarga(DescargaOpcional.MAPA))
            .performScrollTo()
            .performClick()

        // El MAPA y no el dictado: si los dos renglones informaran lo mismo, la
        // sección abriría siempre la misma pantalla y este test lo dice.
        assertEquals(DescargaOpcional.MAPA, abierta)
    }

    /**
     * **El toque, medido acá.** `CadaPantallaDeCobranzaRespetaLaBarraDeEstadoTest`
     * barre el grafo de cobranza y Configuración no vive ahí, así que los >=50 dp
     * que el repo exige por control los cobra este test sobre el renglón real.
     * Si un día no llega, **sube la implementación** — no bajes este mínimo.
     */
    @Test
    fun `cada renglon de descarga conserva sus 50dp tocables`() {
        setDescargas(LAS_DOS)

        val chicos = DescargaOpcional.entries.mapNotNull { cual ->
            val alto = composeTestRule.onNodeWithTag(tagDeLaDescarga(cual))
                .performScrollTo()
                .fetchSemanticsNode()
                .boundsInRoot
                .height
            val enDp = with(composeTestRule.density) { alto.toDp() }
            if (enDp < TOQUE_MINIMO) "$cual mide $enDp" else null
        }
        assertEquals(
            "estos renglones no llegan a los $TOQUE_MINIMO tocables del repo: el cobrador " +
                "toca y el tap no entra",
            emptyList<String>(),
            chicos
        )
    }

    private companion object {

        /** El mínimo tocable del repo, más estricto que los 48 de Material. */
        val TOQUE_MINIMO = 50.dp

        /**
         * Los dos renglones como se ven HOY: el dictado con su peso medido y el
         * mapa sin origen publicado.
         */
        val LAS_DOS = listOf(
            FilaDeDescarga(DescargaOpcional.DICTADO, "43.5", EstadoDeLaDescarga.AUSENTE),
            FilaDeDescarga(DescargaOpcional.MAPA, null, EstadoDeLaDescarga.SIN_ORIGEN)
        )
    }
}
