package com.example.msp_app.core.sync.pendingwork.data.visits

import com.example.msp_app.core.common.sync.pendingwork.domain.ports.DeliveredVisitsStore
import com.example.msp_app.core.database.dao.visit.VisitDao
import javax.inject.Inject

/**
 * Adapter de [DeliveredVisitsStore] sobre la tabla `Visit` de Room
 * (reconciliación inversa, plan visitas P1 Tarea 13).
 *
 * Solo traduce: [VisitDao.entregadasDesde], [VisitDao.confirmar] y
 * [VisitDao.devolverAPendiente] son las consultas de la Tarea 8. Trocea cada
 * `IN (:ids)` por debajo del tope de SQLite (999 parámetros antes de API 31;
 * `minSdk` 24) igual que [RoomPendingVisitsStore], aunque el use case ya
 * entrega tandas de 100: el tope es real y barato de guardar.
 */
class RoomDeliveredVisitsStore @Inject constructor(
    private val visitDao: VisitDao
) : DeliveredVisitsStore {

    override suspend fun entregadasDesde(desde: String): List<String> = visitDao.entregadasDesde(
        desde
    )

    override suspend fun confirmar(ids: List<String>, ahora: String): Int = ids.chunked(
        RoomPendingVisitsStore.SQLITE_MAX_IN_PARAMS
    ).sumOf { visitDao.confirmar(it, ahora) }

    override suspend fun devolverAPendiente(ids: List<String>): Int = ids.chunked(
        RoomPendingVisitsStore.SQLITE_MAX_IN_PARAMS
    ).sumOf { visitDao.devolverAPendiente(it) }
}
