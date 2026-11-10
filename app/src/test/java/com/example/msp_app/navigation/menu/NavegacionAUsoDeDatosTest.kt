package com.example.msp_app.navigation.menu

import androidx.navigation.compose.ComposeNavigator
import androidx.navigation.compose.composable
import androidx.navigation.createGraph
import androidx.navigation.testing.TestNavHostController
import androidx.test.core.app.ApplicationProvider
import com.example.msp_app.feature.usodedatos.ui.UsoDeDatosRutas
import com.example.msp_app.navigation.Screen
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** E-APP-175: "Uso de datos" se apila una sola vez sobre la pantalla vigente. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = android.app.Application::class)
class NavegacionAUsoDeDatosTest {

    @Test
    fun `uso de datos se apila una sola vez`() {
        val nav = TestNavHostController(ApplicationProvider.getApplicationContext())
        nav.navigatorProvider.addNavigator(ComposeNavigator())
        nav.graph = nav.createGraph(startDestination = Screen.Home.route) {
            composable(Screen.Home.route) {}
            composable(UsoDeDatosRutas.USO_DE_DATOS) {}
        }
        navegarAUsoDeDatos(nav)
        navegarAUsoDeDatos(nav)
        val pila = nav.currentBackStack.value.mapNotNull {
            it.destination.route
        }.filter { it != nav.graph.route }
        assertEquals(listOf(Screen.Home.route, UsoDeDatosRutas.USO_DE_DATOS), pila)
    }
}
