package com.example.msp_app.workers

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.msp_app.core.database.entities.GuaranteeImageEntity
import com.example.msp_app.data.api.ApiProvider
import com.example.msp_app.data.api.services.guarantee.GuaranteesApi
import com.example.msp_app.data.local.datasource.guarantee.GuaranteesLocalDataSource
import java.io.File
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody

class PendingGuaranteesWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    private val guaranteesStore = GuaranteesLocalDataSource(appContext)
    private val api = ApiProvider.create(GuaranteesApi::class.java)

    override suspend fun doWork(): Result {
        val externalId = inputData.getString("guarantee_external_id")
            ?: return Result.failure().also {
                Log.e("PendingGuaranteesWorker", "No se proporcionó guarantee_external_id")
            }

        val guarantee = guaranteesStore.getGuaranteeByExternalId(externalId)
            ?: return Result.failure().also {
                Log.e("PendingGuaranteesWorker", "Garantía no encontrada: $externalId")
            }

        return try {
            Log.d("PendingGuaranteesWorker", "Enviando garantía: ${guarantee.EXTERNAL_ID}")

            val images = guaranteesStore.getImagesByExternalId(externalId)
            Log.d("PendingGuaranteesWorker", "Encontradas ${images.size} imágenes para la garantía")

            val imageParts = buildImageParts(images)

            val textPlain = "text/plain".toMediaTypeOrNull()
            val externalIdBody = externalId.toRequestBody(textPlain)
            val descripcionFallaBody = guarantee.DESCRIPCION_FALLA.toRequestBody(textPlain)
            val observacionesBody = guarantee.OBSERVACIONES?.toRequestBody(textPlain)
            val doctoCcIdBody = guarantee.DOCTO_CC_ID?.toString()?.toRequestBody(textPlain)
            val nombreClienteBody = guarantee.NOMBRE_CLIENTE?.toRequestBody(textPlain)
            val nombreProductoBody = guarantee.NOMBRE_PRODUCTO?.toRequestBody(textPlain)

            api.createNewGuarantee(
                externalId = externalIdBody,
                doctoCcId = doctoCcIdBody,
                nombreCliente = nombreClienteBody,
                nombreProducto = nombreProductoBody,
                descripcionFalla = descripcionFallaBody,
                observaciones = observacionesBody,
                imagenes = imageParts
            )

            guaranteesStore.markGuaranteeAsUploaded(guarantee.EXTERNAL_ID)
            Result.success()
        } catch (e: Exception) {
            Log.e("PendingGuaranteesWorker", "Error al enviar garantía ${guarantee.EXTERNAL_ID}", e)
            Result.retry()
        }
    }

    private fun buildImageParts(images: List<GuaranteeImageEntity>): List<MultipartBody.Part> {
        return images.mapNotNull { image ->
            val file = File(image.IMG_PATH)
            if (file.exists()) {
                val requestFile = file.asRequestBody(image.IMG_MIME.toMediaTypeOrNull())
                val filename = "${image.ID}.${image.IMG_MIME.split("/").getOrNull(1) ?: "jpg"}"
                MultipartBody.Part.createFormData("imagenes", filename, requestFile)
            } else {
                Log.w(
                    "PendingGuaranteesWorker",
                    "Archivo de imagen no encontrado: ${image.IMG_PATH}"
                )
                null
            }
        }
    }
}
