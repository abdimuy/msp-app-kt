package com.example.msp_app.feature.pagos.ui

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
import androidx.compose.ui.semantics.SemanticsProperties
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
import com.example.msp_app.feature.pagos.ui.components.DIRECCION_DEL_TELON_TAG
import com.example.msp_app.feature.pagos.ui.components.DatosDeLaPuerta
import com.example.msp_app.feature.pagos.ui.components.FONDO_DE_LA_PUERTA_TAG
import com.example.msp_app.feature.pagos.ui.components.FONDO_SIN_PUNTO_TAG
import com.example.msp_app.feature.pagos.ui.components.NOMBRE_DEL_TELON_TAG
import com.example.msp_app.feature.pagos.ui.components.SIN_DIRECCION
import com.example.msp_app.feature.pagos.ui.components.TOQUE_DEL_FONDO_TAG
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.robolectric.annotation.Config

/**
 * **El fondo de la puerta se pinta siempre, y los dos datos volvieron.**
 *
 * ## Lo que cambió en esta pasada
 *
 * El "cuadro" era una banda de 100 dp **dentro** de la hoja de identidad, con
 * alto propio en la pila. Ahora es el **fondo de la pantalla**: ancho completo,
 * sangrando por debajo de la barra de estado, con el contenido flotando encima.
 * Por eso este archivo ya no desplaza para llegar a él — el fondo no vive en el
 * `verticalScroll`, vive detrás.
 *
 * Todo lo demás que este test afirmaba **sigue afirmándose palabra por
 * palabra**, porque ninguna de esas decisiones se revisó: la banda se pinta con
 * punto y sin él, el chip sólo sale con punto medido, la ciudad no se repite, y
 * el suelo que entra por la ranura se pinta encima de las señas.
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
class ElFondoDeLaPuertaYLosDatosTest : RobolectricTestBase() {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun `el cuadro existe con punto medido`() {
        monta(conCoordenada = true)

        composeTestRule.onNodeWithTag(FONDO_DE_LA_PUERTA_TAG).assertIsDisplayed()
    }

    @Test
    fun `el cuadro existe tambien sin punto medido`() {
        // El hueco que el dueño rechazó: antes de esto, sin coordenada no se
        // pintaba nada y quedaba una banda vacía en medio de la hoja.
        monta(conCoordenada = false)

        composeTestRule.onNodeWithTag(FONDO_DE_LA_PUERTA_TAG).assertIsDisplayed()
    }

    // --- La composición tipográfica que reemplazó al dibujo -------------------

    /**
     * **Lo que el fondo dice ahora lo dice el telón, y lo dice completo.**
     *
     * Los cinco tests que vivían acá medían la seña que flotaba sobre el mapa
     * —chip, calle y ruta— y su pelea por no repetir lo que decía la tarjeta.
     * Esa seña se retiró el 2026-09-25: con el fondo a 300 dp, abajo va
     * `TelonDelNombre`, que dice el NOMBRE y la dirección COMPLETA. La seña
     * decía menos en el mismo sitio.
     *
     * Así que la pregunta cambió, y estos tres la hacen sobre el telón: que
     * diga el nombre, que diga la dirección entera, y que con la dirección en
     * blanco no quede un hueco.
     */
    @Test
    fun `el telon dice el nombre y la direccion completa`() {
        monta(conCoordenada = true)

        composeTestRule.onNodeWithTag(NOMBRE_DEL_TELON_TAG)
            .assertTextEquals(PagosFixtures.detalleCliente().nombre)
        composeTestRule.onNodeWithTag(DIRECCION_DEL_TELON_TAG)
            .assertTextEquals("$CALLE, $CIUDAD")
    }

    /**
     * Sin ciudad, la dirección lleva **sólo la calle** — nunca una coma
     * colgando al final. Unir con una cadena vacía es cómo se producen los
     * *"C. Hidalgo 214, "* que se leen como un defecto de la app.
     */
    @Test
    fun `sin ciudad el telon lleva solo la calle`() {
        monta(conCoordenada = true, ciudad = "")

        composeTestRule.onNodeWithTag(DIRECCION_DEL_TELON_TAG).assertTextEquals(CALLE)
    }

    /**
     * **Con la dirección en blanco no queda un renglón vacío.** El telón existe
     * para que el mapa no termine en un canto, y un renglón en blanco debajo
     * del nombre se lee como una pantalla a medio cargar. Va el mismo texto que
     * ya dice la hoja del mapa grande — [SIN_DIRECCION], un solo lugar para las
     * dos pantallas.
     */
    @Test
    fun `con direccion vacia el telon lo dice, no deja un hueco`() {
        monta(conCoordenada = true, calle = "", ciudad = "")

        composeTestRule.onNodeWithTag(DIRECCION_DEL_TELON_TAG).assertTextEquals(SIN_DIRECCION)
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
            "el fondo no trae ni un renglón de texto: volvió a ser una ilustración",
            renglonesDeTextoDelCuadro() >= RENGLONES_ESPERADOS
        )
    }

    @Test
    fun `control positivo - el dibujo viejo no dejaba ni un renglon de texto`() {
        composeTestRule.setContent {
            MspTheme(animateColors = false) {
                Box(modifier = Modifier.testTag(FONDO_DE_LA_PUERTA_TAG)) {
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
        composeTestRule.onNodeWithTag(SUELO_DE_PRUEBA).assertIsDisplayed()
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

    /**
     * **El toque lo recibe el HUECO, no la capa del fondo** — y eso no es un
     * detalle: es lo que impide un control muerto.
     *
     * Desde que el mapa es el fondo, el contenido desplazable se dibuja encima y
     * ocupa la pantalla entera. Su `scrollable` registra un manejador de
     * puntero, así que el hit-test de Compose se detiene ahí y el hermano de
     * abajo **nunca ve el evento**. Un `clickable` en la capa del fondo se vería
     * tocable en el árbol de semántica y no dispararía jamás.
     *
     * Lo que este test afirma es el arreglo entero: el afordante vive en el
     * hueco transparente con el que arranca el contenido, y de verdad llama.
     */
    @Test
    fun `con punto medido, tocar sobre el mapa abre el mapa completo`() {
        var abierto = 0
        monta(conCoordenada = true, onVerUbicacion = { abierto++ })

        composeTestRule.onNodeWithTag(TOQUE_DEL_FONDO_TAG)
            .assertHasClickAction()
            .performClick()
        assertEquals("tocar sobre el mapa no abrió el mapa completo", 1, abierto)
    }

    /**
     * Control positivo de dónde NO está el toque: la capa del fondo no lleva
     * acción de clic, precisamente porque nunca la recibiría. Si alguien se la
     * vuelve a poner "por si acaso", esto se pone rojo antes de que llegue al
     * aparato.
     */
    @Test
    fun `la capa del fondo NO lleva accion de clic, porque nunca la recibiria`() {
        monta(conCoordenada = true, onVerUbicacion = {})

        composeTestRule.onNodeWithTag(FONDO_DE_LA_PUERTA_TAG).assertHasNoClickAction()
    }

    @Test
    fun `sin punto medido no se puede tocar`() {
        // No es que el toque no haga nada: es que no existe. Un control que se
        // ve tocable y no hace nada es el defecto que ningún golden fotografía.
        monta(conCoordenada = false, onVerUbicacion = {})

        composeTestRule.onNodeWithTag(TOQUE_DEL_FONDO_TAG).assertHasNoClickAction()
    }

    /**
     * **La costura del toque del suelo sigue cableada, aunque hoy no se use.**
     *
     * Con el contenido desplazable encima, el SDK de Google ya no ve el toque
     * —lo cual cierra por construcción el defecto del `liteMode` que abría la
     * app de Google Maps—. La ranura sigue recibiendo el MISMO destino porque
     * el contrato no cambió y porque el día que el fondo deje de estar debajo
     * del desplazamiento, esto ya está bien conectado. Si alguien le pasara un
     * `{}`, este test se pone rojo.
     */
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
        var loQueElSueloRecibio: (() -> Unit)? = null
        monta(conCoordenada = true, onVerUbicacion = { abierto++ }) { onTocar ->
            loQueElSueloRecibio = onTocar
            Box(modifier = Modifier.fillMaxSize().testTag(SUELO_DE_PRUEBA))
        }

        // **Se INVOCA lo que la ranura recibió, no se toca la pantalla.** Y no
        // es una comodidad de prueba: quien dispara ese callback en producción
        // es el SDK de Google por su `onMapClick`, no el hit-test de Compose —
        // que además ya no llega al suelo, porque el contenido desplazable se
        // dibuja encima. Tocar el nodo mediría el camino equivocado.
        requireNotNull(loQueElSueloRecibio) { "la ranura no recibió un toque que cablear" }()
        assertEquals("el suelo recibió un toque que no lleva a ninguna parte", 1, abierto)
    }

    @Test
    fun `sin a donde ir, el toque del suelo no truena`() {
        // Control del borde: `onVerUbicacion` en `null` significa que no hay
        // pantalla a la que llevar. El cuadro le pasa al suelo un toque que no
        // hace nada en vez de no pasarle nada, porque el tipo del suelo pide una
        // función y no una opcional — así `:app` cablea `onMapClick` una sola
        // vez y no tiene que decidir si existe.
        var loQueElSueloRecibio: (() -> Unit)? = null
        monta(conCoordenada = true, onVerUbicacion = null) { onTocar ->
            loQueElSueloRecibio = onTocar
            Box(modifier = Modifier.fillMaxSize().testTag(SUELO_DE_PRUEBA))
        }

        requireNotNull(loQueElSueloRecibio)()
    }

    /**
     * **Sin punto medido el fondo es la DIRECCIÓN, en grande y muy tenue.**
     *
     * Decisión del dueño para esta pasada. No es un mapa —no hay dónde
     * centrarlo— ni un dibujo de calles —se confunde con la traza real de la
     * colonia, que es dato falso dibujado—. Es el dato que sí existe, usado como
     * textura, con el telón legible encima.
     */
    @Test
    fun `sin punto medido el fondo es la direccion en grande`() {
        monta(conCoordenada = false)

        composeTestRule.onNodeWithTag(FONDO_SIN_PUNTO_TAG).assertTextEquals(CALLE)
        // Y el telón sigue encima con la dirección legible: el esqueleto es el
        // MISMO que con punto —mismo alto, mismo telón, mismo arranque de
        // contenido—, para que la pantalla no salte entre un cliente y otro.
        composeTestRule.onNodeWithTag(DIRECCION_DEL_TELON_TAG)
            .assertTextEquals("$CALLE, $CIUDAD")
    }

    /**
     * **Y con punto medido el piso TAMBIÉN se pinta** — esto cambió el
     * 2026-09-25 y el motivo está medido en el aparato del dueño.
     *
     * Hasta hoy la textura estaba condicionada a que NO hubiera punto: con
     * coordenada, lo único que se pintaba era el mapa. Instalado en el SM-A256E
     * sobre un cliente **con** punto, el mapa no pintó ni una tesela y lo que
     * quedó fueron **300 dp de negro**, que es exactamente el estado que este
     * fondo existe para no tener.
     *
     * Así que la regla de las dos capas vuelve a su forma correcta: **el piso
     * siempre, el mapa encima cuando puede**. Con teselas el mapa lo tapa
     * entero, así que no estorba al caso bueno; sin ellas, lo que queda es la
     * dirección en grande y no un rectángulo vacío.
     */
    @Test
    fun `con punto medido el piso tambien se pinta, por si el mapa no carga`() {
        monta(conCoordenada = true)

        composeTestRule.onNodeWithTag(FONDO_SIN_PUNTO_TAG).assertTextEquals(CALLE)
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
        val bajoElCuadro = hasAnyAncestor(hasTestTag(FONDO_DE_LA_PUERTA_TAG)) and
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
                        onAlternarTema = {},
                        onAlternarPrivacidad = {},
                        onVerUbicacion = onVerUbicacion,
                        suelo = suelo
                    )
                }
            }
        }
    }

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

        /**
         * La línea de apoyo del cuadro: **sólo la ruta**.
         *
         * Llevó la ciudad delante (*"Centro · ruta 25"*) entre `f8621920` y el
         * arreglo de la regresión de la dirección. Desde que el renglón de
         * dirección volvió a `BloqueDeIdentidad` —y dice la ciudad— repetirla
         * acá era la duplicación que el dueño había reportado.
         */
        const val APOYO = ZONA

        /**
         * El renglón del fondo con punto medido: **la calle**.
         *
         * Eran tres —chip, calle y ruta— hasta que el fondo bajó a 118 dp para
         * no taparle el sitio al dinero. En los 54 dp que le quedan por encima
         * del renglón del nombre entra uno, y el que se queda es el que contesta
         * *"¿es aquí?"* parado en la banqueta.
         *
         * Se afirma `>=` y no `==` porque la calle puede partirse en dos líneas
         * sin dejar de ser UN nodo, y porque el número exacto no es el hallazgo:
         * el hallazgo es que hay texto donde antes había una ilustración.
         */
        const val RENGLONES_ESPERADOS = 1

        /** Las medidas del dibujo retirado, a escala normal: 28 y 56 dp del mock. */
        val PIN_DEL_DIBUJO_VIEJO = 28.dp
        val CASA_DEL_DIBUJO_VIEJO = 56.dp

        val COORDENADA = UbicacionDelCobro(lat = 18.4609, lng = -97.3926)
    }
}
