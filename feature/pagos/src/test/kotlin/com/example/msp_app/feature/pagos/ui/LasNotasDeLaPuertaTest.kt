package com.example.msp_app.feature.pagos.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
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
import com.example.msp_app.feature.pagos.domain.model.FichaDelCliente
import com.example.msp_app.feature.pagos.domain.model.SenalDeFicha
import com.example.msp_app.feature.pagos.ui.components.ACCION_DE_CONTACTO_TAG
import com.example.msp_app.feature.pagos.ui.components.APOYO_DEL_CUADRO_TAG
import com.example.msp_app.feature.pagos.ui.components.AVISO_DE_LAS_NOTAS_TAG
import com.example.msp_app.feature.pagos.ui.components.CALLE_DEL_CUADRO_TAG
import com.example.msp_app.feature.pagos.ui.components.CTA_NOTAS_TAG
import com.example.msp_app.feature.pagos.ui.components.CuerpoDeLaFicha
import com.example.msp_app.feature.pagos.ui.components.DISTINTIVO_DE_NOTAS_TAG
import com.example.msp_app.feature.pagos.ui.components.EDAD_DE_LA_NOTA_TAG
import com.example.msp_app.feature.pagos.ui.components.SENAL_TAG
import com.example.msp_app.feature.pagos.ui.components.SUGERENCIA_TAG
import com.example.msp_app.feature.pagos.ui.components.ZONA_DEL_CLIENTE_TAG
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.robolectric.annotation.Config

/**
 * **Las Notas de la puerta: dónde viven, si se nota que tienen algo, y que se
 * sepa quién las llena.**
 *
 * ## Los cuatro defectos que esto fija, todos vistos en vidrio
 *
 * **Uno.** Se llamaban "ficha", que es la palabra de un catálogo de oficina y no
 * la de alguien parado en una puerta. Ahora son **Notas**.
 *
 * **Dos.** Vivían en la fila de iconos de la hoja de identidad, cuatro acciones
 * abajo del pliegue. Ahora están en el **dock**, que se ve sin desplazar y que
 * ya tenía su tercer espacio diseñado y vacío.
 *
 * **Tres.** No se podía saber si una puerta tenía algo anotado **sin abrirla**:
 * el control se veía igual con la puerta en blanco y con *"hay perro"* escrito
 * adentro. Ahora lleva distintivo.
 *
 * **Cuatro.** La hoja no decía **quién la llena ni dónde se guarda**. Ahora lo
 * dice desde el primer renglón, y dice la verdad: hoy las Notas no salen del
 * teléfono.
 *
 * ## Lo que NO se prueba acá
 *
 * El COLOR del distintivo. En Robolectric no hay píxeles que leer, así que lo
 * que se afirma es el portador que acompaña al color —la descripción que oye
 * TalkBack—, que además es la mitad que hace que el significado no viaje sólo
 * en un tono. El color en sí lo miran los goldens `pagos_cliente_*`.
 */
@Config(qualifiers = "w360dp-h800dp-xhdpi")
class LasNotasDeLaPuertaTest : RobolectricTestBase() {

    @get:Rule
    val composeTestRule = createComposeRule()

    // --- El botón en el dock -------------------------------------------------

    @Test
    fun `el boton de Notas vive en el dock y se ve sin desplazar`() {
        cliente()

        // Sin `performScrollTo`: el dock está fijo abajo. Si algún día volviera
        // a la hoja desplazable, esto se pone rojo, que es lo correcto.
        composeTestRule.onNodeWithTag(CTA_NOTAS_TAG).assertIsDisplayed()
    }

    @Test
    fun `tocar Notas abre la hoja`() {
        var abierto = 0
        cliente(onEditar = { abierto += 1 })

        composeTestRule.onNodeWithTag(CTA_NOTAS_TAG).performClick()

        assertEquals("el botón del dock no abrió las Notas", 1, abierto)
    }

    /**
     * El control positivo del distintivo: la mitad que dice que **no** hay
     * punto. Va sobre el árbol SIN fusionar, igual que sus hermanos: el punto
     * vive dentro del `Surface` tocable del botón, que fusiona a sus hijos, y
     * sobre el árbol fusionado el conteo daría cero con punto y sin él — un
     * control positivo que no controla nada. Sin esto, un distintivo que se pintara siempre pasaría los dos
     * tests de abajo y no distinguiría nada.
     */
    @Test
    fun `con la puerta en blanco el boton NO lleva distintivo`() {
        cliente(ficha = FichaDelCliente())

        assertEquals(
            "se pintó el distintivo sobre una puerta sin nada anotado: entonces no " +
                "distingue las puertas que sí tienen algo, que es su único trabajo",
            0,
            composeTestRule.onAllNodesWithTag(DISTINTIVO_DE_NOTAS_TAG, useUnmergedTree = true)
                .fetchSemanticsNodes().size
        )
    }

    @Test
    fun `con algo anotado el boton lleva distintivo, y lo dice`() {
        cliente(ficha = FichaDelCliente(nota = "atiende la suegra"))

        composeTestRule.onNodeWithTag(DISTINTIVO_DE_NOTAS_TAG, useUnmergedTree = true)
            .assertIsDisplayed()
            .assertContentDescriptionEquals("Con notas")
    }

    /**
     * Una advertencia no es "hay datos": es riesgo para quien va a tocar la
     * puerta. El distintivo lo separa, y **no sólo con el color** — un punto de
     * 8 dp que sólo cambia de tono deja el significado íntegramente en el color,
     * que es lo que el resto de esta pantalla ya decidió no hacer.
     */
    @Test
    fun `con una advertencia el distintivo lo dice, no solo lo pinta`() {
        cliente(ficha = FichaDelCliente(senales = setOf(SenalDeFicha.HAY_PERRO)))

        composeTestRule.onNodeWithTag(DISTINTIVO_DE_NOTAS_TAG, useUnmergedTree = true)
            .assertContentDescriptionEquals("Con advertencia")
    }

    // --- La fila que se quedó con tres ---------------------------------------

    /**
     * **El renglón que se habría ganado sin darse cuenta.**
     *
     * Al sacar "Ficha", la fila pasó de cuatro acciones a tres. El reparto era
     * `if (NORMAL) size else size / 2`, y con tres eso da **una por renglón**:
     * tres renglones, una fila más alta, en la única pantalla donde el alto es
     * lo escaso. Con el dos fijo quedan dos renglones, igual que con cuatro.
     */
    @Test
    fun `con tres acciones la fila sigue en dos renglones a escala grande`() {
        cliente(nivel = FontSizeLevel.GRANDE)

        assertEquals("la fila ya no tiene tres acciones", 3, cuantasAcciones())
        assertEquals(
            "la fila de acciones se repartió en ${renglones()} renglones: con `size / 2` y " +
                "tres acciones cae una por renglón y la fila crece un renglón entero",
            2,
            renglones()
        )
    }

    /** Control positivo del de arriba: a escala normal las tres caben en uno. */
    @Test
    fun `a escala normal las tres acciones caben en un renglon`() {
        cliente(nivel = FontSizeLevel.NORMAL)

        assertEquals(1, renglones())
    }

    // --- La dirección, que es con lo que se encuentra la casa ----------------

    /**
     * **El defecto: la dirección se recortaba justo ella.**
     *
     * Iba pegada a la zona en una sola cadena, con la dirección AL FINAL y
     * `maxLines = 1`. A escala grande el renglón decía *"ruta 25 · centro · C.
     * Hi…"*: la zona entera y media dirección. El dueño lo vio en vidrio.
     *
     * **Dónde vive la dirección cambió, lo que se afirma no.** Ya no está en el
     * renglón de identidad —se retiró porque el cuadro de la puerta la decía a
     * tres dedos con las mismas palabras—; hoy la dice ese cuadro, la calle en
     * grande y la ciudad en su línea de apoyo. Esto se pone rojo igual si
     * alguien vuelve a pegarlas en una sola cadena: con la cadena pegada no
     * existe un nodo cuyo texto sea la calle sola.
     */
    @Test
    fun `la calle se ve completa y en su propio renglon`() {
        cliente()

        composeTestRule.onNodeWithTag(CALLE_DEL_CUADRO_TAG)
            .assertIsDisplayed()
            .assertTextEquals(CALLE)
    }

    /**
     * Y en las tres escalas, que es donde se rompía. Un `assertTextEquals` mide
     * el texto del nodo, no los píxeles pintados, así que esto sólo prueba que
     * la calle **llega entera al nodo**; que quepa sin elipsis lo miran los
     * goldens `pagos_cliente_*`, que un assert no puede ver.
     *
     * A `MUY_GRANDE` el cuadro se aprieta a 40 dp y deja **sólo** este renglón:
     * es la escala en la que el dueño reportó *"la dirección ni se ve casi"*, así
     * que es justo donde no se puede perder.
     */
    @Test
    fun `la calle llega entera tambien a escala muy grande`() {
        cliente(nivel = FontSizeLevel.MUY_GRANDE)

        composeTestRule.onNodeWithTag(CALLE_DEL_CUADRO_TAG).assertTextEquals(CALLE)
    }

    /**
     * La **ciudad** no se pierde al sacarla del renglón grande: baja a la línea
     * de apoyo del cuadro, junto a la ruta.
     *
     * Es el control que impide "arreglar" el ancho de la calle tirando la ciudad
     * a la basura — que pasaría en verde con sólo el test de arriba.
     */
    @Test
    fun `la ciudad baja a la linea de apoyo, con la ruta`() {
        cliente()

        composeTestRule.onNodeWithTag(APOYO_DEL_CUADRO_TAG).assertTextEquals(APOYO)
    }

    /**
     * Control positivo del de arriba: la **zona** sigue siendo el encabezado de
     * la tarjeta y sigue estando fuera del cuadro. Sin esto, un
     * `BloqueDeIdentidad` que hubiera tirado la zona a la basura junto con la
     * dirección pasaría en verde.
     *
     * Se apunta por `testTag` y no por texto: la ruta se dice en DOS lugares
     * —acá y dentro de *"Centro · ruta 25"*—, y un `onNodeWithText` a secas
     * encuentra los dos y falla por ambigüedad.
     */
    @Test
    fun `la zona sigue estando, y aparte`() {
        cliente()

        composeTestRule.onNodeWithTag(ZONA_DEL_CLIENTE_TAG)
            .assertIsDisplayed()
            .assertTextEquals(ZONA)
    }

    /**
     * **La dirección se dice UNA vez.** El defecto que el dueño vio en el
     * golden: el bloque de identidad y el cuadro decían las mismas palabras a
     * tres dedos de distancia, y eso no se lee como jerarquía sino como un
     * error de copiado.
     *
     * Se cuenta el texto de la calle en la pantalla entera, sin anclar a ningún
     * `testTag`: si alguien devuelve el renglón a identidad, el conteo da dos y
     * esto se pone rojo. El control positivo es el propio `assertEquals`, que
     * exige UNO y no CERO — un selector ciego daría cero y también fallaría.
     */
    @Test
    fun `la calle aparece una sola vez en la pantalla`() {
        cliente()

        assertEquals(
            "la calle se pinta dos veces: el bloque de identidad volvió a decirla",
            1,
            composeTestRule.onAllNodesWithText(CALLE).fetchSemanticsNodes().size
        )
    }

    // --- La hoja -------------------------------------------------------------

    @Test
    fun `la hoja dice quien la llena desde el primer renglon, con la puerta en blanco`() {
        hoja(senales = emptySet(), nota = "")

        composeTestRule.onNodeWithTag(AVISO_DE_LAS_NOTAS_TAG).assertIsDisplayed()
    }

    /**
     * Y también con contenido: el aviso es **siempre visible**, no una invitación
     * que se retira en cuanto hay algo escrito. Quien vuelve dentro de un mes
     * necesita saber igual que eso no está respaldado en ningún lado.
     */
    @Test
    fun `la hoja dice quien la llena tambien con algo escrito`() {
        hoja(senales = setOf(SenalDeFicha.HAY_PERRO), nota = "portón verde")

        composeTestRule.onNodeWithTag(AVISO_DE_LAS_NOTAS_TAG).performScrollTo().assertIsDisplayed()
    }

    @Test
    fun `la edad de la nota se ve tambien al editar`() {
        hoja(senales = emptySet(), nota = "atiende la suegra", anotada = "hace 3 días")

        composeTestRule.onNodeWithTag(EDAD_DE_LA_NOTA_TAG)
            .performScrollTo()
            .assertIsDisplayed()
        composeTestRule.onNodeWithText("Anotada hace 3 días").assertIsDisplayed()
    }

    /** Nunca se escribió: no hay edad que decir, y no se inventa una. */
    @Test
    fun `sin nota guardada no se pinta edad`() {
        hoja(senales = emptySet(), nota = "")

        assertEquals(
            0,
            composeTestRule.onAllNodesWithTag(EDAD_DE_LA_NOTA_TAG).fetchSemanticsNodes().size
        )
    }

    // --- Prosa → estructura --------------------------------------------------

    @Test
    fun `si la nota dice hay perro, se ofrece la casilla`() {
        hoja(senales = emptySet(), nota = "cuidado, hay perro en el patio")

        composeTestRule.onNodeWithTag(SUGERENCIA_TAG + SenalDeFicha.HAY_PERRO.ordinal)
            .performScrollTo()
            .assertIsDisplayed()
    }

    @Test
    fun `tocar la sugerencia marca la senal`() {
        var marcada: SenalDeFicha? = null
        hoja(senales = emptySet(), nota = "hay perro", onSenal = { marcada = it })

        composeTestRule.onNodeWithTag(SUGERENCIA_TAG + SenalDeFicha.HAY_PERRO.ordinal)
            .performScrollTo()
            .performClick()

        assertEquals(SenalDeFicha.HAY_PERRO, marcada)
    }

    /**
     * Lo que ya es dato no se vuelve a ofrecer. Sin esto la hoja le pediría al
     * cobrador que marcara lo que acaba de marcar, que es el ruido que convierte
     * una ayuda en una molestia.
     */
    @Test
    fun `lo que ya esta marcado no se vuelve a ofrecer`() {
        hoja(senales = setOf(SenalDeFicha.HAY_PERRO), nota = "hay perro")

        assertEquals(
            "se ofreció una casilla que ya estaba marcada",
            0,
            composeTestRule.onAllNodesWithTag(SUGERENCIA_TAG + SenalDeFicha.HAY_PERRO.ordinal)
                .fetchSemanticsNodes().size
        )
        // Control positivo: la casilla del catálogo SÍ sigue ahí, marcada. Sin
        // esto, una hoja que no pintara nada pasaría igual.
        composeTestRule.onNodeWithTag(SENAL_TAG + SenalDeFicha.HAY_PERRO.ordinal)
            .performScrollTo()
            .assertIsDisplayed()
    }

    @Test
    fun `una nota sin vocabulario del catalogo no ofrece nada`() {
        hoja(senales = emptySet(), nota = "el portón es verde y tiene número 214")

        SenalDeFicha.entries.forEach { senal ->
            assertEquals(
                "se ofreció $senal sobre una nota que no la nombra",
                0,
                composeTestRule.onAllNodesWithTag(SUGERENCIA_TAG + senal.ordinal)
                    .fetchSemanticsNodes().size
            )
        }
    }

    // -----------------------------------------------------------------------

    private fun cuantasAcciones(): Int =
        composeTestRule.onAllNodesWithTag(ACCION_DE_CONTACTO_TAG).fetchSemanticsNodes().size

    /** En cuántos renglones quedó repartida la fila de acciones. */
    private fun renglones(): Int = composeTestRule.onAllNodesWithTag(ACCION_DE_CONTACTO_TAG)
        .fetchSemanticsNodes()
        .map { it.boundsInRoot.top }
        .distinct()
        .size

    private companion object {
        /** La calle SOLA, que es lo que el cuadro pinta en grande. */
        const val CALLE = "C. Hidalgo 214"

        /** La ruta, encabezado de la tarjeta de identidad. */
        const val ZONA = "ruta 25"

        /** La línea de apoyo del cuadro: ciudad primero, ruta después. */
        const val APOYO = "Centro · ruta 25"
    }

    private fun cliente(
        ficha: FichaDelCliente? = FichaDelCliente(),
        nivel: FontSizeLevel = FontSizeLevel.NORMAL,
        onEditar: () -> Unit = {}
    ) {
        composeTestRule.setContent {
            CompositionLocalProvider(LocalFontSizeLevel provides nivel) {
                MspTheme(animateColors = false) {
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
                        fichaDelCliente = AccionesDeLaFicha(onEditar = onEditar)
                    )
                }
            }
        }
    }

    @Composable
    private fun Hoja(
        senales: Set<SenalDeFicha>,
        nota: String,
        anotada: String?,
        onSenal: (SenalDeFicha) -> Unit
    ) {
        CuerpoDeLaFicha(
            senales = senales,
            nota = nota,
            guardando = false,
            fallo = false,
            onSenal = onSenal,
            onNota = {},
            onGuardar = {},
            anotada = anotada
        )
    }

    /**
     * Monta el cuerpo de la hoja **dentro de un contenedor desplazable**, que es
     * lo que `ModalBottomSheet` le da en producción. Sin él, `performScrollTo`
     * no tiene a quién pedirle el desplazamiento y lo que hay abajo del pliegue
     * no se puede alcanzar — un rojo del andamio, no de la pantalla.
     */
    private fun hoja(
        senales: Set<SenalDeFicha>,
        nota: String,
        anotada: String? = null,
        onSenal: (SenalDeFicha) -> Unit = {}
    ) {
        composeTestRule.setContent {
            MspTheme(animateColors = false) {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    Hoja(senales, nota, anotada, onSenal)
                }
            }
        }
    }
}
