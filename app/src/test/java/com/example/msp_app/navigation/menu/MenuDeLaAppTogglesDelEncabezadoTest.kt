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
import org.junit.After
import org.junit.Assert.assertEquals
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
 * contra `DrawerDefaults.shape` (asimétrica). Sin esta anotación, las dos
 * pruebas de abajo serían un falso verde si alguien rompiera el cableado real
 * (el click fallaría en silencio, el cajón se cerraría y las aserciones
 * compararían el estado ANTES del tap contra sí mismo) — exactamente el
 * defecto que esta prueba existe para cerrar.
 *
 * ## Movimiento reducido
 *
 * `LocalReduceMotion provides true`: la rama CON reveal circular no completa
 * en Robolectric (`toImageBitmap()`, límite ya documentado en
 * `MspThemeRevealHostTest`); con movimiento reducido el tap de
 * `MspThemeToggle` llega de forma SÍNCRONA a `onAlternarTema` (medido en la
 * Task 4, re-revisión ronda 1).
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
    }

    /**
     * Monta `MenuDeLaApp` dentro del grafo Hilt real (la misma Activity
     * `@AndroidEntryPoint` que usan `CadaDestinoDeCobranzaSeMontaTest` y
     * `LaHamburguesaDeClientesAbreElCajonTest`) y abre el cajón tocando un
     * botón real — no invoca `abrirMenu()` capturado a mano, para pasar por el
     * mismo camino (`performClick`) que cualquier otra aserción de este
     * archivo.
     */
    private fun montarMenuAbierto() {
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
            CompositionLocalProvider(LocalReduceMotion provides true) {
                NavHost(navController = nav, graph = grafo)
            }
        }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag(ABRIR_MENU_TAG).performClick()
        composeTestRule.waitForIdle()
    }

    /**
     * **Control de rojo, medido**: con `onAlternarTema = {}` en `MenuDeLaApp`
     * (en vez de `viewModel::alternarTema`) esta prueba falla con
     * `java.lang.AssertionError: expected:<true> but was:<false>` — el tap
     * sucede (el ícono sigue reaccionando al `testTag`) pero `ThemeController`
     * nunca se mueve. Restaurado de inmediato tras medir.
     */
    @Test
    fun `tocar el sol-luna del encabezado real cambia el tema de la app`() {
        montarMenuAbierto()

        assertEquals("arranca en claro", false, ThemeController.isDarkMode)

        composeTestRule.onNodeWithContentDescription(DESCRIPCION_A_OSCURO).performClick()
        composeTestRule.waitForIdle()

        assertEquals("el tema real de la app cambió", true, ThemeController.isDarkMode)
    }

    /**
     * **Control de rojo, medido**: con `onAlternarMontos = {}` en
     * `MenuDeLaApp` el `onNodeWithTag` sigue encontrando el nodo (existe en el
     * árbol) pero [esperarA] agota sus intentos sin que
     * `privacidad.ocultosAhora()` se mueva, y la aserción final queda
     * `expected:<true> but was:<false>`. Restaurado de inmediato tras medir.
     *
     * `onNodeWithTag` con el literal `"msp_privacy_eye_toggle"` y no la
     * constante `PRIVACY_EYE_TOGGLE_TAG`: esa constante es `internal` de
     * `:core:designsystem` y no cruza a `:app` (mismo motivo documentado en el
     * Task 4 report para `THEME_TOGGLE_TAG`).
     *
     * [esperarA] y no `composeTestRule.waitUntil`: medido que `waitUntil`
     * agota sus 5 s con
     * `UnExecutedRunnablesException: Main looper has queued unexecuted
     * runnables` — `alternarMontos()` corre en una corrutina
     * (`viewModelScope.launch`) sobre `PrivacidadDelMenuPort.alternar()`
     * —`suspend`, de ida y vuelta a `DataStore`—, a diferencia del tema que es
     * síncrono, y su idling no drena por sí solo la cola del *main looper* de
     * Robolectric donde esa corrutina quedó encolada.
     */
    @Test
    fun `tocar el ojo del encabezado real oculta los montos en el puerto real`() {
        montarMenuAbierto()

        assertEquals("arranca sin ocultar", false, privacidad.ocultosAhora())

        composeTestRule.onNodeWithTag(PRIVACY_EYE_TOGGLE_TAG_LITERAL).performClick()
        esperarA { privacidad.ocultosAhora() }

        assertEquals("el puerto real quedó en oculto", true, privacidad.ocultosAhora())
    }

    /**
     * Drena el *main looper* de Robolectric a mano, en vez de
     * `composeTestRule.waitUntil` — ver el KDoc de la prueba que lo usa.
     *
     * El `Thread.sleep` corto es deliberado: `PrivacidadDelMenuPort.alternar()`
     * de verdad escribe a `DataStore` en un hilo real de `Dispatchers.IO`, no
     * en el *main looper* que [shadowOf] drena — sin dejarle pasar tiempo de
     * reloj real a ese hilo, las 50 vueltas de [idle] se agotan sin que el hilo
     * de IO haya tenido oportunidad de completar y reencolar su continuación.
     * Medido: sin el `sleep`, esta espera agota sus intentos siempre.
     */
    private fun esperarA(condicion: () -> Boolean) {
        var intentos = 0
        while (!condicion() && intentos < MAX_INTENTOS_DE_ESPERA) {
            shadowOf(Looper.getMainLooper()).idle()
            composeTestRule.waitForIdle()
            Thread.sleep(ESPERA_ENTRE_INTENTOS_MS)
            intentos++
        }
    }

    private companion object {
        const val ABRIR_MENU_TAG = "abrir_menu_prueba_toggles"
        const val PRIVACY_EYE_TOGGLE_TAG_LITERAL = "msp_privacy_eye_toggle"
        const val MAX_INTENTOS_DE_ESPERA = 50
        const val ESPERA_ENTRE_INTENTOS_MS = 20L
    }
}
