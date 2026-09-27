package com.example.msp_app.feature.ubicacion.domain

import com.example.msp_app.core.geo.Punto
import kotlin.math.PI
import kotlin.math.atan
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.log2
import kotlin.math.max
import kotlin.math.min
import kotlin.math.tan

/** Una cámara: el centro y el zoom de Google Maps. */
data class Camara(val centro: Punto, val zoom: Float)

/**
 * **El encuadre, calculado con Web Mercator y no con `newLatLngBounds`.**
 *
 * `newLatLngBounds` exige que el mapa ya esté medido (lanza si no), y bajo
 * Robolectric no hay mapa. Calcular aquí el centro y el zoom deja el mismo
 * encuadre en el aparato y en la prueba, y deja probar "los sueltos no mueven
 * el encuadre" sin GL.
 *
 * Unidades: dp. En Google Maps el mundo mide `256 · 2^zoom` dp de ancho.
 */
object Encuadre {
    private const val MUNDO_DP = 256.0
    private const val ZOOM_DE_UN_PUNTO = 17f
    private const val ZOOM_MAXIMO = 17.5f
    private const val ZOOM_MINIMO = 2f
    private const val GRADOS_VUELTA = 360.0
    private const val MEDIA_VUELTA = 180.0

    private fun x(lon: Double) = (lon + MEDIA_VUELTA) / GRADOS_VUELTA * MUNDO_DP
    private fun y(lat: Double): Double {
        val r = Math.toRadians(lat)
        return (1 - ln(tan(r) + 1 / cos(r)) / PI) / 2 * MUNDO_DP
    }
    private fun lon(x: Double) = x / MUNDO_DP * GRADOS_VUELTA - MEDIA_VUELTA
    private fun lat(y: Double): Double {
        val n = PI * (1 - 2 * y / MUNDO_DP)
        return Math.toDegrees(atan((exp(n) - exp(-n)) / 2))
    }

    /** Metros por dp a esta latitud y zoom (para la regla de escala). */
    fun metrosPorDp(latitud: Double, zoom: Float): Double =
        Punto.RADIO_DE_LA_TIERRA_M * 2 * PI * cos(Math.toRadians(latitud)) / (MUNDO_DP * Math.pow(2.0, zoom.toDouble()))

    /**
     * La cámara que mete [puntos] en un área de [anchoDp] × [altoDp] con
     * [margenDp] por lado. Un solo punto va a zoom 17: "la cuadra, no la ciudad".
     */
    fun de(puntos: List<Punto>, anchoDp: Float, altoDp: Float, margenDp: Float): Camara? {
        if (puntos.isEmpty()) return null
        val xs = puntos.map { x(it.lon) }
        val ys = puntos.map { y(it.lat) }
        val cx = (xs.min() + xs.max()) / 2
        val cy = (ys.min() + ys.max()) / 2
        val centro = Punto(lat(cy), lon(cx))
        val spanX = xs.max() - xs.min()
        val spanY = ys.max() - ys.min()
        if (spanX <= 0.0 && spanY <= 0.0) return Camara(centro, ZOOM_DE_UN_PUNTO)
        val w = max(1f, anchoDp - 2 * margenDp).toDouble()
        val h = max(1f, altoDp - 2 * margenDp).toDouble()
        val zx = if (spanX > 0) log2(w / spanX) else Double.MAX_VALUE
        val zy = if (spanY > 0) log2(h / spanY) else Double.MAX_VALUE
        val zoom = min(zx, zy).toFloat().coerceIn(ZOOM_MINIMO, ZOOM_MAXIMO)
        return Camara(centro, zoom)
    }

    /** `true` si [punto] cae dentro de lo que enseña [camara] en un área de [anchoDp] × [altoDp]. */
    fun seVe(punto: Punto, camara: Camara, anchoDp: Float, altoDp: Float): Boolean {
        val escala = Math.pow(2.0, camara.zoom.toDouble())
        val dx = (x(punto.lon) - x(camara.centro.lon)) * escala
        val dy = (y(punto.lat) - y(camara.centro.lat)) * escala
        return kotlin.math.abs(dx) <= anchoDp / 2 && kotlin.math.abs(dy) <= altoDp / 2
    }
}

/**
 * Los sueltos que **no se ven** con esta cámara: alimentan "N puntos lejos · Ver".
 */
fun MapaDelCliente.sueltosFueraDeLaVista(
    camara: Camara?,
    anchoDp: Float,
    altoDp: Float
): List<LugarClasificado> = if (camara == null) {
    emptyList()
} else {
    sueltos.filterNot {
        Encuadre.seVe(it.centro, camara, anchoDp, altoDp)
    }
}

/** Una distancia "redonda" para la regla de escala, y su ancho en dp. */
fun reglaDeEscala(metrosPorDp: Double, anchoMaximoDp: Double = 60.0): Pair<String, Double> {
    val maximo = metrosPorDp * anchoMaximoDp
    val m = DISTANCIAS_REDONDAS.lastOrNull { it <= maximo } ?: DISTANCIAS_REDONDAS.first()
    val etiqueta = if (m >= METROS_POR_KM) "${m / METROS_POR_KM} km" else "$m m"
    return etiqueta to m / metrosPorDp
}

// La tabla de distancias "redondas" de la regla de escala (como la de Google Maps):
// los números SON el dato, no hay nombre mejor que la lista misma.
@Suppress("MagicNumber")
private val DISTANCIAS_REDONDAS =
    listOf(5, 10, 20, 50, 100, 200, 500, 1_000, 2_000, 5_000, 10_000, 20_000, 50_000, 100_000)

private const val METROS_POR_KM = 1_000
