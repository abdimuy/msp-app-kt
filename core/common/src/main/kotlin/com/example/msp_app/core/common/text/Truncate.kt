package com.example.msp_app.core.common.text

/**
 * Recorta el string a lo sumo a [max] caracteres, agregando "…" cuando se
 * recorta. Utilidad genuinamente compartida (UI-agnóstica): pensada para
 * nombres de cliente, direcciones o descripciones de artículo que necesitan
 * caber en espacios fijos (listas, chips, notificaciones) sin importar el
 * framework de UI que los consuma.
 *
 * - Si [max] es menor o igual a 0, retorna un string vacío.
 * - Si el string ya cabe en [max] caracteres, se retorna sin cambios.
 * - En caso contrario, se recorta a `max - 1` caracteres y se agrega "…"
 *   para que el resultado final nunca exceda [max] caracteres.
 */
fun String.ellipsize(max: Int): String {
    if (max <= 0) return ""
    if (length <= max) return this
    if (max == 1) return "…"
    return take(max - 1) + "…"
}
