package com.example.msp_app.data.ventacorreccion

import android.content.Context
import androidx.work.WorkManager
import com.example.msp_app.feature.ventacorreccion.domain.port.ReencolarSubidaPort
import com.example.msp_app.workmanager.enqueuePendingLocalSalesWorker

/**
 * Adapter real de [ReencolarSubidaPort] — vive en `:app` porque [reencolar] necesita
 * referenciar `PendingLocalSalesWorker` (vía [enqueuePendingLocalSalesWorker]), algo que
 * `:feature:ventaCorreccion` no puede hacer (dependencia unidireccional `:app` →
 * `:feature:ventaCorreccion`). El nombre de trabajo único (`sync_pending_local_sale_$id`) es el
 * mismo que usa
 * [com.example.msp_app.core.sync.pendingwork.data.enqueuers.LocalSalesWorkManagerEnqueuer] —
 * cancelar aquí y reencolar desde el barrido apuntan al mismo trabajo de WorkManager.
 */
class WorkManagerReencolarSubidaAdapter(
    private val context: Context
) : ReencolarSubidaPort {

    override fun cancelarTrabajoEncolado(saleId: String) {
        WorkManager.getInstance(context).cancelUniqueWork("sync_pending_local_sale_$saleId")
    }

    override fun reencolar(saleId: String, userEmail: String) {
        enqueuePendingLocalSalesWorker(context, saleId, userEmail, replace = true)
    }
}
