package com.example.msp_app.data.local.datasource.visit

import android.content.Context
import androidx.room.Transaction
import com.example.msp_app.data.local.AppDatabase
import com.example.msp_app.data.local.entities.VisitEntity
import com.example.msp_app.data.models.sale.EstadoCobranza

class VisitsLocalDataSource(private val context: Context) {
    private val visitDao = AppDatabase.getInstance(context).visitDao()
    private val saleDao = AppDatabase.getInstance(context).saleDao()

    suspend fun getVisitById(id: String): VisitEntity {
        return visitDao.getVisitById(id)
    }

    suspend fun saveVisit(visit: VisitEntity) {
        visitDao.insertVisit(visit)
    }

    suspend fun getPendingVisits(): List<VisitEntity> {
        return visitDao.getPendingVisits()
    }

    suspend fun getVisitsByDate(start: String, end: String): List<VisitEntity> {
        return visitDao.getVisitsByDate(start, end)
    }

    suspend fun updateVisitState(id: String, newState: Int) {
        visitDao.updateState(id, newState)
    }

    suspend fun updateVisitLocation(
        id: String,
        lat: Double,
        lng: Double
    ) {
        visitDao.updateLocation(id, lat, lng)
    }

    suspend fun changeVisitStatus(id: String, status: Boolean) {
        visitDao.updateState(
            id,
            if (status) 1 else 0
        )
    }

    @Transaction
    suspend fun insertVisitAndUpdateState(
        saleId: Int,
        visit: VisitEntity,
        newState: EstadoCobranza
    ) {
        visitDao.insertVisit(visit)
        saleDao.updateTotal(
            saleId,
            0.0,
            newState
        )
    }

    suspend fun updateTemporaryCollectionDate(
        saleId: Int,
        newDate: String,
    ) {
        saleDao.updateTemporaryCollectionDate(saleId, newDate)
    }

    suspend fun deleteAllVisits() {
        visitDao.deleteAllVisits()
    }
}