package com.example.msp_app.feature.ventacorreccion.data.fake

import com.example.msp_app.feature.ventacorreccion.domain.port.ReencolarSubidaPort

/**
 * Fake de [ReencolarSubidaPort] que CUENTA llamadas (brief de Task 3) y puede simular el
 * proceso muerto justo después del commit: [lanzarEnReencolar], si no es `null`, se lanza desde
 * [reencolar] en vez de contar de verdad la operación como exitosa.
 */
class FakeReencolar : ReencolarSubidaPort {
    var llamadasCancelar = 0
        private set
    var llamadasReencolar = 0
        private set

    val idsReencolados = mutableListOf<String>()
    val emailsReencolados = mutableListOf<String>()

    /** Si no es `null`, [reencolar] lo lanza (simula el proceso muerto justo después del commit). */
    var lanzarEnReencolar: Throwable? = null

    override fun cancelarTrabajoEncolado(saleId: String) {
        llamadasCancelar++
    }

    override fun reencolar(saleId: String, userEmail: String) {
        llamadasReencolar++
        idsReencolados += saleId
        emailsReencolados += userEmail
        lanzarEnReencolar?.let { throw it }
    }
}
