package com.example.msp_app.data.diadecobro

import com.example.msp_app.feature.pagos.domain.DiaDeCobroDeLaVenta
import com.example.msp_app.feature.pagos.domain.port.DiaDeCobroPort

/**
 * [DiaDeCobroPort] sobre lo guardado en disco. **No toca la red**: sin señal
 * enseña lo último que se bajó, con la fecha en que el servidor lo calculó.
 *
 * Solo enseña lo guardado **de la zona actual** del cobrador ([zonaActual]): lo
 * de otra zona es de otro cobro y se calla. Si la zona actual aún no se conoce
 * (`null`), no hay con qué comparar y se enseña lo guardado: los `docto_cc_id`
 * son únicos, y la descarga de la zona nueva lo reemplaza.
 */
class DiaDeCobroAdapter(
    private val almacen: DiaDeCobroAlmacen,
    private val zonaActual: () -> Int? = { null }
) : DiaDeCobroPort {

    override suspend fun deLaVenta(ventaId: Int): DiaDeCobroDeLaVenta? {
        val guardado = almacen.leer() ?: return null
        val actual = zonaActual()
        if (actual != null && guardado.zona != actual) return null
        return guardado.respuesta.deLaVenta(ventaId)
    }
}
