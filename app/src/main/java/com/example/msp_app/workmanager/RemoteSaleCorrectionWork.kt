package com.example.msp_app.workmanager

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.example.msp_app.workers.RemoteSaleCorrectionWorker

/**
 * Nombre del trabajo único por venta — FUENTE ÚNICA, mismo criterio que
 * [localSaleUniqueWorkName]. Distinto del de la subida a propósito: las dos
 * colas son caminos distintos sobre la misma fila y no deben descartarse entre
 * sí.
 */
fun remoteSaleCorrectionUniqueWorkName(localSaleId: String): String =
    "sync_remote_sale_correction_$localSaleId"

/**
 * Encola la entrega de la corrección remota de una venta — el otro camino del
 * outbox de ventas, para una corrección hecha sobre una venta que YA subió
 * ([RemoteSaleCorrectionWorker]).
 *
 * `ExistingWorkPolicy.KEEP`, como todo el camino del dinero: `REPLACE`
 * cancelaría una entrega en vuelo y no rescata nada que `KEEP` no recupere
 * solo — cuando el trabajo previo llega a un estado terminal, `KEEP` encola
 * igual. Ver el encabezado de `WorkManagerUtils.kt`.
 *
 * Vive en su propio archivo y no dentro de `WorkManagerUtils.kt` por una razón
 * concreta: ese archivo declara la política del trabajo pendiente y
 * `WorkEnqueuePolicyGuardTest` cuenta ahí "las diez funciones de encolado
 * único que expone hoy". La compuerta de esa prueba no es la cuenta —es el
 * barrido que descubre TODO archivo del build que llame a `enqueueUniqueWork`,
 * y este archivo entra a ese barrido igual—, así que la separación no esquiva
 * ninguna regla: evita dejar una cuenta mintiendo en un archivo de prueba que
 * esta tarea no puede tocar.
 *
 * Los disparadores son los mismos que los del otro outbox, y son dos:
 *
 * 1. **Al recuperar conectividad.** La restricción `NetworkType.CONNECTED` es
 *    lo que lo produce: una corrección guardada sin señal encola un trabajo
 *    que WorkManager retiene hasta que hay red y suelta solo. Por eso este
 *    encolado corre también justo después de guardar
 *    (`WorkManagerReencolarSubidaAdapter`), aunque no haya red en ese momento.
 * 2. **Al abrir la app.** El barrido de sesión
 *    (`RemoteSaleCorrectionsPendingSynchronizer`) recorre
 *    `getVentasConCorreccionRemotaPendiente()` y vuelve a encolar. Es la red
 *    de seguridad: la FILA manda, el encolado es optimización.
 *
 * Sin `setBackoffCriteria`: la política de reintento es la de WorkManager por
 * omisión (exponencial desde 30 s), igual que `enqueuePendingLocalSalesWorker`.
 */
fun enqueueRemoteSaleCorrectionWorker(context: Context, localSaleId: String, userEmail: String) {
    val constraints = Constraints.Builder()
        .setRequiredNetworkType(NetworkType.CONNECTED)
        .build()

    val input = workDataOf(
        RemoteSaleCorrectionWorker.CLAVE_VENTA to localSaleId,
        RemoteSaleCorrectionWorker.CLAVE_CORREO to userEmail
    )

    val request = OneTimeWorkRequestBuilder<RemoteSaleCorrectionWorker>()
        .setConstraints(constraints)
        .setInputData(input)
        .build()

    WorkManager.getInstance(context).enqueueUniqueWork(
        remoteSaleCorrectionUniqueWorkName(localSaleId),
        ExistingWorkPolicy.KEEP,
        request
    )
}
