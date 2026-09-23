package com.example.msp_app.feature.pagos.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertHasNoClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.dp
import com.example.msp_app.core.designsystem.theme.FontSizeLevel
import com.example.msp_app.core.designsystem.theme.LocalFontSizeLevel
import com.example.msp_app.core.designsystem.theme.MspTheme
import com.example.msp_app.core.testing.RobolectricTestBase
import com.example.msp_app.feature.pagos.domain.model.DetalleCliente
import com.example.msp_app.feature.pagos.domain.model.UbicacionDelCobro
import com.example.msp_app.feature.pagos.ui.components.APOYO_DEL_CUADRO_TAG
import com.example.msp_app.feature.pagos.ui.components.CALLE_DEL_CUADRO_TAG
import com.example.msp_app.feature.pagos.ui.components.CUADRO_DE_LA_PUERTA_TAG
import com.example.msp_app.feature.pagos.ui.components.DatosDeLaPuerta
import com.example.msp_app.feature.pagos.ui.components.PUNTO_MEDIDO_TAG
import com.example.msp_app.feature.pagos.ui.components.SIN_DIRECCION
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
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
 * siempre. Lo que cambia con el dato es el **chip "Punto medido"** y si el
 * cuadro se puede tocar para abrir el mapa completo.
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
 * entra por la ranura se pinte encima de las señas, y que sin ranura las señas
 * queden a la vista. Eso último es lo que hace que un mapa que no carga degrade
 * a las señas en vez de a la retícula gris de Google.
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

    // --- La composición tipográfica que reemplazó al dibujo -------------------

    @Test
    fun `con punto medido se ven el chip, la calle y la linea de apoyo`() {
        monta(conCoordenada = true)

        composeTestRule.onNodeWithTag(PUNTO_MEDIDO_TAG).performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText(PUNTO_MEDIDO).assertIsDisplayed()
        composeTestRule.onNodeWithTag(CALLE_DEL_CUADRO_TAG).assertTextEquals(CALLE)
        composeTestRule.onNodeWithTag(APOYO_DEL_CUADRO_TAG).assertTextEquals(APOYO)
    }

    /**
     * **La calle NO lleva la ciudad pegada.** Es el defecto que el dueño vio en
     * el golden: con *"C. Hidalgo 214, Centro"* a 26 sp la cadena se come el
     * ancho entero y la tipografía grande deja de servir para algo.
     *
     * El control positivo va incluido y es lo que hace honesta la afirmación:
     * la ciudad **sí** se encuentra, en la línea de apoyo. Sin él, un cuadro que
     * hubiera tirado la ciudad a la basura pasaría igual de verde.
     */
    @Test
    fun `la calle del cuadro no incluye la ciudad`() {
        monta(conCoordenada = true)

        val calle = composeTestRule.onNodeWithTag(CALLE_DEL_CUADRO_TAG)
            .performScrollTo()
            .fetchSemanticsNode()
            .textoPlano()
        assertFalse(
            "el renglón grande dice \"$calle\": la ciudad volvió a pegarse a la calle",
            calle.contains(CIUDAD)
        )
        // Control positivo: la ciudad no se perdió, cambió de renglón.
        composeTestRule.onNodeWithTag(APOYO_DEL_CUADRO_TAG).assertTextEquals(APOYO)
    }

    /**
     * **Sin punto medido no se afirma que lo haya.** El chip dice "esta puerta
     * está medida"; sin coordenada esa frase es falsa, así que el chip no está —
     * y no se sustituye por uno apagado que diga lo contrario a media voz.
     */
    @Test
    fun `sin punto medido el chip no aparece`() {
        monta(conCoordenada = false)

        assertEquals(
            "se afirmó \"Punto medido\" en una puerta que ningún abono midió",
            0,
            composeTestRule.onAllNodesWithTag(PUNTO_MEDIDO_TAG).fetchSemanticsNodes().size
        )
        // Y la banda no queda muda: la calle sigue, que es lo que contesta
        // "¿es aquí?" parado en la banqueta.
        composeTestRule.onNodeWithTag(CALLE_DEL_CUADRO_TAG).performScrollTo()
            .assertTextEquals(CALLE)
    }

    /**
     * Control positivo del de arriba: el MISMO selector, con coordenada, sí
     * encuentra el chip. Sin esto, un `PUNTO_MEDIDO_TAG` que nadie pusiera nunca
     * —un `testTag` mal escrito, por ejemplo— también daría cero y el test de la
     * ausencia pasaría en verde sin medir nada.
     */
    @Test
    fun `control positivo - con punto medido el mismo selector si ve el chip`() {
        monta(conCoordenada = true)

        assertEquals(
            1,
            composeTestRule.onAllNodesWithTag(PUNTO_MEDIDO_TAG).fetchSemanticsNodes().size
        )
    }

    /**
     * **Con la calle en blanco no queda un renglón vacío.** El cuadro existe
     * para que esa banda nunca se lea como una pantalla a medio cargar, y un
     * renglón en blanco es exactamente eso. Va el mismo texto que ya dice la hoja
     * del mapa grande — [SIN_DIRECCION], un solo lugar para las dos pantallas.
     */
    @Test
    fun `con direccion vacia se dice que no hay direccion, no un hueco`() {
        monta(conCoordenada = true, calle = "")

        composeTestRule.onNodeWithTag(CALLE_DEL_CUADRO_TAG)
            .performScrollTo()
            .assertTextEquals(SIN_DIRECCION)
    }

    /**
     * Sin ciudad, la línea de apoyo lleva **sólo la ruta** — nunca un separador
     * colgando. Con la ciudad puesta el test de arriba ya midió la frase entera,
     * así que éste es el borde que faltaba.
     */
    @Test
    fun `sin ciudad la linea de apoyo lleva solo la ruta`() {
        monta(conCoordenada = true, ciudad = "")

        composeTestRule.onNodeWithTag(APOYO_DEL_CUADRO_TAG)
            .performScrollTo()
            .assertTextEquals(ZONA)
    }

    /**
     * **El dibujo de la casa ya no se pinta.**
     *
     * El dibujo eran dos [Icon] decorativos (`contentDescription = null`), o sea
     * **invisibles a semantics**: no dejó nunca un nodo ni un `testTag` que
     * preguntar, así que "no existe el tag del dibujo" sería una aserción que
     * pasa en verde sin medir nada. Lo que sí es medible es lo contrario: bajo el
     * cuadro ahora hay renglones de TEXTO, cosa que el dibujo no podía producir
     * ni con la coordenada puesta.
     *
     * El control positivo está en el test de abajo, que corre el mismo selector
     * sobre el dibujo viejo reconstruido y lo ve dar **cero**.
     */
    @Test
    fun `el cuadro ya no dibuja la casa - donde iba el dibujo hay texto`() {
        monta(conCoordenada = true)

        assertTrue(
            "el cuadro no trae ni un renglón de texto: volvió a ser una ilustración",
            renglonesDeTextoDelCuadro() >= RENGLONES_ESPERADOS
        )
    }

    @Test
    fun `control positivo - el dibujo viejo no dejaba ni un renglon de texto`() {
        composeTestRule.setContent {
            MspTheme(animateColors = false) {
                Box(modifier = Modifier.testTag(CUADRO_DE_LA_PUERTA_TAG)) {
                    DibujoViejoDeLaPuerta()
                }
            }
        }

        assertEquals(
            "el selector cuenta texto donde no lo hay: no sirve para afirmar el cambio",
            0,
            renglonesDeTextoDelCuadro()
        )
    }

    @Test
    fun `el suelo que entra por la ranura se pinta encima de las senas`() {
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

    /**
     * Cuántos renglones de TEXTO cuelgan del cuadro.
     *
     * Se cuenta sobre el árbol **sin fusionar**: el `clickable` del cuadro no
     * fusiona a sus descendientes, pero el chip sí junta su pin con su etiqueta,
     * y lo que se quiere contar son los renglones, no los nodos que Compose
     * decida agrupar.
     *
     * **No se mide con `boundsInRoot` contra la pantalla**, que es la trampa ya
     * medida en este repo: un nodo fuera del viewport devuelve `Rect.Zero` y pasa
     * cualquier aserción de contención. Contar texto es más honesto acá.
     */
    private fun renglonesDeTextoDelCuadro(): Int {
        val bajoElCuadro = hasAnyAncestor(hasTestTag(CUADRO_DE_LA_PUERTA_TAG)) and
            SemanticsMatcher.keyIsDefined(SemanticsProperties.Text)
        return composeTestRule.onAllNodes(bajoElCuadro, useUnmergedTree = true)
            .fetchSemanticsNodes().size
    }

    /**
     * El dibujo retirado, tal como era: el pin sobre la casa, los dos
     * decorativos. Vive acá **sólo como control positivo** — es lo que prueba
     * que [renglonesDeTextoDelCuadro] sabe dar cero, y por lo tanto que el cero
     * de una consulta ciega no se puede confundir con este hallazgo.
     */
    @Composable
    private fun DibujoViejoDeLaPuerta() {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = AccionesIconos.Pin,
                contentDescription = null,
                tint = MspTheme.colors.brand,
                modifier = Modifier.size(PIN_DEL_DIBUJO_VIEJO)
            )
            Spacer(Modifier.height(MspTheme.spacing.xs))
            Icon(
                imageVector = AccionesIconos.Casa,
                contentDescription = null,
                tint = MspTheme.colors.onSurfaceMuted,
                modifier = Modifier.size(CASA_DEL_DIBUJO_VIEJO)
            )
        }
    }

    private fun monta(
        conCoordenada: Boolean,
        calle: String = CALLE,
        ciudad: String = CIUDAD,
        onVerUbicacion: (() -> Unit)? = null,
        suelo: (@Composable (onTocar: () -> Unit) -> Unit)? = null
    ) {
        composeTestRule.setContent {
            CompositionLocalProvider(LocalFontSizeLevel provides FontSizeLevel.NORMAL) {
                MspTheme(animateColors = false) {
                    DetalleClienteContent(
                        state = DetalleClienteUiState(
                            cargando = false,
                            detalle = detalle(conCoordenada, calle, ciudad)
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

    /** El texto de un nodo, ya aplanado — un `Text` sólo trae uno. */
    private fun SemanticsNode.textoPlano(): String =
        config.getOrNull(SemanticsProperties.Text).orEmpty().joinToString(" ") { it.text }

    private fun detalle(conCoordenada: Boolean, calle: String, ciudad: String): DetalleCliente =
        PagosFixtures.detalleCliente().copy(
            calle = calle,
            ciudad = ciudad,
            ultimoCobroAqui = if (conCoordenada) COORDENADA else null
        )

    private companion object {
        const val SUELO_DE_PRUEBA = "suelo_de_prueba"
        const val ETIQUETA_AVAL = "Aval o responsable"
        const val ETIQUETA_TELEFONO_AVAL = "Teléfono del aval"
        const val ETIQUETA_VISITA = "Última visita"
        const val AVAL = "Rosa María Ramírez"
        const val TELEFONO_DEL_AVAL = "238 118 4402"

        /** Lo que dice el chip del cuadro cuando la puerta tiene coordenada. */
        const val PUNTO_MEDIDO = "Punto medido"

        /** Lo que el fixture trae, ya separado como lo trae el adaptador. */
        const val CALLE = "C. Hidalgo 214"
        const val CIUDAD = "Centro"
        const val ZONA = "ruta 25"

        /** La línea de apoyo del cuadro: ciudad primero, ruta después. */
        const val APOYO = "$CIUDAD · $ZONA"

        /**
         * Los tres renglones del cuadro con punto medido: chip, calle y ruta.
         * Se afirma `>=` y no `==` porque la calle puede partirse en dos líneas
         * sin dejar de ser UN nodo, y porque el número exacto no es el hallazgo:
         * el hallazgo es que hay texto donde antes había una ilustración.
         */
        const val RENGLONES_ESPERADOS = 3

        /** Las medidas del dibujo retirado, a escala normal: 28 y 56 dp del mock. */
        val PIN_DEL_DIBUJO_VIEJO = 28.dp
        val CASA_DEL_DIBUJO_VIEJO = 56.dp

        val COORDENADA = UbicacionDelCobro(lat = 18.4609, lng = -97.3926)
    }
}
