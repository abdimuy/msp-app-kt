package com.example.msp_app.navigation

import android.content.Context
import androidx.activity.compose.setContent
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.navigation.compose.ComposeNavigator
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.createGraph
import androidx.navigation.testing.TestNavHostController
import androidx.test.core.app.ApplicationProvider
import com.example.msp_app.feature.pagos.ui.BOTON_MENU_TAG
import com.example.msp_app.feature.pagos.ui.PagosRutas
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * **La hamburguesa de la lista de clientes abre el cajón ACTUAL, no un no-op.**
 *
 * Monta el MISMO grafo que `AppNavigation` compone ([destinosDeCobranza]),
 * navega a `pagos/clientes` y toca el botón de menú real
 * ([BOTON_MENU_TAG]). Tiene que aparecer el contenido de `DrawerContainer`
 * —su encabezado fijo "Menú" y "Cerrar sesión", que no dependen de sesión ni
 * de `MODULOS` (ver el KDoc de `DrawerContainer.authViewModel` para por qué
 * esta red no necesita una sesión real)—.
 *
 * Mismo patrón de montaje que [CadaPantallaDeCobranzaRespetaLaBarraDeEstadoTest]:
 * `createEmptyComposeRule` y una Activity `@AndroidEntryPoint` construida a mano
 * con Robolectric, porque `hiltViewModel()` dentro de la lambda `composable{}`
 * de [destinoDeListaDeClientes] exige esa Activity y `createAndroidComposeRule`
 * no puede resolverla por `Intent` bajo Robolectric.
 *
 * **Control de rojo, medido**: antes de cablear [DrawerContainer] en
 * `DestinosDeCobranzaGraph`, `destinoDeListaDeClientes` recibía
 * `onAbrirMenu = {}` — tocar la hamburguesa no hacía NADA, así que "Menú"
 * nunca aparecía y la segunda aserción de este test quedaba roja.
 *
 * `FirebaseApp.initializeApp` con opciones de relleno —mismo fixture que
 * `CorreccionSubeCorregidaTest`—: `DrawerContainer` pinta `UpdateBanner` en el
 * pie del cajón (componiéndose SIEMPRE, cerrado o abierto, porque
 * `ModalNavigationDrawer` compone su `drawerContent` de entrada para
 * sostener el estado), y `UpdateChecker` —`object` que ese banner toca— pide
 * `FirebaseFirestore.getInstance()` en su `init`. Medido: sin este fixture,
 * el `setContent` de abajo revienta con `IllegalStateException("Default
 * FirebaseApp is not initialized...")` antes de llegar a pintar nada.
 */
@HiltAndroidTest
@RunWith(RobolectricTestRunner::class)
@Config(application = HiltTestApplication::class, sdk = [33])
class LaHamburguesaDeClientesAbreElCajonTest {

    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val composeTestRule = createEmptyComposeRule()

    @Before
    fun setUp() {
        hiltRule.inject()
        val context: Context = ApplicationProvider.getApplicationContext()
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
    }

    @Test
    fun `tocar abrir menu en la lista de clientes abre el cajon real`() {
        val activity = Robolectric.buildActivity(DestinosDeCobranzaTestActivity::class.java)
            .setup()
            .get()
        val nav = TestNavHostController(activity)
        nav.navigatorProvider.addNavigator(ComposeNavigator())
        nav.setLifecycleOwner(activity)
        nav.setViewModelStore(activity.viewModelStore)
        val grafo = nav.createGraph(startDestination = RAIZ) {
            composable(RAIZ) {}
            destinosDeCobranza(nav, activity)
        }
        nav.graph = grafo
        // Se navega ANTES de componer — mismo motivo que `CadaDestinoDeCobranzaSeMontaTest`:
        // con `startDestination = ruta` se pierde el grafo de arranque; navegando antes, el
        // destino concreto queda posicionado cuando `setContent` compone.
        nav.navigate(PagosRutas.LISTA_CLIENTES)

        activity.setContent { NavHost(navController = nav, graph = grafo) }
        composeTestRule.waitForIdle()

        // Control positivo: `ModalNavigationDrawer` compone su `drawerContent`
        // SIEMPRE, cerrado o abierto (para sostener el estado) — "Menú" ya
        // existe en el árbol de semántica, sólo que fuera de pantalla. Por eso
        // la aserción es `assertIsNotDisplayed`, no `assertDoesNotExist`: medido,
        // la segunda falla incluso con el cajón funcionando de verdad.
        composeTestRule.onNodeWithText("Menú").assertIsNotDisplayed()

        composeTestRule.onNodeWithTag(BOTON_MENU_TAG).performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("Menú").assertIsDisplayed()
        composeTestRule.onNodeWithText("Cerrar sesión").assertIsDisplayed()
    }

    private companion object {
        const val RAIZ = "raiz_de_la_prueba_de_la_hamburguesa"
    }
}
