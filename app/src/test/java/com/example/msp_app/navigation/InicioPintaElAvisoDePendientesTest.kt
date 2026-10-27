package com.example.msp_app.navigation

import android.content.Context
import androidx.activity.compose.setContent
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.navigation.compose.ComposeNavigator
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.createGraph
import androidx.navigation.testing.TestNavHostController
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.msp_app.core.context.LocalAuthViewModel
import com.example.msp_app.core.database.AppDatabase
import com.example.msp_app.core.database.entities.VisitEntity
import com.example.msp_app.core.utils.FuenteDePosicion
import com.example.msp_app.feature.visitas.ui.components.AVISO_DE_PENDIENTES_TAG
import com.example.msp_app.features.auth.viewModels.AuthViewModel
import com.example.msp_app.features.home.screens.HomeScreen
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * E-APP-096: Inicio pinta el aviso "N sin enviar · no cambies de teléfono".
 *
 * Las pruebas del aviso viven en `:feature:visitas` y montan el composable
 * suelto; ninguna se pone roja si `HomeScreen` deja de llamarlo. Esta monta el
 * `HomeScreen` real (con el grafo de Hilt de la app) sobre una base Room en
 * memoria con una visita sin enviar, y busca el aviso con su texto.
 *
 * Vive en `navigation.*` para correr en `testDevlocalDebugNavegacionUnitTest`,
 * una JVM por clase (E-APP-111): es una prueba Compose sobre el sandbox
 * compartido de Robolectric.
 *
 * Rojo medido el 2026-10-19: con el `item { AvisoDePendientes(...) }` quitado
 * de `Home.kt`, esta prueba falla con `ComposeTimeoutException` (el aviso no
 * aparece en 10 s); restaurado, pasa.
 */
@HiltAndroidTest
@RunWith(RobolectricTestRunner::class)
@Config(application = HiltTestApplication::class, sdk = [33], qualifiers = "w360dp-h2400dp-xhdpi")
class InicioPintaElAvisoDePendientesTest {

    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val composeTestRule = createEmptyComposeRule()

    private lateinit var db: AppDatabase

    @Before
    fun setUp() {
        val context: Context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        AppDatabase.setInstanceForTesting(db)
        hiltRule.inject()
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

    @After
    fun tearDown() {
        AppDatabase.clearInstance()
    }

    @Test
    fun `E-APP-096 inicio pinta el aviso de pendientes con una visita sin enviar`() {
        runBlocking { db.visitDao().insertVisit(visitaSinEnviar()) }

        val activity = Robolectric.buildActivity(DestinosDeCobranzaTestActivity::class.java)
            .setup()
            .get()
        val auth = AuthViewModel(activity.application)
        val nav = TestNavHostController(activity)
        nav.navigatorProvider.addNavigator(ComposeNavigator())
        nav.setLifecycleOwner(activity)
        nav.setViewModelStore(activity.viewModelStore)
        val grafo = nav.createGraph(startDestination = INICIO) {
            composable(INICIO) {
                HomeScreen(navController = nav, fuenteDePosicion = FuenteDePosicion { emptyFlow() })
            }
        }
        nav.graph = grafo
        activity.setContent {
            CompositionLocalProvider(LocalAuthViewModel provides auth) {
                NavHost(navController = nav, graph = grafo)
            }
        }

        composeTestRule.waitUntil(timeoutMillis = 10_000) {
            composeTestRule.onAllNodesWithTagCount(AVISO_DE_PENDIENTES_TAG) > 0
        }
        composeTestRule.onNodeWithTag(AVISO_DE_PENDIENTES_TAG, useUnmergedTree = true)
            .assertTextEquals("1 sin enviar · no cambies de teléfono")
    }

    private fun ComposeTestRule.onAllNodesWithTagCount(tag: String): Int =
        onAllNodes(hasTestTag(tag), useUnmergedTree = true)
            .fetchSemanticsNodes().size

    private fun visitaSinEnviar() = VisitEntity(
        ID = "vis-inicio-pendiente",
        CLIENTE_ID = 30144,
        COBRADOR = "Martín Salgado",
        COBRADOR_ID = 12,
        FECHA = "2026-10-18T14:00:00Z",
        FORMA_COBRO_ID = 157,
        LAT = 18.46,
        LNG = -97.39,
        TIPO_VISITA = "No se encontraba",
        ZONA_CLIENTE_ID = 25,
        GUARDADO_EN_MICROSIP = 0
    )

    private companion object {
        const val INICIO = "inicio_de_la_prueba_del_aviso"
    }
}
