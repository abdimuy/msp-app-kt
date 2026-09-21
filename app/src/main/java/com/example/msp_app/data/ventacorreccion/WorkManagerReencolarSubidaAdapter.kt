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

    /**
     * **Integración (2026-09-21): ya no se reencola con reemplazo.** La rama de pagos y visitas
     * borró el parámetro `replace` de las cinco funciones de trabajo pendiente y clavó
     * `ExistingWorkPolicy.KEEP` dentro de ellas — el camino del dinero no puede cancelar una
     * subida en vuelo, porque `REPLACE` la mata y arriesga un envío duplicado. La compuerta que
     * lo sostiene es `WorkEnqueuePolicyGuardTest`, que además barre los call sites buscando un
     * booleano que pida otra política.
     *
     * El cambio no toca la correctitud de la corrección, y eso está escrito en el contrato del
     * propio puerto: [ReencolarSubidaPort] dice que reencolar es SIEMPRE una optimización y
     * NUNCA un requisito — "la fila manda". Y en el camino real las dos políticas hacen lo
     * mismo: al RECLAMAR ya se llamó a [cancelarTrabajoEncolado], así que el trabajo previo
     * está en un estado terminal (`CANCELLED`) y `KEEP` encola igual que `REPLACE`. La única
     * diferencia aparece cuando hay un trabajo VIVO bajo el mismo nombre; ahí `KEEP` descarta
     * este encolado, y ese trabajo vivo sube la fila YA corregida, porque
     * `PendingLocalSalesWorker` lee la venta de Room al ejecutarse, no al encolarse.
     */
    override fun reencolar(saleId: String, userEmail: String) {
        enqueuePendingLocalSalesWorker(context, saleId, userEmail)
    }
}
