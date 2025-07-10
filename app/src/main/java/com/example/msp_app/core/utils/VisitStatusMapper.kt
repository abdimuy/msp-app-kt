package com.example.msp_app.core.utils

import com.example.msp_app.data.models.sale.EstadoCobranza

object VisitStatusMapper {
    private val volverAVisitar = listOf(
        Constants.NO_SE_ENCONTRABA,
        Constants.CASA_CERRADA,
        Constants.SOLO_MENORES,
        Constants.PIDE_TIEMPO,
        Constants.FUE_GROSERO,
        Constants.SE_ESCONDE,
        Constants.NO_RESPONDE,
        Constants.SE_ESCUCHAN_RUIDOS,
        Constants.PIDE_REAGENDAR,
        Constants.TIENE_PERO_NO_PAGA
    )

    private val noPagado = listOf(
        Constants.NO_VA_A_DAR_PAGO,
    )

    fun map(visitType: String): EstadoCobranza = when (visitType) {
        in volverAVisitar -> EstadoCobranza.VOLVER_VISITAR
        in noPagado -> EstadoCobranza.NO_PAGADO
        else -> EstadoCobranza.PENDIENTE
    }
}