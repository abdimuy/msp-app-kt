package com.example.msp_app.workers

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.msp_app.data.api.ApiProvider
import com.example.msp_app.data.api.services.guarantee.GuaranteesApi
import com.example.msp_app.data.local.datasource.guarantee.GuaranteesLocalDataSource
import com.example.msp_app.data.models.guarantee.toApiRequest

class PendingGuaranteeEventsWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    private val guaranteesStore = GuaranteesLocalDataSource(appContext)
    private val api = ApiProvider.create(GuaranteesApi::class.java)

    override suspend fun doWork(): Result {
        val eventId = inputData.getString("event_id")
            ?: return Result.failure().also {
                Log.e("PendingGuaranteeEventsWorker", "No se proporcionó event_id")
            }

        return try {
            Log.d("PendingGuaranteeEventsWorker", "Procesando eventos pendientes...")

            val pendingEvents = guaranteesStore.getAllGuaranteeEvents().filter { it.ENVIADO == 0 }

            if (pendingEvents.isEmpty()) {
                Log.d("PendingGuaranteeEventsWorker", "No hay eventos pendientes")
                return Result.success()
            }

            for (event in pendingEvents) {
                try {
                    Log.d(
                        "PendingGuaranteeEventsWorker",
                        "Enviando evento: ID=${event.ID}, Tipo=${event.TIPO_EVENTO}, GarantiaID=${event.GARANTIA_ID}"
                    )

                    api.saveGuaranteeEvent(
                        garantiaId = event.GARANTIA_ID,
                        event = event.toApiRequest()
                    )

                    guaranteesStore.updateEventSentStatus(event.ID, 1)
                    Log.i(
                        "PendingGuaranteeEventsWorker",
                        "✅ Evento enviado exitosamente: ID=${event.ID}, Tipo=${event.TIPO_EVENTO}"
                    )

                } catch (e: Exception) {
                    Log.e(
                        "PendingGuaranteeEventsWorker",
                        "❌ Error enviando evento ID=${event.ID}, Tipo=${event.TIPO_EVENTO}: ${e.message}",
                        e
                    )
                }
            }

            Log.i(
                "PendingGuaranteeEventsWorker",
                "🎉 Procesamiento completado: ${pendingEvents.size} eventos procesados"
            )
            Result.success()
        } catch (e: Exception) {
            Log.e("PendingGuaranteeEventsWorker", "Error general enviando eventos", e)
            Result.retry()
        }
    }
}