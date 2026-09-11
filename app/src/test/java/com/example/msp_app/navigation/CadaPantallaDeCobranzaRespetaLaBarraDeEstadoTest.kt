package com.example.msp_app.navigation

import android.content.Context
import android.provider.Settings
import android.view.View
import android.view.ViewGroup
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.statusBars
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.unit.dp
import androidx.core.graphics.Insets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.navigation.NavDestination
import androidx.navigation.NavType
import androidx.navigation.compose.ComposeNavigator
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.createGraph
import androidx.navigation.testing.TestNavHostController
import androidx.test.core.app.ApplicationProvider
import com.example.msp_app.core.designsystem.component.DESCRIPCION_A_OSCURO
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * **Ningún control de cobranza debajo de la barra de estado — las siete pantallas,
 * descubiertas, nunca escritas a mano (Ruling BR).**
 *
 * ## El defecto que esta red existe para no repetir
 *
 * La app corre `enableEdgeToEdge()` (`MainActivity`), así que el contenido de
 * una pantalla arranca en `y = 0` y la ventana `StatusBar` de SystemUI queda
 * **encima** del encabezado: **se come todo tap por arriba de su borde
 * inferior**, y el evento no entra al proceso — cero líneas en logcat, la app
 * viva, el botón muerto. Medido en el emulador (1280x2856, densidad 480, barra
 * de **156 px**) antes de este arreglo:
 *
 * | Pantalla | Control | Píxeles útiles |
 * |---|---|---|
 * | Lista de clientes | toggle de tema | 24 de 144 |
 * | Detalle de cliente | "atrás" y "anotar ficha" | 36 de 168 |
 * | Detalle de venta | "atrás" | 36 de 168 |
 * | Registrar abono | "atrás" | 24 de 168 |
 * | Registrar visita | "atrás" y "agregar foto" | 36 de 168 |
 *
 * Barridos de taps, con 1.2-2 s entre uno y otro y leyendo el efecto después de
 * cada uno: sobre el toggle, `y ≤ 155` **perdidos** y `y ≥ 157` **llegados**;
 * sobre el "atrás" del detalle de cliente, `y=108/140/155` perdidos y
 * `y=157/175` llegados. La frontera cae **al píxel** en el borde de la ventana
 * del sistema, medido aparte con `dumpsys window`. El cobrador toca atrás en el
 * centro del botón y no pasa nada cuatro de cada cinco veces.
 *
 * `:feature:collectionReport` ya había aprendido esto —el KDoc de
 * `CollectionReportContent` lo llama "fix defecto visual" y aplica el mismo
 * `statusBarsPadding()` después del `background`—; `:feature:pagos` y
 * `:feature:visitas` se escribieron después y no lo copiaron.
 *
 * ## Por qué ninguna de las 3743 lo veía
 *
 * `performClick()` no inyecta en el sistema de ventanas: despacha directo sobre
 * el nodo de semántica. Y en una composición de test **no hay ventana
 * `StatusBar` y `WindowInsets.statusBars` vale cero**, así que un encabezado
 * dibujado en `y = 0` es perfectamente tocable en el mundo del test. La cadena
 * que los tests del toggle afirman —tap → puerto → `ThemeController` →
 * `theme_prefs`— es **verdadera**; medían la pregunta equivocada. Faltaba una
 * prueba de **geometría del control contra las barras del sistema**, y no
 * existía ninguna de esa clase en el repo.
 *
 * ⚠️ **Los goldens no pueden demostrar este arreglo, y no hay que confundirlo.**
 * En Robolectric el inset vale cero, así que `statusBarsPadding()` no aporta un
 * solo píxel a un `.png`. Lo que demuestra el arreglo es **este** archivo más la
 * verificación en el aparato; lo que los goldens siguen demostrando es el resto
 * del acabado.
 *
 * ## Las dos mitades, y por qué hacen falta las dos
 *
 * [ningunControlDeCobranzaArrancaDebajoDeLaBarraDeEstado] es la mitad
 * **medida**: monta el `NavHost` de producción, despacha un inset de barra de
 * estado y mide la caja de cada nodo tocable que encuentre. No nombra ni las
 * rutas (salen de [destinosDeCobranza], la misma función que llama
 * `AppNavigation`) ni los controles (salen de `hasClickAction()` sobre el árbol
 * de semántica).
 *
 * Su techo está **medido, no supuesto**: de los siete destinos, sólo tres
 * renderizan controles en este entorno —la lista y los dos tickets—; los otros
 * cuatro (`pagos/cliente/1`, `pagos/venta/1`, `pagos/abono/1`,
 * `visitas/registrar/1`) se quedan en su estado de carga, porque en `:app` el
 * grafo es el real y la base de Robolectric no tiene el cliente 1 ni la venta 1.
 * En ese estado no hay encabezado que medir.
 *
 * [todaPantallaQueSePintaCompletaConsumeElInset] es la mitad que llega adonde la
 * otra no llega, con el mismo reparto que [CadaPantallaMspProveeSuTemaTest]
 * hereda de `CadaDestinoDeCobranzaSeMontaTest`: escanea las fuentes de
 * producción de **todos** los módulos del build (vía [EscanerDeFuentes], que los
 * lee de `settings.gradle.kts`) y exige que **toda cadena de modificadores que
 * pinte una pantalla completa** —`modifier` entrante, `.fillMaxSize()` y
 * `.background(MspTheme.colors.…)`— consuma el inset en esa misma cadena. Una
 * pantalla número ocho entra el día que se escribe: acá no hay lista de
 * pantallas, ni de módulos, ni de controles.
 */
@HiltAndroidTest
@RunWith(RobolectricTestRunner::class)
@Config(application = HiltTestApplication::class, sdk = [33], qualifiers = "w360dp-h2400dp-xhdpi")
class CadaPantallaDeCobranzaRespetaLaBarraDeEstadoTest {

    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    /**
     * `createEmptyComposeRule` y no `createAndroidComposeRule<…>`: la segunda lanza la Activity
     * por `Intent` y Robolectric **no puede resolver** una Activity que no está en el
     * manifiesto — medido: `Unable to resolve activity for Intent … DestinosDeCobranzaTestActivity`.
     * Así que la Activity se construye a mano, igual que en [CadaDestinoDeCobranzaSeMontaTest] y
     * `WarehouseHiltGraphTest`, y esta regla sólo aporta la sincronización y las consultas de
     * semántica sobre una composición que el test montó él mismo. Tiene que ser **esa** Activity
     * y no una `ComponentActivity` pelada: el `hiltViewModel()` de cada lambda `composable{}`
     * resuelve con `EntryPoints.get(activity)` y exige `@AndroidEntryPoint`.
     */
    @get:Rule(order = 1)
    val composeTestRule = createEmptyComposeRule()

    private val escaner = EscanerDeFuentes()

    /** Lo que la sonda dentro de la composición vio de `WindowInsets.statusBars`, en px. */
    private var insetVistoPorCompose: Int = -1

    private lateinit var nav: TestNavHostController

    @Before
    fun setUp() {
        hiltRule.inject()
        // Movimiento reducido, igual que `ElToggleDeLaListaCambiaElTemaDeLaAppTest`: la rama
        // animada de `MspThemeRevealHost` graba un `GraphicsLayer` que Robolectric no sostiene.
        Settings.Global.putFloat(
            ApplicationProvider.getApplicationContext<Context>().contentResolver,
            Settings.Global.ANIMATOR_DURATION_SCALE,
            0f
        )
    }

    /**
     * **La mitad medida.** Recorre cada destino que el grafo registre y junta todo control
     * tocable cuya caja ARRANQUE encima del inset. Se juntan todos en vez de cortar en el
     * primero para que el rojo nombre todas las pantallas de una corrida — lo que convirtió
     * "las siete crashean" de deducción en medición, la vez pasada.
     */
    @Test
    fun `ningun control de cobranza arranca debajo de la barra de estado`() {
        val rutas = montarElGrafo()
        assertTrue(
            "no se descubrió ningún destino desde destinosDeCobranza: no probaría nada",
            rutas.isNotEmpty()
        )
        val insetPx = with(composeTestRule.density) { ALTO_DE_LA_BARRA.roundToPx() }
        assertEquals(
            "el inset despachado no llegó a Compose: la medición no probaría nada",
            insetPx,
            insetVistoPorCompose
        )

        val tapados = mutableListOf<String>()
        val medidos = mutableListOf<String>()
        rutas.forEach { ruta ->
            controlesDe(ruta).forEach { control ->
                val caja = control.boundsInRoot
                medidos += nombreDe(control)
                if (caja.top < insetPx) {
                    val utiles = (caja.bottom - maxOf(caja.top, insetPx.toFloat())).toInt()
                    tapados += "$ruta → ${nombreDe(control)} " +
                        "[${caja.top.toInt()}..${caja.bottom.toInt()}] " +
                        "útiles ${utiles}px de ${caja.height.toInt()}px"
                }
            }
        }

        // Control positivo: el barrido tiene que haber mirado un encabezado REAL. El toggle de
        // tema es el control cuyo defecto se reportó, y su etiqueta viene de la constante del
        // design system, no de un literal. Si esto falla, ningún destino renderizó su
        // encabezado y el `assertEquals` de abajo pasaría por vacío.
        assertTrue(
            "no se midió el toggle de tema en ningún destino (medidos: $medidos): " +
                "el barrido no está viendo encabezados y no probaría nada",
            medidos.any { it.contains(DESCRIPCION_A_OSCURO) }
        )

        assertEquals(
            "estos controles arrancan debajo de la barra de estado de ${insetPx}px: el sistema " +
                "se come sus taps y el cobrador los toca sin que pase nada",
            emptyList<String>(),
            tapados
        )
    }

    /**
     * **La mitad que llega adonde el `NavHost` no llega**: las cuatro pantallas que en este
     * entorno se quedan en su estado de carga, y cualquier pantalla futura de cualquier módulo.
     *
     * Lo que se exige es que **la cadena que pinta la pantalla completa** consuma el inset, y en
     * **esa misma cadena**: el orden importa y es la mitad del arreglo — `statusBarsPadding()`
     * va DESPUÉS del `background` para que el color se pinte a sangre (también debajo de la
     * barra, que es lo que `enableEdgeToEdge()` pide) y el inset solo baje el contenido. Al
     * revés, la franja de la barra quedaría con el fondo del tema legado de `MainActivity` y se
     * vería clara en modo oscuro.
     */
    @Test
    fun `toda pantalla que se pinta completa consume el inset`() {
        val pantallas = cadenasQuePintanUnaPantalla()

        // Control positivo, derivado y no escrito: el escaneo tiene que ver al menos tantas
        // pantallas como destinos registra el grafo. Si la regex se rompiera y encontrara dos,
        // el `assertEquals` de abajo pasaría sin haber mirado nada.
        val destinos = destinosRegistrados()
        assertTrue(
            "el escaneo encontró ${pantallas.size} pantallas y el grafo registra $destinos " +
                "destinos: el método no está viendo las pantallas y no probaría nada",
            pantallas.size >= destinos
        )

        val sinInset = pantallas.filterNot { it.consumeElInset }.map { it.donde }.sorted()
        assertEquals(
            "estas cadenas pintan una pantalla completa y no consumen el inset de la barra de " +
                "estado: con enableEdgeToEdge() su encabezado queda debajo de la ventana del " +
                "sistema y el sistema se come sus taps",
            emptyList<String>(),
            sinInset
        )
    }

    // -----------------------------------------------------------------------

    /**
     * Monta el `NavHost` de producción **una sola vez** y devuelve las rutas que registró, con
     * sus argumentos ya rellenados desde el `NavType` que la propia ruta declara. Una
     * composición para las siete —y no una por destino como en
     * [CadaDestinoDeCobranzaSeMontaTest]— porque acá no se esperan excepciones que dejen el
     * árbol inservible: lo que se mide es geometría de un árbol que ya se sabe que compone.
     *
     * El inset se despacha sobre el `AndroidComposeView` (y no sobre el `DecorView`) porque es
     * ahí donde Compose instala su listener de insets: es lo que hace el sistema en el teléfono
     * y lo único que una composición de test nunca recibe sola.
     */
    private fun montarElGrafo(): List<String> {
        val activity = Robolectric.buildActivity(DestinosDeCobranzaTestActivity::class.java)
            .setup()
            .get()
        nav = TestNavHostController(activity)
        nav.navigatorProvider.addNavigator(ComposeNavigator())
        nav.setLifecycleOwner(activity)
        nav.setViewModelStore(activity.viewModelStore)
        val grafo = nav.createGraph(startDestination = RAIZ) {
            composable(RAIZ) {}
            destinosDeCobranza(nav)
        }
        nav.graph = grafo
        activity.setContent {
            Box {
                NavHost(navController = nav, graph = grafo)
                // Sonda del control positivo: lo que ESTA composición ve del inset. No pinta.
                val visto = WindowInsets.statusBars.getTop(LocalDensity.current)
                SideEffect { insetVistoPorCompose = visto }
            }
        }
        composeTestRule.waitForIdle()
        val alto = with(composeTestRule.density) { ALTO_DE_LA_BARRA.roundToPx() }
        val insets = WindowInsetsCompat.Builder()
            .setInsets(WindowInsetsCompat.Type.statusBars(), Insets.of(0, alto, 0, 0))
            .build()
        composeTestRule.runOnUiThread {
            vistasDeCompose(activity.window.decorView).forEach { vista ->
                ViewCompat.dispatchApplyWindowInsets(vista, insets)
            }
        }
        composeTestRule.waitForIdle()
        return grafo.filter { it.route != RAIZ }.map(::conArgumentos).toList()
    }

    /** Navega a [ruta] dentro de la composición viva y devuelve sus nodos tocables. */
    private fun controlesDe(ruta: String): List<SemanticsNode> {
        composeTestRule.runOnUiThread { nav.navigate(ruta) }
        composeTestRule.waitForIdle()
        return composeTestRule.onAllNodes(hasClickAction()).fetchSemanticsNodes()
    }

    private fun conArgumentos(destino: NavDestination): String {
        var ruta = requireNotNull(destino.route) { "un destino sin ruta no es navegable" }
        destino.arguments.forEach { (nombre, argumento) ->
            val valor = if (argumento.type == NavType.IntType) VALOR_INT else VALOR_STRING
            ruta = ruta.replace("{$nombre}", valor)
        }
        return ruta
    }

    private fun destinosRegistrados(): Int {
        val sonda = TestNavHostController(ApplicationProvider.getApplicationContext())
        sonda.navigatorProvider.addNavigator(ComposeNavigator())
        sonda.graph = sonda.createGraph(startDestination = RAIZ) {
            composable(RAIZ) {}
            destinosDeCobranza(sonda)
        }
        return sonda.graph.count() - 1 // -1 por la raíz de prueba
    }

    private fun vistasDeCompose(raiz: View): List<View> {
        val encontradas = mutableListOf<View>()
        if (raiz.javaClass.simpleName == "AndroidComposeView") encontradas += raiz
        if (raiz is ViewGroup) {
            for (i in 0 until raiz.childCount) encontradas += vistasDeCompose(raiz.getChildAt(i))
        }
        return encontradas
    }

    /** Una cadena de modificadores que pinta una pantalla completa, y si consume el inset. */
    private data class Pantalla(val donde: String, val consumeElInset: Boolean)

    private fun cadenasQuePintanUnaPantalla(): List<Pantalla> = escaner.archivos
        .flatMap { archivo ->
            val codigo = archivo.readText()
            CADENA.findAll(codigo)
                .map { it to it.groupValues[1] }
                .filter { (_, cadena) -> LLENA_LA_PANTALLA.containsMatchIn(cadena) }
                .filter { (_, cadena) -> PINTA_EL_FONDO.containsMatchIn(cadena) }
                .map { (coincidencia, cadena) ->
                    val linea = codigo.take(coincidencia.range.first).count { it == '\n' } + 1
                    Pantalla(
                        donde = "${archivo.name}:$linea",
                        consumeElInset = CONSUME_EL_INSET.containsMatchIn(cadena)
                    )
                }
                .toList()
        }

    private companion object {

        /** Raíz vacía: se arranca aquí y se navega, igual que la red de destinos. */
        const val RAIZ = "raiz_de_la_sonda"

        const val VALOR_INT = "1"
        const val VALOR_STRING = "x"

        /**
         * Alto con el que se despacha el inset. El emulador donde se midió el defecto tiene
         * 156 px a densidad 480 (52 dp); acá se usa el valor clásico de 24 dp porque lo que se
         * exige es que la pantalla consuma **el** inset, no que acierte un número.
         */
        val ALTO_DE_LA_BARRA = 24.dp

        /**
         * La cadena encadenada sobre el `modifier` **entrante** — el que el llamador pasa, que
         * es lo que distingue la raíz de una pantalla de un componente hoja que pinta su propio
         * fondo. Acepta líneas de comentario en medio, porque el arreglo lleva una.
         */
        val CADENA = Regex(
            """modifier = modifier((?:\n[ \t]*(?://[^\n]*|\.[A-Za-z][A-Za-z0-9]*\([^\n]*\)))+)"""
        )

        val LLENA_LA_PANTALLA = Regex("""\.fillMaxSize\(\)""")
        val PINTA_EL_FONDO = Regex("""\.background\(MspTheme\.colors\.[A-Za-z0-9]+\)""")
        val CONSUME_EL_INSET = Regex("""\.(statusBars|systemBars|safeDrawing)Padding\(\)""")

        /** Cómo nombrar un control descubierto, para que el rojo diga cuál es. */
        fun nombreDe(nodo: SemanticsNode): String {
            val descripcion = nodo.config.getOrNull(SemanticsProperties.ContentDescription)
                ?.firstOrNull()
            val texto = nodo.config.getOrNull(SemanticsProperties.Text)?.firstOrNull()?.text
            val etiqueta = descripcion ?: texto
            return if (etiqueta.isNullOrBlank()) "control sin etiqueta" else "\"$etiqueta\""
        }
    }
}
