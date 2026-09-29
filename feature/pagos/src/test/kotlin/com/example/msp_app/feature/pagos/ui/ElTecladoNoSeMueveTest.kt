package com.example.msp_app.feature.pagos.ui

import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.height
import androidx.compose.ui.unit.width
import com.example.msp_app.core.designsystem.theme.FontSizeLevel
import com.example.msp_app.core.designsystem.theme.LocalFontSizeLevel
import com.example.msp_app.core.designsystem.theme.MspTheme
import com.example.msp_app.core.testing.RobolectricTestBase
import com.example.msp_app.feature.pagos.ui.components.AGREGAR_FOTO_TAG
import com.example.msp_app.feature.pagos.ui.components.ATRAS_TAG
import com.example.msp_app.feature.pagos.ui.components.AVISO_TAG
import com.example.msp_app.feature.pagos.ui.components.BLOQUEO_TAG
import com.example.msp_app.feature.pagos.ui.components.CUOTA_DUDOSA_TAG
import com.example.msp_app.feature.pagos.ui.components.DESPLEGABLE_DEL_PRODUCTO_TAG
import com.example.msp_app.feature.pagos.ui.components.FALLO_FOTO_TAG
import com.example.msp_app.feature.pagos.ui.components.FOTO_CONTADOR_TAG
import com.example.msp_app.feature.pagos.ui.components.FOTO_TAG
import com.example.msp_app.feature.pagos.ui.components.FRANJA_SALDO_NUEVO_TAG
import com.example.msp_app.feature.pagos.ui.components.FRANJA_TAG
import com.example.msp_app.feature.pagos.ui.components.HOJA_DE_FOTOS_TAG
import com.example.msp_app.feature.pagos.ui.components.METODO_TAG
import com.example.msp_app.feature.pagos.ui.components.NOMBRE_DEL_CLIENTE_TAG
import com.example.msp_app.feature.pagos.ui.components.PRODUCTO_TAG
import com.example.msp_app.feature.pagos.ui.components.QUITAR_FOTO_TAG
import com.example.msp_app.feature.pagos.ui.components.SALDO_DE_LA_VENTA_TAG
import com.example.msp_app.feature.pagos.ui.components.TECLA_BORRAR
import com.example.msp_app.feature.pagos.ui.components.TECLA_PUNTO
import com.example.msp_app.feature.pagos.ui.components.TECLA_TAG
import com.example.msp_app.feature.pagos.ui.components.VELO_DE_FOTOS_TAG
import kotlin.math.abs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * **El teclado se ve entero al entrar y no se mueve** (mock
 * `docs/design/mocks/registrar-abono-fijo.html`, aprobado por el dueño el
 * 2026-09-29).
 *
 * El defecto que trajo esto, medido en los goldens de antes: con un aviso de un
 * renglón el teclado bajaba **48 dp** (de 404 a 452) y su última fila —el punto,
 * el 0 y borrar— quedaba debajo del botón; a 1.5× y 2.0× el teclado ni siquiera
 * se veía al entrar. La regla del dueño: *"siempre el teclado numérico se vea
 * completo cuando se entra y no se ha hecho scroll aún"*.
 *
 * Todo se mide con cajas reales (`getUnclippedBoundsInRoot`) contra la raíz, y
 * cada medida lleva su control positivo: la raíz mide lo que la configuración
 * dice (744 o 640 dp) y cada caja medida tiene alto mayor que cero — una caja
 * vacía "cabría" en cualquier pantalla.
 *
 * `h800dp` y `h696dp` dan una raíz de 744 y 640 dp: Robolectric descuenta las
 * barras del sistema (es el mismo 720 × 1488 px de los goldens).
 *
 * Con gráficos nativos (`GraphicsMode.NATIVE`), como los goldens: sin ellos
 * Robolectric no mide texto de verdad —un "$1,450" mide 7 px— y ni el
 * desborde del nombre del producto ni lo que cede la zona de arriba serían los
 * del teléfono.
 */
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h800dp-xhdpi")
class ElTecladoNoSeMueveTest : RobolectricTestBase() {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    private var estado by mutableStateOf(AbonoFixtures.enCaptura())
    private var nivel by mutableStateOf(FontSizeLevel.NORMAL)

    private var fotosPedidas = 0
    private val fotosQuitadas = mutableListOf<String>()
    private var revisiones = 0

    // --- 1. El teclado entero y el botón, sin desplazar ----------------------

    @Test
    fun `a 744 dp el teclado entero y el boton caben sin desplazar, con y sin aviso`() {
        cabeEnteroEn(alto = 744.dp)
    }

    @Test
    @Config(qualifiers = "w360dp-h696dp-xhdpi")
    fun `a 640 dp el teclado entero y el boton caben sin desplazar, con y sin aviso`() {
        cabeEnteroEn(
            alto = 640.dp,
            niveles = listOf(FontSizeLevel.NORMAL, FontSizeLevel.MUY_GRANDE)
        )
    }

    private fun cabeEnteroEn(alto: Dp, niveles: List<FontSizeLevel> = FontSizeLevel.entries) {
        pinta()
        val raiz = composeTestRule.onRoot().getUnclippedBoundsInRoot()
        assertEquals(
            "control positivo: la raíz mide lo que dice la configuración",
            alto,
            raiz.height
        )
        val fallas = niveles.flatMap { n ->
            ESTADOS.flatMap { (nombre, fixture) -> fallasDelTeclado(n, nombre, fixture(), raiz) }
        }
        assertEquals(emptyList<String>(), fallas)
    }

    private fun fallasDelTeclado(
        n: FontSizeLevel,
        nombre: String,
        fixture: RegistrarAbonoUiState,
        raiz: DpRect
    ): List<String> {
        estado = fixture
        nivel = n
        composeTestRule.waitForIdle()
        val boton = boton()
        val ultima = listOf(TECLA_PUNTO, "0", TECLA_BORRAR).map { caja(TECLA_TAG + it) }
        val donde = "${n.nominalScale}× $nombre"
        // La medida queda en la salida de la prueba: es la evidencia del reporte.
        println(
            "MEDIDA ${raiz.height} $donde: teclado desde ${caja(TECLA_TAG + "1").top}, " +
                "última fila hasta ${ultima.maxOf { it.bottom }}, botón hasta ${boton.bottom}"
        )
        (ultima + boton).forEach {
            assertTrue("control positivo ($donde): la caja medida tiene alto", it.height > 0.dp)
        }
        val fallas = mutableListOf<String>()
        ultima.forEach { tecla ->
            if (tecla.bottom > raiz.bottom + TOLERANCIA) {
                fallas += "$donde: la última fila termina en ${tecla.bottom} de ${raiz.bottom}"
            }
            if (tecla.bottom > boton.top + TOLERANCIA) {
                fallas += "$donde: la última fila (hasta ${tecla.bottom}) queda debajo del " +
                    "botón (desde ${boton.top})"
            }
        }
        if (boton.bottom > raiz.bottom + TOLERANCIA) {
            fallas += "$donde: el botón termina en ${boton.bottom} de ${raiz.bottom}"
        }
        return fallas
    }

    // --- 2. El teclado no se mueve con el aviso ------------------------------

    @Test
    fun `la primera fila del teclado no se mueve entre sin aviso y con aviso`() {
        pinta()
        val fallas = mutableListOf<String>()
        FontSizeLevel.entries.forEach { n ->
            nivel = n
            estado = AbonoFixtures.enCaptura()
            composeTestRule.waitForIdle()
            val base = caja(TECLA_TAG + "1")
            assertTrue("control positivo: la tecla 1 tiene alto", base.height > 0.dp)
            ESTADOS.drop(1).forEach { (nombre, fixture) ->
                estado = fixture()
                composeTestRule.waitForIdle()
                val ahora = caja(TECLA_TAG + "1")
                if (abs((ahora.top - base.top).value) > TOLERANCIA.value) {
                    fallas += "${n.nominalScale}× $nombre: la tecla 1 pasó de ${base.top} a ${ahora.top}"
                }
            }
        }
        assertEquals(emptyList<String>(), fallas)
    }

    // --- 3. La cabecera -------------------------------------------------------

    @Test
    fun `la cabecera es solo el cliente, sin Abono, sin flecha y sin folio`() {
        pinta()
        composeTestRule.onNodeWithTag(NOMBRE_DEL_CLIENTE_TAG)
            .assertIsDisplayed()
            .assertTextEquals("Victoria Flores Olmedo")
        assertEquals(
            "sin el rótulo Abono",
            0,
            composeTestRule.onAllNodesWithText("Abono").fetchSemanticsNodes().size
        )
        assertEquals(
            "sin flecha de volver",
            0,
            composeTestRule.onAllNodesWithTag(ATRAS_TAG).fetchSemanticsNodes().size
        )
        assertEquals(
            "sin el folio de la venta",
            0,
            composeTestRule.onAllNodesWithText(
                "V-5188",
                substring = true
            ).fetchSemanticsNodes().size
        )
        composeTestRule.onNodeWithTag(
            SALDO_DE_LA_VENTA_TAG
        ).assertIsDisplayed().assertTextEquals("$1,450")
    }

    @Test
    fun `sin hojas abiertas la pantalla no le quita el atras al sistema`() {
        pinta()
        assertFalse(
            "un BackHandler vivo se comería el gesto de atrás",
            composeTestRule.activity.onBackPressedDispatcher.hasEnabledCallbacks()
        )
        // Control positivo: con la hoja de fotos abierta SÍ hay quien atienda el
        // atrás, así que la consulta de arriba sabe ver un BackHandler.
        estado = AbonoFixtures.enCapturaConComprobantes()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag(FOTO_TAG).performClick()
        composeTestRule.waitForIdle()
        assertTrue(composeTestRule.activity.onBackPressedDispatcher.hasEnabledCallbacks())
        composeTestRule.activity.onBackPressedDispatcher.onBackPressed()
        composeTestRule.waitForIdle()
        assertEquals(
            0,
            composeTestRule.onAllNodesWithTag(HOJA_DE_FOTOS_TAG).fetchSemanticsNodes().size
        )
    }

    @Test
    fun `el producto largo va en dos renglones y se despliega entero encima, sin mover el teclado`() {
        estado = AbonoFixtures.conProductoLargo()
        pinta()
        val antes = caja(TECLA_TAG + "1")
        val producto = caja(PRODUCTO_TAG)
        assertTrue("control positivo: la tarjeta del producto tiene alto", producto.height > 0.dp)
        assertEquals(
            0,
            composeTestRule.onAllNodesWithTag(
                DESPLEGABLE_DEL_PRODUCTO_TAG
            ).fetchSemanticsNodes().size
        )
        composeTestRule.onNodeWithTag(PRODUCTO_TAG).performClick()
        composeTestRule.onNodeWithTag(DESPLEGABLE_DEL_PRODUCTO_TAG)
            .assertIsDisplayed()
            .assert(hasAnyDescendant(hasText("Recamara cantaro king size chocolate")))
            .assert(hasAnyDescendant(hasText("Refrigerador Mabe 14'")))
        assertEquals("el desplegable no empuja el teclado", antes.top, caja(TECLA_TAG + "1").top)
        val desplegable = caja(DESPLEGABLE_DEL_PRODUCTO_TAG)
        assertTrue(
            "el desplegable no tapa el teclado",
            desplegable.bottom <= antes.top + TOLERANCIA
        )
    }

    // --- 4. La franja fija ----------------------------------------------------

    @Test
    fun `sin nada que decir la franja dice el saldo nuevo`() {
        pinta()
        composeTestRule.onNodeWithTag(FRANJA_SALDO_NUEVO_TAG).assertIsDisplayed()
        composeTestRule.onNodeWithText("Saldo nuevo $1,230").assertIsDisplayed()
    }

    @Test
    fun `la franja dice una sola cosa, en su lugar, con su prioridad`() {
        pinta()
        val franja = caja(FRANJA_TAG)
        assertTrue("control positivo: la franja tiene alto", franja.height > 0.dp)
        listOf(
            Triple("bloqueo", AbonoFixtures::enBloqueo, BLOQUEO_TAG),
            Triple("aviso en vivo", AbonoFixtures::enAvisoLargo, AVISO_TAG),
            Triple("parcialidad dudosa", AbonoFixtures::conCuotaDudosa, CUOTA_DUDOSA_TAG)
        ).forEach { (nombre, fixture, tag) ->
            estado = fixture()
            composeTestRule.waitForIdle()
            val mensaje = caja(tag)
            val ahora = caja(FRANJA_TAG)
            assertTrue(
                "$nombre ocupa la franja (${ahora.top} vs ${mensaje.top})",
                abs((ahora.top - mensaje.top).value) <= TOLERANCIA.value
            )
            // La franja termina donde terminaba: si el mensaje es más largo que
            // lo reservado, cede la cifra (crece hacia ARRIBA), no los sugeridos
            // ni lo que está anclado.
            assertTrue(
                "$nombre termina donde terminaba la franja (${franja.bottom} vs ${ahora.bottom})",
                abs((ahora.bottom - franja.bottom).value) <= TOLERANCIA.value
            )
            assertEquals(
                "$nombre desplaza al saldo nuevo",
                0,
                composeTestRule.onAllNodesWithTag(FRANJA_SALDO_NUEVO_TAG).fetchSemanticsNodes().size
            )
        }
    }

    @Test
    fun `con la verificacion pendiente revisar toma el lugar del boton y el teclado se apaga`() {
        pinta()
        val lugar = boton()
        estado = AbonoFixtures.enDudaDeVerificacion()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("No se pudo confirmar, revisa de nuevo").assertIsDisplayed()
        assertEquals(
            "el botón muerto ya no se pinta",
            0,
            composeTestRule.onAllNodesWithTag(CTA_ABONO_TAG).fetchSemanticsNodes().size
        )
        val revisar = caja(REVISAR_DE_NUEVO_TAG)
        assertEquals("revisar ocupa el lugar del botón", lugar, revisar)
        composeTestRule.onNodeWithTag(REVISAR_DE_NUEVO_TAG).performClick()
        assertEquals(1, revisiones)
        composeTestRule.onNodeWithTag(TECLA_TAG + "7").assertIsNotEnabled()
        composeTestRule.onNodeWithTag(TECLA_TAG + TECLA_BORRAR).assertIsNotEnabled()
    }

    // --- 5. La foto -----------------------------------------------------------

    @Test
    fun `el boton Foto vive en la fila del metodo y sin fotos va directo a agregar`() {
        pinta()
        val foto = caja(FOTO_TAG)
        val metodo = caja(METODO_TAG + "efectivo")
        assertTrue("control positivo: el botón Foto tiene alto", foto.height > 0.dp)
        assertEquals("en la misma fila que el método", metodo.top, foto.top)
        assertTrue("y se toca: ${foto.height}", foto.height >= 50.dp && foto.width >= 50.dp)
        composeTestRule.onNodeWithTag(FOTO_TAG).performClick()
        assertEquals("sin comprobantes abre de una vez el origen", 1, fotosPedidas)
        assertEquals(
            0,
            composeTestRule.onAllNodesWithTag(HOJA_DE_FOTOS_TAG).fetchSemanticsNodes().size
        )
    }

    @Test
    fun `con fotos el boton las cuenta, avisa el fallo y abre la rejilla de siempre`() {
        estado = AbonoFixtures.enCapturaConComprobantes()
        pinta()
        composeTestRule.onNodeWithTag(FOTO_CONTADOR_TAG, useUnmergedTree = true)
            .assertTextEquals("3")
            .assert(
                SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Uno no entró")
            )
        composeTestRule.onNodeWithTag(FOTO_TAG).performClick()
        composeTestRule.onNodeWithTag(HOJA_DE_FOTOS_TAG).assertIsDisplayed()
        composeTestRule.onNodeWithTag(FALLO_FOTO_TAG + "ARCH-9").assertIsDisplayed()
        composeTestRule.onNodeWithTag(QUITAR_FOTO_TAG + "IMG-2").performClick()
        assertEquals(listOf("IMG-2"), fotosQuitadas)
        composeTestRule.onNodeWithTag(AGREGAR_FOTO_TAG).performClick()
        assertEquals(1, fotosPedidas)
        // El velo cierra la hoja.
        composeTestRule.onNodeWithTag(VELO_DE_FOTOS_TAG).performClick()
        assertEquals(
            0,
            composeTestRule.onAllNodesWithTag(HOJA_DE_FOTOS_TAG).fetchSemanticsNodes().size
        )
    }

    @Test
    fun `control positivo - sin fallo el contador no avisa, y lleno ya no ofrece agregar`() {
        estado = AbonoFixtures.comprobantesLlenos()
        pinta()
        composeTestRule.onNodeWithTag(FOTO_CONTADOR_TAG, useUnmergedTree = true)
            .assertTextEquals("5")
            .assert(SemanticsMatcher.keyNotDefined(SemanticsProperties.StateDescription))
        composeTestRule.onNodeWithTag(FOTO_TAG).performClick()
        composeTestRule.onNodeWithTag(HOJA_DE_FOTOS_TAG).assertIsDisplayed()
        assertEquals(
            0,
            composeTestRule.onAllNodesWithTag(AGREGAR_FOTO_TAG).fetchSemanticsNodes().size
        )
    }

    // --- Plomería ---------------------------------------------------------------

    private fun boton(): DpRect {
        val cta = composeTestRule.onAllNodesWithTag(CTA_ABONO_TAG).fetchSemanticsNodes()
        val tag = if (cta.isNotEmpty()) CTA_ABONO_TAG else REVISAR_DE_NUEVO_TAG
        return caja(tag)
    }

    private fun caja(tag: String): DpRect = composeTestRule.onNodeWithTag(
        tag
    ).getUnclippedBoundsInRoot()

    private fun pinta() {
        composeTestRule.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(
                LocalDensity provides Density(density.density, nivel.nominalScale),
                LocalFontSizeLevel provides nivel
            ) {
                MspTheme(darkTheme = false, animateColors = false) {
                    RegistrarAbonoContent(
                        state = estado,
                        onAtras = {},
                        onDigito = {},
                        onPunto = {},
                        onBorrar = {},
                        onMetodo = {},
                        onSugerido = {},
                        onRegistrar = {},
                        onConfirmar = {},
                        onEditar = {},
                        onRevisar = { revisiones += 1 },
                        onAgregarFoto = { fotosPedidas += 1 },
                        onOrigen = {},
                        onCerrarOrigenes = {},
                        onQuitarFoto = { fotosQuitadas += it }
                    )
                }
            }
        }
        composeTestRule.waitForIdle()
    }

    private companion object {
        val TOLERANCIA: Dp = 1.dp

        /** Sin aviso primero: es la referencia del test de "no se mueve". */
        val ESTADOS: List<Pair<String, () -> RegistrarAbonoUiState>> = listOf(
            "sin aviso" to { AbonoFixtures.enCaptura() },
            "aviso nivel 2 largo" to AbonoFixtures::enAvisoLargo,
            "aviso nivel 3" to AbonoFixtures::enAvisoDeAfirmar,
            "bloqueo" to AbonoFixtures::enBloqueo
        )
    }
}
