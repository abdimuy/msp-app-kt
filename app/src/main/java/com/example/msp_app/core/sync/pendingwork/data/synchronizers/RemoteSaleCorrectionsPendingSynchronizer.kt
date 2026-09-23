package com.example.msp_app.core.sync.pendingwork.data.synchronizers

import com.example.msp_app.core.common.sync.pendingwork.domain.models.SyncContext
import com.example.msp_app.core.common.sync.pendingwork.domain.models.SyncResult
import com.example.msp_app.core.common.sync.pendingwork.domain.ports.PendingWorkSynchronizer
import com.example.msp_app.core.common.sync.pendingwork.domain.ports.RemoteSaleCorrectionsWorkEnqueuer
import com.example.msp_app.core.common.sync.pendingwork.domain.usecases.SyncAllPendingWorkUseCase.Companion.MAX_ITEMS_PER_SYNC
import com.example.msp_app.core.database.entities.LocalSaleEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * El barrido de sesión de la cola de correcciones remotas: en cada apertura de
 * la app reencola `RemoteSaleCorrectionWorker` por cada venta con
 * `CORRECCION_REMOTA_PENDIENTE = 1`.
 *
 * Gemelo exacto de [LocalSalesPendingSynchronizer] — misma forma, mismo tope
 * ([MAX_ITEMS_PER_SYNC]) y el mismo contrato: es la red de seguridad, no el
 * camino normal. La FILA manda; si este barrido no corriera, la corrección
 * seguiría pendiente y la entregaría el encolado que ya hizo `GuardarCorreccion`
 * en cuanto volviera la red.
 *
 * `fetchPending` se inyecta (en vez de recibir el DAO) para que el barrido no
 * conozca Room: el cableado real lo hace `PendingWorkSyncFactory` con
 * `LocalSaleDao.getVentasConCorreccionRemotaPendiente()`.
 */
class RemoteSaleCorrectionsPendingSynchronizer(
    private val fetchPending: suspend () -> List<LocalSaleEntity>,
    private val enqueuer: RemoteSaleCorrectionsWorkEnqueuer
) : PendingWorkSynchronizer {

    override val name: String = NAME

    override suspend fun sync(context: SyncContext): SyncResult = withContext(Dispatchers.IO) {
        val pending = fetchPending()
        if (pending.isEmpty()) return@withContext SyncResult.NothingPending

        // Sin correo no se encola nada: de él sale el almacén de origen del
        // cuerpo, y el worker rechaza el trabajo que llegue sin él. Mismo
        // criterio que el barrido de subidas.
        val email = context.userEmail
        if (email.isNullOrBlank()) return@withContext SyncResult.Skipped

        val capped = pending.take(MAX_ITEMS_PER_SYNC)
        var successCount = 0
        capped.forEach { sale ->
            runCatching { enqueuer.enqueue(sale.LOCAL_SALE_ID, email) }
                .onSuccess { successCount++ }
        }
        SyncResult.Enqueued(itemCount = capped.size, workRequestCount = successCount)
    }

    companion object {
        const val NAME: String = "REMOTE_SALE_CORRECTIONS"
    }
}
