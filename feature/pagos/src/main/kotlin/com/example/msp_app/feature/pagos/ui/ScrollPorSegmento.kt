package com.example.msp_app.feature.pagos.ui

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.listSaver

/**
 * Un [LazyListState] por [SegmentoDeCobranza] — cada chip de la lista de
 * clientes recuerda su propia posición — **y que sobrevive a salir de la
 * pantalla y volver**.
 *
 * Por qué existe (residual de la re-revisión final de
 * `feat/lista-venta-nueva-orden`, R20): el mapa vivía en un `remember`, que se
 * pierde al navegar a registrar un abono y regresar, o cuando Android recrea
 * la actividad. Al volver, sólo se restauraba el `listState` hospedado —el del
 * PRIMER segmento mostrado— y el mapa nuevo se lo asignaba al segmento
 * VISIBLE: "Después" aparecía con el scroll de "Sin visitar". Medido con
 * `StateRestorationTester` (`ScrollDeLaListaPorOrdenYSegmentoTest`).
 *
 * Se guarda con [Saver] en `rememberSaveable` — el mismo mecanismo que usa
 * `rememberLazyListState` —: por segmento, el índice y el desplazamiento del
 * primer elemento visible. Un segmento guardado con un nombre que ya no existe
 * (la app se actualizó con estado guardado) se descarta en vez de tronar.
 */
internal class ScrollPorSegmento private constructor(
    private val estados: MutableMap<SegmentoDeCobranza, LazyListState>
) {
    /** Siembra el segmento con el que la pantalla se monta con [estadoInicial]. */
    constructor(segmentoInicial: SegmentoDeCobranza, estadoInicial: LazyListState) :
        this(mutableMapOf(segmentoInicial to estadoInicial))

    /** El estado de [segmento]; uno nuevo, en el tope, la primera vez que se visita. */
    fun de(segmento: SegmentoDeCobranza): LazyListState =
        estados.getOrPut(segmento) { LazyListState() }

    /**
     * Descarta la posición de todos los segmentos menos [visible]: al
     * cambiar el orden, el índice guardado de otro segmento apunta a otros
     * clientes en el orden nuevo. La próxima visita a ese segmento arranca
     * en el tope con un estado nuevo (ver [de]). El visible no se toca aquí:
     * su `LazyColumn` está montado y quien llama lo regresa al tope con
     * `scrollToItem`.
     */
    fun olvidarTodosMenos(visible: SegmentoDeCobranza) {
        estados.keys.retainAll(setOf(visible))
    }

    companion object {
        /** Campos por segmento en la forma plana guardada: nombre, índice, desplazamiento. */
        private const val CAMPOS = 3

        val Saver: Saver<ScrollPorSegmento, Any> = listSaver(
            save = { scroll ->
                scroll.estados.flatMap { (segmento, estado) ->
                    listOf(
                        segmento.name,
                        estado.firstVisibleItemIndex,
                        estado.firstVisibleItemScrollOffset
                    )
                }
            },
            restore = { plano ->
                val estados = plano.chunked(CAMPOS).mapNotNull { (nombre, indice, offset) ->
                    val segmento = SegmentoDeCobranza.entries.firstOrNull { it.name == nombre }
                    if (segmento == null || indice !is Int || offset !is Int) {
                        null
                    } else {
                        segmento to LazyListState(indice, offset)
                    }
                }
                ScrollPorSegmento(estados.toMap(mutableMapOf()))
            }
        )
    }
}
