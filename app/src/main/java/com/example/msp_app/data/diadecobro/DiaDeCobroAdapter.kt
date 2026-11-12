package com.example.msp_app.data.diadecobro

import com.example.msp_app.feature.pagos.domain.DiaDeCobroDeLaVenta
import com.example.msp_app.feature.pagos.domain.port.DiaDeCobroPort

/**
 * [DiaDeCobroPort] sobre lo guardado en disco. **No toca la red**: sin señal
 * enseña lo último que se bajó, con la fecha en que el servidor lo calculó.
 */
class DiaDeCobroAdapter(private val almacen: DiaDeCobroAlmacen) : DiaDeCobroPort {

    override suspend fun deLaVenta(ventaId: Int): DiaDeCobroDeLaVenta? =
        almacen.leer()?.respuesta?.deLaVenta(ventaId)
}
