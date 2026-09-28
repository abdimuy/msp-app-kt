package com.example.msp_app.navigation

import androidx.navigation.compose.ComposeNavigator
import androidx.navigation.compose.composable
import androidx.navigation.createGraph
import androidx.navigation.testing.TestNavHostController
import androidx.test.core.app.ApplicationProvider
import com.example.msp_app.feature.pagos.ui.PagosRutas
import com.example.msp_app.features.forgiveness.screens.irAlTicketDeLaCondonacion
import com.example.msp_app.features.sales.components.saleactionssection.irAlTicketDesdeElDetalleLegado
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * **Una condonación guardada lleva a SU ticket, y atrás lleva a la venta.**
 *
 * El defecto (E-APP-031, P1 de la compuerta del 2026-09-26): el diálogo
 * empujaba `payment_ticket/{id}` y después llamaba a `onDismissRequest`, que en
 * `ForgivenessScreen` es `popBackStack()` — y lo que sacaba era **el ticket
 * recién empujado**. El cobrador volvía a la condonación, con la venta vieja y
 * sin ninguna señal de que se había guardado; reintentaba, y cada reintento era
 * otra condonación (E-APP-029: cuatro en 39 segundos).
 *
 * Se mide sobre el grafo REAL ([destinosDeCobranza]) con las dos entradas
 * reales a la condonación: el detalle de venta y el de cliente nuevos.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = android.app.Application::class)
class LaCondonacionGuardadaVaAlTicketTest {

    private lateinit var nav: TestNavHostController

    @Before
    fun montarElGrafo() {
        nav = TestNavHostController(ApplicationProvider.getApplicationContext())
        nav.navigatorProvider.addNavigator(ComposeNavigator())
        nav.graph = nav.createGraph(startDestination = RAIZ) {
            composable(RAIZ) {}
            destinosDeCobranza(nav)
            composable(Screen.SaleDetails.route) {}
            composable(Screen.Guarantee.route) {}
            composable(Screen.PaymentTicket.route) {}
        }
    }

    /** `getBackStackEntry` lanza si la ruta no está en la pila. */
    private fun enLaPila(ruta: String): Boolean =
        runCatching { nav.getBackStackEntry(ruta) }.isSuccess

    @Test
    fun `desde el detalle de venta, guardar lleva al ticket y atras vuelve a la venta`() {
        nav.navigate(PagosRutas.detalleVenta(VENTA))
        nav.navigate(Screen.Forgiveness.createRoute(VENTA))

        nav.irAlTicketDeLaCondonacion(PAGO)

        assertEquals(Screen.PaymentTicket.route, nav.currentDestination?.route)
        assertEquals(PAGO, nav.currentBackStackEntry?.arguments?.getString("paymentId"))
        assertTrue(
            "control positivo: la venta sí está en la pila",
            enLaPila(PagosRutas.DETALLE_VENTA)
        )
        assertFalse(
            "la condonación se quedó en la pila debajo del ticket",
            enLaPila(Screen.Forgiveness.route)
        )

        assertTrue(nav.popBackStack())
        assertEquals(PagosRutas.DETALLE_VENTA, nav.currentDestination?.route)
    }

    @Test
    fun `desde el detalle de cliente, atras desde el ticket vuelve al cliente`() {
        nav.navigate(PagosRutas.detalleCliente(CLIENTE))
        nav.navigate(Screen.Forgiveness.createRoute(VENTA))

        nav.irAlTicketDeLaCondonacion(PAGO)

        assertEquals(Screen.PaymentTicket.route, nav.currentDestination?.route)
        assertTrue(nav.popBackStack())
        assertEquals(PagosRutas.DETALLE_CLIENTE, nav.currentDestination?.route)
    }

    /**
     * La pantalla de condonación cablea ESA salida a `onGuardada`, y cancelar
     * sigue siendo `popBackStack()`. Escáner de fuente, igual que
     * `SaleIdSpacesCallSitesTest`: la pantalla levanta Firebase y no se puede
     * montar en una prueba unitaria.
     */
    @Test
    fun `ForgivenessScreen cablea guardar al ticket y cancelar a atras`() {
        val fuente = File(
            "src/main/java/com/example/msp_app/features/forgiveness/screens/ForgivenessScreen.kt"
        ).readText()

        assertTrue(
            "onGuardada ya no navega al ticket con irAlTicketDeLaCondonacion",
            fuente.contains(
                "onGuardada = { pagoId -> navController.irAlTicketDeLaCondonacion(pagoId) }"
            )
        )
        assertTrue(
            "cancelar dejó de ser popBackStack()",
            fuente.contains("onDismissRequest = { navController.popBackStack() }")
        )
    }

    /**
     * **La puerta legada** (`SaleActionSection`, dentro del detalle viejo): el
     * ticket se empuja encima del detalle y atrás vuelve al detalle. El diálogo
     * no es un destino, así que no hay nada que sacar de la pila.
     */
    @Test
    fun `desde el detalle legado, guardar lleva al ticket y atras vuelve al detalle`() {
        nav.navigate(Screen.SaleDetails.createRoute(VENTA))

        nav.irAlTicketDesdeElDetalleLegado(PAGO)

        assertEquals(Screen.PaymentTicket.route, nav.currentDestination?.route)
        assertEquals(PAGO, nav.currentBackStackEntry?.arguments?.getString("paymentId"))
        assertTrue(nav.popBackStack())
        assertEquals(Screen.SaleDetails.route, nav.currentDestination?.route)
    }

    @Test
    fun `SaleActionSection cablea guardar al ticket legado`() {
        val fuente = File(
            "src/main/java/com/example/msp_app/features/sales/components/" +
                "saleactionssection/SaleActionsSection.kt"
        ).readText()

        assertTrue(
            "la puerta legada dejó de ir al ticket al guardar",
            fuente.contains("navController.irAlTicketDesdeElDetalleLegado(pagoId)")
        )
    }

    private companion object {
        const val RAIZ = "raiz"
        const val VENTA = 13_662_829
        const val CLIENTE = 4821
        const val PAGO = "3f1c2b8e-6d0a-4c3e-9a51-2b7d8e4f0c11"
    }
}
