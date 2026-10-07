package com.example.msp_app.navigation.menu

import android.content.Context
import androidx.activity.compose.setContent
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
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
import com.example.msp_app.navigation.DestinosDeCobranzaTestActivity
import com.example.msp_app.navigation.Screen
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
import org.robolectric.annotation.GraphicsMode

/**
 * **Review Focus 4 del plan: sin sesión, el menú se pinta con sólo el pie —
 * nunca truena.**
 *
 * `MenuDeLaApp` envuelve `LocalAuthViewModel.current` en `runCatching`
 * (`MenuDeLaApp.kt`) porque una red de prueba —o cualquier composición fuera
 * de `AppNavigation`, la única que provee `LocalAuthViewModel` en
 * producción— puede montar este menú sin sesión. Esta prueba monta el menú
 * SIN proveer `LocalAuthViewModel` en absoluto: el camino real de ese caso
 * (el mismo que ya cubre `LaHamburguesaDeClientesAbreElMenuLateralTest` para
 * `MenuDeLaApp`), no un mock.
 *
 * `@GraphicsMode(GraphicsMode.Mode.NATIVE)` (Ruling R12): esta prueba no hace
 * click en nada DENTRO de la hoja, pero sí abre el cajón y LEE nodos dentro
 * de él — se anota por el mismo criterio que las demás pruebas de este
 * paquete, aunque el recorte asimétrico de LEGACY afecta el *hit-testing* de
 * clicks, no la lectura de nodos por semántica.
 */
@HiltAndroidTest
@RunWith(RobolectricTestRunner::class)
@Config(application = HiltTestApplication::class, sdk = [33])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class MenuDeLaAppSinSesionTest {

    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val composeTestRule = createEmptyComposeRule()

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
    }

    /**
     * **Control de rojo, medido**: con `MenuDeLaApp.kt` temporalmente
     * modificado para que `usuario` sea un `User(NOMBRE = "Efraín Domínguez
     * Reyes", MODULOS = listOf("COBRO"))` hardcodeado (en vez de derivarlo de
     * `LocalAuthViewModel`), esta MISMA prueba falla en la primera aserción
     * (`onNodeWithText("?").assertIsDisplayed()`) con
     * `java.lang.AssertionError: Assert failed: The component is not
     * displayed!` — el avatar ahora pinta "ED", no "?". Restaurado de
     * inmediato tras medir — confirma que esta prueba distingue de verdad el
     * caso sin sesión del caso con sesión, no una coincidencia de que ambos
     * casos pintan igual.
     */
    @Test
    fun `sin usuario el menu queda con solo el pie`() {
        val activity = Robolectric.buildActivity(DestinosDeCobranzaTestActivity::class.java)
            .setup()
            .get()
        val nav = TestNavHostController(activity)
        nav.navigatorProvider.addNavigator(ComposeNavigator())
        nav.setLifecycleOwner(activity)
        nav.setViewModelStore(activity.viewModelStore)
        val grafo = nav.createGraph(startDestination = Screen.Home.route) {
            composable(Screen.Home.route) {
                // Nota deliberada: a diferencia de otras pruebas de este paquete, NINGÚN
                // `CompositionLocalProvider(LocalAuthViewModel provides ...)` envuelve esto
                // — es justo lo que esta prueba necesita medir.
                MenuDeLaApp(navController = nav) { abrirMenu ->
                    Button(onClick = abrirMenu, modifier = Modifier.testTag(ABRIR_MENU_TAG)) {
                        Text("abrir")
                    }
                }
            }
        }
        nav.graph = grafo

        activity.setContent { NavHost(navController = nav, graph = grafo) }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag(ABRIR_MENU_TAG).performClick()
        composeTestRule.waitForIdle()

        // Avatar de iniciales: "?" es lo que `ReglasDelMenu.iniciales("")` devuelve.
        composeTestRule.onNodeWithText("?").assertIsDisplayed()
        // Sin MODULOS no hay secciones (CatalogoDelMenu.secciones(emptyList(), ...) = emptyList()):
        // ninguna opción de ningún módulo debe aparecer.
        composeTestRule.onNodeWithText("Clientes").assertDoesNotExist()
        composeTestRule.onNodeWithText("Ventas").assertDoesNotExist()
        composeTestRule.onNodeWithText("Traspasos").assertDoesNotExist()
        composeTestRule.onNodeWithText("Garantías").assertDoesNotExist()
        // El pie es fijo (spec §2.1.4): se pinta sin importar MODULOS ni sesión.
        composeTestRule.onNodeWithText("Configuración").assertIsDisplayed()
    }

    private companion object {
        const val ABRIR_MENU_TAG = "abrir_menu_prueba_sin_sesion"
    }
}
