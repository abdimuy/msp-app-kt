package com.example.msp_app.feature.compartirproductos.domain

import java.time.Instant

/** Junta nombre, categoría, precios y fecha en la [TarjetaDeProducto] que se dibuja. */
object ArmadorDeTarjeta {
    const val TITULO_VACIO = "Producto"
    const val LARGO_MAXIMO_DE_TITULO = 120

    fun armar(
        articulo: ArticuloParaCompartir,
        seleccion: SeleccionDePrecios,
        tituloEditado: String?,
        ahora: Instant
    ): TarjetaDeProducto {
        val partes = PartidorDeNombre.partir(articulo.nombre)
        val editado = tituloEditado?.trim()?.takeIf { it.isNotEmpty() }?.take(
            LARGO_MAXIMO_DE_TITULO
        )
        return TarjetaDeProducto(
            titulo = editado ?: partes.titulo.ifBlank { TITULO_VACIO },
            detalle = partes.detalle,
            categoria = articulo.categoria?.let {
                NormalizadorDeNombre.normalizar(it)
            }?.takeIf { it.isNotBlank() },
            precios = PreciosDeTarjeta.armar(articulo.precios, seleccion),
            vigencia = FechaVigente.leyenda(ahora)
        )
    }

    /** El título que se le propone al vendedor para editar. */
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
    fun tituloEditado(articuloId: Int): String?

    /** Un título vacío borra el recuerdo: el producto vuelve a su título automático. */
    fun guardarTituloEditado(articuloId: Int, titulo: String?)
}
