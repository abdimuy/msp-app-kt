package com.example.msp_app.feature.compartirproductos.domain

/** Lo que pasó al tocar un producto en la selección múltiple. */
sealed interface ResultadoDeSeleccion {
    data class Cambio(val seleccion: SeleccionDeProductos) : ResultadoDeSeleccion

    /** Ya había [MAXIMO] y se pidió uno más: no cambia nada y se avisa. */
    data object Excedido : ResultadoDeSeleccion {
        const val MENSAJE = "Máximo $MAXIMO productos por envío"
    }

    companion object {
        /** Tope por envío (decisión del dueño, 2026-11-11). */
        const val MAXIMO = 10
    }
}

private const val MAXIMO_POR_ENVIO = ResultadoDeSeleccion.MAXIMO

/** Los productos marcados en el catálogo, en el orden en que se marcaron. */
data class SeleccionDeProductos(val ids: List<Int> = emptyList()) {
    val cantidad: Int get() = ids.size
    val activa: Boolean get() = ids.isNotEmpty()

    fun contiene(id: Int): Boolean = id in ids

    /** Marca el producto, o lo desmarca si ya estaba. */
    fun alternar(id: Int): ResultadoDeSeleccion = when {
        id in ids -> ResultadoDeSeleccion.Cambio(SeleccionDeProductos(ids - id))
        ids.size >= MAXIMO_POR_ENVIO -> ResultadoDeSeleccion.Excedido
        else -> ResultadoDeSeleccion.Cambio(SeleccionDeProductos(ids + id))
    }
}
