package com.example.msp_app.feature.compartirproductos.domain

import com.example.msp_app.core.common.ventas.precios.TipoDePrecio
import java.math.BigDecimal

/**
 * Un precio del catálogo ya CLASIFICADO por la app (`preciosParaMostrar`: por
 * `precios_ordenados` del servidor, o por nombre con `ClasificadorDePrecios`).
 * Este módulo no clasifica ni ordena nada: recibe la lista en el orden de la app.
 */
data class PrecioDeCatalogo(val tipo: TipoDePrecio, val monto: BigDecimal)

/** Lo que la app sabe de un artículo y hace falta para su tarjeta. */
data class ArticuloParaCompartir(
    val id: Int,
    /** El nombre COMPLETO de Microsip, tal cual. Es lo que va siempre en el texto del mensaje. */
    val nombre: String,
    val categoria: String?,
    val precios: List<PrecioDeCatalogo>,
    /** Archivo local de la primera foto del producto, si la hay. */
    val fotoPath: String?
)

/** Las filas que puede llevar el bloque de precios, en el orden en que se dibujan. */
enum class FilaDePrecio(val etiqueta: String, val tipo: TipoDePrecio) {
    CONTADO("Contado", TipoDePrecio.CONTADO),
    CORTO_PLAZO("Corto plazo", TipoDePrecio.CORTO_PLAZO),

    /** «Crédito» en la tarjeta es el precio de lista (largo plazo) que ya usa la app. */
    CREDITO("Crédito", TipoDePrecio.LARGO_PLAZO)
}

/** Las tres casillas de la hoja; las tres vienen marcadas por omisión. */
data class SeleccionDePrecios(
    val contado: Boolean = true,
    val cortoPlazo: Boolean = true,
    val credito: Boolean = true
) {
    fun incluye(fila: FilaDePrecio): Boolean = when (fila) {
        FilaDePrecio.CONTADO -> contado
        FilaDePrecio.CORTO_PLAZO -> cortoPlazo
        FilaDePrecio.CREDITO -> credito
    }

    fun con(fila: FilaDePrecio, marcada: Boolean): SeleccionDePrecios = when (fila) {
        FilaDePrecio.CONTADO -> copy(contado = marcada)
        FilaDePrecio.CORTO_PLAZO -> copy(cortoPlazo = marcada)
        FilaDePrecio.CREDITO -> copy(credito = marcada)
    }
}

data class PrecioDeTarjeta(val fila: FilaDePrecio, val monto: BigDecimal, val texto: String)

/** Todo lo que el dibujo necesita, ya resuelto: el dibujo no decide nada. */
data class TarjetaDeProducto(
    val titulo: String,
    val detalle: String?,
    val categoria: String?,
    val precios: List<PrecioDeTarjeta>,
    val vigencia: String
)
