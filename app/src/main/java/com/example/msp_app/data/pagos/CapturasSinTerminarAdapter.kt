package com.example.msp_app.data.pagos

import com.example.msp_app.core.database.dao.captura.CapturaEnCursoDao
import com.example.msp_app.data.auth.emailDeLaSesion
import com.example.msp_app.feature.pagos.domain.port.CapturasSinTerminar
import com.example.msp_app.feature.pagos.domain.port.CapturasSinTerminarPort
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf

/**
 * "N capturas sin terminar" y "Un envío no coincide con la oficina" del
 * cobrador en sesión, leídos de `capturas_en_curso` (E-APP-084, E-APP-118).
 * Sólo lectura.
 */
class CapturasSinTerminarAdapter(
    private val capturas: CapturaEnCursoDao,
    private val emailEnSesion: () -> String? = ::emailDeLaSesion
) : CapturasSinTerminarPort {

    override fun observar(): Flow<CapturasSinTerminar> {
        val usuario = emailEnSesion()?.takeIf { it.isNotBlank() }
            ?: return flowOf(CapturasSinTerminar.NINGUNA)
        // E-APP-118: además del conteo, cuántos envíos no coinciden con la
        // oficina (pagos y condonaciones) y la venta a la que lleva el toque.
        return combine(
            capturas.observarSinTerminar(usuario),
            capturas.observarVentaDelAviso(usuario),
            capturas.observarEnviosQueNoCoinciden(usuario)
        ) { cuantas, llave, noCoinciden ->
            CapturasSinTerminar(
                cuantas = cuantas,
                ventaId = llave?.toIntOrNull(),
                noCoinciden = noCoinciden
            )
        }
    }
}
