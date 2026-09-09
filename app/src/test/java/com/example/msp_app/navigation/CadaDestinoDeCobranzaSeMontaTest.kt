package com.example.msp_app.navigation

import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.navigation.NavDestination
import androidx.navigation.NavType
import androidx.navigation.compose.ComposeNavigator
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.createGraph
import androidx.navigation.testing.TestNavHostController
import androidx.test.core.app.ApplicationProvider
import dagger.hilt.android.AndroidEntryPoint
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
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * Activity mínima para esta prueba, calcada de
 * [com.example.msp_app.features.warehouses.WarehouseHiltGraphTestActivity]: el
 * `hiltViewModel()` que hay dentro de CADA lambda `composable{}` de producción
 * resuelve contra el `HiltViewModelFactory` que Hilt instala como
 * `defaultViewModelProviderFactory` de cualquier Activity `@AndroidEntryPoint`.
 * Sin una Activity así, la composición del `NavHost` muere en `EntryPoints.get`
 * mucho antes de llegar a la pantalla.
 */
@AndroidEntryPoint
class DestinosDeCobranzaTestActivity : ComponentActivity()

/**
 * **La red de grano de destino: el `NavHost` de producción, compuesto de verdad.**
 *
 * Monta el MISMO `NavHost` que la app —con [destinosDeCobranza], la misma
 * función que llama `AppNavigation`— y **navega a cada destino que esa función
 * registre**, con el `hiltViewModel()` real de cada lambda `composable{}`
 * resolviendo contra el grafo Hilt real. Es el entorno del teléfono, no un
 * harness: nadie aporta `MspTheme`, así que una pantalla que no se envuelva a sí
 * misma revienta aquí con `IllegalStateException("MspTheme ausente")` — la
 * excepción exacta del crash de producción, atravesando `PagosRutas.kt` /
 * `VisitasRutas.kt`.
 *
 * ## Por qué esta red y no las que había
 *
 * Es de **grano de destino** y **no tiene nada que un autor deba mantener a
 * mano**: la lista de rutas sale de [destinosDeCobranza] y los valores de sus
 * argumentos salen del `NavType` que la propia ruta declara. Un destino número
 * ocho entra a esta prueba **el día que se registra**, sin que nadie agregue una
 * entrada a un mapa, un `@Test`, ni un nombre a un conjunto. Las dos formas de
 * franquear una red basada en listas —una entrada de mapa sin su `@Test`, o una
 * pantalla nueva que reusa un `*Content` público sin envolver— aquí no existen,
 * porque aquí no se declara nada.
 *
 * ## Corrección de una afirmación anterior
 *
 * La primera versión de este arreglo declaró que componer el `NavHost` real
 * **no se podía** en la JVM, porque `hiltViewModel()` exige una Activity
 * `@AndroidEntryPoint` «y la única del repo es `MainActivity`, o sea
 * `androidTest`». **Eso era falso.** Lo que se había medido es que un
 * `createComposeRule()` pelado —`ComponentActivity` sin Hilt— no puede; la
 * conclusión no se seguía. `WarehouseHiltGraphTest` declara una Activity
 * `@AndroidEntryPoint` **en `src/test`** desde la Task 8, bajo Robolectric +
 * `HiltTestApplication`, dentro de `:app:testDevlocalDebugUnitTest`, que **sí**
 * corre en la compuerta. La infraestructura estaba completa desde antes.
 */
@HiltAndroidTest
@RunWith(RobolectricTestRunner::class)
@Config(application = HiltTestApplication::class, sdk = [33])
class CadaDestinoDeCobranzaSeMontaTest {

    @get:Rule
    val hiltRule = HiltAndroidRule(this)

    @Before
    fun setUp() {
        hiltRule.inject()
    }

    /**
     * Navega a cada destino de cobranza en un `NavHost` recién montado y junta
     * los que revienten. Se juntan en vez de cortar en el primero para que el
     * mensaje nombre **todas** las pantallas rotas de una corrida — que es lo
     * que convirtió "las siete crashean" de deducción en medición.
     */
    @Test
    fun `cada destino de cobranza se compone dentro del NavHost real`() {
        val rutas = rutasDeCobranza()

        // Control positivo: si el descubrimiento devolviera vacío, el
        // `assertEquals` de abajo pasaría sin haber compuesto nada.
        assertTrue(
            "no se descubrió ningún destino desde destinosDeCobranza: no probaría nada",
            rutas.isNotEmpty()
        )

        val errores = rutas.mapNotNull { ruta ->
            runCatching { montar(ruta) }.exceptionOrNull()?.let { ruta to raiz(it) }
        }
        assertEquals(
            "estos destinos revientan al componerse dentro del NavHost real" +
                errores.firstOrNull()?.let { (ruta, error) ->
                    "\n\nTraza del primero ($ruta):\n" +
                        error.stackTraceToString().lineSequence().take(TRAZA).joinToString("\n")
                }.orEmpty(),
            emptyList<String>(),
            errores.map { (ruta, error) -> "$ruta → ${resumen(error)}" }
        )
    }

    /**
     * Las rutas que [destinosDeCobranza] registra, **con sus argumentos ya
     * rellenados** a partir del `NavType` que la propia ruta declara. Ni la
     * lista ni los valores están escritos a mano.
     */
    private fun rutasDeCobranza(): List<String> {
        val nav = TestNavHostController(ApplicationProvider.getApplicationContext())
        nav.navigatorProvider.addNavigator(ComposeNavigator())
        nav.graph = nav.createGraph(startDestination = RAIZ) {
            composable(RAIZ) {}
            destinosDeCobranza(nav)
        }
        return nav.graph.filter { it.route != RAIZ }.map(::conArgumentos).toList()
    }

    private fun conArgumentos(destino: NavDestination): String {
        var ruta = requireNotNull(destino.route) { "un destino sin ruta no es navegable" }
        destino.arguments.forEach { (nombre, argumento) ->
            val valor = if (argumento.type == NavType.IntType) VALOR_INT else VALOR_STRING
            ruta = ruta.replace("{$nombre}", valor)
        }
        return ruta
    }

    /**
     * Un `NavHost` **nuevo por destino**, ya posicionado en [ruta].
     *
     * Tres decisiones que costaron dos falsos verdes medidos, así que quedan
     * escritas:
     *
     * 1. **Un `NavHost` por destino.** Una excepción de composición deja el
     *    árbol inservible; compartirlo haría que el primer rojo escondiera a los
     *    seis siguientes, que es justo lo que esta prueba existe para no hacer.
     * 2. **Se navega ANTES de componer.** Componer primero y navegar después
     *    deja la composición del destino a cargo del reloj de frames de
     *    `AnimatedContent`, que ni `Looper.idle()` ni `idleFor()` bombean: esa
     *    versión pasó **en verde con las siete pantallas revertidas**. Navegando
     *    antes, el destino se compone DENTRO de `setContent` y la excepción sale
     *    por acá, sincrónica.
     * 3. **`navigate(rutaConcreta)`, no `startDestination = rutaConcreta`.** El
     *    `startDestination` se resuelve por id y **pierde los argumentos**: esa
     *    versión reventaba con "sin ventaId en el SavedStateHandle" en seis de
     *    los siete destinos, escondiendo lo que se quería medir. `navigate()` sí
     *    parsea la ruta y llena el `Bundle` que el `SavedStateHandle` lee.
     *
     * El grafo se le pasa a `NavHost` como la MISMA instancia que ya tiene el
     * controlador, para que `setGraph` tome el camino que **no** reinicia la
     * pila y el destino navegado siga en su lugar.
     */
    private fun montar(ruta: String) {
        val activity = Robolectric.buildActivity(DestinosDeCobranzaTestActivity::class.java)
            .setup()
            .get()
        val nav = TestNavHostController(activity)
        nav.navigatorProvider.addNavigator(ComposeNavigator())
        // Los dos owners van ANTES del grafo: `NavHost` los instala él mismo,
        // pero acá el grafo se monta antes que la composición y el controlador
        // exige el `ViewModelStore` puesto para poder crear los
        // `NavBackStackEntry` — que son el `ViewModelStoreOwner` contra el que
        // resuelve el `hiltViewModel()` de cada ruta.
        nav.setLifecycleOwner(activity)
        nav.setViewModelStore(activity.viewModelStore)
        val grafo = nav.createGraph(startDestination = RAIZ) {
            composable(RAIZ) {}
            destinosDeCobranza(nav)
        }
        nav.graph = grafo
        nav.navigate(ruta)
        activity.setContent { NavHost(navController = nav, graph = grafo) }
        shadowOf(Looper.getMainLooper()).idle()
    }

    private companion object {

        /** Raíz vacía: se arranca aquí y se navega, para no depender de un start con argumentos. */
        const val RAIZ = "raiz_de_la_sonda"

        const val VALOR_INT = "1"
        const val VALOR_STRING = "x"

        /** Cuántas líneas de traza acompañan al primer fallo. */
        const val TRAZA = 12

        /** La causa raíz — la de `MspTheme`, no el envoltorio que ponga Compose. */
        fun raiz(error: Throwable): Throwable {
            var actual: Throwable = error
            while (actual.cause != null && actual.cause !== actual) actual = actual.cause!!
            return actual
        }

        /**
         * `Excepción: mensaje @ Archivo.kt:línea`, con la línea de la PANTALLA
         * —no la del design system, que es la misma para todas y no dice cuál
         * se rompió—.
         */
        fun resumen(error: Throwable): String {
            val marco = error.stackTrace.firstOrNull {
                it.className.startsWith(PAQUETE) &&
                    !it.className.startsWith("$PAQUETE.core.designsystem") &&
                    !it.className.startsWith("$PAQUETE.navigation")
            }
            return "${error::class.java.simpleName}: ${error.message}" +
                marco?.let { " @ ${it.fileName}:${it.lineNumber}" }.orEmpty()
        }

        const val PAQUETE = "com.example.msp_app"
    }
}
