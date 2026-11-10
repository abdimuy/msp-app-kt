package com.example.msp_app.core.datos

/**
 * Nombre amable (español) del servicio al que pertenece una clave de endpoint.
 * Es lo único que ve el cobrador y la oficina. El orden de las reglas importa:
 * lo más específico primero (foto, aviso en vivo, cuadre) y luego el dominio.
 */
object ClasificadorDeServicio {
    const val CLAVE_IMAGENES = "GET [imagen]"
    const val CLAVE_DESCARGAS = "GET [descarga]"
    const val CLAVE_OTROS = "OTROS"

    const val CLIENTES = "Lista de clientes"
    const val PAGOS_Y_VENTAS = "Pagos y ventas (sincronización)"
    const val CUADRE = "Cuadre con la oficina"
    const val AVISOS = "Avisos en vivo"
    const val FOTOS = "Fotos (subida)"
    const val IMAGENES = "Imágenes de productos"
    const val VISITAS = "Visitas"
    const val OTROS = "Otros"
    const val MAPA_FIREBASE_Y_OTROS = "Mapa, Firebase y otros"

    val TODOS: List<String> =
        listOf(CLIENTES, PAGOS_Y_VENTAS, CUADRE, AVISOS, FOTOS, IMAGENES, VISITAS, OTROS)

    private val CUADRE_RUTAS = listOf("/digest", "/ids", "/by-ids", "/conciliacion")
    private val PAGOS_Y_VENTAS_RUTAS = listOf("/v2/cobranza", "/v2/ventas", "/ventas")
    private val VISITAS_RUTAS = listOf("/v2/visitas", "/visitas", "/v2/agenda-compromisos")

    fun servicioDe(clave: String): String {
        val ruta = clave.substringAfter(' ', clave)
        return when {
            clave == CLAVE_IMAGENES -> IMAGENES
            clave.endsWith(NormalizadorDeRutas.SUFIJO_FOTO) -> FOTOS
            ruta.endsWith("/stream") -> AVISOS
            CUADRE_RUTAS.any { ruta.contains(it) } -> CUADRE
            ruta == "/clientes" -> CLIENTES
            PAGOS_Y_VENTAS_RUTAS.any { ruta.startsWith(it) } -> PAGOS_Y_VENTAS
            VISITAS_RUTAS.any { ruta.startsWith(it) } -> VISITAS
            ruta == "/imagenes" -> IMAGENES
            else -> OTROS
        }
    }
}
