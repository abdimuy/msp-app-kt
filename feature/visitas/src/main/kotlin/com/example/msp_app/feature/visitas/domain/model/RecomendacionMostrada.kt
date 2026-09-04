package com.example.msp_app.feature.visitas.domain.model

import java.time.Instant

/**
 * **Lo que el sistema sugirió** — la mitad del par que §10 del expediente pide
 * conservar junto a lo que el cobrador hizo.
 *
 * Sin *qué sugirió el recomendador* al lado de *qué pasó en esa puerta*, el
 * recomendador no se puede evaluar nunca, y esa mitad es **imposible de
 * reconstruir después**: una vez que la ruta se recalcula, la lista que el
 * cobrador vio esa mañana ya no existe en ningún lado.
 *
 * La fila vive en `visita_recomendaciones` (Task 26) y **no** en columnas de
 * `Visit`, por dos razones que el esquema ya documenta: una recomendación puede
 * no terminar en visita, y el brazo de control **por definición** no produce
 * visita. Esta pantalla la lee para mostrarla y la ata a la visita al guardar.
 *
 * @property posicion el renglón en que se mostró, empezando en 0. Sin él no se
 *   distingue "el cobrador siguió la recomendación" de "el cobrador fue al
 *   primero de la lista".
 * @property motivo código de catálogo cerrado, no texto libre: se guarda para
 *   poder contarlo.
 * @property algoritmo la versión del recomendador que la produjo.
 * @property grupo el brazo del experimento. Hoy siempre [GRUPO_TRATAMIENTO]: no
 *   hay aleatorización y esta tarea no la implementa. El día que exista, una
 *   fila de `'control'` se calcula y **no se muestra**, así que nunca llega
 *   aquí — y ese es justo el contrafactual que la hace legible.
 */
data class RecomendacionMostrada(
    val recomendacionId: String,
    val clienteId: Int,
    val ventaId: Int?,
    val posicion: Int,
    val motivo: String,
    val algoritmo: String,
    val grupo: String,
    val generadaEn: Instant
) {
    /** "sugerida 1ª de la ruta" — la línea que la pantalla muestra. */
    val etiquetaDePosicion: String get() = "sugerida ${posicion + 1}ª de la ruta"

    companion object {
        /** El brazo que sí se muestra. Es el default de la columna `GRUPO`. */
        const val GRUPO_TRATAMIENTO: String = "tratamiento"

        /**
         * El brazo que se calcula y NO se muestra. No lo escribe nadie todavía;
         * existe para que el día que se implemente la aleatorización no haga
         * falta otra migración ni otra constante.
         */
        const val GRUPO_CONTROL: String = "control"
    }
}
