package com.example.msp_app.feature.pagos.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertHasNoClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.example.msp_app.core.designsystem.theme.FontSizeLevel
import com.example.msp_app.core.designsystem.theme.LocalFontSizeLevel
import com.example.msp_app.core.designsystem.theme.MspTheme
import com.example.msp_app.core.testing.RobolectricTestBase
import com.example.msp_app.feature.pagos.domain.model.DetalleCliente
import com.example.msp_app.feature.pagos.domain.model.UbicacionDelCobro
import com.example.msp_app.feature.pagos.ui.components.CUADRO_DE_LA_PUERTA_TAG
import com.example.msp_app.feature.pagos.ui.components.DatosDeLaPuerta
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.robolectric.annotation.Config

/**
 * **El cuadro de la puerta se pinta siempre, y los dos datos volvieron.**
 *
 * ## Los dos defectos que esto fija
 *
 * **Uno.** El bloque de ubicación era condicional: sin un abono con coordenadas
 * quedaba un hueco en medio de la hoja. El dueño lo vio en vidrio y fue
 * explícito — *"tiene que ser un mapa o un dibujo"*—. Ahora la banda existe
 * siempre. Lo que cambia con el dato es el **pin** del dibujo y si el cuadro se
 * puede tocar para abrir el mapa completo.
 *
 * **Dos.** El rediseño a hoja continua siguió un mock que ya había perdido la
 * sección "datos del cliente", y con ella `aval` y `ultimaVisita`: los dos
 * quedaron con **cero usos** en la pantalla sin que nadie lo notara. Son las dos
 * preguntas que se hacen parado en la puerta —*"¿a quién le llamo?"* y *"¿hace
 * cuánto vine?"*—, así que se pintan siempre.
 *
 * ## Lo que NO se prueba acá, y por qué
 *
 * El mapa de verdad. Vive en `:app` (`SueloDelUltimoCobro`) porque
 * `play-services-maps` se declara ahí, y necesita red y GL, que en Robolectric
 * no existen. Lo que este módulo garantiza es la **costura**: que el suelo que
 * entra por la ranura se pinte encima del dibujo, y que sin ranura el dibujo
 * quede a la vista. Eso último es lo que hace que un mapa que no carga degrade
 * al dibujo en vez de a la retícula gris de Google.
 *
 * Tampoco se repite acá que "cómo llegar" es una acción más de la fila: eso lo
 * cobra `ComoLlegarEsUnaAccionMasTest`.
 */
@Config(qualifiers = "w360dp-h800dp-xhdpi")
class ElCuadroDeLaPuertaYLosDatosTest : RobolectricTestBase() {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun `el cuadro existe con punto medido`() {
        monta(conCoordenada = true)

        composeTestRule.onNodeWithTag(CUADRO_DE_LA_PUERTA_TAG).performScrollTo().assertIsDisplayed()
    }

    @Test
    fun `el cuadro existe tambien sin punto medido`() {
        // El hueco que el dueño rechazó: antes de esto, sin coordenada no se
        // pintaba nada y quedaba una banda vacía en medio de la hoja.
        monta(conCoordenada = false)

        composeTestRule.onNodeWithTag(CUADRO_DE_LA_PUERTA_TAG).performScrollTo().assertIsDisplayed()
    }

    @Test
    fun `el suelo que entra por la ranura se pinta encima del dibujo`() {
        monta(conCoordenada = true) {
            Box(modifier = Modifier.fillMaxSize().testTag(SUELO_DE_PRUEBA))
        }

        // La ranura es lo que le deja a `:app` poner el mapa sin que este módulo
        // declare `play-services-maps`, y lo que mantiene los goldens sin red. Si
        // dejara de cablearse, la pantalla se vería bien y el mapa no aparecería
        // nunca — un defecto que ningún golden fotografía.
        composeTestRule.onNodeWithTag(SUELO_DE_PRUEBA).performScrollTo().assertIsDisplayed()
    }

    @Test
    fun `sin punto medido el suelo no se pinta, porque no hay donde centrarlo`() {
        monta(conCoordenada = false) {
            Box(modifier = Modifier.fillMaxSize().testTag(SUELO_DE_PRUEBA))
        }

        assertEquals(
            "se montó un mapa sin coordenada que lo centre: eso dice \"es aquí\" " +
                "sobre una puerta que nadie midió",
            0,
            composeTestRule.onAllNodesWithTag(SUELO_DE_PRUEBA).fetchSemanticsNodes().size
        )
    }

    @Test
    fun `con punto medido el cuadro abre el mapa completo`() {
        var abierto = 0
        monta(conCoordenada = true, onVerUbicacion = { abierto++ })

        composeTestRule.onNodeWithTag(CUADRO_DE_LA_PUERTA_TAG)
            .performScrollTo()
            .assertHasClickAction()
            .performClick()
        assertEquals("tocar el cuadro no abrió el mapa completo", 1, abierto)
    }

    @Test
    fun `sin punto medido el cuadro no se puede tocar`() {
        // No es que el toque no haga nada: es que no existe. Un control que se
        // ve tocable y no hace nada es el defecto que ningún golden fotografía.
        monta(conCoordenada = false, onVerUbicacion = {})

        composeTestRule.onNodeWithTag(CUADRO_DE_LA_PUERTA_TAG)
            .performScrollTo()
            .assertHasNoClickAction()
    }

    @Test
    fun `el suelo recibe el toque, porque el mapa se lo come al cuadro`() {
        // El defecto medido en el SM-A256E: en `liteMode` el SDK de Google trae
        // de fábrica su propio manejo del toque —abrir la app de Google Maps— y
        // el `clickable` del cuadro queda DEBAJO del mapa, así que nunca se
        // entera. Salía un `act=VIEW dat=geo:` disparado por el SDK.
        //
        // El arreglo es que el cuadro le PASE al suelo qué significa un toque,
        // para que el suelo lo cablee donde el SDK sí escucha (`onMapClick`).
        // Esto cobra esa costura: si el cuadro volviera a pasar un `{}`, el
        // toque del suelo dejaría de llevar a ninguna parte y este test se pone
        // rojo. Lo que NO se puede cobrar desde aquí es que el SDK respete el
        // `onMapClick`: eso vive en `:app` y necesita red y GL.
        var abierto = 0
        monta(conCoordenada = true, onVerUbicacion = { abierto++ }) { onTocar ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clickable { onTocar() }
                    .testTag(SUELO_DE_PRUEBA)
            )
        }

        composeTestRule.onNodeWithTag(SUELO_DE_PRUEBA).performScrollTo().performClick()
        assertEquals("tocar el suelo no abrió el mapa completo", 1, abierto)
    }

    @Test
    fun `sin a donde ir, el toque del suelo no truena`() {
        // Control del borde: `onVerUbicacion` en `null` significa que no hay
        // pantalla a la que llevar. El cuadro le pasa al suelo un toque que no
        // hace nada en vez de no pasarle nada, porque el tipo del suelo pide una
        // función y no una opcional — así `:app` cablea `onMapClick` una sola
        // vez y no tiene que decidir si existe.
        monta(conCoordenada = true, onVerUbicacion = null) { onTocar ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clickable { onTocar() }
                    .testTag(SUELO_DE_PRUEBA)
            )
        }

        composeTestRule.onNodeWithTag(SUELO_DE_PRUEBA).performScrollTo().performClick()
    }

    @Test
    fun `el aval y la ultima visita se ven`() {
        monta(conCoordenada = true)

        composeTestRule.onNodeWithText(AVAL).performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText(ETIQUETA_AVAL).performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText(ETIQUETA_VISITA).performScrollTo().assertIsDisplayed()
    }

    @Test
    fun `el telefono del aval no se pinta mientras la columna no exista`() {
        monta(conCoordenada = true)

        // Hoy `telefonoAval` es null SIEMPRE: no existe la columna, y lo devuelve
        // así `RoomVentasAdapter.kt:78`. Una fila permanentemente en "—" es el
        // ruido que este rediseño quitó. El día que el dato exista, este test se
        // pone rojo y hay que cambiarlo — que es lo correcto.
        assertEquals(
            "se pintó la fila del teléfono del aval sin dato que poner",
            0,
            composeTestRule.onAllNodesWithText(ETIQUETA_TELEFONO_AVAL).fetchSemanticsNodes().size
        )
    }

    @Test
    fun `el dia que la columna exista, la fila aparece`() {
        // Control positivo del caso de arriba: la ausencia solo vale si el método
        // habría encontrado la fila estando el dato. Sin esto, un
        // `DatosDeLaPuerta` que nunca pintara esa fila —ni con dato— también
        // pasaría en verde.
        composeTestRule.setContent {
            MspTheme(animateColors = false) {
                DatosDeLaPuerta(
                    aval = AVAL,
                    telefonoAval = TELEFONO_DEL_AVAL,
                    ultimaVisita = LocalDate.of(2026, 8, 24)
                )
            }
        }

        composeTestRule.onNodeWithText(ETIQUETA_TELEFONO_AVAL).assertIsDisplayed()
        composeTestRule.onNodeWithText(TELEFONO_DEL_AVAL).assertIsDisplayed()
    }

    private fun monta(
        conCoordenada: Boolean,
        onVerUbicacion: (() -> Unit)? = null,
        suelo: (@Composable (onTocar: () -> Unit) -> Unit)? = null
    ) {
        composeTestRule.setContent {
            CompositionLocalProvider(LocalFontSizeLevel provides FontSizeLevel.NORMAL) {
                MspTheme(animateColors = false) {
                    DetalleClienteContent(
                        state = DetalleClienteUiState(
                            cargando = false,
                            detalle = detalle(conCoordenada)
                        ),
                        onAtras = {},
                        onAbrirVenta = {},
                        onRegistrarAbono = {},
                        onRegistrarVisita = {},
                        onVerContactos = {},
                        onAlternarTema = {},
                        onAlternarPrivacidad = {},
                        onVerUbicacion = onVerUbicacion,
                        suelo = suelo
                    )
                }
            }
        }
    }

    private fun detalle(conCoordenada: Boolean): DetalleCliente =
        PagosFixtures.detalleCliente().copy(
            ultimoCobroAqui = if (conCoordenada) COORDENADA else null
        )

    private companion object {
        const val SUELO_DE_PRUEBA = "suelo_de_prueba"
        const val ETIQUETA_AVAL = "aval o responsable"
        const val ETIQUETA_TELEFONO_AVAL = "teléfono del aval"
        const val ETIQUETA_VISITA = "última visita"
        const val AVAL = "Rosa María Ramírez"
        const val TELEFONO_DEL_AVAL = "238 118 4402"
        val COORDENADA = UbicacionDelCobro(lat = 18.4609, lng = -97.3926)
    }
}
