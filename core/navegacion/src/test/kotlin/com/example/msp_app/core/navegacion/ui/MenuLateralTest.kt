package com.example.msp_app.core.navegacion.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.msp_app.core.designsystem.component.DESCRIPCION_A_CLARO
import com.example.msp_app.core.designsystem.component.DESCRIPCION_A_OSCURO
import com.example.msp_app.core.designsystem.theme.MspTheme
import com.example.msp_app.core.navegacion.MenuFixtures
import com.example.msp_app.core.navegacion.domain.model.ActivoEnElMenu
import com.example.msp_app.core.navegacion.domain.model.CabeceraDelMenu
import com.example.msp_app.core.navegacion.domain.model.ElementoDelMenu
import com.example.msp_app.core.navegacion.domain.model.SeccionDelMenu
import com.example.msp_app.core.navegacion.ui.components.PieDelMenu
import com.example.msp_app.core.testing.RobolectricTestBase
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.robolectric.annotation.Config

// La hoja completa (cabecera + 4 secciones + pie) no entra en el viewport
// chico por defecto de Robolectric: sin esto, secciones por debajo del
// "cobro" quedan fuera de la ventana real y ni se miden ni se tocan bien
// (mismo precedente que `ListaSeVeYSeTocaTest`).
@Config(qualifiers = "w360dp-h2400dp-xhdpi")
class MenuLateralTest : RobolectricTestBase() {

    @get:Rule
    val compose = createComposeRule()

    private val toques = mutableListOf<Pair<String, Boolean>>()
    private val elegidos = mutableListOf<ElementoDelMenu>()
    private var alternanciasDeTema = 0
    private var alternanciasDeMontos = 0

    // R1 (controlador, 2026-10-06): `plegadas` vive en un estado de la prueba y
    // se actualiza desde `onTogglePlegado`, tal como lo haría el ViewModel real
    // (el plan traía una prueba que no podía pasar con un valor fijo).
    @Suppress(
        "LongParameterList"
    ) // un parametro por entrada de MenuLateral; agruparlos los esconde de cada prueba.
    private fun montar(
        secciones: List<SeccionDelMenu> = MenuFixtures.todas,
        activo: ActivoEnElMenu = ActivoEnElMenu("COBRO", "clientes"),
        plegadasIniciales: Set<String> = emptySet(),
        cabecera: CabeceraDelMenu = MenuFixtures.cabecera(),
        alto: Dp = 2_000.dp,
        apertura: AperturaDelMenu = AperturaDelMenu(),
        temaOscuro: Boolean = false,
        montosOcultos: Boolean = false
    ) {
        compose.setContent {
            var plegadas by remember { mutableStateOf(plegadasIniciales) }
            MspTheme(animateColors = false) {
                Box(Modifier.width(ANCHO_DEL_MENU).height(alto)) {
                    MenuLateral(
                        cabecera = cabecera,
                        secciones = secciones,
                        activo = activo,
                        seccionesPlegadas = plegadas,
                        apertura = apertura,
                        onTogglePlegado = { id, plegar ->
                            toques += id to plegar
                            plegadas = if (plegar) plegadas + id else plegadas - id
                        },
                        onElegir = { elegidos += it },
                        temaOscuro = temaOscuro,
                        montosOcultos = montosOcultos,
                        onAlternarTema = { alternanciasDeTema++ },
                        onAlternarMontos = { alternanciasDeMontos++ },
                        pie = { PieDelMenu(version = "Versión 2.19.3", onConfiguracion = {}) }
                    )
                }
            }
        }
    }

    @Test
    fun `resalta solo la opcion activa`() {
        montar()
        compose.onNodeWithTag(FILA_DEL_MENU_TAG + "clientes").assertIsSelected()
        compose.onNodeWithTag(FILA_DEL_MENU_TAG + "inicio").assertIsNotSelected()
    }

    @Test
    fun `tocar una opcion la entrega a onElegir`() {
        montar()
        compose.onNodeWithTag(FILA_DEL_MENU_TAG + "mapa_rutas").performClick()
        assertEquals(listOf("mapa_rutas"), elegidos.map { it.id })
    }

    @Test
    fun `plegar una seccion abierta pide plegar con su id`() {
        montar()
        compose.onNodeWithTag(SECCION_DEL_MENU_TAG + "VENTAS").performClick()
        assertEquals(listOf("VENTAS" to true), toques)
        compose.onNodeWithTag(FILA_DEL_MENU_TAG + "ventas").assertDoesNotExist()
    }

    @Test
    fun `una seccion plegada no pinta sus opciones y desplegarla pide plegar false`() {
        montar(plegadasIniciales = setOf("ALMACEN"))
        compose.onNodeWithTag(FILA_DEL_MENU_TAG + "traspasos").assertDoesNotExist()
        compose.onNodeWithTag(SECCION_DEL_MENU_TAG + "ALMACEN").performClick()
        assertEquals(listOf("ALMACEN" to false), toques)
    }

    @Test
    fun `la seccion activa se abre aunque este plegada y no escribe la preferencia`() {
        montar(plegadasIniciales = setOf("COBRO"))
        compose.onNodeWithTag(FILA_DEL_MENU_TAG + "clientes").assertExists()
        assertEquals(emptyList<Pair<String, Boolean>>(), toques)
    }

    @Test
    fun `tocar la seccion activa forzada la pliega en esta apertura`() {
        montar(plegadasIniciales = setOf("COBRO"))
        compose.onNodeWithTag(SECCION_DEL_MENU_TAG + "COBRO").performClick()
        compose.onNodeWithTag(FILA_DEL_MENU_TAG + "clientes").assertDoesNotExist()
        assertEquals(listOf("COBRO" to true), toques)
    }

    @Test
    fun `el buscador aparece con diez opciones`() {
        montar(secciones = MenuFixtures.todas)
        compose.onNodeWithTag(BUSCADOR_DEL_MENU_TAG).assertExists()
    }

    @Test
    fun `sin diez opciones no hay buscador`() {
        montar(secciones = MenuFixtures.nueve)
        compose.onNodeWithTag(BUSCADOR_DEL_MENU_TAG).assertDoesNotExist()
    }

    @Test
    fun `buscar filtra aunque la seccion este plegada`() {
        montar(plegadasIniciales = setOf("GARANTIAS"))
        compose.onNodeWithTag(BUSCADOR_DEL_MENU_TAG).performTextInput("garantias")
        compose.onNodeWithTag(FILA_DEL_MENU_TAG + "garantias").assertExists()
        compose.onNodeWithTag(FILA_DEL_MENU_TAG + "clientes").assertDoesNotExist()
    }

    @Test
    fun `buscar algo que no existe dice sin resultados`() {
        montar()
        compose.onNodeWithTag(BUSCADOR_DEL_MENU_TAG).performTextInput("nómina")
        compose.onNodeWithText("Sin resultados").assertExists()
    }

    @Test
    fun `el desvanecido aparece solo cuando hay mas por desplazar`() {
        montar(alto = 360.dp)
        compose.onNodeWithTag(DESVANECIDO_DEL_MENU_TAG).assertExists()
    }

    @Test
    fun `sin contenido fuera de vista no hay desvanecido`() {
        montar(secciones = listOf(MenuFixtures.garantias))
        compose.onNodeWithTag(DESVANECIDO_DEL_MENU_TAG).assertDoesNotExist()
    }

    @Test
    fun `cada fila y cada encabezado de seccion miden al menos el toque del repo`() {
        montar()
        compose.onNodeWithTag(FILA_DEL_MENU_TAG + "clientes").assertHeightIsAtLeast(56.dp)
        compose.onNodeWithTag(FILA_DEL_MENU_TAG + ID_CONFIGURACION).assertHeightIsAtLeast(56.dp)
        compose.onNodeWithTag(SECCION_DEL_MENU_TAG + "COBRO").assertHeightIsAtLeast(56.dp)
        compose.onNodeWithTag(BUSCADOR_DEL_MENU_TAG).assertHeightIsAtLeast(56.dp)
    }

    @Test
    fun `el contador se pinta junto a su opcion`() {
        montar()
        compose.onNodeWithText("8 por volver").assertExists()
    }

    @Test
    fun `la tarjeta de envio dice todo enviado con su tiempo`() {
        montar()
        compose.onNodeWithText("Todo enviado · hace 3 min").assertExists()
    }

    @Test
    fun `la tarjeta de envio dice cuantos pagos esperan`() {
        montar(cabecera = MenuFixtures.cabecera(envio = MenuFixtures.DOS_PAGOS_ESPERANDO))
        compose.onNodeWithText("2 pagos esperando señal").assertExists()
    }

    @Test
    fun `sin lectura de envio no hay tarjeta`() {
        montar(cabecera = MenuFixtures.cabecera(envio = null))
        compose.onNodeWithTag(ENVIO_DEL_MENU_TAG).assertDoesNotExist()
    }

    @Test
    fun `el pie lleva configuracion y la version`() {
        montar(secciones = emptyList())
        compose.onNodeWithText("Configuración").assertExists()
        compose.onNodeWithText("Versión 2.19.3").assertExists()
    }

    // R3: la cabecera pinta `nombreDeZona` si no es null; si es null no inventa
    // un respaldo "Zona N" (ni ningún otro texto).
    @Test
    fun `la cabecera pinta el nombre de zona cuando llega`() {
        montar(cabecera = MenuFixtures.cabecera())
        compose.onNodeWithText("R/25").assertExists()
    }

    @Test
    fun `sin nombre de zona la cabecera no inventa uno`() {
        montar(cabecera = MenuFixtures.cabecera().copy(nombreDeZona = null))
        compose.onNodeWithText("R/25").assertDoesNotExist()
    }

    // R2 (decisión del dueño): el encabezado lleva el botón de tema y el de
    // ocultar/mostrar dinero; existen con su descripción accesible y tocarlos
    // invoca su callback.
    @Test
    fun `el boton de tema existe con su descripcion y alterna al tocarlo`() {
        montar(temaOscuro = false)
        compose.onNodeWithContentDescription(DESCRIPCION_A_OSCURO).assertExists().performClick()
        assertEquals(1, alternanciasDeTema)
    }

    @Test
    fun `el boton de tema anuncia pasar a claro cuando ya esta oscuro`() {
        montar(temaOscuro = true)
        compose.onNodeWithContentDescription(DESCRIPCION_A_CLARO).assertExists()
    }

    @Test
    fun `el boton de montos existe y alterna al tocarlo`() {
        montar(montosOcultos = false)
        compose.onNodeWithTag("msp_privacy_eye_toggle").assertExists().performClick()
        assertEquals(1, alternanciasDeMontos)
    }
}
