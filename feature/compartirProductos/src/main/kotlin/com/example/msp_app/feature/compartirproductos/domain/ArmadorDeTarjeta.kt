package com.example.msp_app.feature.compartirproductos.domain

import java.time.Instant

/**
 * Junta nombre, categoría, precios y fecha en la [TarjetaDeProducto] que se dibuja. El título sale
 * SIEMPRE del corte automático del nombre: el vendedor no lo modifica (E-APP-180).
 */
object ArmadorDeTarjeta {
    const val TITULO_VACIO = "Producto"

    fun armar(
        articulo: ArticuloParaCompartir,
        seleccion: SeleccionDePrecios,
        ahora: Instant
    ): TarjetaDeProducto {
        val partes = PartidorDeNombre.partir(articulo.nombre)
        return TarjetaDeProducto(
            titulo = partes.titulo.ifBlank { TITULO_VACIO },
            detalle = partes.detalle,
            categoria = articulo.categoria?.let {
                NormalizadorDeNombre.normalizar(it)
            }?.takeIf { it.isNotBlank() },
            precios = PreciosDeTarjeta.armar(articulo.precios, seleccion),
            vigencia = FechaVigente.leyenda(ahora)
        )
    }

    /** El título de la tarjeta: el corte automático del nombre. */
    fun tituloAutomatico(articulo: ArticuloParaCompartir): String =
        PartidorDeNombre.partir(articulo.nombre).titulo.ifBlank { TITULO_VACIO }
}

/** El texto que acompaña a las imágenes: el nombre COMPLETO y los precios elegidos. */
object TextoDelMensaje {
    fun armar(
        articulos: List<ArticuloParaCompartir>,
        seleccion: SeleccionDePrecios,
        ahora: Instant
    ): String {
        val bloques = articulos.map { articulo ->
            val nombre = articulo.nombre.trim().replace(Regex("[\\s\\u00A0]+"), " ").ifBlank {
                ArmadorDeTarjeta.TITULO_VACIO
            }
            val precios = PreciosDeTarjeta.armar(articulo.precios, seleccion)
            if (precios.isEmpty()) {
                nombre
            } else {
                nombre + "\n" + precios.joinToString(" · ") {
                    "${it.fila.etiqueta}: ${it.texto}"
                }
            }
        }
        return (bloques + FechaVigente.leyenda(ahora)).joinToString("\n\n")
    }
}

/** Lo que el vendedor ya decidió y la app recuerda (preferencias locales; sin Room). */
interface PreferenciasDeCompartir {
    fun seleccionDePrecios(): SeleccionDePrecios
    fun guardarSeleccionDePrecios(seleccion: SeleccionDePrecios)
}
