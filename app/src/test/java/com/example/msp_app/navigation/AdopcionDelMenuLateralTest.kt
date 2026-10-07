package com.example.msp_app.navigation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Cada pantalla adoptada llama a `MenuDeLaApp` y ya no a `DrawerContainer`.
 * [ADOPTADAS] crece una tarea a la vez (plan 2026-10-06-menu-lateral, Tasks
 * 11–15); la Task 18 la reemplaza por "nadie menciona DrawerContainer".
 */
class AdopcionDelMenuLateralTest {

    private val escaner = EscanerDeFuentes()

    @Test
    fun `las pantallas adoptadas llaman al menu nuevo y no al cajon viejo`() {
        val conMenu = escaner.archivosQueMencionan("MenuDeLaApp")
        val conCajon = escaner.archivosQueMencionan("DrawerContainer")

        val sinMenu = ADOPTADAS.filterNot { ruta -> conMenu.any { it.endsWith(ruta) } }
        val conCajonTodavia = ADOPTADAS.filter { ruta -> conCajon.any { it.endsWith(ruta) } }

        assertEquals("no llaman a MenuDeLaApp", emptyList<String>(), sinMenu)
        assertEquals("siguen llamando a DrawerContainer", emptyList<String>(), conCajonTodavia)
    }

    @Test
    fun `control positivo, el escaner ve la definicion del menu y la del cajon`() {
        assertTrue(
            escaner.archivosQueMencionan("MenuDeLaApp").any {
                it.endsWith("navigation/menu/MenuDeLaApp.kt")
            }
        )
        assertTrue(
            escaner.archivosQueMencionan("DrawerContainer").any {
                it.endsWith("components/DrawerContainer.kt")
            }
        )
    }

    companion object {
        val ADOPTADAS: List<String> = listOf(
            "features/home/screens/Home.kt",
            "navigation/DestinosDeCobranzaGraph.kt",
            "features/common/NoModulesScreen.kt",
            "navigation/AppNavigation.kt",
            "features/routemap/RouteMapScreen.kt"
        )
    }
}
