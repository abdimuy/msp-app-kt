package com.example.msp_app.feature.ventacorreccion.domain.usecase

import com.example.msp_app.feature.ventacorreccion.domain.EstadoCorreccion
import com.example.msp_app.feature.ventacorreccion.domain.evaluarCorregibilidad
import com.example.msp_app.feature.ventacorreccion.domain.port.RelojPort
import com.example.msp_app.feature.ventacorreccion.domain.port.VentaLocalCorreccionPort
import javax.inject.Inject

/**
 * Consulta de SÓLO LECTURA de [EstadoCorreccion] — a diferencia de [ReclamarCorreccion], NO toma
 * el candado de edición ni cancela ningún trabajo encolado. Task 5: el punto de entrada
 * (`SaleDescriptionScreen`, en `:app`) la usa para decidir si ofrecer el botón "Corregir venta" o
 * el aviso correspondiente, sin reclamar nada por el simple hecho de que el vendedor abrió la
 * pantalla — reclamar (y sus efectos de cortesía) sólo debe correr cuando de verdad entra a
 * editar (`EditSaleScreen`, vía [ReclamarCorreccion]).
 *
 * `null` si la venta no existe — el llamador no debe ofrecer nada en ese caso.
 */
class ConsultarEstadoCorreccion @Inject constructor(
    private val port: VentaLocalCorreccionPort,
    private val reloj: RelojPort
) {
    suspend operator fun invoke(saleId: String): EstadoCorreccion? {
        val estado = port.leerEstado(saleId) ?: return null
        return evaluarCorregibilidad(
            enviado = estado.enviado,
            permanente = estado.permanente,
            correccionNoEnviada = estado.correccionNoEnviada,
            correccionRemotaPendiente = estado.correccionRemotaPendiente,
            correccionRemotaEstado = estado.correccionRemotaEstado,
            claimKind = estado.claimKind,
            claimedAt = estado.claimedAt,
            ahora = reloj.ahoraEpochMillis()
        )
    }
}
