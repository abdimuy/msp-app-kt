package com.example.msp_app.workers

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.msp_app.data.api.ApiProvider
import com.example.msp_app.data.api.services.visits.VisitsApi
import com.example.msp_app.data.local.datasource.visit.VisitsLocalDataSource
import com.example.msp_app.data.models.visit.toDomain

class PendingVisitsWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    private val visitsStore = VisitsLocalDataSource(appContext)
    private val api = ApiProvider.create(VisitsApi::class.java)

    override suspend fun doWork(): Result {
        val id = inputData.getString("visit_id")
            ?: return Result.failure().also {
                Log.e("PendingVisitsWorker", "No se proporcionó visit_id")
            }

        val visit = visitsStore.getVisitById(id)
            ?: return Result.failure().also {
                Log.e("PendingVisitsWorker", "Visita no encontrada: $id")
            }

        return try {
            Log.d("PendingVisitsWorker", "Enviando visita: ${visit.ID}")
            api.saveVisit(visit.toDomain())
            visitsStore.changeVisitStatus(visit.ID, true)
            Log.d("PendingVisitsWorker", "Visita marcada como enviada: ${visit.ID}")
            Result.success()
        } catch (e: Exception) {
            Log.e("PendingVisitsWorker", "Error al enviar visita ${visit.ID}", e)
            Result.retry()
        }
    }
}