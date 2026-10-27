package com.example.msp_app.core.sync.pendingwork.data.visits

import androidx.annotation.VisibleForTesting
import com.example.msp_app.core.common.sync.pendingwork.domain.ports.DeliveredVisitsStore
import com.example.msp_app.core.common.sync.pendingwork.domain.ports.FotoSinEnviar
import com.example.msp_app.core.database.dao.visit.VisitDao
import com.example.msp_app.core.database.dao.visit.VisitImageDao
import com.example.msp_app.core.visitas.envio.FotoQueViaja
import java.io.File
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
 *
 * Las dos escrituras entran por costuras [confirmarLote] y [devolverLote]
 * solo para que una prueba cuente el tamaño de cada lote: Robolectric usa el
 * SQLite de escritorio, sin el tope de 999, así que un `IN` sin trocear no
 * fallaría ahí (`COBRANZA-SYNC.md` §7.4).
 */
class RoomDeliveredVisitsStore @VisibleForTesting internal constructor(
    private val visitDao: VisitDao,
    private val imagenes: VisitImageDao,
    private val confirmarLote: suspend (List<String>, String) -> Int,
    private val devolverLote: suspend (List<String>) -> Int,
    private val archivoExiste: (String) -> Boolean = { File(it).exists() }
) : DeliveredVisitsStore {

    @Inject
    constructor(visitDao: VisitDao, imagenes: VisitImageDao) : this(
        visitDao = visitDao,
        imagenes = imagenes,
        confirmarLote = { ids, ahora -> visitDao.confirmar(ids, ahora) },
        devolverLote = { ids -> visitDao.devolverAPendiente(ids) }
    )

    override suspend fun entregadasDesde(desde: String): List<String> = visitDao.entregadasDesde(
        desde
    )

    override suspend fun confirmar(ids: List<String>, ahora: String): Int =
        ids.chunked(RoomPendingVisitsStore.SQLITE_MAX_IN_PARAMS).sumOf { confirmarLote(it, ahora) }

    override suspend fun devolverAPendiente(ids: List<String>): Int =
        ids.chunked(RoomPendingVisitsStore.SQLITE_MAX_IN_PARAMS).sumOf { devolverLote(it) }

    /**
     * Barrido (E-APP-079; legadas incluidas, E-VIS-031): solo las fotos que el envío de verdad mandaría, con
     * el MISMO criterio que `partesDeComprobantesDeVisita` (archivo presente y
     * tipo permitido). Una foto sin archivo o de un tipo que nunca viaja no
     * puede llegar al servidor: devolver su visita solo la reenviaría sin
     * ella, cada día.
     */
    override suspend fun fotosSinEnviarDeEntregadas(): List<FotoSinEnviar> =
        imagenes.pendientesDeEntregadas()
            .filter { FotoQueViaja.viaja(it.MIME, it.URI, archivoExiste) }
            .map { FotoSinEnviar(visitaId = it.VISITA_ID, creadaEn = it.CREADA_EN) }
}
