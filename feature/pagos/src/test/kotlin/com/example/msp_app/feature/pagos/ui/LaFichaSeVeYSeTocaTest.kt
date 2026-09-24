package com.example.msp_app.feature.pagos.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
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
import com.example.msp_app.feature.pagos.ui.components.AFORDANTE_TEXTO_TAG
import com.example.msp_app.feature.pagos.ui.components.CHIP_DE_FICHA_TAG
import com.example.msp_app.feature.pagos.ui.components.CTA_NOTAS_TAG
import com.example.msp_app.feature.pagos.ui.components.CuerpoDeLaFicha
import com.example.msp_app.feature.pagos.ui.components.DOCK_DE_ACCIONES_TAG
import com.example.msp_app.feature.pagos.ui.components.EDITAR_FICHA_TAG
import com.example.msp_app.feature.pagos.ui.components.NOTA_DESTACADA_TAG
import com.example.msp_app.feature.pagos.ui.components.NOTA_DE_LA_FICHA_TAG
import com.example.msp_app.feature.pagos.ui.components.PARCIALIDAD_DEL_CLIENTE_TAG
import com.example.msp_app.feature.pagos.ui.components.SALDO_DEL_CLIENTE_TAG
import com.example.msp_app.feature.pagos.ui.components.SENAL_TAG
import com.example.msp_app.feature.pagos.ui.components.TARJETA_DE_LA_FICHA_TAG
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * **La ficha no puede tapar el dinero.** Éste es el test que fija el criterio
 * de aceptación de la ronda 1, y es medible, no una impresión.
 *
 * La primera versión de esta tarea puso la tarjeta de la ficha entre el saldo y
 * "sus ventas": a escala 2.0 no quedaba **ni una venta visible** sin desplazar,
 * y el golden `promesa_sin_fecha` perdió de vista justo el renglón que existe
 * para proteger. El dinero es por lo que el cobrador abre esta pantalla.
 *
 * Lo que se afirma aquí, y que un rediseño futuro no puede romper en silencio:
 *
 * 1. **Las señales de la ficha cuestan cero dp arriba de "sus ventas"** — el
 *    tope de esa sección es el MISMO con señales, sin ellas o con la ficha
 *    ilegible. Medido, no supuesto, y así **agregar un valor al catálogo
 *    tampoco empuja nada**.
 * 2. **El dinero se ve sin desplazar** en 1.0, 1.5 y 2.0 — hoy, **con la
 *    salvedad de la nota** que la sección de abajo detalla y mide.
 * 3. **El afordante ocupa un renglón acotado**, que es la única razón por la que
 *    vive arriba.
 *
 * ## Qué cambió con la hoja continua, y por qué NO es aflojar el test
 *
 * Hasta la hoja continua, el punto 2 se medía sobre **la primera venta**: el
 * saldo iba arriba y "sus ventas" venía enseguida, así que el primer renglón de
 * venta era la frontera del dinero visible.
 *
 * En la variante B —la que el dueño eligió— entre el encabezado y las ventas hay
 * ahora identidad, acciones, saldo, las tres cifras y el ritmo, así que **la
 * primera venta cae bajo la línea de flotación por construcción**. Eso no se
 * puede "arreglar" sin deshacer la pantalla que se pidió.
 *
 * Lo que el punto 2 siempre estuvo protegiendo no era el renglón de venta: era
 * que **un dato de conocimiento no tape el dinero**. Ese dinero ahora es el saldo
 * total y la parcialidad, y los dos siguen arriba de la línea. Así que la
 * medición se re-apunta a ellos —sigue siendo geometría, sigue siendo en las
 * tres escalas— y el punto 1, que es el que de verdad vigila a la ficha, se
 * queda intacto y en verde.
 *
 * ## Lo que cambió con la nota arriba, y qué se recuperó después
 *
 * No se disimula: **el dueño decidió subir la nota del cobrador a una tarjeta
 * propia arriba del dinero**, y eso rompió el punto 2 **cuando hay nota**.
 * Medido entonces en esta misma pantalla, con el fixture de siempre:
 *
 * | Escala | Parcialidad sin nota | Con nota | Tope del dock |
 * |---|---|---|---|
 * | NORMAL | 561.5 dp | 727.5 dp | 672.0 dp |
 * | GRANDE | 549.0 dp | 725.5 dp | 620.0 dp |
 * | MUY_GRANDE | 549.0 dp | 732.0 dp | 620.0 dp |
 *
 * Eso quedó pendiente de la decisión del dueño, que eligió **adelgazar la
 * tarjeta**: el botón de editar al renglón del rótulo y la nota recortada a dos
 * renglones con un toque para desplegarla. Medido con gráficos NATIVOS —ver
 * abajo por qué eso importa— la tarjeta pasó de 154.5 a 131.5 dp a `NORMAL`, de
 * 214.5 a 179.5 a `GRANDE` y de 313.0 a 244.5 a `MUY_GRANDE`.
 *
 * Esos 23 dp habrían bastado a escala nominal. Lo que se los comió fue el otro
 * arreglo de la misma pasada: **la dirección escrita volvió** al bloque de
 * identidad, porque `f8621920` la había quitado y el dueño lo reportó desde el
 * teléfono. Cuesta 19.0 dp con la dirección corta del fixture y 34.5 con una
 * real de dos renglones. La cuenta completa está en el KDoc de
 * `con nota, lo que el dock tapa del SALDO TOTAL no crece`.
 *
 * **A `GRANDE` y `MUY_GRANDE` no cabe, y no se puede hacer caber.** Es
 * aritmética, no una opinión: a `GRANDE` el bloque del saldo termina en 553.0 dp
 * SIN tarjeta y el dock empieza en 620.0, así que la tarjeta entera tendría que
 * medir menos de 55 dp — y un solo renglón de nota más el padding de la tarjeta
 * ya son 63.5. Se reporta en vez de disimularse.
 *
 * Lo que estos tests miden hoy:
 *
 * 1. **Sin nota no cambió nada** — el punto 2 sigue entero, en las tres
 *    escalas. Es el estado de la mayoría de las puertas.
 * 2. **Las señales siguen costando cero** — el punto 1, que es el que vigila a
 *    la ficha, se queda tal cual: agregar un valor al catálogo no empuja nada.
 * 3. **La tarjeta es TODO lo que se movió** — el delta se mide contra el alto
 *    de la propia tarjeta, así que nadie puede colarle alto a la pantalla por
 *    otro lado mientras esto siga verde.
 * 4. **Con nota, lo que el dock tapa del `SALDO TOTAL` no crece** — el criterio
 *    del dueño todavía NO se cumple a escala nominal, y en vez de afirmar algo
 *    falso se mide la deuda y se prohíbe que aumente. El KDoc de ese test tiene
 *    los dp de cada cosa y qué falta decidir.
 *
 * ## Por qué el criterio del dueño se mide con [GraphicsMode.Mode.NATIVE]
 *
 * Porque el resto de este archivo NO lo usa, y con los gráficos legado de
 * Robolectric las métricas de fuente son inventadas: los números de la tabla de
 * arriba son los de ese modo, y **no son los del golden que el dueño mira**. En
 * legado el saldo total ya "cabía" a `NORMAL` por 21.5 dp mientras el golden
 * `pagos_cliente_light_1_0` lo enseñaba cortado por el dock. Una aserción sobre
 * el criterio del dueño en modo legado habría dado verde contra la pantalla
 * rota, que es exactamente la trampa que este repo ya documentó para los anchos
 * de texto y que vale igual para los altos.
 *
 * Los otros tests se quedan en legado a propósito: miden **diferencias** entre
 * dos estados de la misma pantalla, y ahí el modo se cancela.
 */
@Config(qualifiers = "w360dp-h800dp-xhdpi")
class LaFichaSeVeYSeTocaTest : RobolectricTestBase() {

    @get:Rule
    val composeTestRule = createComposeRule()

    private var abrio = 0

    /**
     * La ficha que la pantalla está pintando. Es estado y no parámetro para que
     * un mismo test pueda **cambiarla y volver a medir** — `setContent` solo se
     * puede llamar una vez por prueba, y comparar el layout entre dos fichas es
     * justo lo que cierra I-5.
     */
    private val fichaEnPantalla = mutableStateOf<FichaDelCliente?>(null)

    private fun cliente(
        ficha: FichaDelCliente? = PagosFixtures.fichaDelCliente(),
        nivel: FontSizeLevel = FontSizeLevel.NORMAL
    ) {
        fichaEnPantalla.value = ficha
        composeTestRule.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(
                LocalDensity provides Density(density.density, nivel.nominalScale),
                LocalFontSizeLevel provides nivel
            ) {
                MspTheme(darkTheme = false, animateColors = false) {
                    Pantalla(fichaEnPantalla.value)
                }
            }
        }
    }

    @Composable
    private fun Pantalla(ficha: FichaDelCliente?) {
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

    private fun bordesDe(tag: String): DpRect =
        composeTestRule.onNodeWithTag(tag).getUnclippedBoundsInRoot()

    /** El texto que la pastilla de la barra está diciendo. */
    private fun afordante() =
        composeTestRule.onNodeWithTag(AFORDANTE_TEXTO_TAG, useUnmergedTree = true)

    /** Un chip de la TARJETA (no de la hoja). */
    private fun chip(senal: SenalDeFicha) =
        composeTestRule.onNodeWithTag(CHIP_DE_FICHA_TAG + senal.ordinal, useUnmergedTree = true)

    private fun topeDeSusVentas(): Dp =
        composeTestRule.onNodeWithText(SUS_VENTAS).getUnclippedBoundsInRoot().top

    // --- I-5: el dinero no se tapa ------------------------------------------

    @Test
    fun `sin nota, el dinero se ve sin desplazar a escala NORMAL`() {
        cliente(ficha = PagosFixtures.fichaSinNota())
        elDineroCabeArribaDelDock()
    }

    @Test
    fun `sin nota, el dinero se ve sin desplazar a escala GRANDE`() {
        cliente(ficha = PagosFixtures.fichaSinNota(), nivel = FontSizeLevel.GRANDE)
        elDineroCabeArribaDelDock()
    }

    @Test
    fun `sin nota, el dinero se ve sin desplazar a escala MUY GRANDE`() {
        cliente(ficha = PagosFixtures.fichaSinNota(), nivel = FontSizeLevel.MUY_GRANDE)
        elDineroCabeArribaDelDock()
    }

    /**
     * **Con nota, lo único que empujó el dinero es la tarjeta.**
     *
     * Ésta es la medición que reemplaza al punto 2 mientras el dueño decide
     * qué hacer con el conflicto (ver el encabezado de la clase). No afirma
     * que el dinero quepa —hoy no cabe— ni congela el número en que no cabe:
     * afirma que **el desplazamiento es exactamente el alto de la tarjeta más
     * su separación**, que es lo que impide que alguien le cuele alto a esta
     * pantalla por otro lado y lo achaque a la nota.
     *
     * Y no bloquea el arreglo: el día que la tarjeta adelgace, los dos lados
     * de la igualdad adelgazan juntos y esto sigue verde con el dinero de
     * vuelta arriba.
     */
    @Test
    fun `con nota, lo unico que empuja el dinero es el alto de la tarjeta`() {
        cliente(ficha = PagosFixtures.fichaSinNota())
        val sinNota = bordesDe(PARCIALIDAD_DEL_CLIENTE_TAG).bottom

        fichaEnPantalla.value = PagosFixtures.fichaDelCliente()
        composeTestRule.waitForIdle()
        val conNota = bordesDe(PARCIALIDAD_DEL_CLIENTE_TAG).bottom
        val tarjeta = bordesDe(NOTA_DESTACADA_TAG).let { it.bottom - it.top }

        assertEquals(
            "el dinero bajó " + (conNota - sinNota) + " y la tarjeta mide " + tarjeta +
                ": algo más creció con la nota puesta",
            tarjeta + SEPARACION_DE_LA_TARJETA,
            conNota - sinNota
        )
    }

    /**
     * **El criterio del dueño, cumplido**: *"a escala 1.0 el `SALDO TOTAL` tiene
     * que caber entero sobre el dock"*.
     *
     * Y cumplido con la dirección **real** del padrón, que es lo que hace que
     * esto signifique algo: el fixture de `PagosFixtures` trae desde el 24-sep la
     * cadena que compone el API —calle, número, colonia y población—, de dos
     * renglones, y no los veintiséis caracteres de antes que cabían en cualquier
     * lado. Con la dirección corta esta medición habría dado verde sobre una
     * pantalla que en la calle no cabe.
     *
     * ## Los dp, y de dónde salió cada uno
     *
     * Medido a `NORMAL` con gráficos NATIVOS en `w360dp-h800dp`, contra la banda
     * del dock (655.0 dp):
     *
     * | | Saldo termina en | Tapado |
     * |---|---|---|
     * | Antes de esta pasada (dirección corta, sin renglón) | 669.5 dp | 14.5 |
     * | Tarjeta de nota adelgazada (−23.0 dp) | 646.5 dp | cabe por 8.5 |
     * | Con la dirección escrita de vuelta, ya REAL (+34.5) | 681.0 dp | 26.0 |
     * | Con el cuadro de la puerta a 100 dp (−30.0) | **651.0 dp** | **cabe por 4.0** |
     *
     * Los 26 dp los pagó el cuadro, con permiso del dueño y aplicando el
     * criterio que su propio KDoc ya declaraba —*"entre un dibujo y un dato,
     * cede el dibujo"*—, el mismo con el que ya había cedido a 40 dp en las
     * escalas grandes. Ver `CUADRO_DEL_MOCK` en `PiezasDelCliente.kt`.
     *
     * ## Entero quiere decir el BLOQUE
     *
     * Se mide [SALDO_DEL_CLIENTE_TAG] —rótulo, cifra y pastilla de atrasos— y no
     * el rótulo: apuntando al rótulo la aserción daba verde con el `$3,550`
     * partido a la mitad, que es lo que el golden enseñaba.
     *
     * ## Y contra [DOCK_DE_ACCIONES_TAG], no contra el botón
     *
     * Entre el canto del dock y el botón primario hay 17 dp de hairline y padding
     * que el botón no cubre y la banda sí. Con `CTA_PRIMARIO_TAG` esta medición
     * daba holgura sobre una pantalla cuyo golden enseñaba la cifra cortada.
     *
     * ## A `GRANDE` y `MUY_GRANDE` sigue sin caber, y no se maquilla
     *
     * Es aritmética: a `GRANDE`, sin tarjeta de nota, el saldo ya termina en
     * 553.0 dp contra una banda que empieza en 611.0 — y con la tarjeta más
     * chica posible no alcanza, porque un solo renglón de nota más el padding de
     * la tarjeta ya son 63.5 dp. El control positivo de abajo lo deja medido.
     */
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    @Test
    fun `con nota, el SALDO TOTAL cabe entero arriba del dock a escala NORMAL`() {
        cliente(ficha = PagosFixtures.fichaDelCliente())

        val saldo = bordesDe(SALDO_DEL_CLIENTE_TAG)
        val dock = bordesDe(DOCK_DE_ACCIONES_TAG)
        assertTrue(
            "el saldo total termina en " + saldo.bottom + " y la banda del dock empieza en " +
                dock.top + ": el dinero volvió a quedar tapado",
            saldo.bottom <= dock.top
        )
    }

    /**
     * **Y con la nota LARGA tampoco se tapa**, que es el caso que de verdad
     * aprieta: la nota del fixture cabe en los dos renglones asomados, así que
     * la de arriba mide la tarjeta en su estado más barato.
     *
     * Con una nota que no cabe, la tarjeta paga además el renglón del *"Ver
     * más"*. Si ese renglón hubiera costado más de los 4.0 dp de margen, el
     * criterio del dueño se cumpliría sólo para las puertas con nota corta — que
     * es la clase de "verde" que este archivo existe para no producir.
     */
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    @Test
    fun `con nota LARGA, el SALDO TOTAL tambien cabe entero`() {
        cliente(ficha = PagosFixtures.fichaDelCliente().copy(nota = NOTA_QUE_NO_CABE))

        val saldo = bordesDe(SALDO_DEL_CLIENTE_TAG)
        val dock = bordesDe(DOCK_DE_ACCIONES_TAG)
        assertTrue(
            "el saldo total termina en " + saldo.bottom + " y la banda del dock empieza en " +
                dock.top + ": con la nota larga el dinero se tapa",
            saldo.bottom <= dock.top
        )
    }

    /**
     * **A `GRANDE` NO cabe**, y se afirma para que el límite quede medido y no
     * como una frase en un KDoc.
     *
     * Es también el control positivo de los dos de arriba: sin esto, un
     * `SALDO_DEL_CLIENTE_TAG` mal escrito devolvería `Rect.Zero` —la trampa que
     * este repo ya pagó— y "0 <= 655" pasaría en verde sin haber medido nada.
     * Acá el mismo tag tiene que dar un rectángulo que NO cabe: sólo un nodo
     * real puede fallar.
     *
     * El día que esto se ponga rojo será porque a `GRANDE` ya cabe, y entonces
     * hay que subirlo a afirmación en vez de borrarlo.
     */
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    @Test
    fun `control positivo - con nota, a escala GRANDE el saldo NO cabe`() {
        cliente(ficha = PagosFixtures.fichaDelCliente(), nivel = FontSizeLevel.GRANDE)

        val saldo = bordesDe(SALDO_DEL_CLIENTE_TAG)
        val dock = bordesDe(DOCK_DE_ACCIONES_TAG)
        assertTrue(
            "el saldo total termina en " + saldo.bottom + " y la banda del dock empieza en " +
                dock.top + ": a GRANDE ya cabe, sube esto a afirmación",
            saldo.bottom > dock.top
        )
    }

    /**
     * **La medición, no la impresión.** `assertIsDisplayed()` NO sirve para
     * esto y se comprobó: da verde con el nodo apenas asomado, así que pasaba
     * igual con la ficha empujando el dinero fuera de pantalla. Lo que sí
     * distingue es la geometría: el renglón de la primera venta tiene que caber
     * **entero arriba del dock**, que es donde termina el área visible sin
     * desplazar.
     */
    private fun elDineroCabeArribaDelDock() {
        // La parcialidad es la más BAJA de las cifras de dinero de la pantalla:
        // va debajo del saldo total, en la segunda banda de la hoja del dinero.
        // Si ella cabe, el saldo cabe. Medir la de abajo es la afirmación fuerte.
        val dinero = bordesDe(PARCIALIDAD_DEL_CLIENTE_TAG)
        val dock = bordesDe(DOCK_DE_ACCIONES_TAG)
        assertTrue(
            "el dinero termina en " + dinero.bottom +
                " y el dock empieza en " + dock.top + ": queda tapado",
            dinero.bottom <= dock.top
        )
    }

    /**
     * **La medición que cierra I-5.** Las SEÑALES de la ficha no pueden mover
     * "sus ventas" ni un dp, porque viven abajo y su afordante no cuesta alto.
     * Es también la garantía de que **agregar un valor al catálogo** —cosa que
     * una ronda anterior hizo— no vuelve a empujar el dinero.
     *
     * **Lo que salió de esta medición es la NOTA**, y se dice sin adornos: hoy
     * sí mueve el dinero, porque el dueño pidió subirla a una tarjeta propia.
     * Ese costo lo mide `con nota, lo unico que empuja el dinero es el alto de
     * la tarjeta`, arriba, y su consecuencia está en el encabezado de la clase.
     * Las cuatro fichas que se comparan acá se diferencian **sólo en señales**,
     * que es lo que este test siempre estuvo vigilando.
     */
    @Test
    fun `el tope de sus ventas es el MISMO con senales, sin ellas o con ficha ilegible`() {
        cliente(ficha = PagosFixtures.fichaConAdvertencia().copy(nota = null, actualizada = null))
        val conAdvertencia = topeDeSusVentas()

        fichaEnPantalla.value = FichaDelCliente()
        composeTestRule.waitForIdle()
        assertEquals("una ficha vacía no puede mover el dinero", conAdvertencia, topeDeSusVentas())

        fichaEnPantalla.value = null
        composeTestRule.waitForIdle()
        assertEquals("una ficha ilegible tampoco", conAdvertencia, topeDeSusVentas())

        fichaEnPantalla.value = PagosFixtures.fichaSinNota()
        composeTestRule.waitForIdle()
        assertEquals(conAdvertencia, topeDeSusVentas())
    }

    /**
     * El afordante es **un renglón, y acotado**. Es la única razón por la que
     * puede vivir arriba: si creciera con el contenido de la ficha, agregar una
     * señal al catálogo empujaría el dinero — que es justo lo que el punto 1
     * prohíbe.
     */
    @Test
    fun `el afordante ocupa un renglon acotado`() {
        cliente(ficha = PagosFixtures.fichaConAdvertencia())
        assertTrue(
            "el afordante no puede pasar de un renglón tocable",
            bordesDe(EDITAR_FICHA_TAG).let { it.bottom - it.top } <= TOQUE
        )
    }

    // --- Dónde cae la sección -----------------------------------------------

    @Test
    fun `la ficha vive ABAJO, despues de sus ventas`() {
        cliente()
        val ventas = composeTestRule.onNodeWithText(SUS_VENTAS).getUnclippedBoundsInRoot()
        assertTrue(
            "la ficha no puede quedar arriba del dinero",
            bordesDe(TARJETA_DE_LA_FICHA_TAG).top > ventas.bottom
        )
        // Y ahí abajo no se ve sin desplazar — es el precio aceptado, y por eso
        // lo que no puede esperar sube a la barra.
        composeTestRule.onNodeWithTag(TARJETA_DE_LA_FICHA_TAG).assertIsNotDisplayed()
        composeTestRule.onNodeWithTag(TARJETA_DE_LA_FICHA_TAG)
            .performScrollTo()
            .assertIsDisplayed()
    }

    // --- I-2: la advertencia grita ------------------------------------------

    @Test
    fun `la advertencia se ve SIN desplazar, en la barra`() {
        cliente(ficha = PagosFixtures.fichaConAdvertencia())
        afordante().assertIsDisplayed().assertTextEquals("Hay perro")
    }

    /**
     * **Sin advertencia, la pastilla no está.**
     *
     * Antes decía "ver ficha", y eso era el MISMO camino dos veces: las Notas ya
     * tienen su botón en el dock. La pastilla se quedaba con el ancho
     * del nombre del cliente para no decir nada nuevo — medido en el golden, el
     * título se recortaba a "Victoria Fl…".
     *
     * Lo que se afirma aquí es que el camino a la ficha **no se pierde**: sigue
     * estando, en la acción de abajo.
     */
    @Test
    fun `control positivo - sin advertencia no hay pastilla, pero si hay camino`() {
        cliente()
        composeTestRule.onNodeWithTag(EDITAR_FICHA_TAG).assertDoesNotExist()
        // El camino ya no es el icono de la fila de acciones: las Notas se
        // fueron al dock, que se ve sin desplazar. Lo que este test protege no
        // cambió — que el camino NO se pierda cuando la pastilla no está.
        composeTestRule.onNodeWithTag(CTA_NOTAS_TAG).assertIsDisplayed()
    }

    @Test
    fun `entre dos advertencias manda la de no ir solo`() {
        cliente(
            ficha = FichaDelCliente(
                senales = setOf(SenalDeFicha.HAY_PERRO, SenalDeFicha.NO_IR_SOLO)
            )
        )
        afordante().assertTextEquals("No ir solo")
    }

    @Test
    fun `la advertencia va primero entre los chips de la tarjeta`() {
        cliente(ficha = PagosFixtures.fichaConAdvertencia())
        val perro = chip(SenalDeFicha.HAY_PERRO).performScrollTo().getUnclippedBoundsInRoot()
        val noche = chip(SenalDeFicha.ESTA_EN_LA_NOCHE).getUnclippedBoundsInRoot()
        assertTrue("la advertencia va primero", perro.top <= noche.top && perro.left <= noche.left)
    }

    /**
     * Una ficha vacía tampoco grita: no hay nada que advertir, y la sección de
     * abajo ya invita a anotarla. Lo que sí tiene que seguir habiendo es camino.
     */
    @Test
    fun `sin ficha tampoco hay pastilla, y el camino sigue`() {
        cliente(ficha = FichaDelCliente())
        composeTestRule.onNodeWithTag(EDITAR_FICHA_TAG).assertDoesNotExist()
        // El camino ya no es el icono de la fila de acciones: las Notas se
        // fueron al dock, que se ve sin desplazar. Lo que este test protege no
        // cambió — que el camino NO se pierda cuando la pastilla no está.
        composeTestRule.onNodeWithTag(CTA_NOTAS_TAG).assertIsDisplayed()
    }

    // --- El afordante --------------------------------------------------------

    @Test
    fun `tocar el afordante de la barra abre el editor`() {
        cliente(ficha = PagosFixtures.fichaConAdvertencia())
        composeTestRule.onNodeWithTag(EDITAR_FICHA_TAG).performClick()
        assertEquals(1, abrio)
    }

    @Test
    fun `tocar la tarjeta tambien abre el editor`() {
        cliente()
        composeTestRule.onNodeWithTag(TARJETA_DE_LA_FICHA_TAG).performScrollTo().performClick()
        assertEquals(1, abrio)
    }

    @Test
    fun `si no se pudo leer se dice, y NO hay nada que tocar`() {
        cliente(ficha = null)
        afordante().assertTextEquals("Notas ilegibles")
        composeTestRule.onNodeWithTag(EDITAR_FICHA_TAG).performClick()
        composeTestRule.onNodeWithTag(TARJETA_DE_LA_FICHA_TAG).performScrollTo().performClick()
        assertEquals("una pastilla muerta no puede abrir el editor", 0, abrio)
        composeTestRule.onNodeWithText("No se pudieron leer las notas").assertIsDisplayed()
    }

    // --- Los tres estados de la tarjeta --------------------------------------

    @Test
    fun `con senales se pintan sus etiquetas en espanol`() {
        cliente()
        chip(SenalDeFicha.ESTA_EN_LA_NOCHE).performScrollTo().assertIsDisplayed()
        chip(SenalDeFicha.ATIENDE_OTRA_PERSONA).assertIsDisplayed()
        composeTestRule.onNodeWithText("Está en la noche", useUnmergedTree = true)
            .assertIsDisplayed()
    }

    @Test
    fun `sin ficha se invita a anotar`() {
        cliente(ficha = FichaDelCliente())
        composeTestRule.onNodeWithText("Sin notas — anota lo que sirva mañana")
            .performScrollTo()
            .assertIsDisplayed()
    }

    @Test
    fun `la nota de la VENTA se pinta aparte y con su propia etiqueta`() {
        cliente()
        composeTestRule.onNodeWithText("De la venta").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText("entrega en la puerta de atrás").assertIsDisplayed()
    }

    // --- I-3: el recorte de la nota, por el camino REAL ----------------------

    /**
     * El defecto que este test caza: la guarda de pares suplentes vivía en
     * `limpia`, pero el campo cortaba antes con su propio `take(500)`, así que
     * **ningún camino real la alcanzaba** y su prueba pasaba con el error
     * puesto. Esta versión teclea en el campo de verdad.
     */
    @Test
    fun `tecleando en el campo, el corte no parte un emoji a la mitad`() {
        var capturado = ""
        hoja(onNota = { capturado = it })
        val perro = "🐕"
        composeTestRule.onNodeWithTag(NOTA_DE_LA_FICHA_TAG)
            .performTextInput("a".repeat(FichaDelCliente.NOTA_MAX - 1) + perro)

        assertEquals(FichaDelCliente.NOTA_MAX - 1, capturado.length)
        assertFalse("quedó medio emoji", capturado.last().isHighSurrogate())
        assertEquals("a".repeat(FichaDelCliente.NOTA_MAX - 1), capturado)
    }

    @Test
    fun `tecleando en el campo, un emoji que cabe entero se conserva`() {
        var capturado = ""
        hoja(onNota = { capturado = it })
        val perro = "🐕"
        composeTestRule.onNodeWithTag(NOTA_DE_LA_FICHA_TAG)
            .performTextInput("a".repeat(FichaDelCliente.NOTA_MAX - 2) + perro)

        assertEquals(FichaDelCliente.NOTA_MAX, capturado.length)
        assertTrue(capturado.endsWith(perro))
    }

    @Test
    fun `el catalogo entero cabe en la hoja de edicion`() {
        hoja()
        SenalDeFicha.entries.forEach { senal ->
            composeTestRule.onNodeWithTag(SENAL_TAG + senal.ordinal).assertIsDisplayed()
        }
    }

    private fun hoja(onNota: (String) -> Unit = {}) {
        composeTestRule.setContent {
            MspTheme(darkTheme = false, animateColors = false) {
                CuerpoDeLaFicha(
                    senales = setOf(SenalDeFicha.ESTA_EN_LA_NOCHE),
                    nota = "",
                    guardando = false,
                    fallo = false,
                    onSenal = {},
                    onNota = onNota,
                    onGuardar = {}
                )
            }
        }
    }

    private companion object {
        /**
         * El rótulo de la sección de ventas, **en versalitas**.
         *
         * `LabelDeSeccion` lo pinta con `.uppercase()` —como el `.sl` del mock
         * y como el "ÚLTIMOS PAGOS" de kollect—, así que buscarlo en minúscula
         * no encuentra el nodo. La aserción se arregla; la mayúscula se queda.
         */
        const val SUS_VENTAS = "SUS VENTAS"

        /** El producto de la primera fila de "sus ventas" del fixture. */
        const val PRIMERA_VENTA = "Sala 3 piezas + base"

        /** El alto de la barra de navegación: lo fija su botón redondo. */
        val TOQUE = 56.dp

        /**
         * El aire entre la tarjeta de la nota y la hoja del dinero: el mismo
         * `spacing.sm + spacing.xs` que separa a todas las hojas de esta
         * pantalla, y que la tarjeta paga junto con su alto.
         */
        val SEPARACION_DE_LA_TARJETA = 12.dp

        /**
         * Una nota que NO cabe en los dos renglones que la tarjeta asoma, para
         * medir el criterio del dueño en el caso caro y no sólo en el barato.
         */
        const val NOTA_QUE_NO_CABE =
            "El cliente pidió que pasen el viernes porque cobra ese día en la " +
                "fábrica y no llega antes de las siete. El portón negro está " +
                "abierto pero hay que tocar fuerte porque la señora no oye bien " +
                "desde el patio de atrás."
    }
}
