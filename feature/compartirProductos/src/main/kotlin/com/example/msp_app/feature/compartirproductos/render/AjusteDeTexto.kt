package com.example.msp_app.feature.compartirproductos.render

/**
 * El tamaño del título: el más grande de [maximo] a [minimo] (de [paso] en [paso])
 * con el que el texto cabe en [maxLineas] renglones. Si ni al mínimo cabe, se
 * devuelve el mínimo y el dibujo termina el texto en «…».
 *
 * La medición real (cuántos renglones da el texto a ese tamaño y ese ancho) la
 * entrega quien llama en [lineasPara]; así la regla se prueba sola y el dibujo
 * la usa con `StaticLayout`, no con una estimación.
 */
internal object AjusteDeTexto {

    fun tamanoDeTitulo(
        maximo: Float = Medidas.TITULO_MAXIMO,
        minimo: Float = Medidas.TITULO_MINIMO,
        paso: Float = Medidas.TITULO_PASO,
        maxLineas: Int = Medidas.TITULO_LINEAS,
        lineasPara: (Float) -> Int
    ): Float {
        require(paso > 0f && maximo >= minimo) { "rango de tamaños inválido" }
        var tamano = maximo
        while (tamano > minimo) {
            if (lineasPara(tamano) <= maxLineas) return tamano
            tamano -= paso
        }
        return minimo
    }
}

/** El rectángulo de la foto original que se ve al llenar el destino (`object-fit: cover`). */
internal data class RecorteDeFoto(
    val izquierda: Int,
    val arriba: Int,
    val derecha: Int,
    val abajo: Int
) {
    val ancho: Int get() = derecha - izquierda
    val alto: Int get() = abajo - arriba

    companion object {
        /**
         * Escala la foto hasta cubrir el destino y recorta lo que sobra, repartido según el
         * foco (0 = pegado a un borde, 1 = al otro; 0.5 = centrado). Una foto horizontal pierde
         * costados; una vertical pierde arriba y abajo.
         */
        fun cubrir(
            fotoAncho: Int,
            fotoAlto: Int,
            destinoAncho: Int,
            destinoAlto: Int,
            focoX: Float = Medidas.FOTO_FOCO_X,
            focoY: Float = Medidas.FOTO_FOCO_Y
        ): RecorteDeFoto {
            require(fotoAncho > 0 && fotoAlto > 0 && destinoAncho > 0 && destinoAlto > 0) {
                "medidas inválidas"
            }
            // Se compara por producto cruzado para no depender de divisiones de punto flotante.
            val fotoMasAncha = fotoAncho.toLong() * destinoAlto > destinoAncho.toLong() * fotoAlto
            return if (fotoMasAncha) {
                val ancho = (fotoAlto.toLong() * destinoAncho / destinoAlto).toInt().coerceIn(
                    1,
                    fotoAncho
                )
                val izq = ((fotoAncho - ancho) * focoX).toInt()
                RecorteDeFoto(izq, 0, izq + ancho, fotoAlto)
            } else {
                val alto = (fotoAncho.toLong() * destinoAlto / destinoAncho).toInt().coerceIn(
                    1,
                    fotoAlto
                )
                val arr = ((fotoAlto - alto) * focoY).toInt()
                RecorteDeFoto(0, arr, fotoAncho, arr + alto)
            }
        }
    }
}
