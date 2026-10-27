package com.example.msp_app.feature.pagos.domain.port

/**
 * E-APP-119 (D15, regla del dueño "nada se pierde"): reanuda las visitas y
 * garantías que el cobrador capturó y que no llegaron a escribirse. Es el
 * destino del aviso "N capturas sin terminar" cuando lo pendiente no es un
 * abono (el abono tiene su pantalla, "Guardar"). Total: no lanza.
 */
fun interface ReanudarCapturasPort {
    suspend fun reanudar()

    companion object {
        /** Para montar la lista en una prueba sin nada que reanudar. */
        val NINGUNA = ReanudarCapturasPort { }
    }
}
