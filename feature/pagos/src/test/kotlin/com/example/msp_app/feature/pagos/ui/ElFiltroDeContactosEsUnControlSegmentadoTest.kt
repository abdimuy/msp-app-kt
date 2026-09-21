package com.example.msp_app.feature.pagos.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.example.msp_app.core.common.cobranza.domain.EstadoCuenta
import com.example.msp_app.core.designsystem.theme.FontSizeLevel
import com.example.msp_app.core.designsystem.theme.LocalFontSizeLevel
import com.example.msp_app.core.designsystem.theme.MspTheme
import com.example.msp_app.core.testing.RobolectricTestBase
import com.example.msp_app.feature.pagos.domain.FiltroDeContactos
import com.example.msp_app.feature.pagos.domain.model.ContactoDeCobranza
import com.example.msp_app.feature.pagos.domain.model.TipoDeContacto
import com.example.msp_app.feature.pagos.ui.components.FILTRO_TAG
import com.example.msp_app.feature.pagos.ui.components.FiltrosDeContacto
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.robolectric.annotation.Config

/**
 * **Task 4 — el filtro de contactos es UN control segmentado, con conteo
 * debajo de cada opción, y el cero se enseña igual.**
 *
 * Lo que cobra este archivo y no cobra ningún otro:
 *
 * 1. **El conteo pintado es la MISMA regla que la lista.** `FiltroDeContactos
 *    .conteos` ya está probado en `GruposYFiltrosDeContactosTest` como puro
 *    dominio; aquí se cierra el círculo: el número que aparece DEBAJO de cada
 *    opción del control real es exactamente cuántas filas quedan al elegirla.
 *    Si el conteo usara otra consulta, esto lo pondría rojo aunque el dominio
 *    siguiera en verde.
 * 2. **El cero no se esconde.** `PROMESAS` con conteo 0 se pinta, se puede
 *    tocar y cambia el filtro elegido — no hay rama que la deshabilite ni la
 *    quite de la fila. Es la decisión cerrada del dueño.
 * 3. **El toque sigue siendo >=50dp**, a NORMAL y a MUY_GRANDE — el mismo piso
 *    que ya cobra `ListaSeVeYSeTocaTest` para `SegmentadoDeCobranza`, medido
 *    aquí sobre el segundo llamador del mismo `ControlSegmentado`
 *    (`PiezasDeLaLista.kt`) para que compartir el mecanismo no dé por hecho
 *    que el segundo sitio también lo cumple.
 */
@Config(qualifiers = "w360dp-h800dp-xhdpi")
class ElFiltroDeContactosEsUnControlSegmentadoTest : RobolectricTestBase() {

    @get:Rule
    val composeTestRule = createComposeRule()

    private fun pinta(nivel: FontSizeLevel = FontSizeLevel.NORMAL) {
        composeTestRule.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(
                LocalDensity provides Density(density.density, nivel.nominalScale),
                LocalFontSizeLevel provides nivel
            ) {
                MspTheme(darkTheme = false, animateColors = false) {
                    FiltrosDeContacto(
                        elegido = FiltroDeContactos.TODOS,
                        conteos = FiltroDeContactos.conteos(CONTACTOS),
                        onElegir = {}
                    )
                }
            }
        }
    }

    @Test
    fun `las cuatro opciones se pintan con su conteo, incluida Promesas en cero`() {
        pinta()
        FiltroDeContactos.entries.forEach { filtro ->
            composeTestRule.onNodeWithTag(FILTRO_TAG + filtro.name).assertIsDisplayed()
        }
        composeTestRule
            .onNodeWithTag(FILTRO_TAG + FiltroDeContactos.PROMESAS.name)
            .assertTextContains("0")
    }

    /**
     * **Ronda de arreglo 1, Task 4 — accesibilidad.** El contenedor es un
     * grupo de selección (`selectableGroup`, sobre `ControlSegmentado`) y
     * cada opción expone su propio `selected`: un lector de pantalla sabe
     * cuál está elegida sin depender del color. `Segmento` usaba antes
     * `.clickable` sin rol ni estado — el mock pedía el equivalente de
     * `role="group"` + `aria-pressed`, y esto es lo más cercano en Compose
     * (`Role.RadioButton` + `selected`, sobre `selectableGroup`).
     */
    @Test
    fun `el filtro elegido expone seleccionado y los demas no`() {
        pinta()
        composeTestRule.onNodeWithTag(FILTRO_TAG + FiltroDeContactos.TODOS.name)
            .assertIsSelected()
        FiltroDeContactos.entries.filter { it != FiltroDeContactos.TODOS }.forEach { filtro ->
            composeTestRule.onNodeWithTag(FILTRO_TAG + filtro.name).assertIsNotSelected()
        }
    }

    /**
     * **Una sola cosa, no dos fragmentos sueltos.** `.selectable()` fusiona
     * el texto de sus descendientes en el mismo nodo semántico que antes
     * fusionaba `.clickable()` — el lector de pantalla lee "Promesas, 0", no
     * "Promesas" y "0" por separado. Se afirma sobre el nodo MERGED (por
     * defecto, sin `useUnmergedTree`): si algún día el rótulo o el conteo
     * quedaran en un nodo aparte sin fusionar, esto se pone rojo.
     */
    @Test
    fun `cada segmento se lee como una sola cosa - etiqueta y conteo juntos`() {
        pinta()
        FiltroDeContactos.entries.forEach { filtro ->
            val nodo = composeTestRule.onNodeWithTag(FILTRO_TAG + filtro.name)
            nodo.assertTextContains(filtro.etiqueta)
            nodo.assertTextContains((FiltroDeContactos.conteos(CONTACTOS)[filtro] ?: 0).toString())
        }
    }

    @Test
    fun `cada opcion mide al menos 50dp de alto`() {
        pinta()
        medirLasCuatro()
    }

    /**
     * **Los CUATRO, no solo el primero** — igual que `ListaSeVeYSeTocaTest`:
     * a `MUY_GRANDE` el control deja de repartir el ancho y rueda, y el riesgo
     * es que un segmento fuera del viewport se mida distinto del que se ve.
     */
    @Test
    fun `a escala muy grande las cuatro opciones siguen siendo tocables`() {
        pinta(FontSizeLevel.MUY_GRANDE)
        medirLasCuatro()
    }

    private fun medirLasCuatro() {
        FiltroDeContactos.entries.forEach { filtro ->
            val bordes = composeTestRule
                .onNodeWithTag(FILTRO_TAG + filtro.name)
                .getUnclippedBoundsInRoot()
            val alto = bordes.bottom - bordes.top
            assertTrue("la opción ${filtro.etiqueta} mide $alto", alto >= MINIMO_TOCABLE)
        }
    }

    /**
     * **El corazón de la Task 4.** Por cada filtro: tocarlo dentro de la
     * MISMA composición y contar cuántas filas de [CONTACTOS] quedan, y
     * comparar contra el conteo que el control pintó ANTES de tocar nada
     * (`FiltroDeContactos.conteos(CONTACTOS)`, la fuente de verdad —nunca una
     * consulta aparte). Si `Segmento` pintara un número distinto del que
     * produce `deja`, este test se pone rojo aunque los dos números por
     * separado se vieran razonables.
     */
    @Test
    fun `el conteo de cada opcion es igual al numero de filas que deja ver al elegirla`() {
        val conteos = FiltroDeContactos.conteos(CONTACTOS)
        composeTestRule.setContent {
            MspTheme(darkTheme = false, animateColors = false) {
                var elegido by remember { mutableStateOf(FiltroDeContactos.TODOS) }
                Column {
                    FiltrosDeContacto(
                        elegido = elegido,
                        conteos = conteos,
                        onElegir = { elegido = it }
                    )
                    CONTACTOS.filter(elegido::deja).forEach {
                        Text(
                            text = it.etiqueta,
                            modifier = Modifier.testTag(FILA_VISIBLE_TAG)
                        )
                    }
                }
            }
        }

        FiltroDeContactos.entries.forEach { filtro ->
            composeTestRule.onNodeWithTag(FILTRO_TAG + filtro.name).performClick()
            val filas = composeTestRule.onAllNodesWithTag(
                FILA_VISIBLE_TAG
            ).fetchSemanticsNodes().size
            assertEquals(
                "el filtro ${filtro.etiqueta} pintó ${conteos[filtro]} pero deja ver $filas filas",
                conteos[filtro],
                filas
            )
        }
    }

    private companion object {
        val MINIMO_TOCABLE = 50.dp

        const val FILA_VISIBLE_TAG = "test_fila_visible"

        fun cobro(cuando: String) = ContactoDeCobranza(
            id = "cobro-$cuando",
            fecha = Instant.parse(cuando),
            etiqueta = "Abono",
            nota = null,
            estado = EstadoCuenta.PAGO,
            importe = null,
            tipo = TipoDeContacto.COBRO
        )

        fun visita(cuando: String) = ContactoDeCobranza(
            id = "visita-$cuando",
            fecha = Instant.parse(cuando),
            etiqueta = "No estaba",
            nota = null,
            estado = EstadoCuenta.VISITE_VUELVO,
            importe = null,
            tipo = TipoDeContacto.VISITA
        )

        /**
         * Dos cobros, tres visitas, **ninguna promesa** — así `PROMESAS` da
         * cero sin que el fixture lo declare a mano: sale de que ningún
         * estado de la lista está en `COMPROMETEN_UNA_FECHA`.
         */
        val CONTACTOS = listOf(
            cobro("2026-09-18T16:42:00Z"),
            cobro("2026-09-10T15:20:00Z"),
            visita("2026-09-17T14:00:00Z"),
            visita("2026-09-12T13:00:00Z"),
            visita("2026-09-03T14:15:00Z")
        )
    }
}
