package com.example.msp_app.features.sales.sync

import android.content.Context
import androidx.work.WorkerParameters
import com.example.msp_app.core.sync.BaseSyncWorker
import com.example.msp_app.core.sync.SyncHandler
import com.example.msp_app.data.local.entities.LocalSaleEntity

/**
 * Worker para sincronizar ventas locales (CREATE y UPDATE).
 * Extiende BaseSyncWorker y solo necesita proveer el handler específico.
 */
class LocalSaleSyncWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : BaseSyncWorker<LocalSaleEntity, Any>(appContext, workerParams) {

    // Usamos el key por defecto "entity_id" que es el que usa OfflineSyncManager

    override fun createHandler(): SyncHandler<LocalSaleEntity, Any> {
        return LocalSaleSyncHandler.create(applicationContext)
    }
}
