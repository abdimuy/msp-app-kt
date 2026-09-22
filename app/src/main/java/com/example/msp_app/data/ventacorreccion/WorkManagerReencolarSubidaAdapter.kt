package com.example.msp_app.data.ventacorreccion

import android.content.Context
import androidx.work.WorkManager
import com.example.msp_app.feature.ventacorreccion.domain.port.ReencolarSubidaPort
import com.example.msp_app.workmanager.enqueuePendingLocalSalesWorker
import com.example.msp_app.workmanager.enqueueRemoteSaleCorrectionWorker
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
     *
     * **Nivel 2 (corrección DESPUÉS de subir): se encolan los DOS caminos, siempre.** Este
     * adapter no sabe —ni debe saber— si la venta ya se envió: `GuardarCorreccion` lo llama
     * igual en los dos casos, y la fila es la única que tiene la respuesta. Así que encola las
     * dos colas y cada worker decide al leer la fila: el subidor devuelve `success` sin tocar
     * la red si la venta ya está `ENVIADO = 1` (su `claimForUpload` no reclama una venta
     * enviada), y `RemoteSaleCorrectionWorker` hace lo simétrico si la venta todavía no sale
     * (su `claimForRemote` exige `ENVIADO = 1`). El costo de la cola que no aplica es una
     * lectura de una fila de SQLite.
     *
     * Y encolar acá es lo que da el disparador "al recuperar conectividad": el trabajo queda
     * retenido por la restricción `NetworkType.CONNECTED` y WorkManager lo suelta solo cuando
     * vuelve la señal — que es el caso normal de una corrección hecha en la calle. El barrido
     * de sesión (`RemoteSaleCorrectionsPendingSynchronizer`) es la red de seguridad del otro
     * disparador, al abrir la app. Igual que arriba, esto sigue siendo optimización: la fila
     * manda.
     */
    override fun reencolar(saleId: String, userEmail: String) {
        enqueuePendingLocalSalesWorker(context, saleId, userEmail)
        enqueueRemoteSaleCorrectionWorker(context, saleId, userEmail)
    }
}
