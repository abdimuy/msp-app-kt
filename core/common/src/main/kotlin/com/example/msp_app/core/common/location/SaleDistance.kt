package com.example.msp_app.core.common.location

/**
 * Distancia de la posición actual del cobrador a una venta, o la ausencia de
 * ella.
 *
 * Existe porque el modelo anterior —un `Long` crudo donde `Long.MAX_VALUE`
 * significaba "esta venta no tiene ubicación"— dejaba que un centinela viajara
 * por toda la app como si fuera un dato: cualquier consumidor podía imprimirlo
 * y la tarjeta terminaba mostrando `9.223372036854776E18 m`. Acá la ausencia es
 * un caso del tipo ([Unknown]), no un valor; el compilador obliga a cubrirlo y
 * ningún `when` puede confundirlo con una distancia real.
 *
 * [Known] es total por construcción: su `init` rechaza NaN, infinitos,
 * negativos y cualquier magnitud que no quepa en la Tierra, así que un
 * centinela no puede colarse ni por el constructor ni por `copy`. La entrada
 * segura desde datos crudos es [of], que nunca lanza — traduce esos mismos
 * casos a [Unknown].
 */
sealed interface SaleDistance : Comparable<SaleDistance> {

    /** La venta no tiene ubicación conocida (sin centroide de pagos). */
    data object Unknown : SaleDistance

    /** Distancia real, en metros, siempre finita y dentro de [MAX_PLAUSIBLE_METERS]. */
    data class Known(val meters: Double) : SaleDistance {
        init {
            require(meters.isFinite() && meters >= 0.0 && meters <= MAX_PLAUSIBLE_METERS) {
                "distancia fuera de rango: $meters"
            }
        }
    }

    /** Metros si la distancia es [Known]; `null` si es [Unknown]. */
    val metersOrNull: Double?
        get() = (this as? Known)?.meters

    /**
     * [Unknown] ordena SIEMPRE al final: la pantalla principal lista las ventas
     * cercanas primero y deja las que no se pueden ubicar hasta abajo. Ese
     * comportamiento era el único motivo del centinela `Long.MAX_VALUE`; ahora
     * lo aporta el tipo, sin exponer un número imprimible.
     */
    override fun compareTo(other: SaleDistance): Int = (metersOrNull ?: Double.POSITIVE_INFINITY)
        .compareTo(other.metersOrNull ?: Double.POSITIVE_INFINITY)

    companion object {
        /**
         * Media circunferencia terrestre (~20 000 km): la mayor distancia
         * posible entre dos puntos del planeta. Cualquier magnitud mayor no es
         * una distancia sino un error de cálculo o un centinela heredado, y se
         * traduce a [Unknown].
         */
        const val MAX_PLAUSIBLE_METERS: Double = 20_000_000.0

        /**
         * Construye una distancia desde un valor crudo. Devuelve [Unknown] —en
         * vez de lanzar— cuando el valor no es una distancia usable: NaN,
         * infinito, negativo o mayor a [MAX_PLAUSIBLE_METERS].
         */
        fun of(meters: Double): SaleDistance =
            if (meters.isFinite() && meters >= 0.0 && meters <= MAX_PLAUSIBLE_METERS) {
                Known(meters)
            } else {
                Unknown
            }

        /** Igual que [of], para orígenes que ya redondearon a metros enteros. */
        fun of(meters: Long): SaleDistance = of(meters.toDouble())
    }
}
