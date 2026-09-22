package com.example.msp_app.feature.pagos.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import com.example.msp_app.core.designsystem.theme.FontSizeLevel
import com.example.msp_app.core.designsystem.theme.LocalFontSizeLevel
import com.example.msp_app.core.designsystem.theme.MspTheme
import com.example.msp_app.core.testing.RobolectricTestBase
import com.example.msp_app.feature.pagos.domain.FiltroDeContactos
import com.example.msp_app.feature.pagos.domain.model.BitacoraCompleta
import com.example.msp_app.feature.pagos.ui.components.AVISO_DERECHA_TAG
import com.example.msp_app.feature.pagos.ui.components.AVISO_IZQUIERDA_TAG
import com.example.msp_app.feature.pagos.ui.components.CHIP_DE_SEGMENTO_TAG
import com.example.msp_app.feature.pagos.ui.components.ETIQUETA_DEL_SEGMENTO_TAG
import com.example.msp_app.feature.pagos.ui.components.FILTRO_TAG
import com.example.msp_app.feature.pagos.ui.components.ORILLA_ATRAS_TAG
import com.example.msp_app.feature.pagos.ui.components.ORILLA_CON_MAS_TAG
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * **A cualquier escala de letra, ninguna opción se esconde sin aviso.**
 *
 * ## La prueba que estaba aquí afirmaba esto y no lo medía
 *
 * Hasta el **2026-09-22** este archivo tenía un solo test, verde, que decía
 * *«a MUY_GRANDE las cuatro etiquetas y conteos quedan completos y dentro de la
 * pantalla»* mientras su propio golden (`pagos_bitacora_light_2_0.png`) mostraba
 * "Visitas" rebanado y "Promesas" ausente. Medía con `boundsInRoot`, y
 * `boundsInRoot` **viene recortado por los padres**. El mecanismo se midió, no
 * se dedujo: a `MUY_GRANDE`, en `w360dp-xhdpi`, el chip escondido reportaba
 *
 * ```
 * chip=PAGADOS recortado=Rect.fromLTRB(0,0,0,0) size=274x100 posRoot=(849,276)
 * ```
 *
 * `Rect.Zero`. Las dos aserciones de entonces —`left >= pantalla.left` y
 * `right <= pantalla.right`— las cumple un rectángulo vacío sin esfuerzo, así
 * que el chip que NO se veía era el que más fácil pasaba. Y a `GRANDE` el mismo
 * chip reportaba `680..688` de sus 214px: ocho píxeles, también "dentro de la
 * pantalla". `assertTextContains` tampoco ayudaba: lee la semántica, que trae el
 * texto completo aunque no se pinte un solo píxel de él.
 *
 * ## Lo que se mide ahora
 *
 * `boundsInRoot` (**recortado**) contra `size` (**sin recortar**). Si el nodo
 * perdió ancho o alto contra su propio tamaño, no se ve entero — y da igual si
 * le falta un 90% o un 2%: media palabra es un dato falso. De ahí salen los
 * escondidos, y de ahí el invariante:
 *
 * > **o las cuatro opciones se ven completas, o el aviso contado dice
 * > exactamente cuántas no.**
 *
 * Es a propósito que el invariante no exija que las cuatro se vean: a 1.5 y a
 * 2.0 no caben —la fila pide 428dp y 542dp contra 325dp de ventana— y forzarlo
 * devolvería la rejilla de dos renglones que el dueño rechazó. Lo que no se
 * negocia es que esconder una opción sea **silencioso**.
 *
 * Se cobra en las DOS pantallas que comparten el control —la bitácora
 * (`FiltrosDeContacto`) y la lista de clientes (`SegmentadoDeCobranza`)—: son
 * las etiquetas de la lista ("Sin visitar") las que primero dejan de caber.
 *
 * **Pinta la pantalla real, no el control suelto.** Un primer intento montó sólo
 * `FiltrosDeContacto` dentro de un `Box` con el margen de `BitacoraScreen`
 * copiado a mano, y NO reprodujo el defecto: la aritmética de márgenes a mano no
 * es la pantalla real. Aquí se monta el mismo contenido que produce los goldens.
 */
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [33], qualifiers = "w360dp-h800dp-xhdpi")
class ElControlSegmentadoNoSeRompeALetraGrandeTest : RobolectricTestBase() {

    @get:Rule
    val composeTestRule = createComposeRule()

    // --- La bitácora: Todos / Cobros / Visitas / Promesas ------------------

    @Test
    fun `bitacora a NORMAL ninguna opcion se esconde sin aviso`() {
        pinta(FontSizeLevel.NORMAL) { Bitacora() }
        cobraElInvariante(tagsDeLaBitacora())
    }

    @Test
    fun `bitacora a GRANDE ninguna opcion se esconde sin aviso`() {
        pinta(FontSizeLevel.GRANDE) { Bitacora() }
        cobraElInvariante(tagsDeLaBitacora())
    }

    @Test
    fun `bitacora a MUY_GRANDE ninguna opcion se esconde sin aviso`() {
        pinta(FontSizeLevel.MUY_GRANDE) { Bitacora() }
        cobraElInvariante(tagsDeLaBitacora())
    }

    // --- La lista: Sin visitar / Volver / Después / Pagados ----------------

    @Test
    fun `lista a NORMAL ninguna opcion se esconde sin aviso`() {
        pinta(FontSizeLevel.NORMAL) { Lista() }
        cobraElInvariante(tagsDeLaLista())
    }

    @Test
    fun `lista a GRANDE ninguna opcion se esconde sin aviso`() {
        pinta(FontSizeLevel.GRANDE) { Lista() }
        cobraElInvariante(tagsDeLaLista())
    }

    @Test
    fun `lista a MUY_GRANDE ninguna opcion se esconde sin aviso`() {
        pinta(FontSizeLevel.MUY_GRANDE) { Lista() }
        cobraElInvariante(tagsDeLaLista())
    }

    /**
     * **El aviso no sólo cuenta: lleva.**
     *
     * Un degradado dice "hay más" y deja al cobrador adivinando el gesto. Esto
     * cobra que tocarlo trae de verdad una opción escondida a la vista completa
     * —la PRIMERA, no la última: saltar al extremo le quitaría la referencia de
     * dónde estaba— y que a fuerza de tocarlo se llega a ver cada una.
     *
     * El invariante se cobra **después de cada toque**, no sólo al final: el
     * aviso tiene que seguir diciendo la verdad a media travesía, cuando lo
     * escondido está repartido entre los dos costados.
     */
    @Test
    fun `tocando el aviso se llega a ver cada opcion escondida`() {
        pinta(FontSizeLevel.MUY_GRANDE) { Lista() }
        val tags = tagsDeLaLista()
        val escondidasAlInicio = tags.filterNot { seLeeCompleta(it) }
        assertFalse(
            "el fixture ya no reproduce el caso: a MUY_GRANDE caben las cuatro",
            escondidasAlInicio.isEmpty()
        )

        val primera = escondidasAlInicio.first()
        tocaElAviso()
        assertTrue("tras un toque, $primera sigue sin leerse completa", seLeeCompleta(primera))
        cobraElInvariante(tags)

        var vueltas = 0
        while (cuentaDelAviso(AVISO_DERECHA_TAG) > 0 && vueltas < tags.size) {
            tocaElAviso()
            cobraElInvariante(tags)
            vueltas++
        }
        assertEquals(
            "el aviso derecho no se agota a fuerza de tocarlo",
            0,
            cuentaDelAviso(AVISO_DERECHA_TAG)
        )
        assertTrue("la última opción nunca llegó a leerse", seLeeCompleta(tags.last()))
    }

    private fun tocaElAviso() {
        composeTestRule.onNodeWithTag(AVISO_DERECHA_TAG).performClick()
        composeTestRule.waitForIdle()
    }

    /**
     * **Ningún rótulo baja a dos renglones ni se elide.**
     *
     * `ElUmbralDeFilaCuentaElPaddingTest` barre anchos sintéticos; esto lo cobra
     * sobre la pantalla real y con las etiquetas reales, que es donde la
     * compresión a dos renglones se vio la primera vez.
     */
    @Test
    fun `a MUY_GRANDE ningun rotulo se comprime ni se elide`() {
        pinta(FontSizeLevel.MUY_GRANDE) { Lista() }
        etiquetasColocadas().forEach { etiqueta ->
            val layout = layoutDe(etiqueta)
            assertEquals(
                "el rótulo \"${textoDe(etiqueta)}\" bajó a ${layout.lineCount} renglones",
                1,
                layout.lineCount
            )
            (0 until layout.lineCount).forEach { renglon ->
                assertFalse(
                    "el rótulo \"${textoDe(etiqueta)}\" lleva elipsis en el renglón $renglon",
                    layout.isLineEllipsized(renglon)
                )
            }
        }
    }

    // --- El invariante ----------------------------------------------------

    /**
     * O las opciones se ven completas, o los avisos suman exactamente las que
     * no.
     *
     * Se suman los dos costados porque lo escondido cambia de lado al deslizar:
     * al final del recorrido lo que falta está atrás, y un aviso que sólo mirara
     * hacia adelante dejaría de contar justo entonces.
     */
    private fun cobraElInvariante(tags: List<String>) {
        val escondidos = tags.filterNot { seLeeCompleta(it) }
        val anunciados = cuentaDelAviso(AVISO_IZQUIERDA_TAG) + cuentaDelAviso(AVISO_DERECHA_TAG)
        assertEquals(
            "se esconden ${escondidos.size} opciones ($escondidos) y los avisos " +
                "anuncian $anunciados",
            escondidos.size,
            anunciados
        )
        // El aviso mismo tiene que verse: un aviso recortado no avisa de nada, y
        // es justo el fallo que se está corrigiendo, una vuelta más adentro.
        listOf(AVISO_IZQUIERDA_TAG, AVISO_DERECHA_TAG)
            .filter { cuentaDelAviso(it) > 0 }
            .forEach { tag ->
                assertTrue("el aviso $tag no se ve completo", seVeCompleto(nodo(tag)))
            }
    }

    // --- Lecturas ---------------------------------------------------------

    /**
     * **La medición honesta**, en dos mitades.
     *
     * 1. `boundsInRoot` (**recortado** por los padres) contra `size` (**sin
     *    recortar**): si el recorte le comió ancho o alto, el chip no se ve
     *    entero. Un nodo del todo fuera del viewport reporta `Rect.Zero`, que
     *    por esta comparación es "no se ve" — y por la comparación vieja, contra
     *    los bordes de la pantalla, era "perfectamente dentro".
     * 2. Que no tenga encima el degradado de la orilla. Se lee de los nodos
     *    `ORILLA_*` **pintados**, no de la constante de producción: si algún día
     *    la orilla cambia de ancho, esta prueba mide la que se pintó.
     *
     * La segunda mitad no es teórica. A `GRANDE`, "Después" quedaba entero
     * dentro de la ventana y su conteo se desvanecía a blanco bajo la orilla; el
     * aviso decía "+1" y había dos datos ilegibles en pantalla.
     */
    private fun seLeeCompleta(tag: String): Boolean {
        val chip = nodo(tag)
        if (!seVeCompleto(chip)) return false
        return orillas().none { it.overlaps(chip.boundsInRoot) }
    }

    private fun seVeCompleto(nodo: SemanticsNode): Boolean {
        if (!nodo.layoutInfo.isPlaced) return false
        val recortado = nodo.boundsInRoot
        return recortado.width >= nodo.size.width - TOLERANCIA_PX &&
            recortado.height >= nodo.size.height - TOLERANCIA_PX
    }

    private fun orillas(): List<Rect> = listOf(ORILLA_ATRAS_TAG, ORILLA_CON_MAS_TAG)
        .flatMap {
            composeTestRule.onAllNodesWithTag(it, useUnmergedTree = true).fetchSemanticsNodes()
        }
        .map { it.boundsInRoot }

    private fun nodo(tag: String): SemanticsNode =
        composeTestRule.onNodeWithTag(tag).fetchSemanticsNode()

    /** Lo que anuncia un aviso, o 0 si ese costado no tiene aviso. */
    private fun cuentaDelAviso(tag: String): Int {
        val nodos = composeTestRule.onAllNodesWithTag(tag).fetchSemanticsNodes()
        if (nodos.isEmpty()) return 0
        val texto = textoDe(nodos.single())
        return texto.removePrefix("+").toIntOrNull()
            ?: error("el aviso $tag dice \"$texto\", que no es un conteo")
    }

    private fun etiquetasColocadas(): List<SemanticsNode> = composeTestRule.onAllNodes(
        hasTestTag(ETIQUETA_DEL_SEGMENTO_TAG),
        useUnmergedTree = true
    ).fetchSemanticsNodes().filter { it.layoutInfo.isPlaced }

    private fun textoDe(nodo: SemanticsNode): String =
        nodo.config.getOrNull(SemanticsProperties.Text).orEmpty().joinToString("") { it.text }

    private fun layoutDe(nodo: SemanticsNode): TextLayoutResult {
        val resultados = mutableListOf<TextLayoutResult>()
        nodo.config[SemanticsActions.GetTextLayoutResult].action?.invoke(resultados)
        return resultados.single()
    }

    // --- Las dos pantallas ------------------------------------------------

    private fun tagsDeLaBitacora(): List<String> =
        FiltroDeContactos.entries.map { FILTRO_TAG + it.name }

    private fun tagsDeLaLista(): List<String> =
        SegmentoDeCobranza.entries.map { CHIP_DE_SEGMENTO_TAG + it.name.lowercase() }

    private fun pinta(nivel: FontSizeLevel, contenido: @Composable () -> Unit) {
        composeTestRule.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(
                LocalDensity provides Density(density.density, nivel.nominalScale),
                LocalFontSizeLevel provides nivel
            ) {
                MspTheme(darkTheme = false, animateColors = false) {
                    contenido()
                }
            }
        }
        composeTestRule.waitForIdle()
    }

    @Composable
    private fun Bitacora() {
        BitacoraContent(
            state = BitacoraUiState(cargando = false, bitacora = BITACORA),
            onAtras = {}
        )
    }

    @Composable
    private fun Lista() {
        val proyeccion = CarteraEnPantalla.proyectar(
            clientes = ListaFixtures.rutaConPromesaDeHoy(),
            segmento = SegmentoDeCobranza.SIN_VISITAR,
            query = "",
            hoy = ListaFixtures.HOY
        )
        ListaDeClientesContent(
            state = ListaDeClientesUiState(
                cargando = false,
                clientes = proyeccion.clientes,
                conteos = proyeccion.conteos,
                segmento = SegmentoDeCobranza.SIN_VISITAR
            ),
            onBuscar = {},
            onElegirSegmento = {},
            onAbrirCliente = {},
            onReintentar = {},
            onAlternarTema = {},
            onAlternarPrivacidad = {}
        )
    }

    private companion object {
        /** Medio dp en `xhdpi`, por redondeo a píxel — igual que [ElRenglonDeAbajoNoSeSaleTest]. */
        const val TOLERANCIA_PX = 1f

        /** La MISMA fixture que produce `pagos_bitacora_*` — 5 contactos: 3 cobros, 2 visitas, 1 promesa. */
        val BITACORA: BitacoraCompleta = PagosFixtures.detalleCliente().let { detalle ->
            BitacoraCompleta(
                clienteId = detalle.clienteId,
                nombre = detalle.nombre,
                direccion = detalle.direccion,
                contactos = detalle.contactos,
                hoy = detalle.hoy
            )
        }
    }
}
