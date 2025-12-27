package com.example.msp_app.features.sales.sync

import android.content.Context
import com.example.msp_app.core.sync.OfflineSyncManager
import com.example.msp_app.core.sync.SyncConfig
import com.example.msp_app.core.sync.SyncOperation

/**
 * Configuración de sincronización para ventas locales.
 */
val LocalSaleSyncConfig = SyncConfig.withAttachments("LOCAL_SALE")

/**
 * Encola una nueva venta local para sincronización (CREATE).
 */
fun enqueueLocalSaleCreate(
    context: Context,
    localSaleId: String,
    userEmail: String
) {
    OfflineSyncManager.enqueue<LocalSaleSyncWorker>(
        context = context,
        config = LocalSaleSyncConfig,
        entityId = localSaleId,
        operation = SyncOperation.Create(localSaleId, "LOCAL_SALE"),
        additionalData = mapOf(
            "user_email" to userEmail
        ),
        replaceExisting = false
    )
}

/**
 * Encola una actualización de venta local para sincronización (UPDATE).
 *
 * @param context Context de Android
 * @param localSaleId ID de la venta local
 * @param userEmail Email del usuario
 * @param imagenesAEliminar Lista de IDs de imágenes a eliminar en el servidor (opcional)
 * @param imagenesNuevas Lista de IDs de imágenes NUEVAS a enviar al servidor (opcional)
 * @param almacenOrigenId ID del almacén origen (opcional)
 * @param almacenDestinoId ID del almacén destino (opcional)
 */
fun enqueueLocalSaleUpdate(
    context: Context,
    localSaleId: String,
    userEmail: String,
    imagenesAEliminar: List<String> = emptyList(),
    imagenesNuevas: List<String> = emptyList(),
    almacenOrigenId: Int? = null,
    almacenDestinoId: Int? = null
) {
    val additionalData = mutableMapOf(
        "user_email" to userEmail
    )

    if (imagenesAEliminar.isNotEmpty()) {
        additionalData["imagenes_a_eliminar"] = imagenesAEliminar.joinToString(",")
    }

    if (imagenesNuevas.isNotEmpty()) {
        additionalData["imagenes_nuevas"] = imagenesNuevas.joinToString(",")
    }

    almacenOrigenId?.let {
        additionalData["almacen_origen_id"] = it.toString()
    }

    almacenDestinoId?.let {
        additionalData["almacen_destino_id"] = it.toString()
    }

    OfflineSyncManager.enqueue<LocalSaleSyncWorker>(
        context = context,
        config = LocalSaleSyncConfig,
        entityId = localSaleId,
        operation = SyncOperation.Update(localSaleId, "LOCAL_SALE"),
        additionalData = additionalData,
        replaceExisting = true  // Para updates, siempre usar la última versión
    )
}

/**
 * Cancela la sincronización pendiente de una venta.
 */
fun cancelLocalSaleSync(
    context: Context,
    localSaleId: String
) {
    OfflineSyncManager.cancel(context, LocalSaleSyncConfig, localSaleId)
}

/**
 * Obtiene el estado de sincronización de una venta.
 */
fun getLocalSaleSyncStatus(
    context: Context,
    localSaleId: String
) = OfflineSyncManager.getStatus(context, LocalSaleSyncConfig, localSaleId)

/**
 * Extension function para Context - encolar sincronización de venta.
 */
fun Context.enqueueLocalSaleSync(
    localSaleId: String,
    userEmail: String,
    isUpdate: Boolean = false,
    imagenesAEliminar: List<String> = emptyList(),
    almacenOrigenId: Int? = null,
    almacenDestinoId: Int? = null
) {
    if (isUpdate) {
        enqueueLocalSaleUpdate(
            context = this,
            localSaleId = localSaleId,
            userEmail = userEmail,
            imagenesAEliminar = imagenesAEliminar,
            almacenOrigenId = almacenOrigenId,
            almacenDestinoId = almacenDestinoId
        )
    } else {
        enqueueLocalSaleCreate(this, localSaleId, userEmail)
    }
}
