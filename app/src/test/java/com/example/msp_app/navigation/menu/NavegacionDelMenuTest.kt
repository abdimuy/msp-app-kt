package com.example.msp_app.navigation.menu

import androidx.navigation.compose.ComposeNavigator
import androidx.navigation.compose.composable
import androidx.navigation.createGraph
import androidx.navigation.testing.TestNavHostController
import androidx.test.core.app.ApplicationProvider
import com.example.msp_app.feature.pagos.ui.PagosRutas
import com.example.msp_app.navigation.Screen
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = android.app.Application::class)
class NavegacionDelMenuTest {

    private fun nav(): TestNavHostController {
        val nav = TestNavHostController(ApplicationProvider.getApplicationContext())
        nav.navigatorProvider.addNavigator(ComposeNavigator())
        nav.graph = nav.createGraph(startDestination = Screen.Home.route) {
            composable(Screen.Home.route) {}
            composable(PagosRutas.LISTA_CLIENTES) {}
            composable(Screen.DailyReport.route) {}
            composable(Screen.Configuracion.route) {}
        }
        return nav
    }

    private fun pila(nav: TestNavHostController) =
        nav.currentBackStack.value.mapNotNull { it.destination.route }.filter { it != nav.graph.route }

    @Test
    fun `elegir clientes deja home abajo`() {
        val nav = nav()
        navegarDesdeElMenu(nav, IdsDelMenu.CLIENTES)
        assertEquals(listOf(Screen.Home.route, PagosRutas.LISTA_CLIENTES), pila(nav))
    }

    @Test
    fun `elegir la opcion de la pantalla actual no navega`() {
        val nav = nav()
        navegarDesdeElMenu(nav, IdsDelMenu.CLIENTES)
        val antes = nav.currentBackStack.value.map { it.id }

        navegarDesdeElMenu(nav, IdsDelMenu.CLIENTES)

        assertEquals(antes, nav.currentBackStack.value.map { it.id })
    }

    @Test
    fun `elegir inicio estando en inicio no recrea la pantalla`() {
        val nav = nav()
        val antes = nav.currentBackStack.value.map { it.id }
        navegarDesdeElMenu(nav, IdsDelMenu.INICIO)
        assertEquals(antes, nav.currentBackStack.value.map { it.id })
    }

    @Test
    fun `dos toques rapidos dejan una sola copia`() {
        val nav = nav()
        navegarDesdeElMenu(nav, IdsDelMenu.REPORTE)
        navegarDesdeElMenu(nav, IdsDelMenu.REPORTE)
        assertEquals(listOf(Screen.Home.route, Screen.DailyReport.route), pila(nav))
    }

    @Test
    fun `cambiar de opcion reemplaza la anterior sobre home`() {
        val nav = nav()
        navegarDesdeElMenu(nav, IdsDelMenu.CLIENTES)
        navegarDesdeElMenu(nav, IdsDelMenu.REPORTE)
        assertEquals(listOf(Screen.Home.route, Screen.DailyReport.route), pila(nav))
    }

    @Test
    fun `configuracion se apila una sola vez`() {
        val nav = nav()
        navegarAConfiguracion(nav)
        navegarAConfiguracion(nav)
        assertEquals(listOf(Screen.Home.route, Screen.Configuracion.route), pila(nav))
    }
}
