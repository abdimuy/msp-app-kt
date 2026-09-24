package com.example.msp_app.feature.pagos.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.dp
import com.example.msp_app.core.designsystem.theme.FontSizeLevel
import com.example.msp_app.core.designsystem.theme.LocalFontSizeLevel
import com.example.msp_app.core.designsystem.theme.MspTheme
import com.example.msp_app.core.testing.RobolectricTestBase
import com.example.msp_app.feature.pagos.domain.model.FichaDelCliente
import com.example.msp_app.feature.pagos.domain.model.SenalDeFicha
import com.example.msp_app.feature.pagos.ui.components.ALTERNAR_LA_NOTA_TAG
import com.example.msp_app.feature.pagos.ui.components.ANTIGUEDAD_DE_LA_NOTA_TAG
import com.example.msp_app.feature.pagos.ui.components.EDITAR_NOTA_DESTACADA_TAG
import com.example.msp_app.feature.pagos.ui.components.INDICADOR_DE_LA_NOTA_TAG
import com.example.msp_app.feature.pagos.ui.components.NOTA_DESTACADA_TAG
import com.example.msp_app.feature.pagos.ui.components.PARCIALIDAD_DEL_CLIENTE_TAG
import com.example.msp_app.feature.pagos.ui.components.TEXTO_DE_LA_NOTA_TAG
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * **La nota subió, y se nota.**
 *
 * El dueño la vio en vidrio y dijo lo que había que arreglar: *"están hasta
 * abajo y en diminuto, casi no se ven"*. Lo que esto fija es la forma concreta
 * que eligió — una tarjeta propia, arriba del dinero, con rótulo, antigüedad y
 * un botón que abre la misma hoja que el dock.
 *
 * ## Qué NO se afirma acá, y por qué
 *
 * El color. En Robolectric no hay píxeles que leer: el ámbar lo miran los
 * goldens `pagos_cliente_*` y `pagos_venta_nota_*`. Acá se afirma geometría,
 * texto y callbacks.
 *
 * ## La trampa que este repo ya pagó
 *
 * Para decir que algo "se ve" **no sirve** comparar `boundsInRoot` contra el
 * tamaño de la pantalla: un nodo fuera del viewport devuelve `Rect.Zero` y
 * pasa cualquier aserción de contención, así que el test daría verde
 * exactamente en el caso que existe para cazar. Acá se compara **contra otro
 * nodo** —el dinero— y se usa `assertIsDisplayed`, que sí mira el viewport.
 */
@Config(qualifiers = "w360dp-h800dp-xhdpi")
class LaNotaSeVeArribaTest : RobolectricTestBase() {

    @get:Rule
    val composeTestRule = createComposeRule()

    private var abrio = 0

    /**
     * La ficha que la pantalla pinta. Es estado y no parámetro para que un
     * mismo test pueda **cambiarla y volver a medir**: `setContent` se llama
     * una sola vez por prueba, y comparar la pantalla con nota contra la misma
     * pantalla sin ella es justo lo que hace de control positivo.
     */
    private val fichaEnPantalla = mutableStateOf<FichaDelCliente?>(null)

    // --- El detalle de cliente: la nota del cobrador -------------------------

    @Test
    fun `con nota, la tarjeta se ve arriba del dinero`() {
        cliente()

        composeTestRule.onNodeWithTag(NOTA_DESTACADA_TAG).assertIsDisplayed()
        val tarjeta = bordesDe(NOTA_DESTACADA_TAG)
        val dinero = bordesDe(PARCIALIDAD_DEL_CLIENTE_TAG)
        assertTrue(
            "la tarjeta termina en " + tarjeta.bottom + " y el dinero empieza en " +
                dinero.top + ": la nota no quedó arriba",
            tarjeta.bottom <= dinero.top
        )
    }

    /**
     * **El rótulo y la antigüedad no se pegan a escala MUY GRANDE.**
     *
     * El defecto que esto caza salió del golden, no de una sospecha: la primera
     * versión del renglón era un `Row` con `SpaceBetween`, y `SpaceBetween`
     * reparte el sobrante — cuando no hay sobrante no separa nada. A 2.0
     * `pagos_cliente_light_2_0` mostraba **"LO QUE ANOTASTEhace…"**, sin un dp
     * de aire y con la antigüedad cortada a media palabra.
     *
     * La aserción admite las dos formas de no pegarse: seguir en el mismo
     * renglón con aire de por medio, o que la antigüedad **baje** al renglón de
     * abajo, que es lo que `FlowRow` hace cuando ya no caben.
     *
     * ## [GraphicsMode.Mode.NATIVE] no es adorno: sin él este test miente
     *
     * Medido, no supuesto. Con los gráficos legado de Robolectric este mismo
     * test **daba verde con el `Row` puesto**: el rótulo reportaba 8.0 dp de
     * ancho y la antigüedad 6.5 dp, porque sin gráficos nativos no hay métricas
     * de texto reales y entonces todo cabe. Con `NATIVE` los números son los de
     * verdad —rótulo hasta 260.0 dp, antigüedad desde 260.0 dp— y el test falla
     * contra el `Row`, que es la única forma de saber que sirve de algo.
     *
     * Es la misma razón por la que los goldens lo llevan en
     * `PagosScreenshotTest`: cualquier aserción sobre **ancho de texto** lo
     * necesita. Las otras de este archivo no, porque comparan bordes de
     * tarjetas, y ahí el alto lo fija el layout y no la tipografía.
     */
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    @Test
    fun `a escala MUY GRANDE el rotulo y la antiguedad no se encinan`() {
        cliente(nivel = FontSizeLevel.MUY_GRANDE)

        val rotulo = composeTestRule.onNodeWithText("LO QUE ANOTASTE", useUnmergedTree = true)
            .getUnclippedBoundsInRoot()
        val antiguedad = composeTestRule
            .onNodeWithTag(ANTIGUEDAD_DE_LA_NOTA_TAG, useUnmergedTree = true)
            .getUnclippedBoundsInRoot()
        val bajo = antiguedad.top >= rotulo.bottom
        val alLado = antiguedad.left >= rotulo.right + AIRE_MINIMO
        assertTrue(
            "el rótulo termina en " + rotulo.right + "/" + rotulo.bottom +
                " y la antigüedad empieza en " + antiguedad.left + "/" + antiguedad.top +
                ": quedaron encimadas o pegadas",
            bajo || alLado
        )
        // Y no se paga con recortar ninguno de los dos: los dos textos siguen
        // enteros, que es lo que un `weight` habría sacrificado.
        composeTestRule.onNodeWithTag(ANTIGUEDAD_DE_LA_NOTA_TAG, useUnmergedTree = true)
            .assertTextEquals("hace 1 semana")
    }

    @Test
    fun `la tarjeta dice la nota, su rotulo y su antiguedad`() {
        cliente()

        composeTestRule.onNodeWithText("LO QUE ANOTASTE").assertIsDisplayed()
        composeTestRule.onNodeWithText(NOTA, substring = true).assertIsDisplayed()
        // "hace 1 semana": la nota del fixture es del 24-ago y el "hoy" de la
        // pantalla es el 1-sep. Se lo pide a `TiempoRelativo`, que es donde ya
        // vivía cuando la nota se pintaba al fondo — acá no se recalcula nada.
        composeTestRule.onNodeWithTag(ANTIGUEDAD_DE_LA_NOTA_TAG, useUnmergedTree = true)
            .assertTextEquals("hace 1 semana")
    }

    /**
     * **Sin nota, la tarjeta no está** — y el control positivo es la misma
     * pantalla con la nota puesta, medida con el MISMO selector. Sin esa mitad,
     * un `testTag` mal escrito daría cero siempre y el test pasaría sin
     * distinguir nada.
     *
     * Lo que esto protege es concreto: un bloque vacío arriba empujaría el
     * saldo hacia abajo para no decir nada, que es el costo que la tarjeta sólo
     * puede cobrar cuando hay algo que leer.
     */
    @Test
    fun `sin nota no hay tarjeta, y con nota si`() {
        cliente(ficha = FichaDelCliente(senales = setOf(SenalDeFicha.ESTA_EN_LA_NOCHE)))

        assertEquals(
            "se pintó la tarjeta de una puerta sin nada anotado",
            0,
            cuantasTarjetas()
        )

        fichaEnPantalla.value = PagosFixtures.fichaDelCliente()
        composeTestRule.waitForIdle()
        assertEquals("el mismo selector no ve la tarjeta ni con nota", 1, cuantasTarjetas())
    }

    /** La ficha ilegible tampoco inventa una tarjeta: no hay nota que enseñar. */
    @Test
    fun `con la ficha ilegible tampoco hay tarjeta`() {
        cliente(ficha = null)

        assertEquals(0, cuantasTarjetas())
    }

    @Test
    fun `el boton de la tarjeta abre la hoja de edicion`() {
        cliente()

        composeTestRule.onNodeWithTag(EDITAR_NOTA_DESTACADA_TAG).performClick()

        assertEquals("el botón de la tarjeta no abrió las Notas", 1, abrio)
    }

    /**
     * **La nota se escribe UNA vez.** Subirla y dejarla también al fondo
     * pondría el mismo párrafo dos veces en la pantalla que pelea cada dp: no
     * informa el doble, sólo baja todo lo demás.
     *
     * El control positivo es el propio `assertEquals`, que exige UNO y no CERO
     * — un selector ciego daría cero y también fallaría.
     */
    @Test
    fun `la nota no se pinta dos veces en la pantalla`() {
        cliente()

        assertEquals(
            "la nota se pinta dos veces: la sección del fondo volvió a decirla",
            1,
            composeTestRule.onAllNodesWithText(NOTA, substring = true)
                .fetchSemanticsNodes().size
        )
    }

    // --- La tarjeta adelgazada: botón arriba y nota de dos renglones ---------

    /**
     * **El botón de editar comparte renglón con el rótulo**, y no gasta uno
     * propio.
     *
     * Lo que se afirma es geometría y no estilo: el botón termina **antes** de
     * que empiece el párrafo de la nota. En la versión anterior vivía debajo del
     * párrafo, así que esta misma aserción la habría puesto roja — que es lo que
     * la hace servir de algo.
     *
     * Los 23.5 dp que esto devolvió al dinero los mide
     * `LaFichaSeVeYSeTocaTest`; acá sólo se fija la forma.
     */
    @Test
    fun `el boton de editar va en el renglon del rotulo, no debajo de la nota`() {
        cliente()

        val boton = bordesDe(EDITAR_NOTA_DESTACADA_TAG)
        val texto = composeTestRule.onNodeWithTag(TEXTO_DE_LA_NOTA_TAG, useUnmergedTree = true)
            .getUnclippedBoundsInRoot()
        assertTrue(
            "el botón va de " + boton.top + " a " + boton.bottom + " y la nota empieza en " +
                texto.top + ": el botón volvió a gastar su propio renglón",
            boton.bottom <= texto.top
        )
    }

    /**
     * **El área tocable de *"Editar"* sigue midiendo 50 dp aunque ya no se
     * vea.**
     *
     * Ésta es la prueba que este arreglo necesitaba. La queja del dueño fue
     * que el botón *"se ve horrible"* —una caja rellena de 50 dp recortada en
     * la tarjeta ámbar— y la corrección fue quitarle la superficie pintada.
     * El riesgo de esa corrección tiene nombre: **quitar la caja y llevarse
     * con ella el piso tocable**, dejando un blanco de 19 dp de alto al que
     * hay que apuntar desde la banqueta. Lo visible se encogió; **lo tocable
     * no**, y eso es lo que se afirma acá.
     *
     * Se miden los **dos ejes** a propósito. El alto lo fija `heightIn` y
     * costaría trabajo romperlo sin querer; el **ancho** depende de que el
     * `widthIn` siga puesto, porque la palabra *"Editar"* más su relleno mide
     * menos de 50 dp y sin ese mínimo el objetivo se angosta solo.
     *
     * [GraphicsMode.Mode.NATIVE] no es ceremonia: sin métricas de fuente
     * reales el ancho del texto lo inventa Robolectric, y una aserción de
     * ancho daría verde contra cualquier cosa — la trampa que este repo ya
     * documentó.
     *
     * El control positivo es el propio umbral: se compara contra
     * [ALTO_TOCABLE_ESPERADO] y no contra cero, así que un nodo que midiera
     * `Rect.Zero` —el caso que de verdad da falsos verdes— lo pondría rojo.
     */
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    @Test
    fun `editar dejo de pintarse pero sigue midiendo 50dp tocables`() {
        cliente()

        val boton = bordesDe(EDITAR_NOTA_DESTACADA_TAG)
        val alto = boton.bottom - boton.top
        val ancho = boton.right - boton.left
        assertTrue(
            "el área tocable de Editar mide " + ancho + " x " + alto +
                ": se bajó del piso de " + ALTO_TOCABLE_ESPERADO,
            alto >= ALTO_TOCABLE_ESPERADO && ancho >= ALTO_TOCABLE_ESPERADO
        )
    }

    /**
     * **La nota larga se asoma en dos renglones y ofrece desplegarse.**
     *
     * [GraphicsMode.Mode.NATIVE] es obligatorio acá y no es ceremonia: quien
     * decide que la nota no cabe es `hasVisualOverflow`, o sea el layout del
     * texto. Sin métricas de fuente reales Robolectric cree que todo cabe, el
     * indicador nunca aparece y este test daría verde contra una tarjeta que
     * recorta en silencio.
     */
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    @Test
    fun `la nota larga se recorta y ofrece desplegarla`() {
        cliente(ficha = PagosFixtures.fichaDelCliente().copy(nota = NOTA_LARGA))

        composeTestRule.onNodeWithTag(INDICADOR_DE_LA_NOTA_TAG, useUnmergedTree = true)
            .assertTextEquals("Ver más")
        val asomada = altoDelTexto()
        assertTrue(
            "la nota asomada mide " + asomada + ": no se recortó a dos renglones",
            asomada <= DOS_RENGLONES
        )
    }

    /**
     * **Y un toque la despliega entera.** El par con el de arriba: sin esto, la
     * tarjeta podría recortar y no devolver nunca el texto, que es peor que el
     * defecto original.
     */
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    @Test
    fun `un toque despliega la nota entera`() {
        cliente(ficha = PagosFixtures.fichaDelCliente().copy(nota = NOTA_LARGA))
        val asomada = altoDelTexto()

        composeTestRule.onNodeWithTag(ALTERNAR_LA_NOTA_TAG).performClick()

        assertTrue(
            "la nota mide " + altoDelTexto() + " desplegada y medía " + asomada +
                " asomada: el toque no la desplegó",
            altoDelTexto() > asomada
        )
        composeTestRule.onNodeWithTag(INDICADOR_DE_LA_NOTA_TAG, useUnmergedTree = true)
            .assertTextEquals("Ver menos")
    }

    /**
     * **A escala MUY GRANDE el indicador no le quita ancho al rótulo.**
     *
     * Este test existe porque **el golden encontró el defecto y ninguna prueba
     * lo encontró**. Al mover el *"Ver más"* al renglón del rótulo —para que no
     * costara dp— a 2.0 le quitaba ~90 dp de ancho al `FlowRow`, y
     * `pagos_cliente_light_2_0` enseñaba **"LO QUE ANOTA"**: la palabra
     * *"ANOTASTE"* cortada a la mitad. Una palabra no tiene dónde quebrarse, así
     * que ningún `maxLines` ni ninguna elipsis lo arreglan: hay que devolverle el
     * ancho, y por eso a las escalas grandes el indicador baja bajo el párrafo.
     *
     * ## Por qué se mide el ANCHO DEL HUECO y no "si se recortó"
     *
     * Porque "se recortó" no es observable desde semantics: el nodo sigue
     * diciendo su texto completo aunque se pinte a la mitad, y sus bordes son
     * los del hueco, no los de las letras. Lo que sí es observable —y es la
     * causa, no el síntoma— es que el hueco **encoja** cuando aparece el
     * indicador. Con la nota corta no hay indicador y con la larga sí, así que
     * la misma pantalla medida dos veces da el control positivo sola.
     *
     * [GraphicsMode.Mode.NATIVE] es obligatorio: sin métricas reales el rótulo
     * mide 8 dp, todo cabe en todas partes y esto daría verde contra el código
     * roto — la misma trampa que ya documenta el test de arriba.
     */
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    @Test
    fun `a escala MUY GRANDE el indicador no le quita ancho al rotulo`() {
        // La nota del fixture **también** se recorta a 2.0, así que no sirve de
        // control: con ella el indicador está puesto en los dos lados de la
        // comparación y la diferencia da cero pase lo que pase. Se midió: el
        // primer intento de este test daba verde contra el layout roto por
        // exactamente eso. El estado "sin indicador" necesita una nota que a 2.0
        // quepa de verdad, y [NOTA_QUE_SIEMPRE_CABE] es esa.
        cliente(
            ficha = PagosFixtures.fichaDelCliente().copy(nota = NOTA_QUE_SIEMPRE_CABE),
            nivel = FontSizeLevel.MUY_GRANDE
        )
        assertEquals(
            "el control no controla: la nota corta también ofreció desplegarse",
            0,
            composeTestRule.onAllNodesWithTag(ALTERNAR_LA_NOTA_TAG).fetchSemanticsNodes().size
        )
        val sinIndicador = anchoDelRotulo()

        fichaEnPantalla.value = PagosFixtures.fichaDelCliente().copy(nota = NOTA_LARGA)
        composeTestRule.waitForIdle()

        assertEquals(
            "el rótulo tenía " + sinIndicador + " de ancho y con la nota larga tiene " +
                anchoDelRotulo() + ": el indicador se lo comió y \"ANOTASTE\" se corta",
            sinIndicador,
            anchoDelRotulo()
        )
    }

    /**
     * El otro lado de la misma regla, y lo que la hace valer la pena: **a escala
     * nominal el indicador SÍ va en el renglón del rótulo**, que es donde no
     * cuesta un dp porque el botón ya fija los 50.
     *
     * Se afirma por geometría —el indicador termina antes de que empiece el
     * párrafo— y no por un `testTag` de posición, que sería afirmar la
     * implementación en vez del efecto. Si alguien lo devuelve abajo del
     * párrafo para "que se lea mejor", esto se pone rojo y hay que volver a
     * medir el dinero antes de decidirlo.
     */
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    @Test
    fun `a escala nominal el indicador va en el renglon del rotulo`() {
        cliente(ficha = PagosFixtures.fichaDelCliente().copy(nota = NOTA_LARGA))

        val indicador = composeTestRule
            .onNodeWithTag(INDICADOR_DE_LA_NOTA_TAG, useUnmergedTree = true)
            .getUnclippedBoundsInRoot()
        val texto = composeTestRule.onNodeWithTag(TEXTO_DE_LA_NOTA_TAG, useUnmergedTree = true)
            .getUnclippedBoundsInRoot()
        assertTrue(
            "el indicador va de " + indicador.top + " a " + indicador.bottom +
                " y el párrafo empieza en " + texto.top + ": bajó y volvió a costar alto",
            indicador.bottom <= texto.top
        )
    }

    /**
     * **La nota que cabe no ofrece nada**, porque no hay nada que desplegar y un
     * control que no hace nada es ruido con forma de control.
     *
     * Es el control positivo del par de arriba, con el MISMO selector: si
     * [ALTERNAR_LA_NOTA_TAG] estuviera mal escrito, aquellos tests fallarían y
     * éste pasaría, así que el verde de los tres junto sólo se consigue
     * distinguiendo los dos casos.
     */
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    @Test
    fun `la nota corta no ofrece desplegar nada`() {
        cliente()

        assertEquals(
            "se ofreció desplegar una nota que ya se ve entera",
            0,
            composeTestRule.onAllNodesWithTag(ALTERNAR_LA_NOTA_TAG).fetchSemanticsNodes().size
        )
    }

    // --- El detalle de venta: la nota de la oficina --------------------------

    @Test
    fun `en la venta la nota tambien sube, y sin boton de editar`() {
        venta()

        composeTestRule.onNodeWithTag(NOTA_DESTACADA_TAG).assertIsDisplayed()
        composeTestRule.onNodeWithText("NOTA DE LA VENTA").assertIsDisplayed()
        assertEquals(
            "se ofreció editar una nota que el cobrador no puede escribir: la manda " +
                "la oficina en `sales.NOTAS` y esta pantalla no tiene editor",
            0,
            composeTestRule.onAllNodesWithTag(EDITAR_NOTA_DESTACADA_TAG)
                .fetchSemanticsNodes().size
        )
    }

    /**
     * El control positivo del de arriba: **el mismo selector SÍ encuentra un
     * botón** en el detalle de cliente, donde la nota sí se edita. Sin esto, un
     * `testTag` mal escrito daría cero en las dos pantallas y el test de arriba
     * pasaría sin distinguir nada.
     */
    @Test
    fun `control positivo - en el cliente el mismo selector si encuentra el boton`() {
        cliente()

        assertEquals(
            1,
            composeTestRule.onAllNodesWithTag(EDITAR_NOTA_DESTACADA_TAG)
                .fetchSemanticsNodes().size
        )
    }

    @Test
    fun `en la venta la nota queda arriba del saldo`() {
        venta()

        val tarjeta = bordesDe(NOTA_DESTACADA_TAG)
        val saldo = composeTestRule.onNodeWithText(SALDO_DE_LA_VENTA).getUnclippedBoundsInRoot()
        assertTrue(
            "la tarjeta termina en " + tarjeta.bottom + " y el saldo empieza en " +
                saldo.top + ": la nota no quedó arriba",
            tarjeta.bottom <= saldo.top
        )
    }

    /** Sin nota del servidor, arriba no hay nada y abajo se dice que no hay. */
    @Test
    fun `sin nota la venta no pinta tarjeta arriba, y lo dice abajo`() {
        venta(nota = null)

        assertEquals(0, cuantasTarjetas())
        // Con `performScrollTo` a propósito: el vacío vive AL FONDO y no se ve
        // sin desplazar. Es el precio aceptado — "no hay nota" no es accionable
        // y no puede empujar el saldo.
        composeTestRule.onNodeWithText("Sin notas de esta venta")
            .performScrollTo()
            .assertIsDisplayed()
    }

    // ------------------------------------------------------------------------

    private fun cuantasTarjetas(): Int =
        composeTestRule.onAllNodesWithTag(NOTA_DESTACADA_TAG).fetchSemanticsNodes().size

    private fun bordesDe(tag: String): DpRect =
        composeTestRule.onNodeWithTag(tag).getUnclippedBoundsInRoot()

    /** El ancho del hueco que el layout le da al rótulo. */
    private fun anchoDelRotulo(): Dp =
        composeTestRule.onNodeWithText("LO QUE ANOTASTE", useUnmergedTree = true)
            .getUnclippedBoundsInRoot()
            .let { it.right - it.left }

    /** El alto del párrafo de la nota, que es lo que crece al desplegarla. */
    private fun altoDelTexto(): Dp =
        composeTestRule.onNodeWithTag(TEXTO_DE_LA_NOTA_TAG, useUnmergedTree = true)
            .getUnclippedBoundsInRoot()
            .let { it.bottom - it.top }

    /**
     * La pantalla de cliente, con la escala tipográfica que se le pida.
     *
     * La escala se inyecta **de las dos formas** que esta app usa —el
     * `LocalFontSizeLevel` que leen los composables y el `fontScale` de la
     * densidad, que es quien de verdad agranda el texto—, igual que
     * `LaFichaSeVeYSeTocaTest`. Con sólo la primera el texto no crece y una
     * medición a 2.0 mediría la pantalla a 1.0.
     */
    private fun cliente(
        ficha: FichaDelCliente? = PagosFixtures.fichaDelCliente(),
        nivel: FontSizeLevel = NIVEL
    ) {
        fichaEnPantalla.value = ficha
        composeTestRule.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(
                LocalDensity provides Density(density.density, nivel.nominalScale),
                LocalFontSizeLevel provides nivel
            ) {
                MspTheme(darkTheme = false, animateColors = false) {
                    ClienteEnPantalla(fichaEnPantalla.value)
                }
            }
        }
    }

    @Composable
    private fun ClienteEnPantalla(ficha: FichaDelCliente?) {
        DetalleClienteContent(
            state = DetalleClienteUiState(
                cargando = false,
                detalle = PagosFixtures.detalleCliente().copy(ficha = ficha)
            ),
            onAtras = {},
            onAbrirVenta = {},
            onRegistrarAbono = {},
            onRegistrarVisita = {},
            onVerContactos = {},
            onAlternarTema = {},
            onAlternarPrivacidad = {},
            fichaDelCliente = AccionesDeLaFicha(onEditar = { abrio += 1 })
        )
    }

    private fun venta(nota: String? = NOTA_DE_LA_VENTA) {
        composeTestRule.setContent {
            CompositionLocalProvider(LocalFontSizeLevel provides NIVEL) {
                MspTheme(darkTheme = false, animateColors = false) {
                    DetalleVentaContent(
                        state = DetalleVentaUiState(
                            cargando = false,
                            detalle = PagosFixtures.detalleVenta().copy(nota = nota)
                        ),
                        onAtras = {},
                        onRegistrarAbono = {},
                        onRegistrarVisita = {},
                        onUsarLiquidacion = {},
                        onVerAbonos = {},
                        onVerGarantia = {}
                    )
                }
            }
        }
    }

    private companion object {
        /** La escala de la medición: la nominal, que es donde el dueño mira. */
        val NIVEL = FontSizeLevel.NORMAL

        /**
         * El piso tocable del repo — el mismo `ALTO_TOCABLE` privado de
         * `TarjetaDeNotaDestacada` y el mismo `ALTO_DEL_ALCANCE` de
         * `DetalleVentaScreen`. Se repite acá a propósito: si alguien baja el
         * de allá, este número **no** lo sigue y la prueba se pone roja, que
         * es justamente para lo que sirve.
         */
        val ALTO_TOCABLE_ESPERADO = 50.dp

        /** El primer renglón de la nota del fixture, que es lo que se asoma. */
        const val NOTA = "atiende la suegra"

        /** La nota que el servidor manda con la venta del fixture. */
        const val NOTA_DE_LA_VENTA = "entrega en la puerta de atrás"

        /** El rótulo de la tarjeta de saldo del detalle de venta, en versalitas. */
        const val SALDO_DE_LA_VENTA = "SALDO DE ESTA VENTA"

        /**
         * Una nota que NO cabe en dos renglones a 360 dp. Es larga a propósito:
         * el tope del editor son 500 caracteres y una nota así son ~diez
         * renglones, que es el caso que la tarjeta existe para no pintar entero
         * arriba del saldo.
         */
        const val NOTA_LARGA =
            "El cliente pidió que pasen el viernes porque cobra ese día en la " +
                "fábrica y no llega antes de las siete. El portón negro está " +
                "abierto pero hay que tocar fuerte porque la señora no oye bien " +
                "desde el patio de atrás."

        /**
         * Una nota que cabe en dos renglones **incluso a 2.0**, para poder medir
         * el estado sin indicador. La del fixture no sirve: a 2.0 se recorta.
         */
        const val NOTA_QUE_SIEMPRE_CABE = "Casa azul"

        /**
         * El techo de "dos renglones de `listTitle`" a escala nominal. La rampa
         * da `lineHeight = fontSize * 1.4`, o sea 21 dp por renglón a 15 sp; se
         * afirma con holgura porque lo que se mide es *dos y no tres*, no el
         * redondeo exacto del layout.
         */
        val DOS_RENGLONES = 50.dp

        /**
         * El aire mínimo entre el rótulo y la antigüedad cuando comparten
         * renglón. Cuatro dp no es un criterio de diseño: es el umbral que
         * distingue "separados" de "pegados", que es lo único que se afirma.
         */
        val AIRE_MINIMO = 4.dp
    }
}
