package com.example.msp_app.feature.ventacorreccion.domain.usecase

import com.example.msp_app.feature.ventacorreccion.domain.port.ReencolarSubidaPort
import com.example.msp_app.feature.ventacorreccion.domain.port.VentaLocalCorreccionPort
import javax.inject.Inject

/**
 * Cancela la corrección en curso: suelta el candado (no-op si ya venció y otro lo tomó) y deja
 * los datos ORIGINALES intactos — no se escribe ningún campo, ninguna línea. El reencolado es
 * cortesía, mismo criterio que [GuardarCorreccion].
 */
class CancelarCorreccion @Inject constructor(
    private val port: VentaLocalCorreccionPort,
    private val reencolar: ReencolarSubidaPort
) {
    suspend operator fun invoke(saleId: String, claimId: String, userEmail: String) {
        port.soltar(saleId, claimId)
        runCatching { reencolar.reencolar(saleId, userEmail) }
    }
}
