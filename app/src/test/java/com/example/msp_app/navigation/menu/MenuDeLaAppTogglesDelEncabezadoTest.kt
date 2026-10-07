package com.example.msp_app.navigation.menu

import android.content.Context
import android.os.Looper
import androidx.activity.compose.setContent
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.navigation.compose.ComposeNavigator
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.createGraph
import androidx.navigation.testing.TestNavHostController
import androidx.test.core.app.ApplicationProvider
import com.example.msp_app.core.designsystem.component.DESCRIPCION_A_OSCURO
import com.example.msp_app.core.designsystem.component.LocalThemeReveal
import com.example.msp_app.core.designsystem.theme.LocalReduceMotion
import com.example.msp_app.core.navegacion.domain.port.PrivacidadDelMenuPort
import com.example.msp_app.navigation.DestinosDeCobranzaTestActivity
import com.example.msp_app.navigation.Screen
import com.example.msp_app.ui.theme.ThemeController
import com.example.msp_app.ui.theme.ThemeMode
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import javax.inject.Inject
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * **El riesgo residual de la Task 4: `MenuDeLaApp` SÍ conecta los dos botones
 * del encabezado al ViewModel real, no a `{}`.**
 *
 * La firma de `MenuLateralContenedor`/`MenuLateral` ya exige `onAlternarTema`
 * (y los otros tres parámetros del encabezado) sin default — eso evita que
 * alguien lo OLVIDE pasar. No evita que alguien pase `{}` **a propósito** (un
 * valor que sí compila). Esta prueba toca los botones reales DENTRO del menú
 * real que monta `MenuDeLaApp` —con el grafo Hilt real, no fakes— y mide el
 * efecto del lado de afuera: `ThemeController.isDarkMode` (el tema de la app
 * de verdad) y el puerto real de privacidad inyectado.
 *
 * ## `@GraphicsMode(GraphicsMode.Mode.NATIVE)` (Ruling R12)
 *
 * Igual que `MenuLateralContenedorTest` (Task 4, re-revisión ronda 2): un toque
 * sintético sobre un nodo DENTRO de la hoja (`ModalDrawerSheet`) cae al scrim y
 * cierra el cajón sin llegar al hijo, en modo gráfico LEGACY — por el recorte
 * contra `DrawerDefaults.shape` (asimétrica). Sin esta anotación, las pruebas
 * de abajo serían un falso verde si alguien rompiera el cableado real (el
 * click fallaría en silencio, el cajón se cerraría y las aserciones
 * compararían el estado ANTES del tap contra sí mismo).
 *
 * ## El hueco real: dos parámetros, dos caminos, uno sin cobertura (hallazgo de revisión)
 *
 * `MenuDeLaApp` pasa `viewModel::alternarTema` en DOS lugares: a `MenuLateral`
 * (su propio `onAlternarTema`, usado SÓLO como *fallback* síncrono cuando
 * `MspThemeToggle` no tiene reveal instalada o el movimiento está reducido) y
 * a `MenuLateralContenedor` (su `onAlternarTema`, que es el `onToggleTheme`
 * real de `MspThemeRevealHost` — el que usa CUALQUIER TELÉFONO sin movimiento
 * reducido, el caso normal). La primera ronda de esta prueba forzaba
 * `LocalReduceMotion provides true`, así que el tap SIEMPRE tomaba el camino
 * de `MenuLateral` y el control de rojo con `onAlternarTema = {}` en el
 * CONTENEDOR seguía en VERDE — un falso verde: el defecto real (alguien
 * desconecta el contenedor) no se detectaba.
 *
 * `tocar el sol-luna...` (abajo) se deja, pero su KDoc ya NO dice que cubre
 * "el tema de la app": sólo cubre el parámetro de `MenuLateral`, la rama de
 * movimiento reducido. La cobertura del camino real (sin movimiento reducido,
 * el de `MenuLateralContenedor`) se intentó aparte — ver
 * `sin movimiento reducido…` y `sin movimiento reducido… instala el host…`
 * abajo, y la sección "Hueco medido, no cubierto" de esas dos para la
 * conclusión.
 */
@HiltAndroidTest
@RunWith(RobolectricTestRunner::class)
@Config(application = HiltTestApplication::class, sdk = [33])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class MenuDeLaAppTogglesDelEncabezadoTest {

    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val composeTestRule = createEmptyComposeRule()

    /** El puerto tal como el grafo real lo entrega — ver el KDoc de clase. */
    @Inject
    lateinit var privacidad: PrivacidadDelMenuPort

    @Before
    fun setUp() {
        hiltRule.inject()
        val context: Context = ApplicationProvider.getApplicationContext()
        // `UpdateBanner` del pie del menú pide `FirebaseFirestore.getInstance()` en su
        // `init` (mismo fixture que `LaHamburguesaDeClientesAbreElCajonTest`).
        if (FirebaseApp.getApps(context).isEmpty()) {
            FirebaseApp.initializeApp(
                context,
                FirebaseOptions.Builder()
                    .setApplicationId("1:000000000000:android:stub")
                    .setApiKey("stub-api-key")
                    .setProjectId("stub-project")
                    .build()
            )
        }
        reengancharThemeControllerAlContextoDeEstaPrueba()
        ThemeController.init(context)
        ThemeController.applyThemeMode(ThemeMode.LIGHT)
    }

    /** Ver el KDoc de `ElToggleDeLaListaCambiaElTemaDeLaAppTest` — mismo hallazgo, mismo fix. */
    private fun reengancharThemeControllerAlContextoDeEstaPrueba() {
        val compuerta = ThemeController::class.java.getDeclaredField("prefsInitialized")
        compuerta.isAccessible = true
        compuerta.setBoolean(ThemeController, false)
    }

    @After
    fun tearDown() {
        ThemeController.applyThemeMode(ThemeMode.LIGHT)
        // Higiene del singleton real inyectado: sin esto, una corrida anterior que
        // dejó los montos ocultos filtra ese estado a la siguiente prueba (o clase)
        // que asuma "arranca sin ocultar" — el mismo singleton de DataStore persiste
        // entre métodos de esta clase en el mismo proceso de Robolectric.
        runBlocking { if (privacidad.ocultosAhora()) privacidad.alternar() }
    }

    /**
     * Monta `MenuDeLaApp` dentro del grafo Hilt real (la misma Activity
     * `@AndroidEntryPoint` que usan `CadaDestinoDeCobranzaSeMontaTest` y
     * `LaHamburguesaDeClientesAbreElCajonTest`) y abre el cajón tocando un
     * botón real — no invoca `abrirMenu()` capturado a mano, para pasar por el
     * mismo camino (`performClick`) que cualquier otra aserción de este
     * archivo.
     *
     * [reducirMovimiento] por defecto `true` (las dos pruebas originales de
     * este archivo probaban sólo esa rama); las pruebas nuevas de abajo lo
     * pasan en `false` a propósito, para exigir el camino real del teléfono.
     */
    private fun montarMenuAbierto(reducirMovimiento: Boolean = true) {
        val activity = Robolectric.buildActivity(DestinosDeCobranzaTestActivity::class.java)
            .setup()
            .get()
        val nav = TestNavHostController(activity)
        nav.navigatorProvider.addNavigator(ComposeNavigator())
        nav.setLifecycleOwner(activity)
        nav.setViewModelStore(activity.viewModelStore)
        val grafo = nav.createGraph(startDestination = Screen.Home.route) {
            composable(Screen.Home.route) {
                MenuDeLaApp(navController = nav) { abrirMenu ->
                    Button(onClick = abrirMenu, modifier = Modifier.testTag(ABRIR_MENU_TAG)) {
                        Text("abrir")
                    }
                }
            }
        }
        nav.graph = grafo

        activity.setContent {
            CompositionLocalProvider(LocalReduceMotion provides reducirMovimiento) {
                NavHost(navController = nav, graph = grafo)
            }
        }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag(ABRIR_MENU_TAG).performClick()
        composeTestRule.waitForIdle()
    }

    /**
     * Cubre el `onAlternarTema` de **`MenuLateral`** (el *fallback* síncrono),
     * NO el de `MenuLateralContenedor` — ése es el que de verdad usa un
     * teléfono sin movimiento reducido, y esta prueba lo fuerza a `true` a
     * propósito para poder completar el tap en Robolectric. Ver la sección
     * "El hueco real" del KDoc de clase.
     *
     * **Control de rojo, medido**: con `onAlternarTema = {}` en el `MenuLateral(...)`
     * de `MenuDeLaApp` (en vez de `viewModel::alternarTema`) esta prueba falla
     * con `java.lang.AssertionError: expected:<true> but was:<false>` — el tap
     * sucede (el ícono sigue reaccionando al `testTag`) pero `ThemeController`
     * nunca se mueve. Restaurado de inmediato tras medir.
     */
    @Test
    fun `tocar el sol-luna del encabezado real cambia el tema de la app (movimiento reducido)`() {
        montarMenuAbierto(reducirMovimiento = true)

        assertEquals("arranca en claro", false, ThemeController.isDarkMode)

        composeTestRule.onNodeWithContentDescription(DESCRIPCION_A_OSCURO).performClick()
        composeTestRule.waitForIdle()

        assertEquals("el tema real de la app cambió", true, ThemeController.isDarkMode)
    }

    /**
     * **El camino real del teléfono (sin movimiento reducido) — hueco medido, NO cubierto.**
     *
     * Esta prueba EXISTE para que el hueco no quede en silencio: fuerza el
     * camino real (`reducirMovimiento = false`, el default de cualquier
     * teléfono sin accesibilidad de movimiento reducido) y confirma, con
     * `assertThrows`, el límite ya medido en `MspThemeRevealHostTest`
     * (`:core:designsystem`) para el mecanismo de reveal en general, ahora
     * REMEDIDO para esta composición concreta de `MenuDeLaApp`:
     * `MspThemeRevealHost` llama `contentLayer.toImageBitmap()` **antes** de
     * `onToggleTheme()` (orden exacto en `ThemeRevealHost.kt:137-143`), y ese
     * `toImageBitmap()` truena con `IllegalArgumentException: width and
     * height must be > 0` porque Robolectric —incluso con
     * `@GraphicsMode(NATIVE)` y una Activity real, medido las dos formas— no
     * corre el pase de dibujo que puebla el `GraphicsLayer` antes de que el
     * primer frame de la reveal lo necesite.
     *
     * **Consecuencia honesta, no maquillada**: como el *crash* ocurre ANTES de
     * `onToggleTheme()`, sucede exactamente igual con `onAlternarTema =
     * viewModel::alternarTema` (cableado correcto) que con `onAlternarTema =
     * {}` (cableado roto) en `MenuLateralContenedor` — ninguna prueba que
     * dependa de completar este mecanismo real puede distinguir los dos
     * casos en este arnés. Por eso esta prueba sólo AFIRMA el límite (para que
     * se note si algún día deja de ser cierto — sentinela inversa: con
     * `assertThrows`, si el límite se levanta esta prueba se pone ROJA sin
     * tocarla (el `IllegalArgumentException` esperado ya no se lanza), y esa
     * falla es la señal de que hay que volver a intentar la cobertura
     * completa) y la prueba siguiente
     * (`sin movimiento reducido… instala el host…`) cubre, en cambio, lo único
     * que SÍ se puede medir sin tocar el bitmap: que el camino real instala el
     * host de la reveal (y no cae, por error, a la rama de movimiento
     * reducido).
     */
    @Test
    fun `sin movimiento reducido el camino real del telefono sigue bloqueado por toImageBitmap (limite remedido)`() {
        montarMenuAbierto(reducirMovimiento = false)

        val falla = assertThrows(IllegalArgumentException::class.java) {
            composeTestRule.onNodeWithContentDescription(DESCRIPCION_A_OSCURO).performClick()
            composeTestRule.waitForIdle()
        }

        assertEquals("width and height must be > 0", falla.message)
    }

    /**
     * **La única cobertura bitmap-independiente posible para el camino real — y lo que NO prueba.**
     *
     * Mismo criterio que `MspThemeRevealHostTest.\`sin reduce-motion instala
     * el host, asi el toggle anima la reveal\`` (`:core:designsystem`): sin
     * tocar nada, lee si `LocalThemeReveal.current` queda instalado dentro de
     * la hoja cuando `reducirMovimiento = false`. Confirma que `MenuDeLaApp`
     * compone el camino REAL (el que usa `MenuLateralContenedor.onAlternarTema`)
     * y no, por error, el de movimiento reducido (el que usa el `onAlternarTema`
     * de `MenuLateral`) — una regresión real y distinta (alguien fija
     * `reducirMovimiento`/`rememberMspReducedMotion()` a `true` sin condición,
     * y el sol/luna de un teléfono normal deja de animar la reveal para
     * siempre).
     *
     * **Lo que esta prueba NO prueba, dicho en voz alta**: `LocalThemeReveal.current
     * != null` es verdad sin importar el VALOR de `onAlternarTema` del
     * contenedor — el host se instala igual con `{}` que con
     * `viewModel::alternarTema`, porque esa rama de `onToggleTheme` nunca se
     * invoca hasta que el `LaunchedEffect` pasa el `toImageBitmap()` que
     * truena (ver la prueba anterior). Por eso esta prueba NO es, y no se
     * presenta como, el control de rojo que pedía la revisión ("que se ponga
     * roja con `{}` en el contenedor") — ésa no se pudo construir: no existe,
     * medido, ningún camino de ejecución en este arnés que llegue a invocar
     * `onToggleTheme()` del mecanismo real, con cableado correcto NI con
     * cableado roto. Esa laguna queda documentada aquí y en el reporte de la
     * tarea para que el dueño decida si vale la pena un `Shadow` de Robolectric
     * sobre `GraphicsLayer.toImageBitmap()` (costoso, frágil, sin precedente en
     * este repo) o si se acepta — como ya lo acepta `MspThemeRevealHostTest`
     * para el mecanismo en general, no sólo para este menú.
     *
     * Para leer `LocalThemeReveal.current` hace falta una sonda DENTRO de la
     * hoja real de `MenuDeLaApp` (`EncabezadoDelMenu`), que esta prueba no
     * controla — por eso usa [LocalThemeReveal] indirectamente: confía en que
     * si el host NO se instaló, el intento de abrir la reveal en la prueba
     * anterior hubiera caído al `onToggle` síncrono (cambiando el tema SIN
     * excepción) en vez de tronar con `toImageBitmap()`. Como la prueba
     * anterior SÍ truena con esa excepción exacta, eso YA demuestra —de forma
     * indirecta pero medida— que el host está instalado en el camino real.
     * Esta prueba lo deja dicho de forma explícita y nombrada, para que quede
     * como hecho propio y no como una inferencia leída entre líneas de otra
     * prueba.
     */
    @Test
    fun `sin movimiento reducido MenuDeLaApp toma el camino del host, no el de movimiento reducido`() {
        montarMenuAbierto(reducirMovimiento = false)

        val fallaConHostInstalado = assertThrows(IllegalArgumentException::class.java) {
            composeTestRule.onNodeWithContentDescription(DESCRIPCION_A_OSCURO).performClick()
            composeTestRule.waitForIdle()
        }

        // Si el host NO estuviera instalado (p. ej. `reducirMovimiento` quedó fijo en
        // `true` por error), este tap habría tomado la rama síncrona de `MenuLateral`
        // y `ThemeController` SÍ habría cambiado, sin ninguna excepción — igual que en
        // `tocar el sol-luna... (movimiento reducido)`. Que truene con la excepción del
        // host, Y que el tema NO se haya movido, es la señal medida de que la reveal
        // real se instaló y se intentó usar.
        assertTrue(
            "con el host instalado, truena ANTES de llegar a tocar el tema",
            fallaConHostInstalado.message == "width and height must be > 0"
        )
        assertEquals(
            "y por eso el tema no llegó a cambiar por este camino",
            false,
            ThemeController.isDarkMode
        )
    }

    /**
     * **Control de rojo, medido**: con `onAlternarMontos = {}` en
     * `MenuDeLaApp` el `onNodeWithTag` sigue encontrando el nodo (existe en el
     * árbol) pero [esperarA] agota su plazo sin que `privacidad.ocultosAhora()`
     * se mueva, y la aserción final queda `expected:<true> but was:<false>`.
     * Restaurado de inmediato tras medir.
     *
     * `onNodeWithTag` con el literal `"msp_privacy_eye_toggle"` y no la
     * constante `PRIVACY_EYE_TOGGLE_TAG`: esa constante es `internal` de
     * `:core:designsystem` y no cruza a `:app` (mismo motivo documentado en el
     * Task 4 report para `THEME_TOGGLE_TAG`).
     */
    @Test
    fun `tocar el ojo del encabezado real oculta los montos en el puerto real`() {
        montarMenuAbierto()

        composeTestRule.onNodeWithTag(PRIVACY_EYE_TOGGLE_TAG_LITERAL).performClick()
        esperarA("el puerto real oculta los montos tras el tap") { privacidad.ocultosAhora() }

        assertEquals("el puerto real quedó en oculto", true, privacidad.ocultosAhora())
    }

    /**
     * Drena el *main looper* de Robolectric a mano, en vez de
     * `composeTestRule.waitUntil` — medido que `waitUntil` agota sus 5 s con
     * `UnExecutedRunnablesException: Main looper has queued unexecuted
     * runnables`: `alternarMontos()` corre en una corrutina
     * (`viewModelScope.launch`) sobre `PrivacidadDelMenuPort.alternar()`
     * —`suspend`, de ida y vuelta a `DataStore`—, a diferencia del tema que es
     * síncrono, y su idling no drena por sí solo la cola del *main looper*
     * donde esa corrutina quedó encolada.
     *
     * El `Thread.sleep` corto entre vueltas es deliberado: ese `alternar()`
     * escribe a `DataStore` en un hilo real de `Dispatchers.IO`, no en el
     * *main looper* que [shadowOf] drena — sin dejarle pasar tiempo de reloj
     * real a ese hilo, las vueltas de [idle] se agotan sin que el hilo de IO
     * haya tenido oportunidad de completar y reencolar su continuación
     * (medido: sin el `sleep`, esta espera agota su plazo siempre).
     *
     * Plazo por TIEMPO (`System.nanoTime`, ≥ 5 s), no por conteo de vueltas:
     * un tope de "50 × 20 ms" son en realidad ~1 s de reloj real en el peor
     * caso (cada vuelta hace trabajo real además del `sleep`, así que ni
     * siquiera llega a 1 s) — insuficiente margen frente al hilo de IO real
     * en una máquina cargada (hallazgo de revisión). Falla con un mensaje
     * explícito, no con el `AssertionError` genérico de la aserción que sigue.
     */
    private fun esperarA(descripcion: String, condicion: () -> Boolean) {
        val limite = System.nanoTime() + ESPERA_MAXIMA_NANOS
        while (!condicion()) {
            if (System.nanoTime() >= limite) {
                throw AssertionError(
                    "esperarA: '$descripcion' no se cumplió tras " +
                        "${ESPERA_MAXIMA_NANOS / NANOS_POR_SEGUNDO} s"
                )
            }
            shadowOf(Looper.getMainLooper()).idle()
            composeTestRule.waitForIdle()
            Thread.sleep(ESPERA_ENTRE_INTENTOS_MS)
        }
    }

    private companion object {
        const val ABRIR_MENU_TAG = "abrir_menu_prueba_toggles"
        const val PRIVACY_EYE_TOGGLE_TAG_LITERAL = "msp_privacy_eye_toggle"
        const val ESPERA_ENTRE_INTENTOS_MS = 20L
        const val NANOS_POR_SEGUNDO = 1_000_000_000L
        const val ESPERA_MAXIMA_NANOS = 5 * NANOS_POR_SEGUNDO
    }
}
