package com.example.msp_app.feature.collectionreport.data.adapter

import com.example.msp_app.core.common.time.AppTime
import com.example.msp_app.core.database.dao.visit.VisitDao
import com.example.msp_app.core.database.entities.VisitEntity
import com.example.msp_app.feature.collectionreport.domain.model.CollectionVisit
import com.example.msp_app.feature.collectionreport.domain.model.DateRange
import com.example.msp_app.feature.collectionreport.domain.port.VisitsPort

/**
 * Adapter Room de [VisitsPort] sobre [VisitDao] (schema v27, inmutable).
 * [VisitDao.getVisitsByDate] usa el mismo rango medio-abierto `>= :start AND
 * < :end` sobre `Visit.FECHA` (wire RFC3339 UTC), verificado por
 * `VisitDateRangeHalfOpenTest`.
 */
class RoomVisitsAdapter(
    private val visitDao: VisitDao
) : VisitsPort {

    override suspend fun visitsIn(range: DateRange): List<CollectionVisit> = visitDao
        .getVisitsByDate(range.startIso, range.endExclusiveIso)
        .map { it.toCollectionVisit() }
}

/**
 * Mapea una fila de `Visit` a dominio. `cliente` usa `CLIENTE_ID`: el schema
 * v27 de `Visit` NO guarda el nombre del cliente (solo su id), a diferencia de
 * `Payment.NOMBRE_CLIENTE`; enriquecer el nombre requeriría un join con
 * `Cliente` (deferido, YAGNI). `NOTA` nulo -> cadena vacía.
 */
private fun VisitEntity.toCollectionVisit(): CollectionVisit = CollectionVisit(
    id = ID,
    cliente = CLIENTE_ID.toString(),
    nota = NOTA ?: "",
    visitedAt = AppTime.parseWireFormat(FECHA)
)
