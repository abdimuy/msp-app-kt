package com.example.msp_app.features.sales.sync

import android.content.Context
import com.example.msp_app.core.database.entities.LocalSaleEntity
import com.example.msp_app.core.sync.BaseSyncHandler
import com.example.msp_app.core.sync.ConflictType
import com.example.msp_app.core.sync.MultipartRequest
import com.example.msp_app.core.sync.SyncConfig
import com.example.msp_app.core.sync.SyncContext
import com.example.msp_app.core.sync.SyncOperation
import com.example.msp_app.core.sync.SyncResult
import com.example.msp_app.core.sync.multipartRequest
import com.example.msp_app.core.sync.safeApiCall
import com.example.msp_app.data.api.ApiProvider
import com.example.msp_app.data.api.services.localSales.LocalSaleUpdateResponse
import com.example.msp_app.data.api.services.localSales.LocalSalesApi
import com.example.msp_app.data.local.datasource.sale.ComboLocalDataSource
import com.example.msp_app.data.local.datasource.sale.LocalSaleDataSource
import com.example.msp_app.data.local.datasource.sale.SaleProductLocalDataSource
import com.example.msp_app.data.models.sale.localsale.LocalSaleMappers
import java.io.File

/**
 * Datos preparados para sincronización de venta local.
 */
data class LocalSaleSyncData(
    val multipartRequest: MultipartRequest,
    val imagenesAEliminar: List<String> = emptyList(),
    val isUpdate: Boolean = false
)

/**
 * Se lanza cuando una venta llega al sync sin ningún producto. El backend
 * exige `productos` con al menos un elemento (ver `CrearVentaBody.Productos
 * minItems:1` y el invariante de dominio `ErrVentaProductosVacios` en
 * msp-api); subirla crearía una venta sin renglones en Microsip. Es una
 * `IllegalStateException` para que `BaseSyncWorker` la trate como fallo
 * transitorio y reintente en vez de subir la venta vacía.
 */
class EmptySaleProductsException(saleId: String) :
    IllegalStateException("venta $saleId sin productos: no se sube a Microsip")

/**
 * Handler de sincronización para ventas locales.
 * Soporta CREATE y UPDATE.
 */
class LocalSaleSyncHandler(
    private val localSaleDataSource: LocalSaleDataSource,
    private val productDataSource: SaleProductLocalDataSource,
    private val comboDataSource: ComboLocalDataSource,
    private val api: LocalSalesApi,
    private val mappers: LocalSaleMappers = LocalSaleMappers()
) : BaseSyncHandler<LocalSaleEntity, Any>() {

    override val entityType: String = "LOCAL_SALE"

    override val config: SyncConfig = SyncConfig.withAttachments("LOCAL_SALE")

    override suspend fun getEntity(context: Context, entityId: String): LocalSaleEntity? {
        return localSaleDataSource.getSaleById(entityId)
    }

    override suspend fun prepareRequest(
        context: Context,
        entity: LocalSaleEntity,
        operation: SyncOperation,
        syncContext: SyncContext
    ): Any {
        val products = productDataSource.getProductsForSale(entity.LOCAL_SALE_ID)
        val combos = comboDataSource.getCombosForSale(entity.LOCAL_SALE_ID)

        // GUARD money-path: nunca subir una venta sin productos. El backend la
        // rechaza (productos minItems:1 / dominio ErrVentaProductosVacios) y una
        // venta sin renglones en Microsip es pérdida/inconsistencia. Los
        // datasources ya NO tragan errores del DAO: un fallo real se propaga
        // hasta aquí y BaseSyncWorker lo reintenta. Este guard caza además el
        // caso de datos genuinamente sin productos. Los combos SÍ pueden ir
        // vacíos (una venta puede no llevar combos).
        if (products.isEmpty()) {
            throw EmptySaleProductsException(entity.LOCAL_SALE_ID)
        }

        val allImages = localSaleDataSource.getImagesForSale(entity.LOCAL_SALE_ID)
        val userEmail = syncContext.getString("user_email") ?: ""
        val isUpdate = operation is SyncOperation.Update

        // Para updates, obtener imágenes a eliminar y nuevas del syncContext
        val imagenesAEliminar = if (isUpdate) {
            syncContext.getString("imagenes_a_eliminar")
                ?.split(",")
                ?.filter { it.isNotBlank() }
                ?: emptyList()
        } else {
            emptyList()
        }

        // IDs de imágenes NUEVAS (solo estas se envían en UPDATE)
        val imagenesNuevasIds = if (isUpdate) {
            syncContext.getString("imagenes_nuevas")
                ?.split(",")
                ?.filter { it.isNotBlank() }
                ?.toSet()
                ?: emptySet()
        } else {
            emptySet()
        }

        // Para UPDATE: solo enviar imágenes nuevas
        // Para CREATE: enviar todas las imágenes
        val imagesToSend = if (isUpdate) {
            allImages.filter { it.LOCAL_SALE_IMAGE_ID in imagenesNuevasIds }
        } else {
            allImages
        }

        // Preparar el request JSON según el tipo de operación
        val jsonRequest = if (isUpdate) {
            with(mappers) {
                entity.toUpdateRequest(
                    products = products,
                    userEmail = userEmail,
                    imagenesAEliminar = imagenesAEliminar,
                    almacenOrigenId = syncContext.getString("almacen_origen_id")?.toIntOrNull(),
                    almacenDestinoId = syncContext.getString("almacen_destino_id")?.toIntOrNull(),
                    combos = combos
                )
            }
        } else {
            with(mappers) {
                entity.toServerRequest(products, userEmail, combos)
            }
        }

        // Construir multipart request solo con las imágenes que corresponden
        // Enviamos el UUID de cada imagen para que el servidor pueda identificarlas
        val multipart = multipartRequest {
            withJsonData(jsonRequest)
            imagesToSend.forEachIndexed { index, image ->
                val file = File(image.IMAGE_URI)
                if (file.exists()) {
                    addImage(file)
                    // Enviar el UUID de la imagen (id_0, id_1, etc.)
                    addImageId(index, image.LOCAL_SALE_IMAGE_ID)
                }
            }
        }

        return LocalSaleSyncData(
            multipartRequest = multipart,
            imagenesAEliminar = imagenesAEliminar,
            isUpdate = isUpdate
        )
    }

    override suspend fun executeSync(
        context: Context,
        entity: LocalSaleEntity,
        operation: SyncOperation,
        request: Any
    ): SyncResult<Any> {
        val syncData = request as LocalSaleSyncData
        val multipartRequest = syncData.multipartRequest

        return safeApiCall(config, ::detectConflictType) {
            if (syncData.isUpdate) {
                api.updateLocalSale(
                    localSaleId = entity.LOCAL_SALE_ID,
                    datos = multipartRequest.getJsonBody(),
                    imagenes = multipartRequest.getImageParts()
                )
            } else {
                api.saveLocalSale(
                    datos = multipartRequest.getJsonBody(),
                    imagenes = multipartRequest.getImageParts()
                )
            }
        }
    }

    override suspend fun onSyncSuccess(context: Context, entity: LocalSaleEntity, response: Any) {
        // Marcar como sincronizado
        localSaleDataSource.changeSaleStatus(entity.LOCAL_SALE_ID, true)

        // Si es update response, podríamos hacer algo con los datos adicionales
        if (response is LocalSaleUpdateResponse) {
            // Log de cambios si es necesario
        }
    }

    override suspend fun onSyncError(
        context: Context,
        entity: LocalSaleEntity,
        error: SyncResult.PermanentError
    ) {
        // Aquí podrías actualizar un campo de error en la entidad
        // localSaleDataSource.updateSyncError(entity.LOCAL_SALE_ID, error.message)
    }

    override suspend fun onConflict(
        context: Context,
        entity: LocalSaleEntity,
        conflict: SyncResult.Conflict
    ) {
        when (conflict.conflictType) {
            ConflictType.DUPLICATE -> {
                // Si ya existe, marcar como sincronizado
                localSaleDataSource.changeSaleStatus(entity.LOCAL_SALE_ID, true)
            }
            ConflictType.INSUFFICIENT_STOCK -> {
                // Notificar al usuario - podría guardarse en un campo de error
            }
            else -> {
                // Otros conflictos
            }
        }
    }

    override fun getAdditionalWorkerData(entity: LocalSaleEntity): Map<String, String> {
        return mapOf(
            "local_sale_id" to entity.LOCAL_SALE_ID
        )
    }

    private fun detectConflictType(errorBody: String?): ConflictType {
        if (errorBody == null) return ConflictType.OTHER

        return when {
            errorBody.contains(
                "STOCK_INSUFICIENTE",
                ignoreCase = true
            ) -> ConflictType.INSUFFICIENT_STOCK
            errorBody.contains("duplicado", ignoreCase = true) -> ConflictType.DUPLICATE
            else -> ConflictType.OTHER
        }
    }

    companion object {
        fun create(context: Context): LocalSaleSyncHandler {
            return LocalSaleSyncHandler(
                localSaleDataSource = LocalSaleDataSource(context),
                productDataSource = SaleProductLocalDataSource(context),
                comboDataSource = ComboLocalDataSource(context),
                api = ApiProvider.create(LocalSalesApi::class.java)
            )
        }
    }
}
