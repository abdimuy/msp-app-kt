package com.example.msp_app.workers

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.msp_app.data.api.ApiProvider
import com.example.msp_app.data.api.services.guarantee.GuaranteesApi
import com.example.msp_app.data.local.datasource.guarantee.GuaranteesLocalDataSource
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File

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

            val externalIdBody = externalId.toRequestBody("text/plain".toMediaTypeOrNull())
            val descripcionFallaBody =
                guarantee.DESCRIPCION_FALLA.toRequestBody("text/plain".toMediaTypeOrNull())
            val observacionesBody =
                guarantee.OBSERVACIONES?.toRequestBody("text/plain".toMediaTypeOrNull())

            val imageParts = images.mapNotNull { image ->
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

            api.saveGuaranteeWithImages(
                doctoCcId = guarantee.DOCTO_CC_ID,
                externalId = externalIdBody,
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
}