package com.example.msp_app.feature.visitas.data.adapter

import com.example.msp_app.core.common.time.AppTime
import com.example.msp_app.core.database.dao.visit.VisitRecommendationDao
import com.example.msp_app.core.database.entities.VisitRecommendationEntity
import com.example.msp_app.feature.visitas.domain.model.RecomendacionMostrada
import com.example.msp_app.feature.visitas.domain.port.RecomendacionesPort

/**
 * Adaptador Room de [RecomendacionesPort] sobre `visita_recomendaciones` (tabla
 * de la migración aditiva de la Task 26; aquí no se define ninguna migración).
 *
 * Una fila con `GENERADA_EN` impresentable se descarta —la pantalla se abre sin
 * recomendación— y no se inventa un instante: un timestamp adivinado
 * contaminaría justo el dato con el que después se evalúa al recomendador.
 */
class RoomRecomendacionesAdapter(
    private val dao: VisitRecommendationDao
) : RecomendacionesPort {

    override suspend fun vigenteDe(clienteId: Int): RecomendacionMostrada? =
        dao.vigenteDe(clienteId)?.aRecomendacionMostrada()
}

private fun VisitRecommendationEntity.aRecomendacionMostrada(): RecomendacionMostrada? {
    val generada = AppTime.parseWireFormatOrNull(GENERADA_EN) ?: return null
    return RecomendacionMostrada(
        recomendacionId = ID,
        clienteId = CLIENTE_ID,
        ventaId = VENTA_ID,
        posicion = POSICION,
        motivo = MOTIVO,
        algoritmo = ALGORITMO,
        grupo = GRUPO,
        generadaEn = generada
    )
}
