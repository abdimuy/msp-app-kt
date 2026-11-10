package com.example.msp_app.feature.compartirproductos.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import com.example.msp_app.core.common.ventas.precios.TipoDePrecio
import com.example.msp_app.core.testing.RobolectricTestBase
import com.example.msp_app.feature.compartirproductos.domain.ArticuloParaCompartir
import com.example.msp_app.feature.compartirproductos.domain.FilaDePrecio
import com.example.msp_app.feature.compartirproductos.domain.PrecioDeCatalogo
import com.example.msp_app.feature.compartirproductos.domain.SeleccionDePrecios
import java.math.BigDecimal
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h800dp-xhdpi")
class HojaDeCompartirTest : RobolectricTestBase() {

    @get:Rule val rule = createComposeRule()

    private fun art(id: Int) = ArticuloParaCompartir(
        id,
        "SALA $id",
        "SALAS",
        listOf(PrecioDeCatalogo(TipoDePrecio.CONTADO, BigDecimal("100"))),
        null
    )

    private fun estado(n: Int = 1, extra: EstadoDeCompartir.() -> EstadoDeCompartir = { this }) =
        EstadoDeCompartir(
            visible = true,
            articulos = List(n) {
                art(it)
            },
            titulo = if (n == 1) "Sala Valencia" else ""
        ).extra()

    private fun mostrar(
        e: EstadoDeCompartir,
        titulo: (String) -> Unit = {},
        casilla: (FilaDePrecio, Boolean) -> Unit = { _, _ -> },
        compartir: () -> Unit = {}
    ) = rule.setContent { MaterialTheme { ContenidoDeLaHoja(e, titulo, casilla, compartir) } }

    @Test fun `con un producto muestra el campo de titulo con su valor`() {
        mostrar(estado(1))
        rule.onNodeWithTag(
            EtiquetasDeHoja.TITULO
        ).assertIsDisplayed().assertTextContains("Sala Valencia")
        rule.onNodeWithText("Compartir producto").assertIsDisplayed()
    }

    @Test fun `con varios no hay campo de titulo y el encabezado cuenta los productos`() {
        mostrar(estado(5))
        rule.onNodeWithTag(EtiquetasDeHoja.TITULO).assertDoesNotExist()
        rule.onNodeWithText("Compartir 5 productos").assertIsDisplayed()
    }

    @Test fun `las tres casillas existen con sus etiquetas y reflejan la seleccion`() {
        mostrar(estado(1) { copy(seleccion = SeleccionDePrecios(true, false, true)) })
        rule.onNodeWithText("Contado").assertIsDisplayed()
        rule.onNodeWithText("Corto plazo").assertIsDisplayed()
        rule.onNodeWithText("Crédito").assertIsDisplayed()
        rule.onNodeWithTag(EtiquetasDeHoja.casilla(FilaDePrecio.CONTADO)).assertIsOn()
        rule.onNodeWithTag(EtiquetasDeHoja.casilla(FilaDePrecio.CORTO_PLAZO)).assertIsOff()
        rule.onNodeWithTag(EtiquetasDeHoja.casilla(FilaDePrecio.CREDITO)).assertIsOn()
    }

    @Test fun `tocar una casilla avisa cual y en que estado quedo`() {
        val toques = mutableListOf<Pair<FilaDePrecio, Boolean>>()
        mostrar(estado(1), casilla = { f, m -> toques += f to m })
        rule.onNodeWithTag(EtiquetasDeHoja.casilla(FilaDePrecio.CORTO_PLAZO)).performClick()
        assertEquals(listOf(FilaDePrecio.CORTO_PLAZO to false), toques)
    }

    @Test fun `escribir en el titulo avisa el texto nuevo`() {
        val textos = mutableListOf<String>()
        mostrar(estado(1) { copy(titulo = "") }, titulo = { textos += it })
        rule.onNodeWithTag(EtiquetasDeHoja.TITULO).performTextInput("Hola")
        assertEquals("Hola", textos.last())
    }

    @Test fun `borrar el titulo avisa vacio`() {
        val textos = mutableListOf<String>()
        mostrar(estado(1), titulo = { textos += it })
        rule.onNodeWithTag(EtiquetasDeHoja.TITULO).performTextClearance()
        assertEquals("", textos.last())
    }

    @Test fun `el boton Compartir avisa al tocarlo`() {
        var veces = 0
        mostrar(estado(1), compartir = { veces++ })
        rule.onNodeWithTag(EtiquetasDeHoja.BOTON).assertIsEnabled().assertTextContains("Compartir")
        rule.onNodeWithTag(EtiquetasDeHoja.BOTON).performClick()
        assertEquals(1, veces)
    }

    @Test fun `el aviso de precios viejos solo sale cuando corresponde`() {
        mostrar(estado(1) { copy(avisoPreciosViejos = true) })
        rule.onNodeWithTag(
            EtiquetasDeHoja.AVISO
        ).assertIsDisplayed().assertTextContains("Los precios pueden no estar actualizados")
    }

    @Test fun `sin aviso no hay texto de aviso`() {
        mostrar(estado(1))
        rule.onNodeWithTag(EtiquetasDeHoja.AVISO).assertDoesNotExist()
    }

    @Test fun `mientras genera el boton se desactiva y muestra el progreso`() {
        mostrar(estado(5) { copy(generando = true, hechas = 2) })
        rule.onNodeWithTag(EtiquetasDeHoja.BOTON).assertIsNotEnabled()
        rule.onNodeWithText("Preparando 2 de 5").assertIsDisplayed()
        rule.onNodeWithTag(EtiquetasDeHoja.casilla(FilaDePrecio.CONTADO)).assertIsNotEnabled()
    }

    @Test fun `con un producto generando dice solo Preparando`() {
        mostrar(estado(1) { copy(generando = true) })
        rule.onNodeWithText("Preparando…").assertIsDisplayed()
        rule.onNodeWithTag(EtiquetasDeHoja.TITULO).assertIsNotEnabled()
    }

    @Test fun `un error se ve y el boton sigue disponible para reintentar`() {
        mostrar(estado(1) { copy(error = "No se pudieron preparar las imágenes") })
        rule.onNodeWithText("No se pudieron preparar las imágenes").assertIsDisplayed()
        rule.onNodeWithTag(EtiquetasDeHoja.BOTON).assertIsEnabled()
    }

    @Test fun `sin vista previa todavia hay indicador y la zona de vista previa existe`() {
        mostrar(estado(1))
        rule.onNodeWithTag(EtiquetasDeHoja.VISTA_PREVIA).assertIsDisplayed()
    }

    @Test fun `la barra de seleccion muestra Compartir con el conteo y Cancelar`() {
        var compartir = 0
        var cancelar = 0
        rule.setContent { MaterialTheme { BarraDeSeleccion(3, { compartir++ }, { cancelar++ }) } }
        rule.onNodeWithText("Compartir (3)").assertIsDisplayed().performClick()
        rule.onNodeWithText("Cancelar").assertIsDisplayed().performClick()
        assertEquals(1 to 1, compartir to cancelar)
    }

    @Test fun `la barra con cero no deja compartir pero deja cancelar`() {
        rule.setContent { MaterialTheme { BarraDeSeleccion(0, {}, {}) } }
        rule.onNodeWithTag(EtiquetasDeSeleccion.COMPARTIR).assertIsNotEnabled()
        rule.onNodeWithTag(EtiquetasDeSeleccion.CANCELAR).assertIsEnabled()
    }

    @Test fun `la barra con el maximo dice Compartir (30)`() {
        rule.setContent { MaterialTheme { BarraDeSeleccion(30, {}, {}) } }
        rule.onNodeWithText("Compartir (30)").assertIsDisplayed()
    }
}
