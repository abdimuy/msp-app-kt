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
