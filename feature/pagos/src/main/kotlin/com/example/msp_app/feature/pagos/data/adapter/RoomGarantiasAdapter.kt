package com.example.msp_app.feature.pagos.data.adapter

import com.example.msp_app.core.common.time.AppTime
import com.example.msp_app.core.database.dao.guarantee.GuaranteeDao
import com.example.msp_app.core.database.entities.GuaranteeEntity
import com.example.msp_app.feature.pagos.domain.model.EstadoDeGarantia
import com.example.msp_app.feature.pagos.domain.model.GarantiaDeLaVenta
import com.example.msp_app.feature.pagos.domain.port.GarantiasPort

/**
 * Adaptador Room de [GarantiasPort] sobre [GuaranteeDao.getGuaranteeByDoctoCcId]
 * — una query que **ya existía**: no se agregó nada a `:core:database` para
 * esto, ni columna ni índice ni consulta. Es el mismo movimiento de solo
 * lectura que ya se hizo para las visitas por cliente.
 *
 * `NOMBRE_PRODUCTO` es nullable en el schema; cuando falta se cae a la
 * descripción de la falla, que es lo que el cobrador reconoce, antes que pintar
 * una tarjeta sin título.
 *
 * `FECHA_SOLICITUD` se parsea con `parseWireFormatOrNull`: una fecha ilegible
 * deja la tarjeta **sin** fecha, pero NO tira la garantía — que exista una
 * garantía abierta es más importante que saber de qué día es, y la tarjeta se
 * sigue viendo.
 */
class RoomGarantiasAdapter(
    private val guaranteeDao: GuaranteeDao
) : GarantiasPort {

    override suspend fun garantiaDe(creditoId: Int): GarantiaDeLaVenta? =
        guaranteeDao.getGuaranteeByDoctoCcId(creditoId)?.aGarantiaDeLaVenta()
}

private fun GuaranteeEntity.aGarantiaDeLaVenta(): GarantiaDeLaVenta = GarantiaDeLaVenta(
    garantiaId = EXTERNAL_ID,
    producto = NOMBRE_PRODUCTO?.takeIf { it.isNotBlank() } ?: DESCRIPCION_FALLA,
    reportadaEl = AppTime.parseWireFormatOrNull(FECHA_SOLICITUD)?.let(AppTime::toBusinessDate),
    falla = DESCRIPCION_FALLA,
    estado = EstadoDeGarantia.de(ESTADO)
)
