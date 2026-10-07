package com.example.msp_app.navigation

import android.content.Context
import androidx.activity.compose.setContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.navigation.compose.ComposeNavigator
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.createGraph
import androidx.navigation.testing.TestNavHostController
import androidx.test.core.app.ApplicationProvider
import com.example.msp_app.core.navegacion.ui.MENU_LATERAL_TAG
import com.example.msp_app.feature.pagos.ui.PagosRutas
import com.example.msp_app.feature.visitas.ui.VisitasRutas
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
 * Spec §2.2 (decidido 2026-10-06): en detalle de cliente y de venta el menú se
 * abre deslizando desde la barra superior; Registrar abono y Registrar visita
 * NO tienen menú. Monta el MISMO grafo que `AppNavigation` ([destinosDeCobranza]).
 *
 * **Medido (R12): el modo gráfico NO afecta esta prueba.** El deslizamiento
 * nace en [deslizarEnLaBarra] sobre `onRoot()` —el contenido de la pantalla,
 * no un hijo DENTRO del `ModalDrawerSheet`— y las aserciones son
 * `assertIsDisplayed`/`assertCountEquals`, que no dependen de la prueba de
 * recorte (`Outline`/`clipPath`) que obliga a `GraphicsMode.Mode.NATIVE` en
 * `MenuLateralContenedorTest` (`:core:navegacion`) para un `performClick()`
 * dentro de la hoja. Confirmado anotando esta clase con
 * `@GraphicsMode(GraphicsMode.Mode.NATIVE)` temporalmente: los 5 casos quedan
 * igual de verdes (ver `task-16-report.md`). Se deja en LEGACY (el default).
 */
@HiltAndroidTest
@RunWith(RobolectricTestRunner::class)
@Config(application = HiltTestApplication::class, sdk = [33])
class ElMenuSeAbreDesdeLaBarraTest {

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

    private fun montarEn(ruta: String) {
        val activity = Robolectric.buildActivity(
            DestinosDeCobranzaTestActivity::class.java
        ).setup().get()
        val nav = TestNavHostController(activity)
        nav.navigatorProvider.addNavigator(ComposeNavigator())
        nav.setLifecycleOwner(activity)
        nav.setViewModelStore(activity.viewModelStore)
        val grafo = nav.createGraph(startDestination = RAIZ) {
            composable(RAIZ) {}
            destinosDeCobranza(nav, activity)
        }
        nav.graph = grafo
        nav.navigate(ruta)
        activity.setContent { NavHost(navController = nav, graph = grafo) }
        composeTestRule.waitForIdle()
    }

    private fun deslizarEnLaBarra() {
        composeTestRule.onRoot().performTouchInput {
            swipe(start = Offset(120f, 30f), end = Offset(400f, 30f), durationMillis = 250)
        }
        composeTestRule.waitForIdle()
    }

    @Test
    fun `en el detalle de cliente deslizar desde la barra abre el menu`() {
        montarEn(PagosRutas.detalleCliente(1))
        deslizarEnLaBarra()
        composeTestRule.onNodeWithTag(MENU_LATERAL_TAG).assertIsDisplayed()
    }

    @Test
    fun `en el detalle de venta deslizar desde la barra abre el menu`() {
        montarEn(PagosRutas.detalleVenta(1))
        deslizarEnLaBarra()
        composeTestRule.onNodeWithTag(MENU_LATERAL_TAG).assertIsDisplayed()
    }

    @Test
    fun `registrar abono no tiene menu`() {
        montarEn(PagosRutas.registrarAbono(1))
        composeTestRule.onAllNodesWithTag(MENU_LATERAL_TAG).assertCountEquals(0)
    }

    @Test
    fun `registrar visita no tiene menu`() {
        montarEn(VisitasRutas.registrar(1, 1))
        composeTestRule.onAllNodesWithTag(MENU_LATERAL_TAG).assertCountEquals(0)
    }

    @Test
    fun `control positivo, el detalle de cliente si compone el menu`() {
        montarEn(PagosRutas.detalleCliente(1))
        composeTestRule.onAllNodesWithTag(MENU_LATERAL_TAG).assertCountEquals(1)
    }

    private companion object {
        const val RAIZ = "raiz_de_la_prueba_de_la_barra"
    }
}
