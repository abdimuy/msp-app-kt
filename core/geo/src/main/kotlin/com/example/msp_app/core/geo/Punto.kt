package com.example.msp_app.core.geo

import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Un par de coordenadas, en grados decimales.
 *
 * **Existe como un valor y no como dos `Double` sueltos** por la misma razón que
 * `UbicacionDelCobro` en `:feature:pagos`: media coordenada no ubica nada. Este
 * tipo es el de la aritmética; el de la capa de pagos es el del dominio de
 * cobranza. **No se fusionan a propósito** — `:core:geo` no puede depender de un
 * feature, y un feature no debería heredar la aritmética sólo por nombrar un
 * punto.
 */
data class Punto(val lat: Double, val lon: Double) {

    /**
     * La distancia en metros hasta [otro], por la fórmula del haversine sobre una
     * esfera de radio [RADIO_DE_LA_TIERRA_M].
     *
     * **Por qué el haversine y no una aproximación plana:** a la escala de esta
     * app —decenas de metros— una proyección plana daría prácticamente lo mismo y
     * sería más rápida. Se usa el haversine igual porque el error de la
     * aproximación crece con la latitud y con la distancia, y esta función
     * también contesta "¿están a 8 km?" (el caso de la mudanza, y el de la
     * tienda contra la casa). Una función que es exacta cerca y mentirosa lejos
     * es la clase de trampa que este proyecto documenta, no la que escribe.
     *
     * **El `min(1.0, …)`** no es paranoia decorativa: sin él, dos puntos
     * idénticos pueden dar un radicando apenas mayor que 1 por redondeo de punto
     * flotante, y `asin` devuelve `NaN`. Un `NaN` aquí se propaga a la dispersión
     * y de ahí al círculo de precisión, que es justo el número que no puede
     * mentir.
     */
    fun distanciaA(otro: Punto): Double {
        val fi1 = Math.toRadians(lat)
        val fi2 = Math.toRadians(otro.lat)
        val dFi = fi2 - fi1
        val dLambda = Math.toRadians(otro.lon - lon)
        val h = sin(dFi / 2) * sin(dFi / 2) +
            cos(fi1) * cos(fi2) * sin(dLambda / 2) * sin(dLambda / 2)
        return 2 * RADIO_DE_LA_TIERRA_M * asin(min(1.0, sqrt(h)))
    }

    companion object {
        /**
         * Radio medio de la Tierra, en metros (esfera IUGG).
         *
         * Control con el que se verificó la fórmula el 2026-09-24: un grado de
         * latitud da 111,194.9 m (esperado 111,195), 0.001° de latitud da
         * 111.19 m, y 0.001° de longitud a 18.42° de latitud da 105.5 m
         * (= 111.19 · cos 18.42). Dos puntos iguales dan 0.0, no `NaN`.
         */
        const val RADIO_DE_LA_TIERRA_M: Double = 6_371_000.0
    }
}
