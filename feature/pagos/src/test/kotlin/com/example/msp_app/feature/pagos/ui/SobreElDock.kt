package com.example.msp_app.feature.pagos.ui

import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performScrollTo
import com.example.msp_app.feature.pagos.ui.components.AIRE_DEL_DOCK_TAG

/**
 * **Deja todo el contenido restante ARRIBA de la barra de acciones.**
 *
 * ## Por qué hace falta desde el rediseño, y qué se rompía sin esto
 *
 * El dock dejó de ser un hermano en un `Column` y pasó a estar **encima** del
 * contenido, que es lo que hace que se pueda ver algo por detrás de su
 * degradado. La consecuencia para las pruebas es concreta y costó ocho tests en
 * rojo: **`performScrollTo()` ya no garantiza que un nodo se pueda tocar.**
 *
 * `performScrollTo()` desplaza **lo mínimo** para que el nodo entre en la
 * ventana del `verticalScroll`, y esa ventana ahora llega hasta el borde de
 * abajo de la pantalla. Un renglón cerca del final entra en la ventana, la
 * prueba lo ve, `assertHasClickAction()` pasa… y `performClick()` toca un punto
 * que está **por detrás de los botones del dock**, así que el toque se lo lleva
 * el dock. El síntoma no es un error: es un callback que nunca se llamó y un
 * `expected:<…> but was:<null>`.
 *
 * ## Por qué desplazar hasta el hueco y no "un poco más"
 *
 * Porque el hueco mide **exactamente lo que la barra tapa** — el contenido lo
 * reserva con el alto que la barra midió de sí misma, que no es constante (89 dp
 * a letra normal, 133 a las grandes, porque el dock se apila). Al traerlo a la
 * vista, cualquier nodo que esté antes queda por fuerza arriba del canto de la
 * barra. Un `swipeUp()` a ojo daría lo mismo hoy y otra cosa el día que el dock
 * gane un botón.
 */
fun ComposeContentTestRule.desplazaSobreElDock() {
    onNodeWithTag(AIRE_DEL_DOCK_TAG).performScrollTo()
}
