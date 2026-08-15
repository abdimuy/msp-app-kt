package com.example.msp_app.features.sales.components

import android.app.Application
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.unit.Density
import androidx.test.core.app.ApplicationProvider
import com.example.msp_app.data.cache.CitiesCache
import com.example.msp_app.data.models.city.City
import com.example.msp_app.features.cities.CitiesRepository
import com.example.msp_app.features.cities.CitiesViewModel
import com.example.msp_app.features.sales.components.cityselector.CITY_BACK_TO_CATALOG_LABEL
import com.example.msp_app.features.sales.components.cityselector.CITY_NOT_LISTED_LABEL
import com.example.msp_app.features.sales.components.cityselector.CitySelector
import java.io.IOException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Las dos reglas que definen este componente, fijadas como pruebas:
 *
 * 1. **Ciudad y estado viajan juntos.** No hay forma de mover uno sin el otro:
 *    la única salida de selección entrega la fila completa. El catálogo abarca
 *    Puebla, Oaxaca y Veracruz, así que elegirlos por separado produciría
 *    clientes con la ciudad de un estado y el estado de otro.
 *
 * 2. **Una ciudad faltante NO bloquea capturar; bloquea aplicar.** Siempre hay
 *    salida a texto libre — incluso sin catálogo y sin red — y el texto escrito
 *    se conserva. Quien bloquea es el servidor, al aplicar, con
 *    `ciudad_no_en_catalogo`; la venta se queda en borrador hasta que la oficina
 *    dé de alta la fila. La app NUNCA inserta en `CIUDADES`.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [33], application = android.app.Application::class)
class CitySelectorTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private class FakeCitiesRepository(
        private val resultado: () -> Result<List<City>>
    ) : CitiesRepository() {
        override suspend fun getCities(): Result<List<City>> = resultado()
    }

    private fun app(): Application = ApplicationProvider.getApplicationContext()

    private lateinit var cache: CitiesCache

    @Before
    fun limpiarCache() {
        cache = CitiesCache(app())
        runBlocking { cache.clear() }
    }

    private fun viewModel(resultado: () -> Result<List<City>>) =
        CitiesViewModel(app(), FakeCitiesRepository(resultado), cache)

    /** Registro de lo que el componente le entrega al formulario. */
    private class Captura {
        var seleccionada: City? = null
        var textoLibre: String? = null
        var vecesSeleccion = 0
    }

    /**
     * Monta el selector con el mismo contrato que el formulario real: el estado
     * lo posee el llamador y se reescribe con lo que el componente entrega.
     */
    private fun montar(
        catalogo: () -> Result<List<City>> = { Result.success(CATALOGO) },
        ciudadInicial: String = "",
        estadoInicial: String = "",
        enCatalogoInicial: Boolean = false,
        fontScale: Float = 1f
    ): Captura {
        val captura = Captura()
        val vm = viewModel(catalogo)

        composeTestRule.setContent {
            var ciudad by remember { mutableStateOf(ciudadInicial) }
            var estado by remember { mutableStateOf(estadoInicial) }
            var enCatalogo by remember { mutableStateOf(enCatalogoInicial) }

            val densidad = LocalDensity.current
            CompositionLocalProvider(
                LocalDensity provides Density(densidad.density, fontScale)
            ) {
                MaterialTheme {
                    CitySelector(
                        ciudad = ciudad,
                        estado = estado,
                        enCatalogo = enCatalogo,
                        onCitySelected = { city ->
                            // El formulario real fija ambos en la misma
                            // actualización; aquí se replica para poder afirmar
                            // que nunca llega uno sin el otro.
                            ciudad = city.ciudad
                            estado = city.estado
                            enCatalogo = true
                            captura.seleccionada = city
                            captura.vecesSeleccion++
                        },
                        onFreeTextChanged = { texto ->
                            ciudad = texto
                            estado = ""
                            enCatalogo = false
                            captura.textoLibre = texto
                        },
                        viewModel = vm
                    )
                }
            }
        }

        return captura
    }

    private fun esperarTexto(texto: String) {
        composeTestRule.waitUntil(timeoutMillis = 10_000) {
            composeTestRule.onAllNodesWithTextSeguro(texto).isNotEmpty()
        }
    }

    private fun androidx.compose.ui.test.junit4.ComposeContentTestRule.onAllNodesWithTextSeguro(
        texto: String
    ) = onAllNodes(
        androidx.compose.ui.test.hasText(texto, substring = true)
    ).fetchSemanticsNodes()

    /**
     * Abre el desplegable del catálogo.
     *
     * Se toca la flecha ([ABRIR_LISTA]) y no el campo: el `clickable` del campo no
     * llega a dispararse porque el `OutlinedTextField` consume el toque, así que la
     * flecha es el único disparador real del desplegable.
     */
    private fun abrirCatalogo() {
        esperarTexto(CONTEO_OPCIONES)
        composeTestRule.onNodeWithContentDescription(ABRIR_LISTA).performClick()
        composeTestRule.waitForIdle()
    }

    // ─── Regla 1: ciudad y estado viajan juntos ──────────────────────────────

    @Test
    fun `elegir una ciudad propaga ciudad y estado juntos`() {
        val captura = montar()

        abrirCatalogo()
        composeTestRule.onNodeWithText("OAXACA · OAXACA", substring = true).performClick()

        val elegida = captura.seleccionada
        assertEquals("OAXACA", elegida?.ciudad)
        assertEquals("OAXACA", elegida?.estado)
        assertEquals(11523, elegida?.ciudadId)
        // El estado llega en el MISMO objeto: no hay un segundo callback que lo
        // pudiera dejar desparejado.
        assertEquals(11522, elegida?.estadoId)
        assertNull("el texto libre no debe activarse al elegir", captura.textoLibre)
    }

    @Test
    fun `la ciudad se muestra siempre con su estado`() {
        montar()

        abrirCatalogo()

        // Cada renglón del catálogo es «CIUDAD · ESTADO»: el vendedor no puede
        // leer una ciudad sin ver qué estado se lleva, que es como se detectan las
        // filas cuyo ESTADO_ID contradice al nombre.
        composeTestRule.onNodeWithText("TEHUACAN · PUEBLA", substring = true).assertIsDisplayed()
        composeTestRule.onNodeWithText("ORIZABA · VERACRUZ", substring = true).assertIsDisplayed()
    }

    @Test
    fun `elegir otra ciudad reemplaza el estado, no lo mezcla`() {
        val captura = montar()

        abrirCatalogo()
        composeTestRule.onNodeWithText("OAXACA · OAXACA", substring = true).performClick()
        assertEquals("OAXACA", captura.seleccionada?.estado)

        abrirCatalogoTrasSeleccion()
        composeTestRule.onNodeWithText("ORIZABA · VERACRUZ", substring = true).performClick()

        assertEquals("ORIZABA", captura.seleccionada?.ciudad)
        assertEquals("VERACRUZ", captura.seleccionada?.estado)
    }

    private fun abrirCatalogoTrasSeleccion() {
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithContentDescription(ABRIR_LISTA).performClick()
        composeTestRule.waitForIdle()
    }

    @Test
    fun `un borrador restaurado recupera el estado de su fila`() {
        // El borrador guarda el texto pero vuelve con `enCatalogo = false`. El
        // selector lo reconcilia contra el catálogo y devuelve la fila completa,
        // para que la ciudad no quede huérfana de su estado tras restaurar.
        val captura = montar(ciudadInicial = "tehuacán", enCatalogoInicial = false)

        composeTestRule.waitUntil(timeoutMillis = 10_000) {
            captura.seleccionada != null
        }

        assertEquals("TEHUACAN", captura.seleccionada?.ciudad)
        assertEquals("PUEBLA", captura.seleccionada?.estado)
    }

    // ─── Regla 2: la ciudad faltante no bloquea capturar ─────────────────────

    @Test
    fun `mi ciudad no esta deja capturar texto libre`() {
        val captura = montar()

        esperarTexto(CITY_NOT_LISTED_LABEL)
        composeTestRule.onNodeWithText(CITY_NOT_LISTED_LABEL, substring = true).performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText(ETIQUETA_LIBRE, substring = true).assertIsDisplayed()
        composeTestRule.onNodeWithText(ETIQUETA_LIBRE, substring = true)
            .performTextReplacement("SAN GABRIEL CHILAC")

        assertEquals("SAN GABRIEL CHILAC", captura.textoLibre)
        // Capturar libre NUNCA equivale a elegir del catálogo: la venta no puede
        // aplicarse hasta que la oficina dé de alta la fila.
        assertEquals(0, captura.vecesSeleccion)
    }

    @Test
    fun `el texto libre se conserva en pantalla`() {
        montar()

        esperarTexto(CITY_NOT_LISTED_LABEL)
        composeTestRule.onNodeWithText(CITY_NOT_LISTED_LABEL, substring = true).performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText(ETIQUETA_LIBRE, substring = true)
            .performTextReplacement("SAN GABRIEL CHILAC")
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("SAN GABRIEL CHILAC", substring = true).assertIsDisplayed()
    }

    @Test
    fun `desde el texto libre se puede volver al catalogo`() {
        montar()

        esperarTexto(CITY_NOT_LISTED_LABEL)
        composeTestRule.onNodeWithText(CITY_NOT_LISTED_LABEL, substring = true).performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText(CITY_BACK_TO_CATALOG_LABEL, substring = true).performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText(CITY_NOT_LISTED_LABEL, substring = true).assertIsDisplayed()
    }

    @Test
    fun `sin red el escape a texto libre sigue disponible`() {
        // Sin catálogo y sin cache el selector queda en error, pero capturar no se
        // puede bloquear: el vendedor sigue teniendo salida.
        val captura = montar(catalogo = { Result.failure(IOException("sin red")) })

        esperarTexto(CITY_NOT_LISTED_LABEL)
        composeTestRule.onNodeWithText(CITY_NOT_LISTED_LABEL, substring = true).performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText(ETIQUETA_LIBRE, substring = true)
            .performTextReplacement("COYOMEAPAN")

        assertEquals("COYOMEAPAN", captura.textoLibre)
    }

    @Test
    fun `con el catalogo vacio tambien hay salida`() {
        val captura = montar(catalogo = { Result.success(emptyList()) })

        esperarTexto(CITY_NOT_LISTED_LABEL)
        composeTestRule.onNodeWithText(CITY_NOT_LISTED_LABEL, substring = true).performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText(ETIQUETA_LIBRE, substring = true)
            .performTextReplacement("ESPERANZA")

        assertEquals("ESPERANZA", captura.textoLibre)
    }

    @Test
    fun `una ciudad fuera del catalogo no se pierde de la pantalla`() {
        // Borrador viejo con una ciudad que la oficina aún no da de alta: el texto
        // tiene que seguir visible, no desaparecer en un selector sin selección.
        montar(ciudadInicial = "SAN GABRIEL CHILAC", enCatalogoInicial = false)

        esperarTexto("SAN GABRIEL CHILAC")

        composeTestRule.onNodeWithText("SAN GABRIEL CHILAC", substring = true).assertIsDisplayed()
    }

    // ─── Accesibilidad: letra al 200% ────────────────────────────────────────

    @Test
    fun `con letra al doble el selector sigue siendo usable`() {
        val captura = montar(fontScale = ESCALA_LETRA_MAXIMA)

        abrirCatalogo()
        composeTestRule.onNodeWithText("TEHUACAN · PUEBLA", substring = true).performClick()

        assertEquals("TEHUACAN", captura.seleccionada?.ciudad)
        // También con letra grande la ciudad llega con su estado.
        assertEquals("PUEBLA", captura.seleccionada?.estado)
    }

    @Test
    fun `con letra al doble el escape a texto libre sigue alcanzable`() {
        val captura = montar(fontScale = ESCALA_LETRA_MAXIMA)

        esperarTexto(CITY_NOT_LISTED_LABEL)
        composeTestRule.onNodeWithText(CITY_NOT_LISTED_LABEL, substring = true)
            .assertIsDisplayed()
        composeTestRule.onNodeWithText(CITY_NOT_LISTED_LABEL, substring = true).performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText(ETIQUETA_LIBRE, substring = true)
            .performTextReplacement("SAN GABRIEL CHILAC")

        assertEquals("SAN GABRIEL CHILAC", captura.textoLibre)
    }

    @Test
    fun `con letra al doble la ciudad y su estado siguen legibles juntos`() {
        montar(fontScale = ESCALA_LETRA_MAXIMA)

        abrirCatalogo()

        assertTrue(
            "la etiqueta perdió el estado con letra grande",
            composeTestRule.onAllNodesWithTextSeguro("ORIZABA · VERACRUZ").isNotEmpty()
        )
    }

    private companion object {
        /** Descripción de la flecha que abre el desplegable. */
        const val ABRIR_LISTA = "Abrir lista"

        /** Pie del selector; solo aparece cuando el catálogo ya cargó. */
        const val CONTEO_OPCIONES = "opciones disponibles"

        const val ETIQUETA_LIBRE = "Ciudad *"
        const val ESCALA_LETRA_MAXIMA = 2f

        val CATALOGO = listOf(
            City(ciudadId = 338, ciudad = "TEHUACAN", estadoId = 337, estado = "PUEBLA"),
            City(ciudadId = 11523, ciudad = "OAXACA", estadoId = 11522, estado = "OAXACA"),
            City(ciudadId = 11751, ciudad = "ORIZABA", estadoId = 11750, estado = "VERACRUZ")
        )
    }
}
