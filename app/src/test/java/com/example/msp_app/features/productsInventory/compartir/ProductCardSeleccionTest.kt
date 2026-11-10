package com.example.msp_app.features.productsInventory.compartir

import android.content.Context
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.navigation.NavController
import androidx.test.core.app.ApplicationProvider
import com.example.msp_app.data.models.productInventory.ProductInventory
import com.example.msp_app.features.productsInventory.screens.ProductCard
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * E-APP-180: la tarjeta del catálogo para la selección múltiple. Mantener presionado activa la
 * selección, tocar con la selección activa marca/desmarca (no navega), y lo marcado se ve.
 * La navegación al detalle (toque sin selección) sigue siendo la de siempre y no se toca aquí.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(
    sdk = [33],
    application = android.app.Application::class,
    qualifiers = "w360dp-h800dp-xhdpi"
)
class ProductCardSeleccionTest {

    @get:Rule
    val rule = createComposeRule()

    private val producto = ProductInventory(
        ARTICULO_ID = 7,
        ARTICULO = "SALA VALENCIA",
        EXISTENCIAS = 3,
        LINEA_ARTICULO_ID = 1,
        LINEA_ARTICULO = "SALAS",
        PRECIOS = "Precio 1 Meses:8990,Precio 4 Meses:10450,Precio de lista:13200"
    )

    private fun mostrar(
        seleccionado: Boolean,
        enSeleccion: Boolean,
        largo: () -> Unit,
        toque: () -> Unit
    ) {
        // Sin grafo: estas pruebas nunca llegan a navegar (con la selección activa el toque marca; sin ella no se toca).
        val navController = NavController(ApplicationProvider.getApplicationContext<Context>())
        rule.setContent {
            MaterialTheme {
                ProductCard(
                    product = producto,
                    navController = navController,
                    seleccionado = seleccionado,
                    enSeleccion = enSeleccion,
                    alMantenerPresionado = largo,
                    alTocarEnSeleccion = toque
                )
            }
        }
    }

    @Test
    fun `mantener presionado avisa para activar la seleccion`() {
        var largos = 0
        var toques = 0
        mostrar(
            seleccionado = false,
            enSeleccion = false,
            largo = { largos++ },
            toque = { toques++ }
        )
        rule.onNodeWithText("SALA VALENCIA").performTouchInput { longClick() }
        assertEquals(1, largos)
        assertEquals(0, toques)
    }

    @Test
    fun `con la seleccion activa un toque marca y no navega`() {
        var toques = 0
        mostrar(seleccionado = false, enSeleccion = true, largo = {}, toque = { toques++ })
        rule.onNodeWithText("SALA VALENCIA").performClick()
        assertEquals(1, toques)
    }

    @Test
    fun `con la seleccion activa mantener presionado tambien alterna`() {
        var largos = 0
        mostrar(seleccionado = true, enSeleccion = true, largo = { largos++ }, toque = {})
        rule.onNodeWithText("SALA VALENCIA").performTouchInput { longClick() }
        assertEquals(1, largos)
    }

    @Test
    fun `lo seleccionado muestra su palomita y lo demas no`() {
        mostrar(seleccionado = true, enSeleccion = true, largo = {}, toque = {})
        rule.onNodeWithContentDescription("Seleccionado").assertIsDisplayed()
    }

    @Test
    fun `sin seleccionar no hay palomita`() {
        mostrar(seleccionado = false, enSeleccion = true, largo = {}, toque = {})
        rule.onNodeWithContentDescription("Seleccionado").assertDoesNotExist()
    }
}
