package com.example.msp_app.core.navegacion.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.height
import com.example.msp_app.core.designsystem.theme.MspTheme
import com.example.msp_app.core.navegacion.domain.model.IconoDelMenu
import com.example.msp_app.core.testing.RobolectricTestBase
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.robolectric.annotation.GraphicsMode

/**
 * Ruling R13 del controlador (2026-10-06, revisión de la Task 5): la etiqueta
 * de una opción NUNCA se parte a media palabra. Hallazgo real: con el
 * contador "8 por volver" y poco ancho (escala de letra 2.0 en los goldens
 * `menu_*_2_0`), "Clientes" se partía en "Client" / "es" porque el contador —
 * hermano fijo de la etiqueta en el mismo `Row` — le quitaba ancho hasta que
 * Compose no tenía dónde meter la palabra completa.
 *
 * Estas pruebas fijan el ancho de la fila directamente en vez de forzar la
 * escala de letra: el ancho disponible es la causa exacta, no el tamaño de
 * fuente en sí — y fijar el ancho hace la prueba determinista sin depender de
 * métricas de fuente que puedan cambiar.
 *
 * `@GraphicsMode(NATIVE)` es obligatorio aquí: el arreglo decide con
 * `TextMeasurer`, y en modo LEGACY de Robolectric (el default, el que usa
 * `MenuLateralTest`) esas medidas salen degeneradas (se midió "Clientes" en
 * 8px) — cualquier prueba de ESTE componente que dependa de medir texto real
 * necesita NATIVE, aunque otras pruebas del módulo no lo necesiten.
 */
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class FilaDelMenuTest : RobolectricTestBase() {

    @get:Rule
    val compose = createComposeRule()

    private fun montar(ancho: Dp, contador: String?) {
        compose.setContent {
            MspTheme(animateColors = false) {
                Box(Modifier.width(ancho)) {
                    FilaDelMenu(
                        etiqueta = "Clientes",
                        icono = IconoDelMenu.CLIENTES,
                        activo = false,
                        tag = "fila_clientes",
                        onClick = {},
                        contador = contador
                    )
                }
            }
        }
    }

    // (a) Poco ancho: la etiqueta no se parte y el contador baja de renglón.
    @Test
    fun `contador largo y poco ancho bajan el contador sin partir la palabra`() {
        montar(ancho = 180.dp, contador = "8 por volver")

        val limitesEtiqueta = compose.onNodeWithText(
            "Clientes",
            useUnmergedTree = true
        ).getBoundsInRoot()
        val limitesContador = compose.onNodeWithText(
            "8 por volver",
            useUnmergedTree = true
        ).getBoundsInRoot()

        // Una sola línea: si "Clientes" se hubiera partido en 2 renglones (a
        // media palabra o no) el alto de su caja crecería muy por encima de
        // una línea de `MspTheme.type.body` (13sp, lh ~18dp a escala 1.0).
        assertTrue(
            "la etiqueta debe ocupar una sola línea, alto medido = ${limitesEtiqueta.height}",
            limitesEtiqueta.height < 30.dp
        )
        // El contador queda en su propio renglón, debajo de la etiqueta y
        // alineado con ella a la izquierda (mismo arranque horizontal).
        assertTrue(
            "el contador debe quedar por debajo de la etiqueta (top mayor)",
            limitesContador.top > limitesEtiqueta.top
        )
        assertTrue(
            "el contador debe empezar donde termina la etiqueta, no superponerse",
            limitesContador.top >= limitesEtiqueta.bottom
        )
        assertTrue(
            "el contador se alinea a la izquierda con la etiqueta",
            limitesContador.left == limitesEtiqueta.left
        )
    }

    // (b) Ancho de sobra (sin regresión): el contador sigue en la misma línea.
    @Test
    fun `con ancho sobrado el contador sigue junto a la etiqueta en la misma linea`() {
        montar(ancho = 320.dp, contador = "8 por volver")

        val limitesEtiqueta = compose.onNodeWithText(
            "Clientes",
            useUnmergedTree = true
        ).getBoundsInRoot()
        val limitesContador = compose.onNodeWithText(
            "8 por volver",
            useUnmergedTree = true
        ).getBoundsInRoot()

        assertTrue(
            "la etiqueta debe ocupar una sola línea, alto medido = ${limitesEtiqueta.height}",
            limitesEtiqueta.height < 30.dp
        )
        // Misma línea: el contador (más bajo, `caption`) queda centrado DENTRO
        // del alto de la etiqueta, no debajo de ella.
        assertTrue(
            "en la misma línea el contador no debe empezar antes que la etiqueta",
            limitesContador.top >= limitesEtiqueta.top
        )
        assertTrue(
            "en la misma línea el contador no debe terminar después que la etiqueta",
            limitesContador.bottom <= limitesEtiqueta.bottom
        )
    }
}
