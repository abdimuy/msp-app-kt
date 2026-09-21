package com.example.msp_app.feature.pagos.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.height
import androidx.compose.ui.unit.width
import com.example.msp_app.core.common.cobranza.domain.EstadoCuenta
import com.example.msp_app.core.common.money.Money
import com.example.msp_app.core.designsystem.theme.FontSizeLevel
import com.example.msp_app.core.designsystem.theme.LocalFontSizeLevel
import com.example.msp_app.core.designsystem.theme.MspTheme
import com.example.msp_app.core.testing.RobolectricTestBase
import com.example.msp_app.feature.pagos.domain.FiltroDeContactos
import com.example.msp_app.feature.pagos.domain.GruposDeContactos
import com.example.msp_app.feature.pagos.domain.model.ContactoDeCobranza
import com.example.msp_app.feature.pagos.domain.model.MetodoDeCobro
import com.example.msp_app.feature.pagos.domain.model.TipoDeContacto
import com.example.msp_app.feature.pagos.ui.components.COBRADO_DEL_GRUPO_TAG
import com.example.msp_app.feature.pagos.ui.components.CONTACTO_EN_LINEA_TAG
import com.example.msp_app.feature.pagos.ui.components.ContactoEnLinea
import com.example.msp_app.feature.pagos.ui.components.ENCABEZADO_DE_GRUPO_TAG
import com.example.msp_app.feature.pagos.ui.components.EncabezadoDeGrupo
import com.example.msp_app.feature.pagos.ui.components.FILTRO_TAG
import com.example.msp_app.feature.pagos.ui.components.FiltrosDeContacto
import com.example.msp_app.feature.pagos.ui.components.PUNTO_DEL_ESTADO_TAG
import java.math.BigDecimal
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.robolectric.annotation.Config

/**
 * **La línea de contactos dice quién cobró, cómo, cuándo y cuánto — y calla lo
 * que no pasó.**
 *
 * El defecto que esto cierra: la fila anterior (`FilaDeContacto`) dejaba
 * **etiqueta y fecha**, y tiraba el resto. Cuatro hechos distintos —un abono en
 * efectivo, una transferencia, una puerta tocada sin nadie, una promesa— se
 * veían como cuatro renglones iguales, y el cobrador no podía reconstruir su
 * día leyendo la pantalla. Aquí se cobra que cada dato que el hecho sabe de sí
 * mismo llegue al árbol de semántica: la **hora**, la **forma de pago**, el
 * **cobrador**, la **nota** y el **importe**.
 *
 * ## Las cuatro cosas que un assert NO puede ver, y que por eso no se afirman
 *
 * **Uno — el recorte de píxeles de la hora.** `assertTextEquals` mide el texto
 * del nodo, no lo que se pintó: una hora recortada a `10:4` llega al nodo
 * entera igual. Lo que sí se puede medir es **el ancho de la columna**, que es
 * exactamente lo que el arreglo cambió (ver
 * `la columna de la hora se ensancha con el nivel de letra`). El recorte en sí
 * lo miran los goldens.
 *
 * **Dos — el color del punto de estado.** Robolectric no tiene píxeles.
 *
 * **Tres — la barra que marca la venta abierta.** Ver el KDoc de
 * `la marca de la venta no corre el contenido de la fila`: es un `Box` con
 * fondo y sin semántica, así que en el árbol **no existe**. Se declara el
 * límite en vez de inventarle un assert.
 *
 * **Cuatro — que la letra crezca de verdad.** [MspTheme] no lee
 * [LocalFontSizeLevel]; el `fontScale` efectivo lo pone la raíz de composición
 * de `app/`. En un test, subir el nivel sólo mueve lo que lee ese local a mano
 * —el ancho de la columna de la hora—, que es justo el arreglo bajo prueba.
 *
 * ## Lo que se prueba en otro lado, y aquí no se repite
 *
 * - `GruposYFiltrosDeContactosTest` — que `porMes`/`porCercania` partan bien y
 *   que `deja` filtre. Es dominio puro.
 * - `UnContactoAbreSuPropioMapaTest` — que la fila con punto abra SU mapa, que
 *   la fila sin `ubicacion` no monte `clickable`, y el piso de 50 dp de la fila.
 */
@Config(qualifiers = "w360dp-h800dp-xhdpi")
class LaLineaDiceQuienComoYCuandoTest : RobolectricTestBase() {

    @get:Rule
    val composeTestRule = createComposeRule()

    // --- La fila enseña el hecho completo ------------------------------------

    /**
     * **Los cinco datos que la fila vieja tiraba a la basura.**
     *
     * Van juntos en un solo test porque son una sola afirmación: *"el renglón
     * dice lo que pasó"*. Si mañana alguien recorta uno para ganar alto, esto se
     * pone rojo nombrando cuál.
     *
     * La hora se afirma en zona de negocio: `22:45Z` es `16:45` en
     * `America/Mexico_City`. Afirmarla en UTC dejaría pasar una fila que le
     * enseñara al cobrador una hora a la que él no estuvo en ninguna puerta.
     */
    @Test
    fun `un cobro pinta su hora, su forma de pago, su cobrador, su nota y su importe`() {
        fila(COBRO)

        composeTestRule.onNodeWithText(HORA_DEL_COBRO, useUnmergedTree = true)
            .assertIsDisplayed()
        composeTestRule.onNodeWithText(ETIQUETA_DEL_COBRO, useUnmergedTree = true)
            .assertIsDisplayed()
        // Forma de pago y cobrador comparten el renglón de meta, separados por
        // el punto medio. Se afirma el renglón entero: así el test también
        // cobra que no se pinte uno sin el otro.
        composeTestRule.onNodeWithText(
            "${MetodoDeCobro.EFECTIVO.etiqueta} · $COBRADOR",
            useUnmergedTree = true
        ).assertIsDisplayed()
        composeTestRule.onNodeWithText("“$NOTA”", useUnmergedTree = true)
            .assertIsDisplayed()
        composeTestRule.onNodeWithText(IMPORTE_PINTADO, useUnmergedTree = true)
            .assertIsDisplayed()
    }

    // --- La visita calla la forma de pago ------------------------------------

    /**
     * **Una visita NO pinta forma de pago, y es lo correcto.**
     *
     * `FORMA_COBRO_ID` existe en `VisitEntity`, pero el escritor de producción
     * la deja fija en 0 y `MetodoDeCobro.de(0)` cae a `EFECTIVO` — ver el KDoc
     * de [ContactoDeCobranza.metodo]. Pintarla diría **"efectivo" sobre un "no
     * estaba"**: un cobro que nunca ocurrió, en una pantalla de dinero.
     *
     * La visita de este test **sí trae cobrador**, a propósito: así el renglón
     * de meta se pinta igual, y la ausencia que se afirma es la de la forma de
     * pago y no la de la línea entera. El control positivo del método de
     * búsqueda está en el test de abajo.
     */
    @Test
    fun `una visita no pinta forma de pago`() {
        fila(VISITA)

        assertEquals(
            "la visita dijo “${MetodoDeCobro.EFECTIVO.etiqueta}”: la columna viene en 0 y " +
                "MetodoDeCobro.de(0) cae a EFECTIVO, así que eso es un cobro inventado",
            0,
            cuantosDicen(MetodoDeCobro.EFECTIVO.etiqueta)
        )
        // Y el renglón de meta SÍ está, con el cobrador solo. Sin esto, una fila
        // que hubiera dejado de pintar la meta entera pasaría en verde.
        composeTestRule.onNodeWithText(COBRADOR, useUnmergedTree = true).assertIsDisplayed()
    }

    /**
     * **Control positivo del de arriba.** El mismo [cuantosDicen] sobre un cobro
     * encuentra la forma de pago. Sin esto, la ausencia de arriba no prueba
     * nada: un `hasText` mal escrito —o una fila que dejara de pintar la meta—
     * daría cero en los dos casos y el test se vería verde para siempre.
     */
    @Test
    fun `control positivo - el mismo metodo si encuentra la forma de pago en un cobro`() {
        fila(COBRO)

        assertTrue(
            "el método de búsqueda no encuentra la forma de pago ni donde SÍ está",
            cuantosDicen(MetodoDeCobro.EFECTIVO.etiqueta) > 0
        )
    }

    // --- La hora, a las tres escalas -----------------------------------------

    /**
     * **La hora llega entera al nodo en los tres niveles de letra.**
     *
     * Control de reversión de un defecto real: la columna de la hora medía dp
     * absolutos y a 1.5 la hora salía cortada —`10:4`, `18:0`—. Una hora a
     * medias no es un dato incompleto, es un dato falso.
     *
     * **Lo que este test NO puede ver:** el recorte. `onNodeWithText` busca el
     * texto que la fila le pasó al nodo, y ese texto llega entero con la columna
     * ancha y con la columna angosta —la elipsis es cosa del render—. Lo que sí
     * prueba es que la fila **compone** la hora completa en las tres escalas, y
     * lo hace con `onNodeWithText`, que además exige que el nodo sea **único**:
     * una hora partida en dos nodos, o pegada a otro texto, no pasaría.
     *
     * La medición de verdad del arreglo está en el test de abajo; el recorte, en
     * los goldens.
     */
    @Test
    fun `la hora llega entera a las tres escalas de letra`() {
        tresEscalas()

        composeTestRule.onNodeWithText(HORA_NORMAL, useUnmergedTree = true)
            .assertTextEquals(HORA_NORMAL)
        composeTestRule.onNodeWithText(HORA_GRANDE, useUnmergedTree = true)
            .assertTextEquals(HORA_GRANDE)
        composeTestRule.onNodeWithText(HORA_MUY_GRANDE, useUnmergedTree = true)
            .assertTextEquals(HORA_MUY_GRANDE)
    }

    /**
     * **Y la columna se ensancha con el nivel — esto es el arreglo, medido.**
     *
     * `anchoDeLaHora()` multiplica el ancho base por `nominalScale`, así que de
     * `NORMAL` (1.0) a `MUY_GRANDE` (2.0) la columna tiene que **duplicarse**.
     * Un ancho fijo en dp —el defecto— daría la misma cifra en los tres niveles
     * y esto se pondría rojo.
     *
     * Se afirma la **proporción** y no un valor en dp a propósito: el ancho base
     * es un detalle privado de la pieza y puede afinarse sin que la regla
     * cambie. Se deja holgura (1.9 en vez de 2.0) por el redondeo a píxeles de
     * la densidad del qualifier.
     */
    @Test
    fun `la columna de la hora se ensancha con el nivel de letra`() {
        tresEscalas()

        val normal = anchoDeLaHora(HORA_NORMAL)
        val muyGrande = anchoDeLaHora(HORA_MUY_GRANDE)

        assertTrue(
            "la columna de la hora mide $normal a escala normal y $muyGrande a muy grande: " +
                "no se está multiplicando por nominalScale, que es el arreglo que evita " +
                "que la hora salga cortada",
            muyGrande.value >= normal.value * CRECIMIENTO_MINIMO
        )
    }

    // --- El punto de estado, a las tres escalas ------------------------------

    /**
     * **El punto de estado crece con la letra — el otro arreglo, medido.**
     *
     * Era `6.dp` fijos, con un `padding(top)` fijo también, mientras la etiqueta
     * de al lado duplica su tamaño y se va a dos renglones. A `MUY_GRANDE` el
     * punto quedaba flotando arriba de un bloque de dos líneas y se leía como un
     * píxel sucio — y el punto es el **único** portador del estado en la fila,
     * justo en la escala que existe para quien ve mal.
     *
     * Se miden el **ancho** (el punto) y el **alto** (el punto más su
     * desplazamiento vertical) porque el defecto tenía esas dos mitades: un
     * arreglo que escalara sólo el tamaño dejaría el punto del tamaño correcto y
     * en el renglón equivocado.
     *
     * Se afirma la proporción y no dp, y con holgura de 1.9, por lo mismo que la
     * columna de la hora: el valor base es privado de la pieza y la densidad
     * redondea a píxeles.
     */
    @Test
    fun `el punto de estado se agranda y baja con el nivel de letra`() {
        tresEscalas()

        val puntos = composeTestRule.onAllNodesWithTag(PUNTO_DEL_ESTADO_TAG, useUnmergedTree = true)
        val normal = puntos[0].getUnclippedBoundsInRoot()
        val muyGrande = puntos[2].getUnclippedBoundsInRoot()

        assertTrue(
            "el punto mide ${normal.width} de ancho a escala normal y ${muyGrande.width} a " +
                "muy grande: no se está multiplicando por nominalScale, así que a 2.0 es " +
                "una mota junto a una etiqueta del doble de alto",
            muyGrande.width.value >= normal.width.value * CRECIMIENTO_MINIMO
        )
        assertTrue(
            "el punto arranca a ${normal.height} del tope de la fila y a ${muyGrande.height} " +
                "a muy grande: su desplazamiento vertical sigue fijo, así que queda pegado " +
                "al borde de arriba de un bloque de dos renglones",
            muyGrande.height.value >= normal.height.value * CRECIMIENTO_MINIMO
        )
    }

    // --- El subtotal del grupo -----------------------------------------------

    /**
     * **Un tramo de puras visitas no pinta subtotal — ni siquiera `$0`.**
     *
     * `GrupoDeContactos.cobrado` llega en `null` y el encabezado no pinta nada.
     * Un `$0` ahí diría *"ese mes se midió y dio cero"*, cuando lo que pasó es
     * que sólo hubo visitas: dos hechos distintos que un cero aplana.
     *
     * El encabezado **sí** se afirma presente: sin eso, un `EncabezadoDeGrupo`
     * que no pintara nada en absoluto pasaría este test.
     */
    @Test
    fun `un tramo de puras visitas no pinta subtotal`() {
        encabezadoDe(listOf(VISITA, OTRA_VISITA))

        composeTestRule.onNodeWithTag(ENCABEZADO_DE_GRUPO_TAG).assertIsDisplayed()
        assertEquals(
            "se pintó un subtotal en un tramo sin un solo cobro: un cero en una pantalla " +
                "de dinero se lee como una medición, y ahí no se midió nada",
            0,
            composeTestRule.onAllNodesWithTag(COBRADO_DEL_GRUPO_TAG, useUnmergedTree = true)
                .fetchSemanticsNodes().size
        )
    }

    /**
     * **Control positivo del de arriba.** Con un cobro adentro el subtotal sí
     * aparece, y con la cifra que entró. Sin esto, un encabezado que hubiera
     * dejado de pintar el subtotal **siempre** pasaría el test de la ausencia.
     */
    @Test
    fun `control positivo - con un cobro adentro el tramo si pinta su subtotal`() {
        encabezadoDe(listOf(VISITA, COBRO))

        composeTestRule.onNodeWithTag(COBRADO_DEL_GRUPO_TAG, useUnmergedTree = true)
            .assertIsDisplayed()
            .assertTextEquals(IMPORTE_PINTADO)
    }

    // --- Los filtros ---------------------------------------------------------

    /**
     * El estado de arranque: con `TODOS` se ven los cinco. Es el control
     * positivo de los dos tests de abajo —sin él, una lista que no pintara nada
     * pasaría cualquier aserción de *"quedan menos"*— y además cobra el default
     * de la pastilla encendida.
     */
    @Test
    fun `al abrir se ven todos los contactos`() {
        lineaFiltrable()

        assertEquals(LA_LINEA.size, cuantasFilas())
    }

    /**
     * Tocar **Cobros** deja sólo lo que dejó dinero. Lo que se cobra aquí es la
     * pastilla: que [FiltrosDeContacto] reporte el filtro que el dedo tocó y que
     * la lista se recomponga con él. Que `deja` separe bien ya lo prueba
     * `GruposYFiltrosDeContactosTest`; lo que sería invisible sin esto es una
     * pastilla que llamara a `onElegir` con el filtro equivocado, o que no lo
     * llamara.
     */
    @Test
    fun `tocar Cobros deja solo los cobros`() {
        lineaFiltrable()

        composeTestRule.onNodeWithTag(FILTRO_TAG + FiltroDeContactos.COBROS.name).performClick()

        assertEquals(COBROS_EN_LA_LINEA, cuantasFilas())
    }

    /** Y **Visitas** deja sólo las puertas tocadas — la otra mitad del filtro. */
    @Test
    fun `tocar Visitas deja solo las visitas`() {
        lineaFiltrable()

        composeTestRule.onNodeWithTag(FILTRO_TAG + FiltroDeContactos.VISITAS.name).performClick()

        assertEquals(VISITAS_EN_LA_LINEA, cuantasFilas())
    }

    // --- La marca de la venta abierta ----------------------------------------

    /**
     * **La marca de la venta abierta NO se puede afirmar, y se dice en vez de
     * fingirlo.**
     *
     * `deEstaVenta` pinta un `Box` de 3 dp con `background(brand)` y **sin
     * semántica**: ni `testTag`, ni texto, ni `contentDescription`. En el árbol
     * de semántica no existe, así que ningún `onNode*` lo alcanza — y en
     * Robolectric no hay píxeles que leer. Cualquier assert que se escribiera
     * aquí pasaría igual con la marca y sin ella, que es peor que no tener test.
     * Quien tenga que cobrar el color: los goldens del detalle de venta.
     *
     * Lo único medible es esto, y sí vale: **la marca no corre el contenido**.
     * El `Box` se compone siempre —`Color.Transparent` cuando la fila no es de
     * la venta— justamente para que el canalón esté reservado. Una
     * "optimización" que lo montara sólo al marcar correría la fila marcada 3 dp
     * (más el `spacedBy`) respecto de sus vecinas, y la columna de horas —que
     * existe para leerse de corrido— quedaría en zigzag.
     */
    @Test
    fun `la marca de la venta no corre el contenido de la fila`() {
        composeTestRule.setContent {
            Tema(FontSizeLevel.NORMAL) {
                Column {
                    ContactoEnLinea(contacto = COBRO, deEstaVenta = true)
                    ContactoEnLinea(contacto = VISITA, deEstaVenta = false)
                }
            }
        }

        val marcada = izquierdaDe(HORA_DEL_COBRO)
        val sinMarcar = izquierdaDe(HORA_DE_LA_VISITA)

        assertEquals(
            "la fila marcada arranca en $marcada y la normal en $sinMarcar: el canalón de " +
                "la marca no se está reservando siempre, así que marcar una fila la corre " +
                "de lugar y la columna de horas deja de alinear",
            sinMarcar.value,
            marcada.value,
            TOLERANCIA_DE_PIXEL
        )
    }

    // --- Montaje -------------------------------------------------------------

    /** Una sola fila, suelta. Es la pieza bajo prueba, no una pantalla. */
    private fun fila(contacto: ContactoDeCobranza, nivel: FontSizeLevel = FontSizeLevel.NORMAL) {
        composeTestRule.setContent {
            Tema(nivel) { ContactoEnLinea(contacto = contacto) }
        }
    }

    /**
     * Las tres escalas **en una sola composición**, una fila por nivel y con
     * horas distintas para poder nombrarlas por separado. Así los dos tests de
     * la hora comparan niveles entre sí sin montar tres veces —`setContent` se
     * llama una vez por regla—.
     */
    private fun tresEscalas() {
        composeTestRule.setContent {
            MspTheme(darkTheme = false, animateColors = false) {
                Column {
                    CompositionLocalProvider(LocalFontSizeLevel provides FontSizeLevel.NORMAL) {
                        ContactoEnLinea(contacto = COBRO.copy(fecha = EN_NORMAL))
                    }
                    CompositionLocalProvider(LocalFontSizeLevel provides FontSizeLevel.GRANDE) {
                        ContactoEnLinea(contacto = COBRO.copy(fecha = EN_GRANDE))
                    }
                    CompositionLocalProvider(LocalFontSizeLevel provides FontSizeLevel.MUY_GRANDE) {
                        ContactoEnLinea(contacto = COBRO.copy(fecha = EN_MUY_GRANDE))
                    }
                }
            }
        }
    }

    /**
     * El encabezado del tramo que [GruposDeContactos.porMes] arma con
     * [contactos]. Se arma con el dominio de producción y no a mano: un
     * `GrupoDeContactos` escrito en el test podría traer un `cobrado` que la app
     * nunca produciría, y entonces el encabezado se estaría probando contra un
     * dato imposible.
     */
    private fun encabezadoDe(contactos: List<ContactoDeCobranza>) {
        composeTestRule.setContent {
            Tema(FontSizeLevel.NORMAL) {
                EncabezadoDeGrupo(grupo = GruposDeContactos.porMes(contactos).single())
            }
        }
    }

    /**
     * Las pastillas y la lista que filtran, **sueltas**: es el mismo cableado
     * que `BitacoraScreen` y `DetalleVentaScreen` hacen —filtrar con
     * `FiltroDeContactos.deja` antes de agrupar—, con el estado en el test en
     * vez de en el ViewModel.
     *
     * **El límite:** esto no cobra que la pantalla real conecte su `onFiltrar`
     * con el ViewModel. Un `BitacoraScreen` que pasara `onFiltrar = {}` dejaría
     * este test en verde.
     */
    private fun lineaFiltrable() {
        composeTestRule.setContent {
            Tema(FontSizeLevel.NORMAL) {
                var elegido by remember { mutableStateOf(FiltroDeContactos.TODOS) }
                Column {
                    FiltrosDeContacto(elegido = elegido, onElegir = { elegido = it })
                    LA_LINEA.filter(elegido::deja).forEach { ContactoEnLinea(contacto = it) }
                }
            }
        }
    }

    @Composable
    private fun Tema(nivel: FontSizeLevel, contenido: @Composable () -> Unit) {
        CompositionLocalProvider(LocalFontSizeLevel provides nivel) {
            MspTheme(darkTheme = false, animateColors = false, content = contenido)
        }
    }

    // --- Lecturas del árbol --------------------------------------------------

    /**
     * Cuántos nodos dicen [texto], en cualquier parte de su cadena. Es **el
     * método** que comparten la aserción de ausencia y su control positivo: si
     * los dos no usan exactamente esta búsqueda, el control no controla nada.
     */
    private fun cuantosDicen(texto: String): Int =
        composeTestRule.onAllNodes(hasText(texto, substring = true), useUnmergedTree = true)
            .fetchSemanticsNodes().size

    private fun cuantasFilas(): Int =
        composeTestRule.onAllNodesWithTag(CONTACTO_EN_LINEA_TAG).fetchSemanticsNodes().size

    private fun anchoDeLaHora(hora: String) =
        composeTestRule.onNodeWithText(hora, useUnmergedTree = true)
            .getUnclippedBoundsInRoot()
            .width

    private fun izquierdaDe(hora: String) =
        composeTestRule.onNodeWithText(hora, useUnmergedTree = true)
            .getUnclippedBoundsInRoot()
            .left

    private companion object {

        /**
         * De `NORMAL` (1.0) a `MUY_GRANDE` (2.0) la columna se duplica. Se exige
         * 1.9 y no 2.0 por el redondeo a píxeles de la densidad.
         */
        const val CRECIMIENTO_MINIMO = 1.9f

        /** Un dp de holgura: comparar bordes al float exacto es frágil. */
        const val TOLERANCIA_DE_PIXEL = 1.0f

        const val COBRADOR = "Marisol Vega"
        const val NOTA = "dejo la mitad, vuelvo el viernes"
        const val ETIQUETA_DEL_COBRO = "Cobré"

        /** `$#,##0` sobre 350 — lo que `MspMoneyText` pinta. */
        const val IMPORTE_PINTADO = "$350"

        // Las horas van en zona de negocio (America/Mexico_City, UTC-6 todo el
        // año desde 2022), que es la que la fila usa para formatear.
        const val HORA_DEL_COBRO = "16:45"
        const val HORA_DE_LA_VISITA = "08:05"

        /** `10:20` y `18:05`: las dos horas que el defecto cortaba a `10:4` y `18:0`. */
        const val HORA_NORMAL = "10:20"
        const val HORA_GRANDE = "18:05"
        const val HORA_MUY_GRANDE = "23:30"

        val EN_NORMAL: Instant = Instant.parse("2026-09-02T16:20:00Z")
        val EN_GRANDE: Instant = Instant.parse("2026-09-03T00:05:00Z")
        val EN_MUY_GRANDE: Instant = Instant.parse("2026-09-04T05:30:00Z")

        /** El hecho completo: entró dinero, con qué, quién y qué se dijo. */
        val COBRO = ContactoDeCobranza(
            fecha = Instant.parse("2026-09-11T22:45:00Z"),
            etiqueta = ETIQUETA_DEL_COBRO,
            nota = NOTA,
            estado = EstadoCuenta.PAGO,
            importe = Money.of(BigDecimal("350.00")),
            tipo = TipoDeContacto.COBRO,
            metodo = MetodoDeCobro.EFECTIVO,
            cobrador = COBRADOR
        )

        /**
         * Una puerta tocada. **Con cobrador y sin método**, que es como llega de
         * producción: el cobrador existe, la forma de pago no.
         */
        val VISITA = ContactoDeCobranza(
            fecha = Instant.parse("2026-09-10T14:05:00Z"),
            etiqueta = "No responde aunque está",
            nota = null,
            estado = EstadoCuenta.VISITE_VUELVO,
            importe = null,
            tipo = TipoDeContacto.VISITA,
            metodo = null,
            cobrador = COBRADOR
        )

        val OTRA_VISITA = ContactoDeCobranza(
            fecha = Instant.parse("2026-09-09T18:30:00Z"),
            etiqueta = "dijo que el viernes",
            nota = null,
            estado = EstadoCuenta.PROMETIO_PROXIMA,
            importe = null,
            tipo = TipoDeContacto.VISITA,
            cobrador = COBRADOR
        )

        /** Dos cobros y tres visitas, todos del mismo mes para no partir tramos. */
        val LA_LINEA = listOf(
            COBRO,
            VISITA,
            OTRA_VISITA,
            COBRO.copy(
                fecha = Instant.parse("2026-09-08T15:10:00Z"),
                metodo = MetodoDeCobro.TRANSFERENCIA,
                nota = null
            ),
            VISITA.copy(
                fecha = Instant.parse("2026-09-07T20:00:00Z"),
                etiqueta = "no estaba",
                estado = EstadoCuenta.NO_ESTABA
            )
        )

        val COBROS_EN_LA_LINEA = LA_LINEA.count { it.tipo == TipoDeContacto.COBRO }
        val VISITAS_EN_LA_LINEA = LA_LINEA.count { it.tipo == TipoDeContacto.VISITA }
    }
}
