package com.example.msp_app.core.datos

/** Cuenta actividades iniciadas: la app está en primer plano si hay al menos una. */
class ContadorDeActividades(private val alCambiar: (primerPlano: Boolean) -> Unit) {
    private var iniciadas = 0

    @Synchronized fun iniciada() {
        iniciadas++
        if (iniciadas == 1) alCambiar(true)
    }

    @Synchronized fun detenida() {
        if (iniciadas == 0) return
        iniciadas--
        if (iniciadas == 0) alCambiar(false)
    }
}

object ClasificadorDeRed {
    fun clasificar(wifi: Boolean, celular: Boolean): TipoDeRed = when {
        wifi -> TipoDeRed.WIFI
        celular -> TipoDeRed.MOVIL
        else -> TipoDeRed.OTRA
    }
}
