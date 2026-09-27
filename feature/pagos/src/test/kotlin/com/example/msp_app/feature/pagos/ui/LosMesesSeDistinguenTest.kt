package com.example.msp_app.feature.pagos.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.height
import androidx.compose.ui.unit.width
import com.example.msp_app.core.common.cobranza.domain.EstadoCuenta
import com.example.msp_app.core.common.money.Money
import com.example.msp_app.core.designsystem.theme.FontSizeLevel
import com.example.msp_app.core.designsystem.theme.LocalFontSizeLevel
import com.example.msp_app.core.designsystem.theme.MspTheme
import com.example.msp_app.core.testing.RobolectricTestBase
import com.example.msp_app.feature.pagos.domain.GruposDeContactos
import com.example.msp_app.feature.pagos.domain.model.BitacoraCompleta
import com.example.msp_app.feature.pagos.domain.model.ContactoDeCobranza
import com.example.msp_app.feature.pagos.domain.model.MetodoDeCobro
import com.example.msp_app.feature.pagos.domain.model.TipoDeContacto
import com.example.msp_app.feature.pagos.ui.components.BLOQUE_DEL_MES_TAG
import com.example.msp_app.feature.pagos.ui.components.BloqueDelMes
import com.example.msp_app.feature.pagos.ui.components.COBRADO_DEL_GRUPO_TAG
import com.example.msp_app.feature.pagos.ui.components.CONTACTO_EN_LINEA_TAG
import com.example.msp_app.feature.pagos.ui.components.ContactoEnLinea
import com.example.msp_app.feature.pagos.ui.components.ENCABEZADO_DE_GRUPO_TAG
import com.example.msp_app.feature.pagos.ui.components.EncabezadoDeGrupo
import com.example.msp_app.feature.pagos.ui.components.tonoDelMes
import com.example.msp_app.feature.pagos.ui.components.tramoDelMes
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * **Un mes se distingue del siguiente a simple vista — y los renglones de pago
 * siguen exactamente donde estaban.**
 *
 * El encargo del dueño, textual: *"está agrupado por meses pero algo que permita
 * diferenciar mejor entre meses a simple vista"*, con tres correcciones encima
 * — **encabezado más fuerte**, **el fondo un poco distinto por mes**, y *"no me
 * gusta esta raya enorme ahí"* por el hairline de ancho completo que partía el
 * encabezado. Y una restricción dura: *"la i pero no tocarás los items de los
 * pagos como tal verdad?"*.
 *
 * Esa restricción es la mitad de este archivo. Un cambio de agrupamiento que
 * "se ve bien" y de paso corre cada renglón 8 dp, o lo hace más bajo, o le
 * recorta una esquina, incumple el encargo aunque el golden se vea bonito — y
 * un golden no lo nota: lo que se mueve entero se sigue viendo alineado. Por eso
 * el renglón se mide contra sí mismo, dentro y fuera del bloque.
 *
 * ## Lo que aquí NO se puede afirmar
 *
 * **El color con el que se pintó.** Robolectric no tiene píxeles, así que
 * ningún assert puede ver el fondo del bloque. Lo que sí se puede es leer el
 * **token** que la pieza elige —[tonoDelMes] es una función pura de
 * composición— y cobrar que dos meses seguidos no elijan el mismo. Que ese par
 * de tonos se vea distinto en pantalla lo miran los goldens
 * (`pagos_bitacora_*`, `pagos_venta_linea_*`).
 *
 * **Que la raya ya no se dibuje.** Era un `Box` con fondo y sin texto: en el
 * árbol de semántica no existe ni antes ni después. Lo que sí dejó rastro
 * medible es **el hueco que ocupaba**: la raya vivía entre el nombre y el
 * subtotal con un `weight(1f)`, así que el título medía lo que medía su texto.
 * Ahora el `weight` lo tiene el título. Ver
 * `el titulo del mes ocupa el renglon, que es el hueco que dejo la raya`.
 */
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h800dp-xhdpi")
class LosMesesSeDistinguenTest : RobolectricTestBase() {

    @get:Rule
    val composeTestRule = createComposeRule()

    // --- El fondo cambia por mes --------------------------------------------

    /**
     * **Dos meses seguidos no comparten fondo, en los dos temas.**
     *
     * Es la corrección *"podríamos cambiar de color el fondo un poco por mes"*.
     * Se cobra en claro **y** en oscuro porque la paleta no es simétrica: los
     * dos roles que se alternan valen `#FFFFFF`/`#F4F6F5` en claro y
     * `#141917`/`#000000` en oscuro, y una implementación que se apoyara en uno
     * solo de los dos temas pasaría medio test.
     */
    @Test
    fun `dos meses seguidos no pintan el mismo fondo`() {
        val porTema = tonosEnLosDosTemas()

        porTema.forEach { (tema, tonos) ->
            assertNotEquals(
                "los meses 0 y 1 eligieron el mismo fondo en tema $tema: dos meses pegados " +
                    "quedan como un solo bloque y el corte desaparece",
                tonos[0],
                tonos[1]
            )
        }
    }

    /**
     * **El mes más reciente sale sobre la hoja, no sobre la página.**
     *
     * El tono alterna, así que alguno de los dos le toca al índice 0 — y cuál
     * importa: `background` es el color de la pantalla, de modo que un mes
     * pintado con él **no se ve como bloque**. Si el más reciente cayera ahí, el
     * caso más común de todos —un cliente con un solo mes de historia— quedaría
     * sin contenedor y el encargo del dueño no se cumpliría justo en la pantalla
     * que más se abre.
     *
     * De paso fija el ciclo en dos: el mes 2 vuelve al tono del 0.
     */
    @Test
    fun `el mes mas reciente sale sobre la hoja y el ciclo es de dos`() {
        val hoja = mutableListOf<Color>()
        val pagina = mutableListOf<Color>()
        val tonos = tonosEnLosDosTemas(hoja, pagina).getValue(TEMA_CLARO)

        assertEquals("el mes 0 no salió sobre `surface`", hoja.first(), tonos[0])
        assertEquals("el mes 1 no salió sobre `background`", pagina.first(), tonos[1])
        assertEquals(
            "el mes 2 no volvió al tono del 0: el ciclo dejó de ser de dos",
            tonos[0],
            tonos[2]
        )
    }

    // --- El encabezado es más fuerte, y la raya se fue ------------------------

    /**
     * **El nombre del mes pesa más que la letra con la que se pintaba antes.**
     *
     * El encabezado iba en `overline` —12 sp `SemiBold`, gris— y el dueño pidió
     * *"un encabezado más fuerte"*. Ahora va en `listTitle`, 15 sp `Bold`, en
     * `onSurface`.
     *
     * Se mide contra una referencia pintada **en la misma composición y con el
     * estilo viejo**, no contra un número de dp escrito a mano: el alto de una
     * línea sale de `lineHeight = tamaño × 1.4` y de la densidad, así que un dp
     * literal aquí envejecería mal. Con la referencia al lado, revertir el
     * estilo deja los dos altos iguales y esto se pone rojo.
     *
     * El peso y el color no se pueden medir —`SemanticsProperties` no lleva el
     * `TextStyle`—; lo que se cobra es el tamaño, que es el eje que se ve de
     * lejos. Los otros dos los miran los goldens.
     */
    @Test
    fun `el nombre del mes se pinta mas grande que la letra que tenia antes`() {
        composeTestRule.setContent {
            Tema(FontSizeLevel.NORMAL) {
                Column(modifier = Modifier.padding(horizontal = MspTheme.spacing.md)) {
                    EncabezadoDeGrupo(grupo = GruposDeContactos.porMes(EN_AGOSTO).single())
                    Text(
                        text = "M",
                        style = MspTheme.type.overline,
                        modifier = Modifier.testTag(REFERENCIA_TAG)
                    )
                }
            }
        }

        val titulo = composeTestRule.onNodeWithText(AGOSTO, useUnmergedTree = true)
            .getUnclippedBoundsInRoot()
            .height
        val comoEraAntes = composeTestRule.onNodeWithTag(REFERENCIA_TAG, useUnmergedTree = true)
            .getUnclippedBoundsInRoot()
            .height

        assertTrue(
            "el nombre del mes mide $titulo de alto y la letra vieja (`overline`) mide " +
                "$comoEraAntes: el encabezado no quedó más fuerte que antes",
            titulo.value > comoEraAntes.value
        )
    }

    /**
     * **El título ocupa el renglón — que es el hueco que dejó la raya.**
     *
     * La raya era un `Box` con `weight(1f)` entre el nombre y el subtotal: se
     * comía todo el ancho sobrante y el título medía lo que medía su texto
     * (*"AGOSTO 2026"* en 12 sp, como un tercio del renglón). Quitarla y darle
     * el `weight` al título es exactamente lo que la vuelve imposible de
     * reintroducir sin que esto se ponga rojo: un relleno que se quedara en
     * medio le robaría el ancho al título otra vez.
     *
     * Es la única forma medible de cobrar la ausencia de la raya: no tiene
     * texto, no tiene tag y en el árbol de semántica no existe.
     */
    @Test
    fun `el titulo del mes ocupa el renglon, que es el hueco que dejo la raya`() {
        encabezadoSolo(FontSizeLevel.NORMAL)

        val encabezado = composeTestRule.onNodeWithTag(ENCABEZADO_DE_GRUPO_TAG)
            .getUnclippedBoundsInRoot()
        val titulo = composeTestRule.onNodeWithText(AGOSTO, useUnmergedTree = true)
            .getUnclippedBoundsInRoot()
        val cobrado = composeTestRule.onNodeWithTag(COBRADO_DEL_GRUPO_TAG, useUnmergedTree = true)
            .getUnclippedBoundsInRoot()

        // Lo que le queda al título si nada más se mete en medio: el renglón
        // entero, menos los 8 dp de cada costado, menos el subtotal, menos el
        // hueco de 8 dp que los separa.
        val sinNadaEnMedio =
            encabezado.width - cobrado.width - HUECO_DEL_ENCABEZADO * COSTADOS_Y_HUECO

        assertTrue(
            "el título mide ${titulo.width} en un renglón de ${encabezado.width} donde le " +
                "tocaban $sinNadaEnMedio: algo con ancho propio se metió entre el nombre y " +
                "el subtotal, y eso era la raya",
            titulo.width.value >= sinNadaEnMedio.value - TOLERANCIA_DE_PIXEL
        )
    }

    /**
     * **Con la letra al máximo el encabezado sigue cabiendo, entero.**
     *
     * La app se usa con la letra del sistema arriba. A 2.0 *"AGOSTO 2026"* en
     * 15 sp `Bold` se pinta a 30 sp y el subtotal a 28 sp, en un renglón de
     * 360 dp menos los márgenes: el riesgo real no es que se vea apretado sino
     * que el subtotal quede aplastado a cero o que el título se recorte **sin
     * elipsis** —el default de `Text` es `Clip`—, que es un mes mutilado que se
     * sigue leyendo como un mes.
     *
     * `getBoundsInRoot` viene recortado por los padres y
     * `getUnclippedBoundsInRoot` no: que sean iguales es, literalmente, "esto no
     * se recortó".
     */
    @Test
    fun `a la letra maxima el encabezado no se recorta ni aplasta el subtotal`() {
        encabezadoSolo(FontSizeLevel.MUY_GRANDE)

        val titulo = composeTestRule.onNodeWithText(AGOSTO, useUnmergedTree = true)
        val cobrado = composeTestRule.onNodeWithTag(COBRADO_DEL_GRUPO_TAG, useUnmergedTree = true)
        cobrado.assertIsDisplayed()

        assertEquals(
            "el nombre del mes se recortó a escala 2.0",
            titulo.getUnclippedBoundsInRoot().width.value,
            titulo.getBoundsInRoot().width.value,
            TOLERANCIA_DE_PIXEL
        )
        assertEquals(
            "el subtotal se recortó a escala 2.0",
            cobrado.getUnclippedBoundsInRoot().width.value,
            cobrado.getBoundsInRoot().width.value,
            TOLERANCIA_DE_PIXEL
        )
        assertTrue(
            "el subtotal quedó de ${cobrado.getBoundsInRoot().width}: el título se comió el " +
                "renglón y la cifra del mes desapareció",
            cobrado.getBoundsInRoot().width.value > 0f
        )
    }

    // --- Los renglones de pago no se tocan -----------------------------------

    /**
     * **La restricción dura del dueño, medida: el renglón dentro del bloque es
     * el mismo renglón que fuera.**
     *
     * Se pintan tres veces el MISMO contacto —suelto, dentro de [BloqueDelMes]
     * (el camino del detalle de venta) y con [tramoDelMes] encima (el camino de
     * la bitácora perezosa)— y se exige que los tres midan igual y arranquen en
     * la misma x. Un `clip`, un `padding` horizontal o un contenedor que midiera
     * por intrínsecos moverían alguno, y es justo lo que el dueño dijo que no
     * pasara: *"no tocarás los items de los pagos"*.
     *
     * Por esto `tramoDelMes` usa `Modifier.background`, que pinta detrás y no
     * recorta, en vez de `clip` + `background`.
     */
    @Test
    fun `el renglon de un pago mide y arranca igual dentro y fuera del bloque`() {
        composeTestRule.setContent {
            Tema(FontSizeLevel.NORMAL) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    ContactoEnLinea(contacto = COBRO)
                    BloqueDelMes(indice = 0) {
                        ContactoEnLinea(contacto = COBRO.copy(id = "en-bloque"))
                    }
                    ContactoEnLinea(
                        contacto = COBRO.copy(id = "en-tramo"),
                        modifier = Modifier.tramoDelMes(indice = 0)
                    )
                }
            }
        }

        val filas = composeTestRule.onAllNodesWithTag(CONTACTO_EN_LINEA_TAG)
            .fetchSemanticsNodes()
            .sortedBy { it.boundsInRoot.top }
            .map { it.boundsInRoot }
        assertEquals("no se pintaron las tres filas de la comparación", 3, filas.size)

        val (suelta, enBloque, enTramo) = filas
        listOf("BloqueDelMes" to enBloque, "tramoDelMes" to enTramo).forEach { (quien, fila) ->
            assertEquals(
                "$quien cambió el ANCHO del renglón",
                suelta.width,
                fila.width,
                TOLERANCIA_DE_PIXEL
            )
            assertEquals(
                "$quien cambió el ALTO del renglón",
                suelta.height,
                fila.height,
                TOLERANCIA_DE_PIXEL
            )
            assertEquals(
                "$quien corrió el renglón de lado",
                suelta.left,
                fila.left,
                TOLERANCIA_DE_PIXEL
            )
        }
    }

    /**
     * **Un solo mes no se ve raro: sigue siendo un bloque entero.**
     *
     * Es el caso más común —un cliente nuevo, o uno con un filtro puesto— y el
     * que más fácil se degrada: una alternancia de fondos puede quedar preciosa
     * con tres meses y dejar el caso de uno sin contenedor. Se cobra que el
     * bloque exista y que **contenga** al encabezado y al renglón: un bloque
     * que se pintara al lado, o de alto cero, pasaría un `assertExists`.
     */
    @Test
    fun `un solo mes queda dentro de un bloque que lo contiene entero`() {
        composeTestRule.setContent {
            Tema(FontSizeLevel.NORMAL) {
                val grupo = GruposDeContactos.porMes(EN_AGOSTO).single()
                BloqueDelMes(indice = 0) {
                    EncabezadoDeGrupo(grupo)
                    grupo.contactos.forEach { ContactoEnLinea(contacto = it) }
                }
            }
        }

        val bloque = composeTestRule.onNodeWithTag(BLOQUE_DEL_MES_TAG).getUnclippedBoundsInRoot()
        val encabezado = composeTestRule.onNodeWithTag(ENCABEZADO_DE_GRUPO_TAG)
            .getUnclippedBoundsInRoot()
        val fila = composeTestRule.onAllNodesWithTag(CONTACTO_EN_LINEA_TAG)[0]
            .getUnclippedBoundsInRoot()

        assertTrue(
            "el encabezado ($encabezado) se salió del bloque ($bloque)",
            encabezado.top.value >= bloque.top.value - TOLERANCIA_DE_PIXEL
        )
        assertTrue(
            "el renglón ($fila) quedó por debajo del bloque ($bloque): el fondo del mes se " +
                "corta antes del último pago",
            fila.bottom.value <= bloque.bottom.value + TOLERANCIA_DE_PIXEL
        )
    }

    // --- La bitácora, cableada -----------------------------------------------

    /**
     * **La bitácora sigue pintando un encabezado por mes, y cada uno con su
     * tramo.**
     *
     * Cobra el cableado de la pantalla real —que es donde vive el `LazyColumn`
     * y donde el fondo se reparte tramo por tramo—, no la pieza suelta. Sin
     * esto, un `BitacoraScreen` que se hubiera quedado con el encabezado viejo
     * dejaría todo lo de arriba en verde.
     */
    @Test
    fun `la bitacora parte la linea en un encabezado por mes`() {
        composeTestRule.setContent {
            Tema(FontSizeLevel.NORMAL) {
                BitacoraContent(
                    state = BitacoraUiState(cargando = false, bitacora = LA_BITACORA),
                    onAtras = {}
                )
            }
        }

        assertEquals(
            "la bitácora no pintó un encabezado por cada mes de la línea",
            2,
            composeTestRule.onAllNodesWithTag(ENCABEZADO_DE_GRUPO_TAG).fetchSemanticsNodes().size
        )
        composeTestRule.onNodeWithText(AGOSTO, useUnmergedTree = true).assertIsDisplayed()
        composeTestRule.onNodeWithText(SEPTIEMBRE, useUnmergedTree = true).assertIsDisplayed()
    }

    // --- Andamio --------------------------------------------------------------

    /**
     * Los tonos de los tres primeros meses **en los dos temas**, leídos de la
     * pieza de producción dentro de una composición real.
     *
     * Los dos temas se montan en una sola llamada a `setContent` porque
     * `createComposeRule` sólo admite una por prueba. [hoja] y [pagina] juntan
     * de paso el `surface` y el `background` de cada tema, para poder afirmar
     * cuál tono es cuál sin copiar un hex al test.
     */
    private fun tonosEnLosDosTemas(
        hoja: MutableList<Color> = mutableListOf(),
        pagina: MutableList<Color> = mutableListOf()
    ): Map<String, List<Color>> {
        val leidos = linkedMapOf<String, List<Color>>()
        composeTestRule.setContent {
            listOf(TEMA_CLARO to false, TEMA_OSCURO to true).forEach { (nombre, oscuro) ->
                MspTheme(darkTheme = oscuro, animateColors = false) {
                    leidos[nombre] = List(MESES_LEIDOS) { tonoDelMes(it) }
                    hoja += MspTheme.colors.surface
                    pagina += MspTheme.colors.background
                }
            }
        }
        composeTestRule.waitForIdle()
        return leidos.toMap()
    }

    /** El encabezado de agosto, solo, con los márgenes que la pantalla le da. */
    private fun encabezadoSolo(nivel: FontSizeLevel) {
        composeTestRule.setContent {
            Tema(nivel) {
                Column(modifier = Modifier.padding(horizontal = MspTheme.spacing.md)) {
                    EncabezadoDeGrupo(grupo = GruposDeContactos.porMes(EN_AGOSTO).single())
                }
            }
        }
    }

    /**
     * El tema, con el nivel de letra puesto **en los dos lados**: el
     * `LocalFontSizeLevel` que leen las piezas que escalan a mano, y el
     * `fontScale` de la densidad, que es lo que hace crecer el texto de verdad.
     */
    @Composable
    private fun Tema(nivel: FontSizeLevel, contenido: @Composable () -> Unit) {
        val densidad = LocalDensity.current
        CompositionLocalProvider(
            LocalDensity provides Density(densidad.density, nivel.nominalScale),
            LocalFontSizeLevel provides nivel
        ) {
            MspTheme(darkTheme = false, animateColors = false, content = contenido)
        }
    }

    private companion object {

        /** Un dp de holgura: comparar bordes al float exacto es frágil. */
        const val TOLERANCIA_DE_PIXEL = 1.0f

        /** Tres meses bastan para ver el ciclo y que vuelva a empezar. */
        const val MESES_LEIDOS = 3

        const val TEMA_CLARO = "claro"
        const val TEMA_OSCURO = "oscuro"

        const val REFERENCIA_TAG = "referencia_overline"

        const val AGOSTO = "AGOSTO 2026"
        const val SEPTIEMBRE = "SEPTIEMBRE 2026"

        /** El hueco entre el nombre y el subtotal — `MspSpacing.sm`. */
        val HUECO_DEL_ENCABEZADO = 8.dp

        /** Tres huecos de `sm`: el costado izquierdo, el de en medio y el derecho. */
        const val COSTADOS_Y_HUECO = 3f

        val COBRO = ContactoDeCobranza(
            id = "cobro-agosto",
            fecha = Instant.parse("2026-08-03T17:10:00Z"),
            etiqueta = "Abono",
            nota = null,
            estado = EstadoCuenta.PAGO,
            importe = Money.of(BigDecimal("350.00")),
            tipo = TipoDeContacto.COBRO,
            metodo = MetodoDeCobro.EFECTIVO,
            cobrador = "Marisol Vega"
        )

        /** Un mes solo — el caso del cliente con poca historia. */
        val EN_AGOSTO = listOf(COBRO)

        /** Dos meses de calendario, que es lo que hay que poder distinguir. */
        val LA_BITACORA = BitacoraCompleta(
            clienteId = 10388,
            ventaId = 77188,
            nombre = "Victoria Flores Olmedo",
            titulo = "Refrigerador Mabe 14'",
            direccion = "C. Hidalgo 214",
            contactos = listOf(
                COBRO.copy(id = "cobro-septiembre", fecha = Instant.parse("2026-09-11T22:45:00Z")),
                COBRO
            ),
            hoy = LocalDate.of(2026, 9, 22)
        )
    }
}
