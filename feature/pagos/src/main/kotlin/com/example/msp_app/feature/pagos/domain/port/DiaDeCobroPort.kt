package com.example.msp_app.feature.pagos.domain.port

import com.example.msp_app.feature.pagos.domain.DiaDeCobroDeLaVenta

/**
 * Lo último que el teléfono guardó del día de cobro. SOLO lee: la descarga vive
 * en `:app` y nunca toca las ventas ni los pagos.
 */
interface DiaDeCobroPort {

    /**
     * El día de cobro de la venta [ventaId] (`DOCTO_CC_ID`), o `null` si no hay
     * nada guardado para ella. Lee lo guardado: funciona sin señal.
     */
    suspend fun deLaVenta(ventaId: Int): DiaDeCobroDeLaVenta?
}
