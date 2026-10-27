package com.example.msp_app.data.captura

import com.example.msp_app.core.database.dao.captura.CapturaEnCursoDao
import com.example.msp_app.data.auth.emailDeLaSesion
import com.example.msp_app.feature.pagos.domain.port.VentaLocalSinTerminar
import com.example.msp_app.feature.pagos.domain.port.VentaLocalSinTerminarPort
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

/**
 * E-APP-119: la venta local CAPTURADA más vieja del usuario en sesión, leída de
 * `capturas_en_curso` (y de `local_sale` para saber si ya se escribió). Sólo
 * lectura.
 */
class VentaLocalSinTerminarAdapter(
    private val capturas: CapturaEnCursoDao,
    private val emailEnSesion: () -> String? = ::emailDeLaSesion
) : VentaLocalSinTerminarPort {

    override fun observar(): Flow<VentaLocalSinTerminar?> {
        val usuario = emailEnSesion()?.takeIf { it.isNotBlank() } ?: return flowOf(null)
        return capturas.observarVentaLocalSinTerminar(usuario).map { fila ->
            fila?.let { VentaLocalSinTerminar(saleId = it.ID, yaSeGuardo = it.ESCRITA) }
        }
    }
}
