package com.example.msp_app.feature.collectionreport.data.adapter

import com.example.msp_app.core.common.time.AppTime
import com.example.msp_app.core.database.dao.ClienteDao
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
 *
 * `cliente` resuelve el NOMBRE real con un join de solo lectura a `cliente`: el
 * schema v27 de `Visit` NO guarda el nombre (solo `CLIENTE_ID`), a diferencia de
 * `Payment.NOMBRE_CLIENTE`. Se resuelve con UN solo query batch por `CLIENTE_ID`
 * distinto ([ClienteDao.getNombresByIds]) — mismo criterio anti-N+1 que
 * `RoomPaymentsAdapter.saleRefs` (nunca un `getById` por visita). Un
 * `CLIENTE_ID` que ya no está en `cliente` local cae a `"Cliente #<id>"` —
 * nunca se inventa un nombre.
 */
class RoomVisitsAdapter(
    private val visitDao: VisitDao,
    private val clienteDao: ClienteDao
) : VisitsPort {

    override suspend fun visitsIn(range: DateRange): List<CollectionVisit> {
        val visits = visitDao.getVisitsByDate(range.startIso, range.endExclusiveIso)
        val nombres = clienteNombres(visits)
        return visits.map { it.toCollectionVisit(nombres) }
    }

    /**
     * Resuelve el `NOMBRE` de cada `CLIENTE_ID` distinto de [visits] con UN solo query batch
     * (evita el N+1 de un `getById` por visita).
     */
    private suspend fun clienteNombres(visits: List<VisitEntity>): Map<Int, String> {
        val ids = visits.map { it.CLIENTE_ID }.distinct()
        if (ids.isEmpty()) return emptyMap()
        return clienteDao.getNombresByIds(ids).associate { it.CLIENTE_ID to it.NOMBRE }
    }
}

/**
 * Mapea una fila de `Visit` a dominio. `cliente` = NOMBRE real resuelto vía [nombres]
 * (fallback `"Cliente #<id>"` cuando el cliente ya no está en local — nunca inventado).
 * `NOTA` nulo -> cadena vacía. `tipo` = `TIPO_VISITA` tal cual (columna NOT NULL del schema
 * v27; fallback defensivo a cadena vacía si algún día llegara en blanco).
 */
private fun VisitEntity.toCollectionVisit(nombres: Map<Int, String>): CollectionVisit =
    CollectionVisit(
        id = ID,
        cliente = nombres[CLIENTE_ID] ?: "Cliente #$CLIENTE_ID",
        nota = NOTA ?: "",
        visitedAt = AppTime.parseWireFormat(FECHA),
        tipo = TIPO_VISITA.ifBlank { "" }
    )
