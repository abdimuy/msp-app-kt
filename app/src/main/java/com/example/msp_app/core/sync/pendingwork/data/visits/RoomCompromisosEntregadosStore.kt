package com.example.msp_app.core.sync.pendingwork.data.visits

import androidx.annotation.VisibleForTesting
import com.example.msp_app.core.common.sync.pendingwork.domain.ports.CompromisosEntregadosStore
import com.example.msp_app.core.common.sync.pendingwork.domain.ports.EntregadaConCompromiso
import com.example.msp_app.core.common.visitas.envio.CompromisoLocal
import com.example.msp_app.core.database.dao.visit.VisitDao
import javax.inject.Inject

/**
 * Adapter de [CompromisosEntregadosStore] sobre la tabla `Visit` (plan visitas
 * P2, Tarea 18; E-APP-132). Lee con [VisitDao.entregadasConCompromisoDesde] (un
 * SELECT) y devuelve a pendiente con [VisitDao.devolverAPendiente], la misma
 * escritura de la inversa de P1; no hay consulta de escritura nueva ni se toca
 * ninguna tabla de dinero. Trocea cada `IN` por debajo del tope de SQLite, como
 * [RoomDeliveredVisitsStore].
 */
class RoomCompromisosEntregadosStore @VisibleForTesting internal constructor(
    private val visitDao: VisitDao,
    private val devolverLote: suspend (List<String>) -> Int
) : CompromisosEntregadosStore {

    @Inject
    constructor(visitDao: VisitDao) : this(
        visitDao = visitDao,
        devolverLote = { ids -> visitDao.devolverAPendiente(ids) }
    )

    override suspend fun entregadasConCompromisoDesde(desde: String): List<EntregadaConCompromiso> =
        visitDao.entregadasConCompromisoDesde(desde).map {
            EntregadaConCompromiso(
                visitaId = it.ID,
                compromiso = CompromisoLocal(
                    promesaVentaId = it.PROMESA_VENTA_ID,
                    promesaFecha = it.PROMESA_FECHA,
                    promesaMontoCentavos = it.PROMESA_MONTO_CENTAVOS,
                    citaFecha = it.CITA_FECHA,
                    citaHora = it.CITA_HORA
                )
            )
        }

    override suspend fun devolverAPendiente(ids: List<String>): Int =
        ids.chunked(RoomPendingVisitsStore.SQLITE_MAX_IN_PARAMS).sumOf { devolverLote(it) }
}
