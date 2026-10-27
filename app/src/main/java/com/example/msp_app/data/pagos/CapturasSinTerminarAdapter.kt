package com.example.msp_app.data.pagos

import com.example.msp_app.core.database.dao.captura.CapturaEnCursoDao
import com.example.msp_app.data.auth.emailDeLaSesion
import com.example.msp_app.feature.pagos.domain.port.CapturasSinTerminar
import com.example.msp_app.feature.pagos.domain.port.CapturasSinTerminarPort
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf

/**
 * "N capturas sin terminar" del cobrador en sesión, leído de
 * `capturas_en_curso` (E-APP-084). Sólo lectura.
 */
class CapturasSinTerminarAdapter(
    private val capturas: CapturaEnCursoDao,
    private val emailEnSesion: () -> String? = ::emailDeLaSesion
) : CapturasSinTerminarPort {

    override fun observar(): Flow<CapturasSinTerminar> {
        val usuario = emailEnSesion()?.takeIf { it.isNotBlank() }
            ?: return flowOf(CapturasSinTerminar.NINGUNA)
        return combine(
            capturas.observarSinTerminar(usuario),
            capturas.observarVentaDelPrimerAbonoSinGuardar(usuario)
        ) { cuantas, llave ->
            CapturasSinTerminar(cuantas = cuantas, ventaId = llave?.toIntOrNull())
        }
    }
}
