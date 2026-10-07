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
    fun `control positivo, el escaner ve la definicion del menu`() {
        assertTrue(
            escaner.archivosQueMencionan("MenuDeLaApp").any {
                it.endsWith("navigation/menu/MenuDeLaApp.kt")
            }
        )
    }

    companion object {
        val ADOPTADAS: List<String> = listOf(
            "features/home/screens/Home.kt",
            "navigation/DestinosDeCobranzaGraph.kt",
            "features/common/NoModulesScreen.kt",
            "navigation/AppNavigation.kt",
            "features/routemap/RouteMapScreen.kt",
            "features/productsInventory/screens/BlankScreen.kt",
            "features/sales/screens/SalesListScreen.kt",
            "features/sales/screens/UnifiedSalesScreen.kt",
            "features/sales/screens/NewSaleScreen.kt",
            "features/cart/screens/CartScreen.kt",
            "features/productsInventory/screens/ProductsCatalogScreen.kt",
            "features/transfers/presentation/list/TransfersListScreen.kt",
            "features/camionetaAssignment/presentation/screens/CamionetaAssignmentScreen.kt",
            "features/guarantees/screens/GuaranteeListScreen.kt",
            "features/guarantees/screens/CreateGuaranteeScreen.kt",
            "features/sales/screens/SaleDetailsScreen.kt",
            "features/sales/screens/SaleDescriptionScreen.kt",
            "features/payments/screens/PaymentTicketScreen.kt",
            "features/visit/screens/VisitTicketScreen.kt",
            "features/productsInventory/screens/ProductDetailsScreen.kt"
        )
    }

    /** Ninguna llamada a `DrawerContainer(` queda fuera de su propia definición. */
    @Test
    fun `no queda ninguna pantalla con el cajon viejo`() {
        val conCajon = escaner.archivosQueMencionan("DrawerContainer")
            .filterNot { it.endsWith("components/DrawerContainer.kt") }
        assertEquals(emptyList<String>(), conCajon)
    }
}
