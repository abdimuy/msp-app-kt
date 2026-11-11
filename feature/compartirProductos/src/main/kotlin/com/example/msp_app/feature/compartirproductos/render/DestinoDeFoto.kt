package com.example.msp_app.feature.compartirproductos.render

import kotlin.math.roundToInt

/**
 * Dónde se dibuja la foto dentro de su recuadro, en coordenadas del recuadro (`object-fit: contain`).
 *
 * La foto se escala para que quepa ENTERA (sin recortar nada, sin deformarla), centrada; si es más
 * chica que el recuadro se agranda hasta caber. El eje que limita toca el borde del recuadro; en el
 * otro sobra espacio, que el dibujo pinta liso con el color del recuadro sin foto.
 */
internal data class DestinoDeFoto(
    val izquierda: Int,
    val arriba: Int,
    val derecha: Int,
    val abajo: Int
) {
    val ancho: Int get() = derecha - izquierda
    val alto: Int get() = abajo - arriba

    companion object {
        fun caber(fotoAncho: Int, fotoAlto: Int, cajaAncho: Int, cajaAlto: Int): DestinoDeFoto {
            require(fotoAncho > 0 && fotoAlto > 0 && cajaAncho > 0 && cajaAlto > 0) {
                "medidas inválidas"
            }
            // Producto cruzado: no depende de divisiones de punto flotante para decidir qué eje limita.
            val limitaElAncho = cajaAncho.toLong() * fotoAlto <= cajaAlto.toLong() * fotoAncho
            val ancho = if (limitaElAncho) {
                cajaAncho
            } else {
                (fotoAncho.toDouble() * cajaAlto / fotoAlto).roundToInt().coerceIn(1, cajaAncho)
            }
            val alto = if (limitaElAncho) {
                (fotoAlto.toDouble() * cajaAncho / fotoAncho).roundToInt().coerceIn(1, cajaAlto)
            } else {
                cajaAlto
            }
            val izquierda = (cajaAncho - ancho) / 2
            val arriba = (cajaAlto - alto) / 2
            return DestinoDeFoto(izquierda, arriba, izquierda + ancho, arriba + alto)
        }
    }
}
