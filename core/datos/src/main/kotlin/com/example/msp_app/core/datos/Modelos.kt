package com.example.msp_app.core.datos

/** Bytes de un endpoint normalizado en un día: [rx] bajada, [tx] subida (cuerpos + estimación de cabeceras). */
data class UsoDeEndpoint(val rx: Long, val tx: Long, val peticiones: Long)

/** Red activa en el momento de la muestra. [OTRA] = ethernet/VPN/otra; [SIN_RED] = ninguna. */
enum class TipoDeRed { MOVIL, WIFI, OTRA, SIN_RED }

/** Lectura de los contadores del sistema para el UID de la app. */
data class MuestraDeTrafico(val rx: Long, val tx: Long)

/**
 * Agregado de un día de negocio (`America/Mexico_City`), `yyyy-MM-dd`.
 *
 * - [rxTotal]/[txTotal]: total REAL de la app según `TrafficStats` (todo lo que
 *   sale por cualquier red, incluido Maps/Firebase).
 * - [movil]/[wifi]/[otraRed] y [primerPlano]/[segundoPlano]: reparto de ese mismo
 *   total (suman `rxTotal + txTotal`).
 * - [endpoints]: lo que pasó por OkHttp, por endpoint normalizado.
 */
data class UsoDelDia(
    val fecha: String,
    val rxTotal: Long,
    val txTotal: Long,
    val movil: Long,
    val wifi: Long,
    val otraRed: Long,
    val primerPlano: Long,
    val segundoPlano: Long,
    val endpoints: Map<String, UsoDeEndpoint>
) {
    val total: Long get() = rxTotal + txTotal

    /** Bytes que vio OkHttp (bajada + subida de todos los endpoints). */
    val desglose: Long get() = endpoints.values.sumOf { it.rx + it.tx }

    val tieneDatos: Boolean get() = total > 0 || endpoints.isNotEmpty()

    companion object {
        fun vacio(fecha: String) = UsoDelDia(fecha, 0, 0, 0, 0, 0, 0, 0, emptyMap())
    }
}
