package com.example.msp_app.feature.compartirproductos.ui

import com.example.msp_app.feature.compartirproductos.domain.FilaDePrecio

/** Etiquetas de prueba (los textos visibles son los de la hoja; estas solo sirven para pruebas de UI). */
object EtiquetasDeHoja {
    const val HOJA = "hoja_compartir"
    const val VISTA_PREVIA = "hoja_vista_previa"
    const val TITULO = "hoja_titulo"
    const val BOTON = "hoja_boton_compartir"
    const val AVISO = "hoja_aviso_precios"
    fun casilla(fila: FilaDePrecio) = "hoja_casilla_${fila.name.lowercase()}"
}

object EtiquetasDeSeleccion {
    const val BARRA = "barra_seleccion"
    const val COMPARTIR = "barra_seleccion_compartir"
    const val CANCELAR = "barra_seleccion_cancelar"
}
