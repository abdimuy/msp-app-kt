package com.example.msp_app.data.ventacorreccion

import android.content.Context
import androidx.work.WorkManager
import com.example.msp_app.feature.ventacorreccion.domain.port.ReencolarSubidaPort
import com.example.msp_app.workmanager.enqueuePendingLocalSalesWorker
import com.example.msp_app.workmanager.localSaleUniqueWorkName

/**
 * Adapter real de [ReencolarSubidaPort] — vive en `:app` porque [reencolar] necesita
 * referenciar `PendingLocalSalesWorker` (vía [enqueuePendingLocalSalesWorker]), algo que
 * `:feature:ventaCorreccion` no puede hacer (dependencia unidireccional `:app` →
 * `:feature:ventaCorreccion`). El nombre de trabajo único viene de [localSaleUniqueWorkName]
 * (Minor #4 de la ronda 1 de arreglo — antes era un literal duplicado aquí y en
 * `WorkManagerUtils.kt`): cancelar aquí y reencolar desde el barrido apuntan al mismo trabajo de
 * WorkManager, por construcción, no por dos literales que alguien tiene que mantener iguales.
 */
class WorkManagerReencolarSubidaAdapter(
    private val context: Context
) : ReencolarSubidaPort {

    override fun cancelarTrabajoEncolado(saleId: String) {
        WorkManager.getInstance(context).cancelUniqueWork(localSaleUniqueWorkName(saleId))
    }

    override fun reencolar(saleId: String, userEmail: String) {
        enqueuePendingLocalSalesWorker(context, saleId, userEmail, replace = true)
    }
}
