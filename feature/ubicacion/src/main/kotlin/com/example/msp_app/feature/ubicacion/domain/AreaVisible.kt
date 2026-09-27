package com.example.msp_app.feature.ubicacion.domain

/**
 * **La parte del mapa que el cobrador de verdad ve**: debajo de la franja de
 * controles de arriba y encima del borde superior de la hoja.
 *
 * El defecto que esto corrige, medido en un SM-A256E (1080 × 2340 px) el
 * 2026-09-27: con el detalle de "Donde más paga" abierto la hoja empezaba en
 * y ≈ 820 px y la punta del pin quedó en y ≈ 810 px, pegada a la hoja, cuando el
 * centro de lo visible (250–820 px) es ≈ 535 px. La cámara centraba sin
 * descontar la hoja.
 *
 * No depende de cómo el SDK aplique el `contentPadding` durante una animación:
 * la pantalla mide dónde quedó el punto (`Projection.toScreenLocation`) y
 * desplaza la cámara lo que diga [desplazamiento]. Todo en píxeles de pantalla.
 */
data class AreaVisible(
    val anchoPx: Float,
    val altoPx: Float,
    /** Alto de la franja de controles de arriba. */
    val arribaPx: Float,
    /** Alto de la hoja, medido desde el borde inferior de la pantalla. */
    val hojaPx: Float
) {
    val centroX: Float get() = anchoPx / 2f
    val centroY: Float get() = arribaPx + (altoPx - hojaPx - arribaPx) / 2f

    /**
     * Cuánto mover la cámara (`CameraUpdateFactory.scrollBy`) para que un punto
     * que hoy se ve en ([x], [y]) quede en el centro de lo visible. `scrollBy`
     * con `y` positiva sube el contenido, así que es "dónde está menos dónde va".
     */
    fun desplazamiento(x: Float, y: Float): Pair<Float, Float> = (x - centroX) to (y - centroY)
}
